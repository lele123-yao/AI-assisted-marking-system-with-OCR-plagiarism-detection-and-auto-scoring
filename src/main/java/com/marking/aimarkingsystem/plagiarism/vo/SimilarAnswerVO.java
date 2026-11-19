package com.marking.aimarkingsystem.plagiarism.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单份答卷的相似答卷条目（用于答卷级查重报告）。
 */
@Data
@Schema(description = "相似答卷条目")
public class SimilarAnswerVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "相似答卷ID", example = "108")
    private Long answerId;

    @Schema(description = "学号", example = "20260008")
    private String studentNo;

    @Schema(description = "考生姓名", example = "李四")
    private String studentName;

    @Schema(description = "汉明距离", example = "4")
    private Integer hammingDistance;

    @Schema(description = "相似度（0~1）", example = "0.9375")
    private Double similarity;

    @Schema(description = "风险等级：HIGH / SUSPICIOUS", example = "HIGH")
    private String riskLevel;

    @Schema(description = "风险等级说明", example = "高度疑似抄袭，建议重点核查")
    private String riskLevelDesc;
}
