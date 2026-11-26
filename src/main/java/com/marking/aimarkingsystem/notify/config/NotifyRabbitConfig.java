package com.marking.aimarkingsystem.notify.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阅卷完成通知的 RabbitMQ 拓扑。
 *
 * 为什么用 Fanout + 匿名队列：
 * - WebSocket 会话是"实例本地"的，集群下同一个用户可能连在任意一台实例上；
 * - 若用普通共享队列，一条消息只会被一台实例抢到，其余实例的在线用户收不到；
 * - Fanout 会把消息复制给每个绑定队列，而每个实例启动时声明自己的 AnonymousQueue
 *   （非持久、独占、连接断开自动删除），天然实现"每实例一份"，即真正的广播。
 *
 * 消息可靠性由 DB 兜底：发送前先把站内信写入 notify_message，
 * 因此匿名队列随实例重启消失、离线期间漏推都不会造成消息永久丢失（可查历史）。
 */
@Configuration
public class NotifyRabbitConfig {

    /** 通知广播交换机名（durable：MQ 重启后拓扑仍在） */
    public static final String EXCHANGE_NOTIFY = "marking.notify.exchange";

    /** 通知专用容器工厂 Bean 名，@RabbitListener 显式指定，避免受全局 manual ack 影响 */
    public static final String NOTIFY_CONTAINER_FACTORY = "notifyListenerContainerFactory";

    /** Fanout 交换机：不关心 routing key，凡是绑定的队列都会收到一份 */
    @Bean
    public FanoutExchange markNotifyExchange() {
        return new FanoutExchange(EXCHANGE_NOTIFY, true, false);
    }

    /**
     * 每个实例独立的匿名队列。
     * Spring 会以 Bean 名 markNotifyFanoutQueue 注册，@RabbitListener 通过
     * #{markNotifyFanoutQueue.name} 拿到真实队列名（带 UUID 后缀）。
     */
    @Bean
    public Queue markNotifyFanoutQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Binding markNotifyBinding(Queue markNotifyFanoutQueue, FanoutExchange markNotifyExchange) {
        // Fanout 忽略 routing key，这里用空串即可
        return BindingBuilder.bind(markNotifyFanoutQueue).to(markNotifyExchange);
    }

    /**
     * 通知监听器专用容器工厂。
     *
     * 项目全局配置了 spring.rabbitmq.listener.simple.acknowledge-mode=manual，
     * 而实时推送属于"尽力而为"：消息即使推送失败（用户不在线 / 网络抖动）也无需重试，
     * 站内信已经落库。因此这里单独使用 AUTO ack，避免监听方法忘记 ack 导致
     * unacked 消息不断堆积、最终触发 prefetch 阻塞。
     */
    @Bean(NOTIFY_CONTAINER_FACTORY)
    public SimpleRabbitListenerContainerFactory notifyListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        // 推送逻辑很轻，单消费者足够；并发上限 2 防止极端情况下消息堆积
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(2);
        return factory;
    }
}
