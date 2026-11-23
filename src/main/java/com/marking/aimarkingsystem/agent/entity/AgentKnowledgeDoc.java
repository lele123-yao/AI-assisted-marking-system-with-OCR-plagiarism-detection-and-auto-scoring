package com.marking.aimarkingsystem.agent.entity;

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
 * Agent 知识库文档，对应表 agent_knowledge_doc。
 *
 * courseId 为空表示全局文档（所有课程可用）；
 * 有值时只在对应课程下被检索到，避免不同课程的教学规范互相污染。
 */
@Data
@TableName("agent_knowledge_doc")
public class AgentKnowledgeDoc implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属课程ID，NULL 表示全局 */
    private Long courseId;

    /** 文档标题 */
    private String title;

    /** 文档正文 */
    private String content;

    /** 标签，逗号分隔，参与检索 */
    private String tags;

    /** 题型，NULL 表示通用；用于按当前题型做权限过滤 */
    private Integer questionType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
