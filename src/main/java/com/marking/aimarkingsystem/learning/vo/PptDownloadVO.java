package com.marking.aimarkingsystem.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 讲评 PPT 下载信息（模块8）。
 *
 * PPT 存在私有 MinIO 桶中，downloadUrl 是短时效签名地址，
 * 过期后重新调用生成接口或报告详情接口即可获取新地址。
 * pptFileUrl 返回 objectKey，便于前端排查"文件是否已生成"。
 */
@Data
@Schema(description = "讲评PPT下载信息")
public class PptDownloadVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "报告ID", example = "1")
    private Long reportId;

    @Schema(description = "报告标题", example = "数据结构与算法期末考试学情分析")
    private String reportTitle;

    @Schema(description = "下载文件名", example = "数据结构与算法期末考试学情分析-讲评课件.pptx")
    private String fileName;

    @Schema(description = "PPT 对象键（MinIO objectKey）",
            example = "learning-report/2026/11/18/3f2a9c8e4b7d4f1e.pptx")
    private String pptFileUrl;

    @Schema(description = "PPT 临时下载地址（签名 URL，会过期）")
    private String downloadUrl;
}
