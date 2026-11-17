package com.marking.aimarkingsystem.answer.enums;

/**
 * 答卷来源枚举。
 *
 * 数据库 exam_answer.answer_source 存枚举名（ONLINE / PAPER_SCAN），
 * 不用数字是为了日志、排查时一眼能看懂来源，也避免新增来源时含义错位。
 *
 * 两种来源共用 exam_answer 一张表，但字段使用侧重点不同：
 * - ONLINE：answerContent 有值，paperImgUrl / ocrText 为空；
 * - PAPER_SCAN：paperImgUrl（扫描件）与 ocrText（OCR文本）有值，answerContent 为空。
 */
public enum AnswerSourceEnum {

    /** 线上作答：学生在系统内答题后提交 */
    ONLINE("线上作答"),

    /** 纸质扫描：线下答题卡扫描后录入，含OCR识别与AI辅助批改 */
    PAPER_SCAN("纸质扫描");

    private final String desc;

    AnswerSourceEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /** 按数据库存储值解析，未匹配返回 null（不抛异常，避免脏数据导致查询崩溃） */
    public static AnswerSourceEnum of(String value) {
        if (value == null) {
            return null;
        }
        for (AnswerSourceEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }

    /** 是否为纸质扫描答卷 */
    public static boolean isPaperScan(String value) {
        return PAPER_SCAN.name().equals(value);
    }
}
