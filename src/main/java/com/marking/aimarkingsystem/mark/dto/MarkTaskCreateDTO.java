package com.marking.aimarkingsystem.mark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 异步任务创建 DTO。
 *
 * 生产者落库（mark_task.status=0）+ 发 MQ 前的请求体，
 * 业务侧（AnswerService / PaperAnswerService）在触发 AI 阅卷或 OCR 识别时构造此 DTO。
 *
 * 字段说明：
 * - taskNo：业务侧生成 UUID 后传入，保证幂等；
 * - answerId：答卷 ID，关联 exam_answer；
 * - taskType：AI_MARK / OCR，枚举值由业务侧控制；
 * 其余字段（status=0、retryCount=0）在 Service 内部设默认值，不暴露给调用方。
 */
@Data
public class MarkTaskCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务编号（业务唯一 UUID），生产者生成后传入，用于幂等消费 */
    @NotBlank(message = "任务编号不能为空")
    private String taskNo;

    /** 答卷ID */
    @NotNull(message = "答卷ID不能为空")
    private Long answerId;

    /** 任务类型：AI_MARK AI阅卷，OCR OCR识别 */
    @NotBlank(message = "任务类型不能为空")
    private String taskType;
}
