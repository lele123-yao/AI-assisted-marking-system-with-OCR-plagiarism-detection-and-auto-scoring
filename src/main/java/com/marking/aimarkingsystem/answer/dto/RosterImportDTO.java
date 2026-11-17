package com.marking.aimarkingsystem.answer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 考生名单批量导入 DTO（纸质试卷线下批改场景）。
 *
 * 使用方式：
 * - Excel 文件通过 multipart 表单字段上传，本 DTO 承载"导入到哪张试卷"等业务参数；
 * - 名单列固定为：学号（必填）、姓名（必填）、班级（可选），见阶段1的模板说明；
 * - defaultClass 用于 Excel 中班级列整列为空时，给本批考生统一补一个班级。
 *
 * 说明：
 * - importBatchNo 由服务端生成（时间戳+序号），不接受前端传入，避免被用来覆盖其他批次数据；
 * - 校验（学号格式、文件内重复、与已有答卷重复）属于 Service 职责，DTO 只做基础约束。
 */
@Data
@Schema(description = "考生名单批量导入请求")
public class RosterImportDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "目标试卷ID（必须为纸质线下批改模式）", example = "3")
    @NotNull(message = "试卷ID不能为空")
    @Min(value = 1, message = "试卷ID不合法")
    private Long paperId;

    @Schema(description = "默认班级：Excel 中班级列为空时统一使用，可空", example = "计算机2026级1班")
    @Size(max = 64, message = "班级名称最长 64 位")
    private String defaultClass;
}
