package com.marking.aimarkingsystem.agent.checker;

import java.util.List;

/**
 * Checker 自检结果。
 *
 * passed 为 false 时，issues 是打回主 Agent 的具体原因；
 * 每条 issue 都用"哪一项、期望是什么、实际是什么"的句式，方便模型直接照着改。
 */
public record CheckResult(boolean passed, List<String> issues) {

    public static CheckResult ok() {
        return new CheckResult(true, List.of());
    }

    public static CheckResult fail(List<String> issues) {
        return new CheckResult(false, List.copyOf(issues));
    }

    /** 拼成一段可读文本，既写进轨迹，也作为修正轮的用户提示 */
    public String toFeedback() {
        if (passed) {
            return "自检通过。";
        }
        StringBuilder sb = new StringBuilder("上一版评分未通过自检，问题如下：\n");
        for (int i = 0; i < issues.size(); i++) {
            sb.append(i + 1).append(". ").append(issues.get(i)).append("\n");
        }
        return sb.toString();
    }
}
