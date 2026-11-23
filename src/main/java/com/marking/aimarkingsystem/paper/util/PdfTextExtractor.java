package com.marking.aimarkingsystem.paper.util;

import com.marking.aimarkingsystem.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * PDF 文本提取工具。
 *
 * 只处理"有文本层"的 PDF（例如 Word 导出的试卷）。
 * 扫描件 PDF 本质是图片，提取结果为空，此时由调用方给出提示，
 * 引导老师改用拍照图片 OCR 或手动录题，而不是强行引入重量级 OCR 运行时。
 */
@Component
public class PdfTextExtractor {

    /**
     * 提取 PDF 全文。
     *
     * @param file PDF 文件
     * @return 提取出的文本（可能为空字符串，表示没有文本层）
     */
    public String extract(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 按页面位置排序，避免多栏排版文本错行
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw new BusinessException("PDF 已加密，暂不支持解析，请上传未加密的 PDF");
        } catch (IOException e) {
            throw new BusinessException("PDF 解析失败：" + e.getMessage());
        }
    }
}
