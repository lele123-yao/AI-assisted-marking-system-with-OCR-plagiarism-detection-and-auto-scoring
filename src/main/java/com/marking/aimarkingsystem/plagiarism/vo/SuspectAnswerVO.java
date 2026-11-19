package com.marking.aimarkingsystem.plagiarism.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 被标记为疑似抄袭的答卷汇总项。
 *
 * 同一份答卷可能与其他多份答卷相似，这里给出它的最高相似度与相似答卷数量，
 * 便于教师按风险从高到低依次核查。
 */
@Data
@Schema(description = "疑似抄袭答卷汇总")
public class SuspectAnswerVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "答卷ID", example = "101")
    private Long answerId;

    @Schema(description = "学号", example = "20260001")
    private String studentNo;

    @Schema(description = "考生姓名", example = "张三")
    private String studentName;

    @Schema(description = "答卷SimHash指纹", example = "3f2a9c1b7d4e5f60")
    private String simHash;

    @Schema(description = "该答卷的最高相似度", example = "0.9375")
    private Double maxSimilarity;

    @Schema(description = "与之相似的答卷数量", example = "2")
    private Integer similarCount;

    @Schema(description = "风险等级：HIGH / SUSPICIOUS", example = "HIGH")
    private String riskLevel;

    @Schema(description = "风险等级说明", example = "高度疑似抄袭，建议重点核查")
    private String riskLevelDesc;
}
