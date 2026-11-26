package com.marking.aimarkingsystem.notify.controller;

import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.notify.entity.NotifyMessage;
import com.marking.aimarkingsystem.notify.service.NotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知接口（所有登录用户）。
 *
 * 只操作"当前登录用户自己的消息"，因此不额外声明 @RequiresPermission：
 * 学生、教师、管理员都应能查看自己的阅卷完成通知。
 */
@Tag(name = "站内通知", description = "阅卷完成通知分页、未读数、标记已读")
@RestController
@RequestMapping("/notify")
@Validated
public class NotifyController {

    private final NotifyService notifyService;

    public NotifyController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @Operation(summary = "当前用户消息分页", description = "按创建时间倒序，仅返回当前登录用户的消息")
    @GetMapping("/page")
    public Result<PageResult<NotifyMessage>> page(
            @Parameter(description = "页码，从 1 开始")
            @RequestParam(defaultValue = "1") @Min(1) Long current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) Long size) {
        return Result.success(notifyService.page(current, size));
    }

    @Operation(summary = "未读消息数")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(notifyService.unreadCount());
    }

    @Operation(summary = "标记单条已读")
    @PostMapping("/read/{id}")
    public Result<Void> read(@PathVariable Long id) {
        notifyService.markRead(id);
        return Result.success();
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public Result<Void> readAll() {
        notifyService.markAllRead();
        return Result.success();
    }
}
