package com.marking.aimarkingsystem.mark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 异步任务消息体（生产者发送到 MQ 的 payload）。
 *
 * 设计取舍：消息体**只传 taskNo 一个字段**，消费者拿到后按 taskNo 查 mark_task → 查 exam_answer，
 * 完整的答卷内容、OCR objectKey、试卷 Prompt 模板等全在 DB 里。
 * 这样做的好处：
 * 1. 消息体极简（几十字节），避免"大对象 JSON 膨胀 + 网络传输慢"；
 * 2. Producer 不需要把完整查询逻辑拉进来，职责清晰（只管发、不管业务细节）；
 * 3. 消费者统一按 taskNo 查 DB，后续字段扩展（如新增 taskType）不用改消息协议。
 *
 * 代价：消费者多一次 DB 查询。对毕设场景可以接受（AI 阅卷本身就会查卷题目等数据）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkTaskMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务编号（业务唯一 UUID），消费者据此查 mark_task / exam_answer */
    private String taskNo;
}
