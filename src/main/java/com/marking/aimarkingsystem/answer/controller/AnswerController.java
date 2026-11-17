package com.marking.aimarkingsystem.answer.controller;

import com.marking.aimarkingsystem.answer.dto.AnswerSubmitDTO;
import com.marking.aimarkingsystem.answer.service.AnswerService;
import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 答卷提交接口（学生端）。
 *
 * 当前阶段只包含：
 * 1. 线上答卷提交：落库后立即返回答卷 id，AI 阅卷由模块4异步消费；
 * 2. 答卷附件上传：文件存入 MinIO，返回 objectKey 供后续提交使用。
 *
 * 权限：仅学生角色可访问；studentId 由 token 解析，不接受前端传入。
 * 纸质扫描答卷的提交与批改接口在后续阶段实现。
 */
@Tag(name = "答卷提交", description = "学生线上答卷提交与答卷附件上传")
@RestController
@RequestMapping("/answers")
@Validated
public class AnswerController {

    private final AnswerService answerService;

    public AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @Operation(summary = "提交线上答卷",
            description = "只保存答卷记录（来源固定 ONLINE，阅卷状态 0 待处理），返回答卷 id；"
                    + "同一学生对同一试卷重复提交会被 Redis 幂等键与数据库查重拦截")
    @RequiresPermission("ROLE_STUDENT")
    @PostMapping("/online")
    public Result<Long> submitOnline(@Valid @RequestBody AnswerSubmitDTO dto) {
        return Result.success("提交成功", answerService.submitOnline(dto));
    }

    @Operation(summary = "上传答卷附件",
            description = "支持 jpg/jpeg/png/webp/pdf，单文件不超过 20MB；"
                    + "返回 MinIO 对象键（objectKey），请随答卷一起提交，不要自行拼接访问地址")
    @RequiresPermission("ROLE_STUDENT")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadAnswerFile(
            @Parameter(description = "答卷附件文件") @RequestPart("file") MultipartFile file) {
        return Result.success("上传成功", answerService.uploadAnswerFile(file));
    }
}
