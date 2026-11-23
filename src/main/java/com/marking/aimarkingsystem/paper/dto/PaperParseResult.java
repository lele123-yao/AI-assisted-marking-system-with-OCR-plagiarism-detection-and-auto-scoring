package com.marking.aimarkingsystem.paper.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 试卷上传解析结果。
 *
 * 职责：
 * - pdfUrl / imageUrls：文件已上传 MinIO，返回对象名供建卷时一并保存；
 * - extractedText：OCR/PDF 提取出的原始文本，前端可用于核对；
 * - source：题目来源 LLM（大模型结构化）/ RULE（规则拆分）/ MANUAL（未识别到文本）；
 * - warnings：非致命提示（未配置 OCR、扫描件无文本层、大模型失败已降级等）；
 * - questions：可直接进入前端可编辑表格的题目草稿。
 */
@Data
@Schema(description = "试卷上传解析结果")
public class PaperParseResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "PDF 对象名，未上传 PDF 时为空")
    private String pdfUrl;

    @Schema(description = "图片对象名列表，未上传图片时为空")
    private List<String> imageUrls;

    @Schema(description = "提取/识别出的原始文本")
    private String extractedText;

    @Schema(description = "题目来源：LLM / RULE / MANUAL", example = "LLM")
    private String source;

    @Schema(description = "非致命提示信息")
    private List<String> warnings;

    @Schema(description = "解析出的题目草稿")
    private List<ParsedQuestionDTO> questions;
}
