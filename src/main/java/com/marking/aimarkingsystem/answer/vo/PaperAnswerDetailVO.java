package com.marking.aimarkingsystem.answer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 纸质答卷详情 VO（教师批改页 / 学生查看页使用）。
 *
 * 与线上答卷 VO 的差异：
 * - 不返回 answerContent（纸质无线上作答内容），核心是 ocrText + 扫描图片；
 * - 图片同时给出两个字段：
 *   paperImgUrl 为库里存的 MinIO objectKey（永久有效、不可直接访问），
 *   paperImgAccessUrl 为 Service 用 MinioUtil 现算的短时效签名地址（会过期）；
 * - 同时展示 aiReferenceScore 与 teacherFinalScore，方便教师对照 AI 结果快速判定；
 * - markingStatusDesc 由 Service 根据 AnswerStatusEnum 填充，前端不必维护枚举文案。
 *
 * 说明：paperTitle、studentName 等跨表字段由 Service 批量查询后填充，
 * 本 VO 只定义出参结构，不承载任何查询逻辑。
 */
@Data
@Schema(description = "纸质答卷详情")
public class PaperAnswerDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "答卷ID", example = "101")
    private Long id;

    @Schema(description = "答卷来源：ONLINE 线上作答，PAPER_SCAN 纸质扫描", example = "PAPER_SCAN")
    private String answerSource;

    @Schema(description = "答卷来源文案", example = "纸质扫描")
    private String answerSourceDesc;

    @Schema(description = "试卷ID", example = "3")
    private Long paperId;

    @Schema(description = "试卷标题（Service 填充）", example = "数据结构与算法 期末考试")
    private String paperTitle;

    @Schema(description = "学生ID，未匹配到系统账号时为空", example = "12")
    private Long studentId;

    @Schema(description = "学号", example = "20260001")
    private String studentNo;

    @Schema(description = "考生姓名", example = "张三")
    private String studentName;

    @Schema(description = "考生班级", example = "计算机2026级1班")
    private String studentClass;

    @Schema(description = "名单导入批次号", example = "20261112-143000-01")
    private String importBatchNo;

    @Schema(description = "OCR识别出的答卷全文")
    private String ocrText;

    @Schema(description = "答卷图片对象键（MinIO objectKey，非访问地址）",
            example = "answer/2026/11/12/3f2a9c8e4b7d4f1e9a0c.jpg")
    private String paperImgUrl;

    @Schema(description = "答卷图片临时访问地址（签名URL，会过期）")
    private String paperImgAccessUrl;

    @Schema(description = "AI参考分", example = "32.50")
    private BigDecimal aiReferenceScore;

    @Schema(description = "教师最终得分，未确认时为空", example = "33.50")
    private BigDecimal teacherFinalScore;

    // ==================== 成绩复核（模块6） ====================

    @Schema(description = "复核状态：0 待复核，1 复核通过，2 已改分，3 复核驳回", example = "1")
    private Integer reviewStatus;

    @Schema(description = "复核状态文案", example = "复核通过")
    private String reviewStatusDesc;

    @Schema(description = "复核人ID", example = "5")
    private Long reviewerId;

    @Schema(description = "复核人姓名", example = "王老师")
    private String reviewerName;

    @Schema(description = "复核时间")
    private LocalDateTime reviewTime;

    @Schema(description = "复核意见", example = "第2题评分偏高，已调整为12分")
    private String reviewRemark;

    @Schema(description = "教师最终分与AI参考分的差值，正数为上调、负数为下调", example = "1.00")
    private BigDecimal scoreDiff;

    @Schema(description = "是否在AI参考分基础上改过分", example = "true")
    private Boolean scoreModified;

    @Schema(description = "答卷状态：0 待录入，1 OCR识别中，2 AI辅助批改中，3 待教师确认，4 阅卷失败，5 教师已确认归档",
            example = "3")
    private Integer markingStatus;

    @Schema(description = "答卷状态文案", example = "AI辅助完成，待教师确认")
    private String markingStatusDesc;

    @Schema(description = "阅卷失败原因（仅状态 4 有值）")
    private String markingError;

    @Schema(description = "提交/收卷时间")
    private LocalDateTime submitTime;

    @Schema(description = "阅卷完成/教师确认时间")
    private LocalDateTime markTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
