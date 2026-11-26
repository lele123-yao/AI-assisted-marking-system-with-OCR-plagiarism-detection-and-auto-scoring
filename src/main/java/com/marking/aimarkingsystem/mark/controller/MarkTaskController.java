package com.marking.aimarkingsystem.mark.controller;

import com.marking.aimarkingsystem.common.annotation.OperationLog;
import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.mark.service.MarkTaskRecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异步阅卷任务运维接口。
 *
 * 只暴露补偿能力：死信消费者只记录日志不进重投，失败任务原本只能人工查表，
 * 这里提供手动重投入口；自动补偿由 MarkTaskRecoveryService 的定时扫描完成。
 */
@Tag(name = "阅卷任务", description = "异步批改任务的重试与补偿")
@RestController
@RequestMapping("/mark-tasks")
public class MarkTaskController {

    private final MarkTaskRecoveryService recoveryService;

    public MarkTaskController(MarkTaskRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @Operation(summary = "重试失败/死信任务",
            description = "仅允许失败(3)或死信(4)状态的任务重投；成功后任务回到待消费(0)并重新发消息")
    @OperationLog(module = "阅卷任务", action = "重试任务")
    @RequiresPermission({"ROLE_ADMIN", "ROLE_TEACHER"})
    @PostMapping("/{id}/retry")
    public Result<String> retry(@Parameter(description = "任务ID") @PathVariable Long id) {
        return Result.success("已重新投递", recoveryService.retry(id));
    }
}
