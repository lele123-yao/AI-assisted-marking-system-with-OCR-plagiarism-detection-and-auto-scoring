package com.marking.aimarkingsystem.answer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 纸质扫描答卷提交 DTO。
 *
 * 使用流程：
 * 1. 先调用文件上传接口，把扫描件存入 MinIO，拿到 objectKey；
 * 2. 再提交本 DTO，把 objectKey 与试卷绑定生成答卷记录；
 * 3. OCR 识别与学号匹配由后续纸质批改模块异步完成，
 *    因此 studentNo 可空，studentId 与 ocrText 都不在此 DTO 中。
 */
@Data
@Schema(description = "纸质扫描答卷提交请求")
public class PaperAnswerSubmitDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "试卷ID", example = "1")
    @NotNull(message = "试卷ID不能为空")
    @Min(value = 1, message = "试卷ID不合法")
    private Long paperId;

    @Schema(description = "学号，可空：未识别时由后续OCR匹配回填", example = "20260001")
    @Size(max = 64, message = "学号最长 64 位")
    private String studentNo;

    @Schema(description = "扫描件在MinIO中的对象键（上传接口返回）",
            example = "answer/2026/11/12/3f2a9c8e4b7d4f1e9a0c.jpg")
    @NotBlank(message = "答卷图片不能为空")
    @Size(max = 512, message = "对象键过长")
    private String paperImage;
}
