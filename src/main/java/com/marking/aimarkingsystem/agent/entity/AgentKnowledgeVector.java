package com.marking.aimarkingsystem.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档向量，对应表 agent_knowledge_vector。
 *
 * 向量以 JSON 字符串存 MySQL LONGTEXT：
 * - 不引入 pgvector / 向量库，避免换数据源；
 * - 知识库规模小（几百篇），检索时按候选集取出、应用层算余弦足够；
 * - 需要扩容时替换 VectorStoreService 的实现即可，上层无感知。
 */
@Data
@TableName("agent_knowledge_vector")
public class AgentKnowledgeVector implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private Long docId;

    private String model;

    /** JSON 数组字符串，例如 [0.12,-0.03,...] */
    private String embedding;

    private LocalDateTime updateTime;
}
