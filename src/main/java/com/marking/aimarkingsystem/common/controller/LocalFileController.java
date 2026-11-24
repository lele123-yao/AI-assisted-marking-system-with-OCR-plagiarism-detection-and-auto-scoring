package com.marking.aimarkingsystem.common.controller;

import com.marking.aimarkingsystem.common.util.MinioUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 文件访问入口。
 *
 * 正常环境走 MinIO 预签名地址；MinIO 不可用时（演示环境）文件落在本地磁盘，
 * 由本接口按对象键直出，前端拿到的地址形如 /api/files/answer/2026/11/23/xxx.jpg。
 */
@Tag(name = "文件访问", description = "本地文件存储回退")
@RestController
@RequestMapping("/files")
public class LocalFileController {

    private final MinioUtil minioUtil;

    public LocalFileController(MinioUtil minioUtil) {
        this.minioUtil = minioUtil;
    }

    @Operation(summary = "读取文件", description = "按对象键读取文件，用于 MinIO 不可用时的本地存储回退")
    @GetMapping("/**")
    public ResponseEntity<Resource> serve(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int index = uri.indexOf("/files/");
        String objectName = index < 0 ? "" : uri.substring(index + "/files/".length());
        if (!StringUtils.hasText(objectName)) {
            return ResponseEntity.notFound().build();
        }
        try (InputStream in = minioUtil.download(objectName)) {
            byte[] bytes = in.readAllBytes();
            String contentType = Files.probeContentType(Paths.get(objectName));
            MediaType mediaType = StringUtils.hasText(contentType)
                    ? MediaType.parseMediaType(contentType)
                    : MediaType.APPLICATION_OCTET_STREAM;
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(new ByteArrayResource(bytes));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
