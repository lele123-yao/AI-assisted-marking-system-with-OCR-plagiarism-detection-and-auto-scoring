package com.marking.aimarkingsystem.answer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 纸质答卷列表项 VO。
 *
 * 与详情 VO 的区别：不返回 ocrText（长文本）与签名图片地址，
 * 避免一页 10 条答卷把几十 KB 的 OCR 文本塞进响应体；
 * 需要查看识别内容时再调详情接口。
 */
@Data
@Schema(description = "纸质答卷列表项")
public class PaperAnswerListVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "答卷ID", example = "101")
    private Long id;

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

    @Schema(description = "名单导入批次号", example = "20261112-143000-317")
    private String importBatchNo;

    @Schema(description = "AI参考分", example = "32.50")
    private BigDecimal aiReferenceScore;

    @Schema(description = "教师最终得分，未确认时为空", example = "33.50")
    private BigDecimal teacherFinalScore;

    // ==================== 成绩复核（模块6） ====================

    @Schema(description = "复核状态：0 待复核，1 复核通过，2 已改分，3 复核驳回", example = "1")
    private Integer reviewStatus;

    @Schema(description = "复核状态文案", example = "复核通过")
    private String reviewStatusDesc;

    @Schema(description = "复核人姓名", example = "王老师")
    private String reviewerName;

    @Schema(description = "复核时间")
    private LocalDateTime reviewTime;

    @Schema(description = "教师最终分与AI参考分的差值", example = "1.00")
    private BigDecimal scoreDiff;

    @Schema(description = "是否在AI参考分基础上改过分", example = "true")
    private Boolean scoreModified;

    @Schema(description = "答卷状态：0 待录入，1 OCR识别中，2 AI批改中，3 待教师确认，4 失败，5 已归档", example = "3")
    private Integer markingStatus;

    @Schema(description = "答卷状态文案", example = "AI辅助完成，待教师确认")
    private String markingStatusDesc;

    @Schema(description = "是否已上传扫描图片", example = "true")
    private Boolean hasImage;

    @Schema(description = "创建时间（名单导入时间）")
    private LocalDateTime createTime;

    @Schema(description = "阅卷完成/教师确认时间")
    private LocalDateTime markTime;
}
