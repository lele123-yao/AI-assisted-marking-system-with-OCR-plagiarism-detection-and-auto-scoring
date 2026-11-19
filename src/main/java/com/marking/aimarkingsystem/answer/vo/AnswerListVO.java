package com.marking.aimarkingsystem.answer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 答卷列表 VO。
 *
 * 与 AnswerVO 的区别：不返回 answerContent / ocrText 长文本，
 * 避免一页 10 条答卷就把几十 KB 的文本塞进响应体；
 * 需要查看作答内容时再调详情接口。
 */
@Data
@Schema(description = "答卷列表项")
public class AnswerListVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "答卷ID", example = "1")
    private Long id;

    @Schema(description = "答卷来源：ONLINE 线上答题，PAPER_SCAN 纸质扫描", example = "PAPER_SCAN")
    private String answerSource;

    @Schema(description = "试卷ID", example = "1")
    private Long paperId;

    @Schema(description = "试卷标题（Service 填充）", example = "数据结构与算法 期中测验")
    private String paperTitle;

    @Schema(description = "学生ID", example = "2")
    private Long studentId;

    @Schema(description = "学号", example = "20260001")
    private String studentNo;

    @Schema(description = "学生姓名（Service 填充）", example = "张三")
    private String studentName;

    @Schema(description = "AI参考分", example = "32.5")
    private BigDecimal aiScore;

    @Schema(description = "最终分数", example = "33.0")
    private BigDecimal finalScore;

    // ==================== 成绩复核（模块6） ====================

    @Schema(description = "复核状态：0 待复核，1 复核通过，2 已改分，3 复核驳回", example = "1")
    private Integer reviewStatus;

    @Schema(description = "复核状态文案", example = "复核通过")
    private String reviewStatusDesc;

    @Schema(description = "复核人姓名", example = "王老师")
    private String reviewerName;

    @Schema(description = "复核时间")
    private LocalDateTime reviewTime;

    @Schema(description = "最终分与AI参考分的差值", example = "1.00")
    private BigDecimal scoreDiff;

    @Schema(description = "是否在AI参考分基础上改过分", example = "true")
    private Boolean scoreModified;

    @Schema(description = "阅卷状态：0 待处理，1 排队中，2 AI阅卷中，3 已完成，4 阅卷失败", example = "2")
    private Integer markingStatus;

    @Schema(description = "提交/收卷时间")
    private LocalDateTime submitTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
