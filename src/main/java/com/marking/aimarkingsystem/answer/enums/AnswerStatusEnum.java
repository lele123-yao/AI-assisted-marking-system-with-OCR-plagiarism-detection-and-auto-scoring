package com.marking.aimarkingsystem.answer.enums;

/**
 * 答卷状态枚举（线上作答与纸质扫描共用同一套状态值）。
 *
 * 数据库 exam_answer.marking_status 存 code，流转规则由 Service 控制，本枚举只定义取值。
 *
 * 状态流转（正常路径）：
 * ONLINE     : 0 待处理 -> 1 排队中 -> 2 AI阅卷中 -> 3 AI辅助完成待确认 -> 5 教师已确认归档
 * PAPER_SCAN : 0 待录入 -> 1 OCR识别中 -> 2 AI辅助批改中 -> 3 待教师确认 -> 5 教师已确认归档
 * 任一环节失败进入 4，原因写入 exam_answer.marking_error，允许重试后回到 0/1。
 *
 * 说明：
 * - 0、1、2 在两种来源下展示文案不同（前端按 answer_source 区分），但都表示"尚未出结果"，
 *   因此共用同一个数值，避免维护两套状态机；
 * - 3 是"AI 已给出参考分，等待教师确认"，只有教师确认后写 teacherFinalScore 才进入 5；
 * - 成绩统计、归档查询以 5 为准，5 之后的答卷应只读。
 */
public enum AnswerStatusEnum {

    /** 待处理 / 待录入 */
    PENDING(0, "待处理/待录入"),

    /** 排队中（线上等待阅卷任务）/ OCR识别中（纸质） */
    PROCESSING(1, "排队中/OCR识别中"),

    /** AI 阅卷中 */
    AI_MARKING(2, "AI阅卷中"),

    /** AI辅助完成，待教师确认（AI参考分已产出） */
    PENDING_TEACHER_CONFIRM(3, "AI辅助完成，待教师确认"),

    /** 阅卷失败（OCR或AI调用异常） */
    FAILED(4, "阅卷失败"),

    /** 教师已确认归档（最终成绩已定稿） */
    ARCHIVED(5, "教师已确认归档");

    private final Integer code;

    private final String desc;

    AnswerStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 按 code 解析，未匹配返回 null（不抛异常，避免历史脏数据导致查询失败） */
    public static AnswerStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (AnswerStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /** 按 code 取展示文案，未匹配返回空串，供 VO 填充状态文本 */
    public static String descOf(Integer code) {
        AnswerStatusEnum item = of(code);
        return item == null ? "" : item.getDesc();
    }

    /** 是否为终态（已归档），终态答卷不允许再次修改分数 */
    public static boolean isArchived(Integer code) {
        return ARCHIVED.code.equals(code);
    }
}
