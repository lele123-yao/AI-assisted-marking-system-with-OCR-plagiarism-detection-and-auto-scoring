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
 * 试卷（答题卡）图片上传 DTO。
 *
 * 使用方式：
 * 1. 图片文件通过 multipart 表单字段上传，本 DTO 承载"上传到哪张试卷、属于哪个考生"；
 * 2. Service 先调 MinioUtil 上传得到 objectKey，再按 paperId + studentNo 定位答卷记录：
 *    - 名单导入时已预生成记录 → 回填 paperImgUrl，状态推进到 1（OCR识别中）；
 *    - 未导入名单的零散答卷 → 以 PAPER_SCAN 来源新建记录；
 * 3. studentId 不在此 DTO 中：能匹配到系统账号就回填，匹配不到只保留学号与姓名。
 *
 * 说明：studentName 可选，仅用于名单里没有该学号时补建记录，防止出现"只有学号没有名字"的脏数据。
 */
@Data
@Schema(description = "试卷图片上传请求")
public class PaperImageUploadDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "试卷ID（必须为纸质线下批改模式）", example = "3")
    @NotNull(message = "试卷ID不能为空")
    @Min(value = 1, message = "试卷ID不合法")
    private Long paperId;

    @Schema(description = "学号：用于匹配名单中的考生记录", example = "20260001")
    @NotBlank(message = "学号不能为空")
    @Size(max = 64, message = "学号最长 64 位")
    private String studentNo;

    @Schema(description = "考生姓名：名单中无该学号时用于补建记录，可空", example = "张三")
    @Size(max = 64, message = "姓名最长 64 位")
    private String studentName;
}
