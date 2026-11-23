package com.marking.aimarkingsystem.paper.util;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 可插拔 OCR 客户端：把图片 POST 到配置的 HTTP 接口，取回识别文本。
 *
 * 为什么不做成强依赖：
 * - Tesseract 等本地 OCR 需要额外安装运行时，部署成本高；
 * - 毕设阶段优先保证主流程可用：未配置 ocr-url 时图片仍上传成功，
 *   解析结果提示"请手动录入题目"，不阻塞建卷。
 *
 * 兼容三种常见响应格式：
 * - {"text": "..."}
 * - {"data": {"text": "..."}}
 * - {"result": "..."}
 */
@Slf4j
@Component
public class OcrClient {

    /** OCR 接口地址，为空表示未配置 */
    @Value("${paper-parse.ocr-url:}")
    private String ocrUrl;

    /** OCR 接口鉴权 Key，为空则不发送鉴权头 */
    @Value("${paper-parse.ocr-api-key:}")
    private String ocrApiKey;

    /** 单次识别超时：30 秒 */
    private static final int TIMEOUT_MS = 30_000;

    /** 是否已配置 OCR 服务 */
    public boolean isConfigured() {
        return StringUtils.hasText(ocrUrl);
    }

    /**
     * 识别单张图片。
     *
     * @param file 图片文件
     * @return 识别文本
     * @throws BusinessException OCR 未配置或识别失败
     */
    public String recognize(MultipartFile file) {
        if (!isConfigured()) {
            throw new BusinessException("未配置 OCR 接口，请手动录入题目");
        }
        try {
            HttpRequest request = HttpRequest.post(ocrUrl)
                    .timeout(TIMEOUT_MS)
                    .form("file", file.getBytes(), file.getOriginalFilename());
            if (StringUtils.hasText(ocrApiKey)) {
                request.header("X-API-Key", ocrApiKey);
            }

            try (HttpResponse response = request.execute()) {
                String body = response.body();
                if (!response.isOk()) {
                    throw new BusinessException("OCR 识别失败[" + response.getStatus() + "]：" + body);
                }
                return extractText(body);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("读取图片失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("OCR 调用异常: url={}", ocrUrl, e);
            throw new BusinessException("OCR 调用异常：" + e.getMessage());
        }
    }

    /** 从 OCR 响应中提取文本，兼容 text / data.text / result 三种字段 */
    private String extractText(String body) {
        if (!JSONUtil.isTypeJSON(body)) {
            return body == null ? "" : body;
        }
        JSONObject json = JSONUtil.parseObj(body);
        String text = json.getStr("text");
        if (!StringUtils.hasText(text)) {
            JSONObject data = json.getJSONObject("data");
            if (data != null) {
                text = data.getStr("text");
            }
        }
        if (!StringUtils.hasText(text)) {
            text = json.getStr("result");
        }
        return text == null ? "" : text;
    }
}
