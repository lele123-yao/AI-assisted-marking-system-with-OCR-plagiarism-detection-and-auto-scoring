package com.marking.aimarkingsystem.paper.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单个上传素材（一页图片或 PDF）的识别状态。
 *
 * 之前只在 warnings 里写一句"图片 xx 识别失败"，前端无法区分哪张成功、哪张重复、哪张失败；
 * 现在逐文件返回结构化状态，前端可直接标出需要重拍/补录的那一页。
 */
@Data
@NoArgsConstructor
@Schema(description = "单个上传素材的识别状态")
public class PageParseStatus implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "原始文件名", example = "page-1.jpg")
    private String fileName;

    @Schema(description = "状态：SUCCESS 已识别 / FAILED 识别失败 / SKIPPED_DUPLICATE 重复跳过")
    private String status;

    @Schema(description = "识别出的字符数")
    private int chars;

    @Schema(description = "说明信息，失败时为失败原因")
    private String message;

    public PageParseStatus(String fileName, String status, int chars, String message) {
        this.fileName = fileName;
        this.status = status;
        this.chars = chars;
        this.message = message;
    }
}
