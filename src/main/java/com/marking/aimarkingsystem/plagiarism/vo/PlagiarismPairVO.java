package com.marking.aimarkingsystem.plagiarism.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 抄袭比对结果中的一对答卷。
 *
 * 报告里以 A/B 两方呈现，前端可直接渲染成"张三 ↔ 李四 相似度 0.93"的条目。
 */
@Data
@Schema(description = "疑似抄袭答卷对")
public class PlagiarismPairVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "答卷A的ID", example = "101")
    private Long answerIdA;

    @Schema(description = "答卷A学号", example = "20260001")
    private String studentNoA;

    @Schema(description = "答卷A考生姓名", example = "张三")
    private String studentNameA;

    @Schema(description = "答卷B的ID", example = "108")
    private Long answerIdB;

    @Schema(description = "答卷B学号", example = "20260008")
    private String studentNoB;

    @Schema(description = "答卷B考生姓名", example = "李四")
    private String studentNameB;

    @Schema(description = "汉明距离（不同bit数，0~64，越小越相似）", example = "4")
    private Integer hammingDistance;

    @Schema(description = "相似度（0~1，保留4位小数）", example = "0.9375")
    private Double similarity;

    @Schema(description = "风险等级：HIGH 高度疑似，SUSPICIOUS 可疑", example = "HIGH")
    private String riskLevel;

    @Schema(description = "风险等级说明", example = "高度疑似抄袭，建议重点核查")
    private String riskLevelDesc;
}
