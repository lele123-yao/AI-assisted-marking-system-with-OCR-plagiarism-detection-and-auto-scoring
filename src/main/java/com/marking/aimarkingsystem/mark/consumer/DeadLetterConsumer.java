package com.marking.aimarkingsystem.mark.consumer;

import com.marking.aimarkingsystem.mark.entity.MarkTask;
import com.marking.aimarkingsystem.mark.mapper.MarkTaskMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 死信队列消费者。
 *
 * 职责：**只打日志 + ack**，不自动重投。
 *
 * 为什么不自动重投？
 * - 进死信队列的消息已经被消费端 retry_count ≥ 3 次了，重投回去只会继续失败再进死信，无限循环；
 * - 这是"毒消息"（答卷已删 / OCR 图片被覆盖 / DB 连接挂了），自动重投解决不了问题；
 * - mark_task 已经被回写为 status=4（已入死信终态）+ error_msg 里有失败原因，
 *   老师打开 mark_task 表就能看到所有死信任务，人工排查后手动重投。
 *
 * 扩展点（后续可加）：
 * - 邮件/钉钉告警：有新消息进死信队列就通知运维/老师；
 * - 定时任务扫描 status=4 的 mark_task，在后台提供"手动重投"按钮。
 *
 * 注意：RabbitMQConfig 里 DLQ 没有配置 x-dead-letter-exchange，
 * 所以 DLQ 不会把消息再投递到任何交换机——这是我们故意防无限循环的设计。
 */
@Component
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

    private final MarkTaskMapper markTaskMapper;

    public DeadLetterConsumer(MarkTaskMapper markTaskMapper) {
        this.markTaskMapper = markTaskMapper;
    }

    /**
     * 监听死信队列。
     * 死信队列的消息全是"正常消费时 retry_count ≥ 3 次仍失败"的毒消息。
     * 这里的 ack 是唯一动作——消息被消费后就被 RabbitMQ 从 DLQ 里清掉，
     * 但 mark_task 的 status=4 保留，用于人工排查。
     */
    @RabbitListener(queues = "${mq.dlq.queue:marking.dlq.queue}")
    public void consume(String messageBody, Channel channel, Message message) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 消息体里提取 taskNo
            String taskNo = extractTaskNo(message);

            MarkTask task = null;
            if (taskNo != null) {
                task = markTaskMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MarkTask>()
                                .eq("task_no", taskNo));
            }

            log.error("━━━━━ 消息进入死信队列 DLQ ━━━━━");
            log.error("  taskNo     : {}", taskNo);
            log.error("  taskType   : {}", task == null ? "(任务表已删)" : task.getTaskType());
            log.error("  answerId   : {}", task == null ? "(任务表已删)" : task.getAnswerId());
            log.error("  retryCount : {}", task == null ? "-" : task.getRetryCount());
            log.error("  errorMsg   : {}", task == null ? "-" : task.getErrorMsg());
            log.error("  messageBody: {}", messageBody.length() > 500
                    ? messageBody.substring(0, 500) + "...(truncated)" : messageBody);
            log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // ack：消息被"处理"了（仅日志），从 DLQ 清除
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("死信消费者异常: {}", e.getMessage());
            try {
                // 死信队列里的消息不能再进 DLQ，否则还是循环；
                // 所以 basicNack(requeue=false) + DLQ 没配置 DLX → 消息被丢弃
                // 这里选择直接 ack，因为"死信消费者本身失败"几乎意味着 messageBody 解析 bug，
                // 手动修复代码后重启消费者就能继续处理
                channel.basicAck(deliveryTag, false);
            } catch (IOException ignored) {
            }
        }
    }

    private String extractTaskNo(Message message) {
        try {
            String body = new String(message.getBody());
            int start = body.indexOf("\"taskNo\"");
            if (start < 0) return null;
            int colon = body.indexOf(':', start);
            int q1 = body.indexOf('"', colon + 1);
            int q2 = body.indexOf('"', q1 + 1);
            return body.substring(q1 + 1, q2);
        } catch (Exception e) {
            log.warn("从死信消息体提取 taskNo 失败: {}", e.getMessage());
            return null;
        }
    }
}
