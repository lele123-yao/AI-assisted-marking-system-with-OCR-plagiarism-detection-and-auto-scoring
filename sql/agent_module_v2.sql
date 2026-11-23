-- ============================================================
-- Agent 模块 V2：混合检索 + 推理轨迹持久化
--
-- 只新增/扩展 Agent 模块自己的表，不修改任何业务表：
-- 1. agent_knowledge_doc 增加 question_type（按题型过滤）；
-- 2. agent_knowledge_vector 存文档向量（MySQL LONGTEXT，应用层算余弦）；
-- 3. agent_run_trace 存每一轮 Thought/Action/Observation，支持追溯审计。
--
-- 为什么不是 pgvector：
-- 当前项目是 MySQL，pgvector 是 PostgreSQL 扩展，换库会破坏现有业务。
-- 所以向量存储做成接口，默认 MySQL 实现；后续迁移 PG 只需新增适配器。
--
-- 执行：mysql -uroot -p ai_marking < sql/agent_module_v2.sql
-- ============================================================

-- ---------- 1. 知识库文档增加题型字段（幂等） ----------
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'agent_knowledge_doc'
    AND column_name = 'question_type'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE agent_knowledge_doc ADD COLUMN question_type INT NULL COMMENT ''题型，NULL 表示通用'' AFTER tags',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. 文档向量表 ----------
CREATE TABLE IF NOT EXISTS agent_knowledge_vector (
  doc_id      BIGINT       NOT NULL PRIMARY KEY COMMENT '关联 agent_knowledge_doc.id',
  model       VARCHAR(100) NULL COMMENT 'embedding 模型名',
  embedding   LONGTEXT     NOT NULL COMMENT 'JSON 数组，如 [0.12,-0.03,...]',
  update_time DATETIME     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 知识库向量（MySQL 存储，应用层余弦）';

-- ---------- 3. Agent 推理轨迹表 ----------
CREATE TABLE IF NOT EXISTS agent_run_trace (
  id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  run_no       VARCHAR(64)  NOT NULL COMMENT '一次复核/对话的唯一编号',
  agent_type   VARCHAR(32)  NOT NULL COMMENT 'MARKING_REVIEW / TEACHER_CHAT',
  answer_id    BIGINT       NULL,
  question_id  BIGINT       NULL,
  attempt_no   INT          NOT NULL DEFAULT 1 COMMENT '第几次尝试：1 初评，2/3 修正',
  round_no     INT          NOT NULL DEFAULT 0 COMMENT 'ReAct 轮次',
  thought      TEXT         NULL,
  tool_name    VARCHAR(64)  NULL,
  tool_input   TEXT         NULL,
  observation  TEXT         NULL,
  final_answer TEXT         NULL,
  success      TINYINT      NOT NULL DEFAULT 0,
  duration_ms  BIGINT       NULL,
  create_time  DATETIME     NULL,
  KEY idx_run_no (run_no),
  KEY idx_answer_id (answer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 推理轨迹，支持追溯与审计';
