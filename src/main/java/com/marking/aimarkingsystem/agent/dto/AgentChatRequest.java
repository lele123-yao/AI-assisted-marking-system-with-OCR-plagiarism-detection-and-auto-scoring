package com.marking.aimarkingsystem.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 教师助手对话入参。
 *
 * history 由前端回传（服务端不存会话），保持无状态、可水平扩容；
 * 服务端只取最近几轮拼进 prompt，避免上下文无限增长。
 */
@Data
@Schema(description = "教师助手对话请求")
public class AgentChatRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "答卷ID不能为空")
    @Schema(description = "当前查看的答卷ID", example = "101")
    private Long answerId;

    @NotBlank(message = "问题不能为空")
    @Schema(description = "教师问题", example = "第15题为什么只给了8分？")
    private String message;

    @Schema(description = "历史对话（可选，按时间正序）")
    private List<ChatTurn> history;

    @Data
    @Schema(description = "一轮历史对话")
    public static class ChatTurn implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "角色：user / assistant", example = "user")
        private String role;

        @Schema(description = "内容")
        private String content;
    }
}
