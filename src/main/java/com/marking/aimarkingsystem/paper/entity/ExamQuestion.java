package com.marking.aimarkingsystem.paper.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试卷题目实体，对应表 exam_question。
 *
 * 约定：
 * - questionType 与 prompt_template.question_type 对应，是"题型 -> AI评分模板"的桥梁；
 * - standardAnswer 给 AI 做语义比对，scoringRule 给 AI 做按点给分，两者配合提高主观题评分准确度；
 * - promptTemplateId 为空时，由 AI 阅卷模块按"课程+题型 -> 通用题型"的优先级自动匹配模板；
 * - 题目与知识点的多对多绑定存在 question_knowledge_rel，本实体不持有知识点字段；
 * - MyBatis-Plus 不支持多表级联，知识点信息的填充由 Service 层完成。
 */
@Data
@TableName("exam_question")
public class ExamQuestion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属试卷ID，关联 exam_paper.id */
    private Long paperId;

    /** 题号，如 1 / 一.1 */
    private String questionNo;

    /** 大题名称，如 简答题 */
    private String sectionName;

    /** 题型：1单选 2多选 3判断 4填空 5简答 6论述 7计算 8案例 */
    private Integer questionType;

    /** 题干内容 */
    private String questionContent;

    /** 标准答案/参考答案，AI评分依据 */
    private String standardAnswer;

    /** 评分细则/得分点，如 要点1(4分)；要点2(3分) */
    private String scoringRule;

    /** 本题分值 */
    private BigDecimal score;

    /** 难度：1~5，3 为中等 */
    private Integer difficulty;

    /** 评分提示词模板ID，关联 prompt_template.id，为空时按题型自动匹配 */
    private Long promptTemplateId;

    /** 题目排序号，升序展示（拖动调序时更新此字段，不依赖 id 排序） */
    private Integer sortOrder;

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
