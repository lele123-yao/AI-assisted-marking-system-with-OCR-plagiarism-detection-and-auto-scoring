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
 * 知识点标签实体，对应表 question_knowledge。
 *
 * 约定：
 * - parent_id + level 构成树形结构（如 树与二叉树 -> 二叉树遍历），
 *   供后续【学情分析】模块按章节/知识点聚合得分率；
 * - 树的组装（父子节点嵌套）由 Service 层完成，实体只存平铺字段；
 * - 知识点按课程隔离，不同课程的同名知识点互不干扰。
 */
@Data
@TableName("question_knowledge")
public class QuestionKnowledge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识点编码，可选，如 DS0201 */
    private String knowledgeCode;

    /** 知识点名称 */
    private String knowledgeName;

    /** 所属课程ID，关联 course.id */
    private Long courseId;

    /** 父知识点ID，0 表示根节点 */
    private Long parentId;

    /** 层级：1 一级、2 二级… */
    private Integer level;

    /** 知识点说明 */
    private String description;

    /** 同级排序号 */
    private Integer sort;

    /** 状态：1 启用，0 停用 */
    private Integer status;

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
