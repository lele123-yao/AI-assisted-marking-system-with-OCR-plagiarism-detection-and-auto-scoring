package com.marking.aimarkingsystem.plagiarism.enums;

/**
 * 抄袭风险等级。
 *
 * 由相似度阈值划分，阈值可在 application.yml 的 plagiarism.simhash 下调整：
 * - HIGH：相似度 ≥ 高阈值（默认 0.90），高度疑似抄袭；
 * - SUSPICIOUS：相似度位于可疑阈值与高阈值之间（默认 0.80 ~ 0.90），建议人工复核。
 */
public enum PlagiarismRiskEnum {

    HIGH("高度疑似抄袭，建议重点核查"),
    SUSPICIOUS("存在相似，建议人工复核");

    private final String desc;

    PlagiarismRiskEnum(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    public static String descOf(String name) {
        for (PlagiarismRiskEnum value : values()) {
            if (value.name().equals(name)) {
                return value.getDesc();
            }
        }
        return null;
    }
}
