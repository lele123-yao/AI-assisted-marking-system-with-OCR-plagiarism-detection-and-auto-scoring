package com.marking.aimarkingsystem.notify.mq;

import com.marking.aimarkingsystem.notify.config.NotifyRabbitConfig;
import com.marking.aimarkingsystem.notify.websocket.WsSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 阅卷完成通知的 MQ 监听器。
 *
 * 监听的是本实例启动时声明的匿名队列（#{markNotifyFanoutQueue.name}），
 * Fanout 交换机保证每个实例都会收到同一份事件；这里只负责把事件推给
 * "连在本实例"的接收人会话。用户不在本实例时由其他实例的监听器推送。
 *
 * containerFactory 使用通知专用的 AUTO ack 工厂：实时推送失败无需重试，
 * 站内信已在发送前落库，用户可离线查看。
 */
@Component
public class MarkNotifyListener {

    private static final Logger log = LoggerFactory.getLogger(MarkNotifyListener.class);

    private final WsSessionRegistry sessionRegistry;

    public MarkNotifyListener(WsSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @RabbitListener(queues = "#{markNotifyFanoutQueue.name}",
            containerFactory = NotifyRabbitConfig.NOTIFY_CONTAINER_FACTORY)
    public void onMarkNotify(MarkNotifyEvent event) {
        if (event == null || event.getReceiverIds() == null || event.getReceiverIds().isEmpty()) {
            return;
        }
        // 先把接收人列表取出来，再清空：下发给客户端的报文不暴露其他接收人的用户ID
        List<Long> receiverIds = event.getReceiverIds();
        event.setReceiverIds(null);
        for (Long userId : receiverIds) {
            sessionRegistry.sendToUser(userId, event);
        }
        log.debug("阅卷完成通知已在本地推送: eventId={}, receivers={}, 本地在线用户数={}",
                event.getEventId(), receiverIds, sessionRegistry.onlineUserCount());
    }
}
