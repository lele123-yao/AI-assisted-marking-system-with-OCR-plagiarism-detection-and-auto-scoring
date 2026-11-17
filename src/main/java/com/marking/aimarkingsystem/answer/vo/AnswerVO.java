package com.marking.aimarkingsystem.answer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 答卷详情 VO（用于详情页 / 教师批改页）。
 *
 * 说明：
 * - paperTitle、studentName 由 Service 关联试卷与用户表填充；
 * - paperImageUrl 是 Service 用 MinioUtil 现算的短时效签名地址，
 *   与库里存的 paperImage（objectKey）区分：前者会过期，后者永久有效；
 * - 包含 answerContent / ocrText 长文本，列表场景请改用 AnswerListVO。
 */
@Data
@Schema(description = "答卷详情")
public class AnswerVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "答卷ID", example = "1")
    private Long id;

    @Schema(description = "答卷来源：ONLINE 线上答题，PAPER_SCAN 纸质扫描", example = "ONLINE")
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

    @Schema(description = "线上作答内容（按题号组织的JSON）")
    private String answerContent;

    @Schema(description = "OCR识别全文（仅纸质扫描）")
    private String ocrText;

    @Schema(description = "答卷图片对象键（MinIO objectKey）")
    private String paperImage;

    @Schema(description = "答卷图片临时访问地址（签名URL，会过期）")
    private String paperImageUrl;

    @Schema(description = "AI参考分", example = "32.5")
    private BigDecimal aiScore;

    @Schema(description = "最终分数", example = "33.0")
    private BigDecimal finalScore;

    @Schema(description = "阅卷状态：0 待处理，1 排队中，2 AI阅卷中，3 已完成，4 阅卷失败", example = "3")
    private Integer markingStatus;

    @Schema(description = "阅卷失败原因")
    private String markingError;

    @Schema(description = "提交/收卷时间")
    private LocalDateTime submitTime;

    @Schema(description = "阅卷完成时间")
    private LocalDateTime markTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
