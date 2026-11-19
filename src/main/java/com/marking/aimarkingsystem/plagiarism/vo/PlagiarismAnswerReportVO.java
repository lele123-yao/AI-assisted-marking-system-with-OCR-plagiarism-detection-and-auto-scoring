package com.marking.aimarkingsystem.plagiarism.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 单份答卷的查重报告。
 *
 * 只与同一试卷内的其他答卷比对，跨试卷没有比对意义（题目不同）。
 */
@Data
@Schema(description = "单份答卷查重报告")
public class PlagiarismAnswerReportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "答卷ID", example = "101")
    private Long answerId;

    @Schema(description = "试卷ID", example = "3")
    private Long paperId;

    @Schema(description = "学号", example = "20260001")
    private String studentNo;

    @Schema(description = "考生姓名", example = "张三")
    private String studentName;

    @Schema(description = "当前答卷的SimHash指纹", example = "3f2a9c1b7d4e5f60")
    private String simHash;

    @Schema(description = "参与比对的答卷数量（同试卷内已生成指纹的其他答卷）", example = "57")
    private Integer comparedCount;

    @Schema(description = "最高相似度，无相似对象时为空", example = "0.9375")
    private Double maxSimilarity;

    @Schema(description = "风险等级：HIGH / SUSPICIOUS，无风险时为空", example = "HIGH")
    private String riskLevel;

    @Schema(description = "风险等级说明")
    private String riskLevelDesc;

    @Schema(description = "相似答卷列表，按相似度倒序")
    private List<SimilarAnswerVO> similarAnswers;
}
