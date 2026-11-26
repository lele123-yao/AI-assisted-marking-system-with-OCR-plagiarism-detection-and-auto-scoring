package com.marking.aimarkingsystem.paper.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 试卷解析任务（异步）。
 *
 * 提交接口只返回 taskId 与初始状态，前端轮询本对象获取进度；
 * 任务完成后 result 里才是题目草稿，避免长耗时 OCR 占着 HTTP 连接。
 */
@Data
@Schema(description = "试卷解析任务")
public class PaperParseTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "状态：PENDING 排队 / RUNNING 识别中 / SUCCESS 成功 / FAILED 失败")
    private String status;

    @Schema(description = "进度说明")
    private String message;

    @Schema(description = "任务提交者，仅本人可查询")
    private String owner;

    @Schema(description = "待识别图片总数")
    private int total;

    @Schema(description = "已处理图片数")
    private int done;

    @Schema(description = "创建时间戳（毫秒）")
    private long createdAt;

    @Schema(description = "更新时间戳（毫秒）")
    private long updatedAt;

    @Schema(description = "解析结果，仅 SUCCESS 时存在")
    private PaperParseResult result;
}
