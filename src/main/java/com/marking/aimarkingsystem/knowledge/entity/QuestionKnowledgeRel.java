package com.marking.aimarkingsystem.knowledge.entity;

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
 * 题目-知识点关联实体，对应表 question_knowledge_rel（多对多桥表）。
 *
 * 约定：
 * - 与 sys_user_role 同类：不承载业务数据的关联表；
 * - 当前建表 SQL 未加 (question_id, knowledge_id) 唯一索引，重复绑定由 Service 层校验；
 * - 若后续补充唯一索引，则必须改为物理删除（参考 UserRoleMapper 的做法），
 *   否则逻辑删除残留记录会在重复绑定时与唯一键冲突。
 */
@Data
@TableName("question_knowledge_rel")
public class QuestionKnowledgeRel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 题目ID，关联 exam_question.id */
    private Long questionId;

    /** 知识点ID，关联 question_knowledge.id */
    private Long knowledgeId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
