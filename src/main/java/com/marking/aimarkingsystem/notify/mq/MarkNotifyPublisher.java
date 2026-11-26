package com.marking.aimarkingsystem.notify.mq;

import com.marking.aimarkingsystem.notify.config.NotifyRabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 通知事件发布者。
 *
 * 只负责把事件发到 Fanout 交换机：
 * - Fanout 会把消息复制给每个实例的匿名队列，因此各实例都能收到并推给本地在线用户；
 * - 发布失败（如 MQ 未启动）只记 warn，绝不影响阅卷主流程——
 *   站内信此时已经写入 DB，用户下次打开消息列表仍能看到。
 */
@Component
public class MarkNotifyPublisher {

    private static final Logger log = LoggerFactory.getLogger(MarkNotifyPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public MarkNotifyPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** 广播通知事件；routing key 对 Fanout 无意义，传空串 */
    public void publish(MarkNotifyEvent event) {
        try {
            rabbitTemplate.convertAndSend(NotifyRabbitConfig.EXCHANGE_NOTIFY, "", event);
            log.info("阅卷完成通知已广播: eventId={}, bizId={}, receivers={}",
                    event.getEventId(), event.getBizId(), event.getReceiverIds());
        } catch (Exception e) {
            // 实时推送是增强能力，失败不影响已落库的站内信与阅卷结果
            log.warn("阅卷完成通知广播失败（站内信已落库，可离线查看）: eventId={}, error={}",
                    event.getEventId(), e.getMessage());
        }
    }
}
