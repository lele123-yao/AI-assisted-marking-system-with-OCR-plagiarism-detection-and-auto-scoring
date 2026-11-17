package com.marking.aimarkingsystem.paper.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试卷新增 / 编辑 DTO（编辑时 id 通过路径参数传递，body 不含 id）。
 *
 * 刻意不放进 DTO 的字段（均由 Service 层管理，防止前端越权篡改）：
 * - status：新增固定为 0（草稿），发布/归档走独立的状态流转接口；
 * - totalScore / questionCount：题目分值汇总，保存题目后由 Service 回写；
 * - creatorId：取当前登录用户，不信任前端传值。
 */
@Data
@Schema(description = "试卷保存请求（新增/编辑共用）")
public class PaperSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "试卷编号（唯一）", example = "DS-2026-MID")
    @NotBlank(message = "试卷编号不能为空")
    @Size(max = 64, message = "试卷编号最长 64 位")
    private String paperNo;

    @Schema(description = "试卷标题", example = "数据结构与算法 期中测验")
    @NotBlank(message = "试卷标题不能为空")
    @Size(max = 200, message = "试卷标题最长 200 位")
    private String paperTitle;

    @Schema(description = "所属课程ID", example = "1")
    @NotNull(message = "所属课程不能为空")
    @Min(value = 1, message = "课程ID不合法")
    private Long courseId;

    @Schema(description = "作答模式：1 线上作答，2 纸质线下阅卷", example = "1")
    @NotNull(message = "作答模式不能为空")
    @Min(value = 1, message = "作答模式只能为 1 或 2")
    @Max(value = 2, message = "作答模式只能为 1 或 2")
    private Integer answerMode;

    @Schema(description = "及格分", example = "24.0")
    @DecimalMin(value = "0.0", message = "及格分不能小于 0")
    private BigDecimal passScore;

    @Schema(description = "考试时长（分钟），纸质模式可不传", example = "90")
    @Min(value = 1, message = "考试时长至少 1 分钟")
    private Integer duration;

    @Schema(description = "开考时间，线上模式用于控制作答入口")
    private LocalDateTime startTime;

    @Schema(description = "收卷时间，线上模式截止提交")
    private LocalDateTime endTime;

    @Schema(description = "是否启用AI阅卷：1 启用，0 仅人工；不传默认 1", example = "1")
    @Min(value = 0, message = "AI阅卷开关只能为 0 或 1")
    @Max(value = 1, message = "AI阅卷开关只能为 0 或 1")
    private Integer aiMarking;

    @Schema(description = "试卷说明", example = "线上作答，提交后进入AI异步阅卷")
    @Size(max = 512, message = "试卷说明最长 512 位")
    private String description;
}
