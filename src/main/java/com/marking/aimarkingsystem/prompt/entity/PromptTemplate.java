package com.marking.aimarkingsystem.prompt.entity;

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
 * 主观题 AI 评分提示词模板实体，对应表 prompt_template。
 *
 * 约定：
 * - questionType 按题型存储提示词，0 表示通用兜底；
 *   匹配优先级：题目指定 promptTemplateId > 课程+题型 > 通用+题型 > 代码内置默认；
 * - systemPrompt 定义 AI 角色与硬性评分原则，userPrompt 是评分正文模板，
 *   支持占位符 {{question}} {{standardAnswer}} {{scoringRule}} {{studentAnswer}} {{score}}，
 *   占位符替换在 AI 阅卷模块（异步批改）中实现，本模块只负责模板管理；
 * - version 由 Service 管理（修改模板时递增或另存新版本），DTO 不接收该字段；
 * - temperature 建议评分场景取 0~0.3，保证输出稳定。
 */
@Data
@TableName("prompt_template")
public class PromptTemplate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码，业务唯一，如 SHORT_ANSWER_DEFAULT */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 适用题型：1单选 2多选 3判断 4填空 5简答 6论述 7计算 8案例；0 表示通用 */
    private Integer questionType;

    /** 限定课程ID，NULL 表示全校通用 */
    private Long courseId;

    /** 系统提示词：设定AI角色与评分原则 */
    private String systemPrompt;

    /** 用户提示词模板，支持占位符 */
    private String userPrompt;

    /** 指定大模型名称，NULL 使用系统默认模型 */
    private String modelName;

    /** 采样温度，评分场景建议 0~0.3 */
    private BigDecimal temperature;

    /** 模板版本号，便于迭代与历史回溯 */
    private Integer version;

    /** 状态：1 启用，0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

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
