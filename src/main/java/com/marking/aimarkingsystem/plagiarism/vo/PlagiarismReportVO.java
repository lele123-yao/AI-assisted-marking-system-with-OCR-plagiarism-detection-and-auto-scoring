package com.marking.aimarkingsystem.plagiarism.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 试卷级抄袭比对报告。
 *
 * 报告由"同一试卷内全部已生成指纹的答卷"两两比对得出：
 * pairs 给出所有超过可疑阈值的答卷对，suspects 按答卷维度汇总，
 * 教师可以先看 suspects 定位人，再看 pairs 看具体和谁相似。
 */
@Data
@Schema(description = "试卷抄袭比对报告")
public class PlagiarismReportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "试卷ID", example = "3")
    private Long paperId;

    @Schema(description = "试卷标题", example = "数据结构与算法 期末考试")
    private String paperTitle;

    @Schema(description = "试卷下答卷总数", example = "60")
    private Integer totalAnswerCount;

    @Schema(description = "已生成指纹的答卷数", example = "58")
    private Integer fingerprintedCount;

    @Schema(description = "因文本为空或过短未能生成指纹的答卷数", example = "2")
    private Integer skippedCount;

    @Schema(description = "实际比对的答卷对数量", example = "1653")
    private Integer comparedPairCount;

    @Schema(description = "高度疑似抄袭的答卷对数量", example = "2")
    private Integer highRiskPairCount;

    @Schema(description = "可疑的答卷对数量", example = "5")
    private Integer suspiciousPairCount;

    @Schema(description = "报告生成时间（yyyy-MM-dd HH:mm:ss）", example = "2026-11-18 10:20:30")
    private String generatedTime;

    @Schema(description = "超过可疑阈值的答卷对，按相似度倒序，最多返回上限条数")
    private List<PlagiarismPairVO> pairs;

    @Schema(description = "被标记为疑似抄袭的答卷汇总，按最高相似度倒序")
    private List<SuspectAnswerVO> suspects;
}
