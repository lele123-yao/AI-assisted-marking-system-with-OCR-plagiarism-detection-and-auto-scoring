package com.marking.aimarkingsystem.answer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 纸质答卷分页查询条件。
 *
 * 说明：
 * - 只查 answer_source = PAPER_SCAN 的数据，线上答卷走线上答卷接口；
 * - keyword 同时匹配学号与姓名（模糊查询），用于批改页快速定位某个考生；
 * - markingStatus 可按状态筛选（如只看"待教师确认"的答卷）。
 *
 * 数据权限（占位）：查询条件会自动追加"当前教师所授课程"的范围，
 * 具体实现见 PaperAnswerService 的占位注释。
 */
@Data
@Schema(description = "纸质答卷分页查询条件")
public class PaperAnswerQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Long pageNum = 1L;

    @Schema(description = "每页条数，最大 100", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Long pageSize = 10L;

    @Schema(description = "试卷ID，可空：不传则查询全部纸质试卷", example = "3")
    @Min(value = 1, message = "试卷ID不合法")
    private Long paperId;

    @Schema(description = "关键字：学号或姓名模糊匹配", example = "20260001")
    @Size(max = 64, message = "关键字最长 64 位")
    private String keyword;

    @Schema(description = "答卷状态：0 待录入，1 OCR识别中，2 AI批改中，3 待教师确认，4 失败，5 已归档", example = "3")
    @Min(value = 0, message = "状态不合法")
    @Max(value = 5, message = "状态不合法")
    private Integer markingStatus;
}
