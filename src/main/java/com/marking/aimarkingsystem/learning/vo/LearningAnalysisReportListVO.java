package com.marking.aimarkingsystem.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学情分析报告列表项 VO。
 *
 * 不返回 analysisText（Markdown 长文本）与统计快照，
 * 避免一页 10 条报告把几十 KB 正文塞进响应体；需要全文时调详情接口。
 */
@Data
@Schema(description = "学情分析报告列表项")
public class LearningAnalysisReportListVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "报告ID", example = "1")
    private Long id;

    @Schema(description = "试卷ID", example = "3")
    private Long paperId;

    @Schema(description = "试卷标题（Service 填充）", example = "数据结构与算法 期末考试")
    private String paperTitle;

    @Schema(description = "课程ID", example = "1")
    private Long courseId;

    @Schema(description = "报告类型：PAPER 单试卷，COURSE 课程阶段", example = "PAPER")
    private String reportType;

    @Schema(description = "报告标题", example = "数据结构与算法期末考试学情分析")
    private String reportTitle;

    @Schema(description = "生成状态：0 生成中，1 成功，2 失败", example = "1")
    private Integer status;

    @Schema(description = "生成状态文案", example = "生成成功")
    private String statusDesc;

    @Schema(description = "失败原因（仅状态 2 有值）")
    private String errorMsg;

    @Schema(description = "是否已生成 PPT", example = "true")
    private Boolean hasPpt;

    @Schema(description = "创建教师ID", example = "5")
    private Long creatorId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
