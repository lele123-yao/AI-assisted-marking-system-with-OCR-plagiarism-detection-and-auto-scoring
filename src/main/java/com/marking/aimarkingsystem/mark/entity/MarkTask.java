package com.marking.aimarkingsystem.mark.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 异步阅卷/OCR 任务实体，对应表 mark_task。
 *
 * 一条记录对应一次异步任务的全生命周期：
 * - 生产者先落库（status=0 待消费）再发 MQ；
 * - 消费者拿到消息后按 taskNo 做幂等处理，回写 start_time/status；
 * - 成功：status=2 + finish_time；失败：retry_count++，达到上限转 status=4（死信）。
 *
 * 字段约定：
 * - taskNo 是业务唯一键，用于幂等消费锚点；与模块3-2 阶段3 triggerOcr 返回的 UUID 保持同一字段；
 * - taskType 目前两个值：AI_MARK（AI 阅卷）、OCR（OCR 识别）；后续抄袭检测可扩展；
 * - status / retryCount / errorMsg 三列共同表达"任务执行态"，消费者据此决定是否重试或放弃；
 * - 时间列：createTime 自动填充（插入时），updateTime 自动填充（插入+更新时）。
 *
 * 与 exam_answer.markingStatus 的分工：
 * - exam_answer：面向"答卷当前处于哪个环节"的状态机（PENDING→PROCESSING→AI_MARKING→...），
 *   直接展示给前端用户；
 * - mark_task：面向"某次具体任务执行的成败与异常"的任务表，一份答卷可有多条任务记录
 *   （OCR 失败重试、AI 阅卷重新触发等）。
 *
 * 表结构见模块4 阶段1 的 SQL。
 */
@Data
@TableName("mark_task")
public class MarkTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编号（业务唯一，UUID），用于幂等消费与链路追踪 */
    private String taskNo;

    /** 答卷ID，关联 exam_answer.id */
    private Long answerId;

    /** 任务类型：AI_MARK AI阅卷，OCR OCR识别 */
    private String taskType;

    /** 任务状态：0 待消费，1 处理中，2 成功，3 失败待重试，4 已入死信（放弃） */
    private Integer status;

    /** 已重试次数，达到上限（默认3次）后任务转入死信 */
    private Integer retryCount;

    /** 最近一次异常信息（堆栈摘要），便于定位失败原因 */
    private String errorMsg;

    /** 开始处理时间，消费者拿到消息时回填 */
    private LocalDateTime startTime;

    /** 处理结束时间，成功或最终失败时回填 */
    private LocalDateTime finishTime;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入与更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 未删除，1 已删除 */
    @TableLogic
    private Integer delFlag;
}
