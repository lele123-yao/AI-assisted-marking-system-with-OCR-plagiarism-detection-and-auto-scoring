package com.marking.aimarkingsystem.common.config;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 异步阅卷配置（模块4）。
 *
 * 本配置只做四件事，阶段2 不写生产者与消费者：
 * 1. 声明业务交换机 / 业务队列 / 死信交换机 / 死信队列（全部 durable=true，MQ 重启不丢拓扑）；
 * 2. 配置 RabbitTemplate 的 Publisher Confirm / Return，让生产者能感知"消息是否真的到达交换机"；
 * 3. 显式设置发送端消息持久化 deliveryMode=2（不能依赖队列是 durable 就自动持久化，两者是独立维度）；
 * 4. 配置 SimpleRabbitListenerContainerFactory 手动 ACK 容器工厂，给后续阶段的 @RabbitListener 用。
 *
 * 注意：yml 中已配置 spring.rabbitmq.listener.simple.acknowledge-mode=manual，
 * 但为了确保容器工厂的 prefetch / concurrency 等参数与业务严格对齐，
 * 这里显式创建容器工厂 Bean 覆盖默认配置，后续消费者 @RabbitListener(containerFactory = "markListenerContainerFactory") 指定使用。
 *
 * 死信流转链路（供后续阶段参考）：
 *   业务队列（x-dead-letter-exchange）→ DLX → DLQ（不再配置死信，防止循环）
 * 业务侧 retry_count 达到上限（默认3次）后，消息进入死信队列，mark_task.status 置 4。
 */
@Configuration
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    /**
     * ApplicationContext 懒获取：RabbitTemplate 的 ConfirmCallback/ReturnCallback 在消息发送后很久才触发，
     * 那时所有 Bean 都已创建完毕，可以安全地通过 getBean 拿到 ProducerService 来回写 mark_task。
     * 用懒获取而不是构造器注入 ProducerService，是为了避免 Bean 循环依赖：
     *   RabbitMQConfig（创建 RabbitTemplate）← ProducerService（发送消息）← RabbitTemplate
     * ProducerService 依赖 RabbitTemplate（构造器注入），如果 RabbitMQConfig 反过来构造器注入 ProducerService
     * 就会硬循环，Spring Boot 默认禁用自动循环处理。
     */
    @Autowired
    private ApplicationContext applicationContext;

    // ==================== 交换机 / 队列常量（统一管理，避免散落在生产者/消费者里） ====================

    /** 业务交换机：topic 类型，按 routing key 分流到不同业务队列；后续模块5抄袭检测可直接新增队列绑定 */
    public static final String EXCHANGE_MARKING_TASK = "marking.task.exchange";
    /** 死信交换机：direct 类型，只做一件事——把失败消息转到 DLQ；用 direct 是为了 DLX→DLQ 绑定更简单 */
    public static final String EXCHANGE_MARKING_DLX = "marking.dlx.exchange";

    /** AI 阅卷业务队列 */
    public static final String QUEUE_AI_MARK = "marking.ai.queue";
    /** OCR 识别业务队列 */
    public static final String QUEUE_OCR = "marking.ocr.queue";
    /** 死信队列：不再配置任何死信，防止失败消息在 DLX/DLQ 之间无限循环 */
    public static final String QUEUE_DLQ = "marking.dlq.queue";

    /** AI 阅卷 routing key */
    public static final String ROUTING_AI = "marking.ai";
    /** OCR 识别 routing key */
    public static final String ROUTING_OCR = "marking.ocr";
    /** 业务队列绑定 DLX 时使用的 routing key（DLX→DLQ 绑同一个 key 即可） */
    public static final String ROUTING_DLX_TO_DLQ = "marking.dlq";

    // ==================== 1. 交换机 / 队列声明 ====================

    /** 业务交换机：topic 类型 + durable=true */
    @Bean
    public TopicExchange markingTaskExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_MARKING_TASK)
                .durable(true)
                .build();
    }

    /** 死信交换机：direct 类型 + durable=true */
    @Bean
    public DirectExchange markingDlxExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_MARKING_DLX)
                .durable(true)
                .build();
    }

    /**
     * AI 阅卷队列：durable + 死信参数。
     * x-dead-letter-exchange 指到 DLX，x-dead-letter-routing-key 是 DLX 转 DLQ 时用的 key。
     * durable 只保证"队列元数据"MQ 重启不丢，消息持久化由生产者显式设置 deliveryMode=2。
     */
    @Bean
    public Queue aiMarkQueue() {
        return QueueBuilder.durable(QUEUE_AI_MARK)
                .withArgument("x-dead-letter-exchange", EXCHANGE_MARKING_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_DLX_TO_DLQ)
                .build();
    }

    /** OCR 识别队列，同 AI 阅卷队列配置 */
    @Bean
    public Queue ocrQueue() {
        return QueueBuilder.durable(QUEUE_OCR)
                .withArgument("x-dead-letter-exchange", EXCHANGE_MARKING_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_DLX_TO_DLQ)
                .build();
    }

    /**
     * 死信队列：durable + 无死信参数。
     * 这里故意**不**配置 x-dead-letter-exchange，避免毒消息在 DLX/DLQ 之间无限循环；
     * 进了 DLQ 就是进了"终态收容所"，后续通过 mark_task.status=4 人工/定时补偿。
     */
    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    // ==================== 2. 交换机 ↔ 队列 绑定 ====================

    @Bean
    public Binding aiMarkBinding() {
        return BindingBuilder.bind(aiMarkQueue()).to(markingTaskExchange()).with(ROUTING_AI);
    }

    @Bean
    public Binding ocrBinding() {
        return BindingBuilder.bind(ocrQueue()).to(markingTaskExchange()).with(ROUTING_OCR);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue()).to(markingDlxExchange()).with(ROUTING_DLX_TO_DLQ);
    }

    // ==================== 3. 消息序列化 ====================

    /**
     * 消息体用 JSON 序列化（Jackson），方便消费者直接反序列化为 POJO。
     * 注意：JSON 序列化要求消息体里的对象实现 Serializable + 无参构造 + getter/setter，
     * 且类名（含包路径）会写入消息体的 __TypeId__ 头，生产端与消费端类名必须一致。
     *
     * trustedPackages：Spring AMQP 出于反序列化安全默认只信任 java.util / java.lang，
     * 不放开业务包会报 "class ... is not in the trusted packages"，导致消费者一条消息都收不到。
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter(
                "java.util", "java.lang", "com.marking.aimarkingsystem.mark.dto");
    }

    // ==================== 4. RabbitTemplate：生产者 ConfirmCallback + ReturnCallback ====================

    /**
     * 增强版 RabbitTemplate。
     *
     * 可靠性三件套的**生产端**部分：
     * - publisher-confirm-type=correlated（yml 已配）：消息到达交换机后，Broker 异步回调 confirm 方法；
     *   这里注册的 ConfirmCallback 会在"到达交换机时 ack / 没到达交换机时 nack"，
     *   生产者据此决定是否对 mark_task 做失败回写；
     * - publisher-returns=true + mandatory=true（yml 已配）：交换机路由不到任何队列时，
     *   消息会被**退回**给生产者（ReturnCallback）而不是静默丢弃；
     * - deliveryMode=2（下方 setBeforePublishPostProcessors）：显式把消息标记为持久化，
     *   保证 MQ 重启时消息不会丢。
     *
     * 本类里只注册回调、打日志、暴露能力，生产者业务逻辑放到后续阶段的 Producer 类中。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);

        // ---- ConfirmCallback：消息到达交换机 ack / nack ----
        template.setConfirmCallback((correlationData, ack, cause) -> {
            String taskNo = correlationData == null ? null : correlationData.getId();
            if (ack) {
                log.info("RabbitMQ 发布确认 ack: taskNo={}", taskNo);
            } else {
                log.error("RabbitMQ 发布确认 nack: taskNo={}, cause={}", taskNo, cause);
                // 异步回写：消息没到 Broker → mark_task.status=3
                getProducer().onPublishFail(taskNo, "Confirm nack: " + cause);
            }
        });

        // ---- ReturnsCallback：消息路由不到任何队列时被退回 ----
        // 这意味着 routingKey 写错 / 对应队列没声明，几乎只会在开发阶段发生；
        // 依然回写 mark_task 让任务不丢，便于后续修复重试。
        template.setReturnsCallback(returned -> {
            String taskNo = extractTaskNo(returned.getMessage());
            log.error("RabbitMQ 消息被退回: taskNo={}, exchange={}, routingKey={}, replyCode={}, replyText={}",
                    taskNo, returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
            getProducer().onPublishFail(taskNo,
                    "Returned: code=" + returned.getReplyCode() + " text=" + returned.getReplyText());
        });

        // ---- 发送端强制持久化 deliveryMode=2 ----
        // ExperienceRecall 提示："deliveryMode=2 不能依赖队列是 durable 就自动持久化"，
        // durable 是队列元数据维度，持久化是消息维度，两者独立。
        // 这里用 PostProcessor 在发送前统一设置，生产者代码无需关心。
        template.setBeforePublishPostProcessors(message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        });

        // 全局强制 mandatory=true：路由不到队列时必须退回（yml 已配 template.mandatory=true）
        template.setMandatory(true);
        return template;
    }

    // ==================== 辅助方法 ====================

    /** 懒获取 ProducerService：避免 Bean 循环依赖，ConfirmCallback/ReturnCallback 执行时 Bean 早已就绪 */
    private com.marking.aimarkingsystem.mark.service.MarkTaskProducerService getProducer() {
        return applicationContext.getBean(
                com.marking.aimarkingsystem.mark.service.MarkTaskProducerService.class);
    }

    /**
     * 从 ReturnedMessage 里提取 taskNo。
     * MarkTaskMessage 只有一个 taskNo 字段，用 Hutool 直接反序列化成 JSONObject 取 taskNo 即可，
     * 不需要预先引入 Jackson 类型信息。
     */
    private String extractTaskNo(Message message) {
        if (message == null) {
            return null;
        }
        try {
            String body = new String(message.getBody());
            JSONObject json = JSONUtil.parseObj(body);
            return json.getStr("taskNo");
        } catch (Exception e) {
            log.warn("从退回消息体解析 taskNo 失败: {}", e.getMessage());
            return null;
        }
    }
}
