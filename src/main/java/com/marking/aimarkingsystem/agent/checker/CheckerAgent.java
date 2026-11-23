package com.marking.aimarkingsystem.agent.checker;

import com.marking.aimarkingsystem.agent.core.ScoreDraft;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checker 自检 Agent（Reflexion 里的"反思者"角色）。
 *
 * 关键边界：
 * - 不调用任何工具，不查数据库，只看主 Agent 的"分数 + 评语"和本题评分细则；
 * - 只做规则校验，不做语义打分，因此没有"最终打分权"；
 * - Checker 自己出错时只影响是否发起修正，不会覆盖主 Agent 的原始初评
 *   （Service 层在修正失败时会保留第一版结果）。
 *
 * 为什么用规则而不是再调一次大模型：
 * - 分数越界、扣分算术、得分点编号这类问题是确定性的，规则 100% 可复现；
 * - 再调一次模型等于让模型"自己检查自己"，既增加成本，也可能一起编；
 * - 规则检查可以逐条讲给面试官听，不存在"黑盒判定"。
 *
 * 三类规则（对应用户需求）：
 * 1. 分数区间：必须在 [0, 满分]；
 * 2. 依据匹配：评语必须引用评分细则中的得分点，不能出现细则里不存在的编号；
 * 3. 前后一致：评语里写的得分/扣分要与分数算得上，不能"写了扣 2 分但没扣"。
 */
@Component
public class CheckerAgent {

    /** 显式扣分合计，例如"共扣 3 分" */
    private static final Pattern DEDUCT_TOTAL = Pattern.compile(
            "(?:共扣|合计扣|总计扣|总共扣|一共扣)\\s*(\\d+(?:\\.\\d+)?)\\s*分");

    /** 单处扣分，排除"不扣/未扣/没扣/无扣" */
    private static final Pattern DEDUCT_ONE = Pattern.compile(
            "(?<![不未没无])扣\\s*(\\d+(?:\\.\\d+)?)\\s*分");

    /** 评语里显式写的本题得分，兼容"该题得7分""本题得分7分""总分：7分"等写法 */
    private static final Pattern STATED_SCORE = Pattern.compile(
            "(?:总分|合计得分|总得分|该题得分|本题得分|最终得分"
                    + "|该题得|本题得|最终得|合计得|该题|本题)"
                    + "\\s*(?:为|是|：|:)?\\s*(\\d+(?:\\.\\d+)?)\\s*分");

    /** 引用的得分点编号 */
    private static final Pattern POINT_REF = Pattern.compile("(?:得分点|要点)\\s*(\\d+)");

    /** 评分细则的得分点分隔 */
    private static final Pattern POINT_SPLIT = Pattern.compile(
            "\\r?\\n|;|；|①|②|③|④|⑤|⑥|⑦|⑧|⑨|⑩");

    private static final Pattern TOKEN = Pattern.compile("[\\u4e00-\\u9fa5]+|[a-zA-Z0-9]+");

    /** 与评分点统计工具一致的虚词表，避免"写出/正确"这类词造成假命中 */
    private static final List<String> NOISE_WORDS = List.of(
            "写出", "求出", "说明", "计算", "得出", "给出", "正确", "完整", "答案",
            "过程", "步骤", "结果", "能够", "可以", "要求", "必须", "需要", "以及",
            "要点", "得分点", "分值", "给分", "评分", "若", "则", "且");

    /**
     * 校验一版评分。
     *
     * @param draft       主 Agent 的评分草稿
     * @param fullScore   本题满分
     * @param scoringRule 本题评分细则，可为空
     */
    public CheckResult check(ScoreDraft draft, BigDecimal fullScore, String scoringRule) {
        if (draft == null || draft.score() == null) {
            return CheckResult.fail(List.of("没有解析出分数，请输出 JSON：{\"score\": 数字, \"comment\": \"依据\"}"));
        }
        BigDecimal score = draft.score();
        String comment = draft.comment() == null ? "" : draft.comment().trim();
        BigDecimal full = fullScore == null ? new BigDecimal("100") : fullScore;

        List<String> issues = new ArrayList<>();

        // 规则 1：分数区间
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(full) > 0) {
            issues.add("分数 " + plain(score) + " 超出 [0, " + plain(full) + "] 区间");
        }

        // 规则 2：评语基本质量
        if (!StringUtils.hasText(comment)) {
            issues.add("评语为空，必须写明命中的得分点和扣分依据");
        } else if (comment.length() < 10) {
            issues.add("评语过短（" + comment.length() + " 字），不足以说明得分依据");
        }

        if (StringUtils.hasText(comment)) {
            // 规则 3：评语与分数是否算得上
            checkStatedScore(comment, score, issues);
            checkDeductions(comment, score, full, issues);
            checkContradiction(comment, score, full, issues);

            // 规则 4：依据必须来自评分细则
            checkScoringRule(comment, scoringRule, issues);
        }

        return issues.isEmpty() ? CheckResult.ok() : CheckResult.fail(issues);
    }

    // ==================== 规则实现 ====================

    private void checkStatedScore(String comment, BigDecimal score, List<String> issues) {
        Matcher matcher = STATED_SCORE.matcher(comment);
        if (matcher.find()) {
            BigDecimal stated = new BigDecimal(matcher.group(1));
            if (stated.compareTo(score) != 0) {
                issues.add("评语写明本题得分 " + plain(stated) + " 分，但实际分数是 " + plain(score) + " 分");
            }
        }
    }

    private void checkDeductions(String comment, BigDecimal score, BigDecimal fullScore, List<String> issues) {
        BigDecimal totalDeduct = null;
        Matcher total = DEDUCT_TOTAL.matcher(comment);
        if (total.find()) {
            totalDeduct = new BigDecimal(total.group(1));
        } else {
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
                    + " 应为 " + plain(expected) + " 分，实际却是 " + plain(score) + " 分");
        }
    }

    private void checkContradiction(String comment, BigDecimal score, BigDecimal fullScore, List<String> issues) {
        if (score.compareTo(BigDecimal.ZERO) == 0
                && (comment.contains("完全正确") || comment.contains("全部正确")
                || comment.contains("全对") || comment.contains("满分"))) {
            issues.add("分数为 0，但评语写了“完全正确/满分”，前后矛盾");
        }
        if (score.compareTo(BigDecimal.ZERO) > 0
                && (comment.contains("未作答") || comment.contains("空白") || comment.contains("没有作答"))) {
            issues.add("分数大于 0，但评语说学生“未作答/空白”，前后矛盾");
        }
        if (score.compareTo(fullScore) == 0 && DEDUCT_ONE.matcher(comment).find()) {
            issues.add("分数为满分，但评语仍然写了扣分");
        }
    }

    /**
     * 依据匹配：评语必须能在评分细则里找到落点。
     *
     * 两个检查：
     * 1. 评语引用的"得分点 N / 要点 N"不能超过细则实际条数（编造得分点）；
     * 2. 评语与细则的词法重合不能为 0（完全没有引用细则，属于凭空写理由）。
     */
    private void checkScoringRule(String comment, String scoringRule, List<String> issues) {
        if (!StringUtils.hasText(scoringRule)) {
            return;
        }
        int pointCount = countPoints(scoringRule);
        Matcher ref = POINT_REF.matcher(comment);
        while (ref.find()) {
            int index = Integer.parseInt(ref.group(1));
            if (pointCount > 0 && index > pointCount) {
                issues.add("评语引用了得分点 " + index + "，但评分细则只有 " + pointCount + " 条，疑似编造得分点");
                break;
            }
        }

        Set<String> ruleTokens = tokenize(scoringRule);
        if (ruleTokens.isEmpty()) {
            return;
        }
        Set<String> commentTokens = tokenize(comment);
        boolean overlapped = false;
        for (String token : ruleTokens) {
            if (commentTokens.contains(token)) {
                overlapped = true;
                break;
            }
        }
        if (!overlapped) {
            issues.add("评语没有引用评分细则中的任何得分点表述，扣分理由可能与细则无关");
        }
    }

    private int countPoints(String scoringRule) {
        int count = 0;
        for (String piece : POINT_SPLIT.split(scoringRule)) {
            String trimmed = piece == null ? "" : piece.trim();
            trimmed = trimmed.replaceAll("^[（(]?[1-9][）)、.．:：]?", "").trim();
            if (trimmed.length() >= 2) {
                count++;
            }
        }
        return count;
    }

    private Set<String> tokenize(String text) {
        String cleaned = text;
        for (String noise : NOISE_WORDS) {
            cleaned = cleaned.replace(noise, " ");
        }
        cleaned = cleaned.replaceAll("\\d+(?:\\.\\d+)?\\s*分", " ");
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(cleaned.toLowerCase());
        while (matcher.find()) {
            String piece = matcher.group();
            if (piece.charAt(0) >= '\u4e00' && piece.charAt(0) <= '\u9fa5') {
                if (piece.length() == 1) {
                    continue;
                }
                for (int i = 0; i + 1 < piece.length(); i++) {
                    tokens.add(piece.substring(i, i + 2));
                }
            } else if (piece.length() > 1) {
                tokens.add(piece);
            }
        }
        return tokens;
    }

    private String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
