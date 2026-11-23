package com.marking.aimarkingsystem.answer.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 答卷逐题结果，对应表 exam_answer_item。
 *
 * 为什么要单独建表：
 * - AI 阅卷会给出逐题分数与评语，教师复核又会改写分数与评语；
 *   若继续塞在 exam_answer.answer_content 里，教师保存时会把 AI 评语覆盖掉；
 * - 这里把 AI 结果与教师结果分成两组列，各自 upsert，互不覆盖，
 *   AI 任务重试也不会冲掉教师已确认的成绩。
 *
 * 字段约定：
 * - aiScore / aiComment：AI 异步阅卷写入的参考分与评语；
 * - finalScore / finalComment：教师人工复核后写入的最终分与最终评语；
 * - (answerId, questionId) 唯一，一条答卷的同一道题只有一行。
 */
@Data
@TableName("exam_answer_item")
public class ExamAnswerItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 答卷ID，关联 exam_answer.id */
    private Long answerId;

    /** 题目ID，关联 exam_question.id */
    private Long questionId;

    /** AI 参考分 */
    private BigDecimal aiScore;

    /** AI 评语 */
    private String aiComment;

    /** 教师最终分 */
    private BigDecimal finalScore;

    /** 教师最终评语 */
    private String finalComment;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入与更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
