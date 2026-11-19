package com.marking.aimarkingsystem.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学情分析报告详情 VO。
 *
 * 与列表 VO 的差异：返回 analysisText 全文、PPT 访问地址与统计快照，
 * 用于报告详情页一次性展示"统计图表 + AI 讲评 + PPT 下载"。
 *
 * PPT 字段说明：
 * - pptFileUrl 是数据库里的 MinIO objectKey，永久有效但不能直接访问；
 * - pptAccessUrl 是 Service 现算的短时效签名地址，过期后需要重新查询详情。
 */
@Data
@Schema(description = "学情分析报告详情")
public class LearningAnalysisReportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "报告ID", example = "1")
    private Long id;

    @Schema(description = "试卷ID", example = "3")
    private Long paperId;

    @Schema(description = "试卷标题", example = "数据结构与算法 期末考试")
    private String paperTitle;

    @Schema(description = "课程ID", example = "1")
    private Long courseId;

    @Schema(description = "报告类型：PAPER 单试卷，COURSE 课程阶段", example = "PAPER")
    private String reportType;

    @Schema(description = "报告标题", example = "数据结构与算法期末考试学情分析")
    private String reportTitle;

    @Schema(description = "大模型生成的 Markdown 讲评正文")
    private String analysisText;

    @Schema(description = "PPT 对象键（MinIO objectKey，非访问地址）",
            example = "learning-report/2026/11/18/3f2a9c8e4b7d4f1e.pptx")
    private String pptFileUrl;

    @Schema(description = "PPT 临时访问地址（签名 URL，会过期）")
    private String pptAccessUrl;

    @Schema(description = "生成状态：0 生成中，1 成功，2 失败", example = "1")
    private Integer status;

    @Schema(description = "生成状态文案", example = "生成成功")
    private String statusDesc;

    @Schema(description = "失败原因（仅状态 2 有值）")
    private String errorMsg;

    @Schema(description = "创建教师ID", example = "5")
    private Long creatorId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "统计快照，详情接口返回")
    private LearningStatisticsVO statistics;
}
