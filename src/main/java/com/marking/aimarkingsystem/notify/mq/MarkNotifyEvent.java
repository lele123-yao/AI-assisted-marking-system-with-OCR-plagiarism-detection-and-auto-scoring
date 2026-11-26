package com.marking.aimarkingsystem.notify.mq;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 阅卷完成通知事件（MQ 消息体 + WebSocket 下行报文）。
 *
 * 注意：这里**故意不包含 taskNo**。
 * RabbitMQConfig 给 RabbitTemplate 注册了 ReturnCallback，消息被退回时会把消息体里的
 * "taskNo" 解析出来并回写到 mark_task（视为投递失败）。通知事件若带同名业务字段，
 * 一旦匿名队列尚未绑定导致消息被退回，就会误把已成功的阅卷任务改成失败状态。
 *
 * occurredAt 使用字符串（ISO-8601）而不是 LocalDateTime：
 * 跨实例/跨版本传输时不依赖 Jackson 的 JavaTime 模块配置，序列化行为最稳定。
 */
@Data
public class MarkNotifyEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件唯一ID，前端用于去重（断线重连后可能重复收到同一事件） */
    private String eventId;

    /** 事件类型：MARK_COMPLETED 阅卷完成 */
    private String type;

    /** 标题，如「阅卷完成」 */
    private String title;

    /** 正文，如「试卷《期中测试》的答卷已完成 AI 阅卷，参考分 85.50 分」 */
    private String content;

    /** 业务类型：EXAM_ANSWER 答卷 */
    private String bizType;

    /** 业务主键（答卷ID） */
    private Long bizId;

    /** 试卷ID */
    private Long paperId;

    /** AI 参考分 */
    private BigDecimal score;

    /** 是否需要教师复核（存在注入嫌疑、异常分或两次评分偏差过大） */
    private Boolean needsReview;

    /** 接收人ID列表（学生 + 任课教师），Listener 据此只推送给本地命中的会话 */
    private List<Long> receiverIds;

    /** 事件发生时间（ISO-8601 字符串） */
    private String occurredAt;
}
