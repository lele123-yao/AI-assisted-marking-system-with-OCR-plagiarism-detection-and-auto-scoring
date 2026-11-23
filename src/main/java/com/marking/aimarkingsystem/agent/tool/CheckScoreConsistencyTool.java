package com.marking.aimarkingsystem.agent.tool;

import cn.hutool.json.JSONObject;
import com.marking.aimarkingsystem.answer.entity.ExamAnswerItem;
import com.marking.aimarkingsystem.paper.entity.ExamQuestion;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具：分数一致性校验。
 *
 * 解决的问题：大模型经常"评语写扣 2 分、总分却没扣"，教师看评语和分数对不上。
 * 这个工具把这类矛盾显式指出来，让主 Agent 在下一轮自己修正，也给 Checker 提供依据。
 *
 * 校验的是"算术一致性"，不判断评分是否正确——那是教师和 Checker 的事：
 * 1. 分数是否落在 [0, 满分]；
 * 2. 评语里显式写的"总分/该题得分"是否与记录分数一致；
 * 3. 评语里写的"共扣 X 分"或若干处"扣 X 分"合计，是否等于 满分 - 分数；
 * 4. 0 分却写"正确/完整"，或非 0 分却写"未作答/空白"这类明显矛盾。
 *
 * 全部用正则做，零额外依赖，规则可解释，面试时可以逐条说明。
 */
@Component
public class CheckScoreConsistencyTool implements AgentTool {

    /** 显式的扣分合计，例如"共扣 3 分""合计扣2分" */
    private static final Pattern DEDUCT_TOTAL = Pattern.compile(
            "(?:共扣|合计扣|总计扣|总共扣|一共扣)\\s*(\\d+(?:\\.\\d+)?)\\s*分");

    /** 单处扣分，前面排除"不扣/未扣/没扣/无扣"，避免把"不扣分"算进去 */
    private static final Pattern DEDUCT_ONE = Pattern.compile(
            "(?<![不未没无])扣\\s*(\\d+(?:\\.\\d+)?)\\s*分");

    /** 评语里显式写的本题总分，兼容"该题得7分""本题得分7分""总分：7分"等写法 */
    private static final Pattern STATED_SCORE = Pattern.compile(
            "(?:总分|合计得分|总得分|该题得分|本题得分|最终得分"
                    + "|该题得|本题得|最终得|合计得|该题|本题)"
                    + "\\s*(?:为|是|：|:)?\\s*(\\d+(?:\\.\\d+)?)\\s*分");

    private static final Pattern ZERO_HINT = Pattern.compile("未作答|没有作答|空白|未写|没写|完全错误|零分|0\\s*分");

    private static final Pattern FULL_HINT = Pattern.compile("完全正确|全部正确|全对|满分|思路完整|过程完整");

    private final AnswerContextProvider contextProvider;

    public CheckScoreConsistencyTool(AnswerContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    @Override
    public String name() {
        return "check_score_consistency";
    }

    @Override
    public String description() {
        return "校验某题当前评分与评语是否自相矛盾（分数越界、评语写了扣分但总分没扣、0分却写全对等）";
    }

    @Override
    public String argumentHint() {
        return "{\"answerId\": 数字, \"questionId\": 数字}";
    }

    @Override
    public List<String> requiredArgs() {
        return List.of("answerId", "questionId");
    }

    @Override
    public String execute(JSONObject input) {
        Long answerId = input.getLong("answerId");
        Long questionId = input.getLong("questionId");
        ExamQuestion question = contextProvider.questionOf(answerId, questionId);
        ExamAnswerItem item = contextProvider.itemOf(answerId, questionId);
        if (item == null) {
            return "该题还没有评分记录，无法做一致性校验。请先基于评分细则给出初评。";
        }

        BigDecimal fullScore = question.getScore() == null ? new BigDecimal("100") : question.getScore();

        // 有教师定稿就校验定稿，否则校验 AI 初评，并在结果里标明校验对象
        boolean useFinal = StringUtils.hasText(item.getFinalComment());
        BigDecimal score = useFinal ? item.getFinalScore() : item.getAiScore();
        String comment = useFinal ? item.getFinalComment() : item.getAiComment();

        StringBuilder sb = new StringBuilder();
        sb.append("【分数一致性校验】\n");
        sb.append("校验对象：").append(useFinal ? "教师定稿" : "AI 初评").append("\n");
        sb.append("本题满分：").append(plain(fullScore)).append("；记录分数：")
                .append(plain(score)).append("\n");
        sb.append("评语：").append(StringUtils.hasText(comment) ? comment : "（空）").append("\n\n");

        List<String> issues = new ArrayList<>();
        if (score == null) {
            issues.add("分数为空，无法校验");
        } else {
            if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(fullScore) > 0) {
                issues.add("分数 " + plain(score) + " 超出 [0, " + plain(fullScore) + "] 区间");
            }
            if (!StringUtils.hasText(comment)) {
                issues.add("评语为空，无法说明扣分依据");
            } else {
                checkStatedScore(comment, score, issues);
                checkDeductions(comment, score, fullScore, issues);
                checkZeroOrFull(comment, score, fullScore, issues);
            }
        }

        if (issues.isEmpty()) {
            sb.append("结论：一致。未发现分数与评语的算术矛盾。\n");
            sb.append("说明：本工具只做算术核对，不判断评分点是否给得合理。");
            return sb.toString();
        }
        sb.append("结论：发现 ").append(issues.size()).append(" 处矛盾\n");
        for (int i = 0; i < issues.size(); i++) {
            sb.append(i + 1).append(". ").append(issues.get(i)).append("\n");
        }
        sb.append("建议：请核对后修正分数或评语，保证二者能互相解释。");
        return sb.toString();
    }

    /** 评语里如果自己写了总分，必须与记录分数一致 */
    private void checkStatedScore(String comment, BigDecimal score, List<String> issues) {
        Matcher matcher = STATED_SCORE.matcher(comment);
        if (matcher.find()) {
            BigDecimal stated = new BigDecimal(matcher.group(1));
            if (stated.compareTo(score) != 0) {
                issues.add("评语写明本题得分 " + plain(stated) + " 分，但记录分数是 " + plain(score) + " 分");
            }
        }
    }

    /** 扣分合计 vs（满分 - 分数），这是"写了扣2分但没扣"最典型的检测 */
    private void checkDeductions(String comment, BigDecimal score, BigDecimal fullScore, List<String> issues) {
        BigDecimal totalDeduct = null;
        Matcher total = DEDUCT_TOTAL.matcher(comment);
        if (total.find()) {
            totalDeduct = new BigDecimal(total.group(1));
        } else {
            // 没有"共扣"时，把各处"扣 X 分"累加；本可扣但未扣的表述很少见，先按简单规则处理
            BigDecimal sum = BigDecimal.ZERO;
            Matcher one = DEDUCT_ONE.matcher(comment);
            while (one.find()) {
                sum = sum.add(new BigDecimal(one.group(1)));
            }
            if (sum.compareTo(BigDecimal.ZERO) > 0) {
                totalDeduct = sum;
            }
        }
        if (totalDeduct == null) {
            return;
        }
        BigDecimal expected = fullScore.subtract(totalDeduct);
        if (expected.compareTo(score) != 0) {
            issues.add("评语累计扣 " + plain(totalDeduct) + " 分，按满分 " + plain(fullScore)
                    + " 应为 " + plain(expected) + " 分，但记录分数是 " + plain(score) + " 分");
        }
    }

    /** 0 分写全对、非 0 分写未作答，都是明显的前后矛盾 */
    private void checkZeroOrFull(String comment, BigDecimal score, BigDecimal fullScore, List<String> issues) {
        if (score.compareTo(BigDecimal.ZERO) == 0 && FULL_HINT.matcher(comment).find()) {
            issues.add("记录分数为 0，但评语出现“完全正确/满分”等表述");
        }
        if (score.compareTo(BigDecimal.ZERO) > 0 && ZERO_HINT.matcher(comment).find()) {
            issues.add("记录分数为 " + plain(score) + "，但评语出现“未作答/空白/零分”等表述");
        }
        if (score.compareTo(fullScore) == 0 && DEDUCT_ONE.matcher(comment).find()) {
            issues.add("记录分数为满分，但评语仍然写了扣分");
        }
    }

    private String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
