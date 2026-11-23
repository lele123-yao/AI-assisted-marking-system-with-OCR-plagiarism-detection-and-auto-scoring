package com.marking.aimarkingsystem.agent.tool;

import cn.hutool.json.JSONObject;
import com.marking.aimarkingsystem.paper.entity.ExamQuestion;
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
 * 工具：评分点命中统计。
 *
 * 把"评分细则"拆成一条条得分点，逐条判断学生作答是否覆盖，
 * 输出"命中几条、缺几条、命中分值合计"，让主 Agent 按点给分，而不是拍脑袋。
 *
 * 为什么用词法覆盖而不是再调一次模型：
 * - 这一步要的是稳定、可复现的规则统计，模型只需要拿着统计结果做最终判断；
 * - 模型每次调用结果可能不同，规则统计每次都一样，方便教师核对；
 * - 少一次模型调用，成本和延迟都更低。
 *
 * 命中判定：得分点去掉"写出/说明/正确"等虚词后切二元词，
 * 学生作答覆盖 50% 以上视为命中；纯词法有误差，因此结果里明确标注"仅供参考"。
 */
@Component
public class ScorePointStatsTool implements AgentTool {

    /** 得分点分隔：换行、分号、圈号、括号序号、1、2、 这类编号 */
    private static final Pattern POINT_SPLIT = Pattern.compile(
            "\\r?\\n|;|；|①|②|③|④|⑤|⑥|⑦|⑧|⑨|⑩"
                    + "|(?=（\\d{1,2}）)"
                    + "|(?=\\(\\d{1,2}\\))"
                    + "|(?=(?<![\\d.])(?:[1-9]|10)[.、．](?!\\d))");

    /** 单个得分点上的分值，如"（4分）""4分" */
    private static final Pattern POINT_SCORE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*分");

    private static final Pattern TOKEN = Pattern.compile("[\\u4e00-\\u9fa5]+|[a-zA-Z0-9]+");

    /** 评分细则里的虚词，不参与命中判定，否则"写出"两个字会污染结果 */
    private static final List<String> NOISE_WORDS = List.of(
            "写出", "求出", "说明", "计算", "得出", "给出", "正确", "完整", "答案",
            "过程", "步骤", "结果", "能够", "可以", "要求", "必须", "需要", "以及",
            "要点", "得分点", "分值", "给分", "评分", "若", "则", "且");

    /** 命中阈值：得分点二元词被作答覆盖的比例 */
    private static final double HIT_RATIO = 0.5;

    private final AnswerContextProvider contextProvider;

    public ScorePointStatsTool(AnswerContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    @Override
    public String name() {
        return "score_point_stats";
    }

    @Override
    public String description() {
        return "把评分细则拆成得分点，统计学生作答命中了哪些、缺失哪些，并汇总命中分值";
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

        String rule = question.getScoringRule();
        if (!StringUtils.hasText(rule)) {
            return "本题未配置评分细则，无法统计得分点。请依据参考答案判断，或提示教师补充细则。";
        }
        String answer = contextProvider.studentAnswer(answerId, questionId);
        if (!StringUtils.hasText(answer)) {
            return "学生该题作答为空，所有得分点均未命中（0 分）。";
        }

        List<String> points = splitPoints(rule);
        if (points.isEmpty()) {
            return "评分细则格式无法拆分出得分点，原文：" + rule;
        }

        String answerNorm = normalize(answer);
        int hitCount = 0;
        BigDecimal hitScore = BigDecimal.ZERO;
        BigDecimal ruleScore = BigDecimal.ZERO;
        List<String> lines = new ArrayList<>();

        for (int i = 0; i < points.size(); i++) {
            String point = points.get(i);
            BigDecimal pointScore = extractScore(point);
            boolean hit = isHit(point, answerNorm);
            if (hit) {
                hitCount++;
                if (pointScore != null) {
                    hitScore = hitScore.add(pointScore);
                }
            }
            if (pointScore != null) {
                ruleScore = ruleScore.add(pointScore);
            }
            lines.add("  " + (i + 1) + ". [" + (hit ? "命中" : "缺失") + "] "
                    + point.trim() + (pointScore == null ? "" : "（" + plain(pointScore) + " 分）"));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【评分点命中统计】\n");
        sb.append("满分：").append(question.getScore() == null ? "-" : plain(question.getScore()))
                .append("；细则拆分得分点：").append(points.size()).append(" 条")
                .append("；命中：").append(hitCount).append(" 条")
                .append("；缺失：").append(points.size() - hitCount).append(" 条\n");
        sb.append("命中分值合计：").append(plain(hitScore))
                .append("；细则标注分值合计：").append(plain(ruleScore)).append("\n");
        sb.append("逐点明细：\n");
        lines.forEach(line -> sb.append(line).append("\n"));
        sb.append("说明：命中判定基于词法覆盖（阈值 ")
                .append((int) (HIT_RATIO * 100)).append("%），仅供参考，最终以评分细则人工判断为准。");
        return sb.toString();
    }

    // ==================== 拆分与匹配 ====================

    private List<String> splitPoints(String rule) {
        List<String> points = new ArrayList<>();
        for (String piece : POINT_SPLIT.split(rule)) {
            String trimmed = piece == null ? "" : piece.trim();
            // 去掉残余的括号序号与分隔符，太短的碎片丢弃
            trimmed = trimmed.replaceAll("^[（(]?[1-9][）)、.．:：]?", "").trim();
            if (trimmed.length() >= 2) {
                points.add(trimmed);
            }
        }
        return points;
    }

    private BigDecimal extractScore(String point) {
        Matcher matcher = POINT_SCORE.matcher(point);
        if (matcher.find()) {
            return new BigDecimal(matcher.group(1));
        }
        return null;
    }

    /**
     * 命中判定：把得分点切成二元词（去掉虚词），看作答覆盖了多少。
     *
     * 例："写出椭圆标准方程（4分）" -> 去掉"写出"、去掉分值 ->
     * 二元词 椭圆/圆标/标准/准方/方程，作答含"椭圆方程"可覆盖 2/5。
     */
    private boolean isHit(String point, String answerNorm) {
        Set<String> tokens = tokenize(point);
        if (tokens.isEmpty()) {
            return false;
        }
        int matched = 0;
        for (String token : tokens) {
            if (answerNorm.contains(token)) {
                matched++;
            }
        }
        if (tokens.size() <= 2) {
            return matched >= 1;
        }
        return matched >= Math.max(2, (int) Math.ceil(tokens.size() * HIT_RATIO));
    }

    private Set<String> tokenize(String text) {
        String cleaned = text;
        for (String noise : NOISE_WORDS) {
            cleaned = cleaned.replace(noise, " ");
        }
        cleaned = POINT_SCORE.matcher(cleaned).replaceAll(" ");
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

    private String normalize(String text) {
        return text.replaceAll("\\s+", "")
                .replace("，", ",")
                .replace("。", ".")
                .toLowerCase();
    }

    private String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
