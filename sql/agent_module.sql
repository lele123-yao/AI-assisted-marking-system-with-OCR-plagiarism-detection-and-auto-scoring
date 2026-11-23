-- ============================================================
-- Agent 模块：知识库文档表
--
-- 用途：
-- 1. 存放教师沉淀的评分规范、知识点讲解、讲评建议；
-- 2. 教师助手 Agent 通过 search_knowledge 工具做检索增强（RAG），
--    先召回文档再生成回答，减少大模型凭空编造。
--
-- 检索实现说明：
-- 不依赖向量库 / Embedding 服务，当前用"中文二元组分词 + BM25"做词法召回，
-- 零外部依赖、可离线运行；后续接入 Embedding 时只需替换 KnowledgeBaseService.search。
--
-- 执行方式：
--   mysql -uroot -p ai_marking < sql/agent_module.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS agent_knowledge_doc (
  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  course_id   BIGINT       NULL COMMENT '所属课程ID，NULL 表示全局通用',
  title       VARCHAR(200) NOT NULL COMMENT '文档标题',
  content     TEXT         NOT NULL COMMENT '文档正文',
  tags        VARCHAR(300) NULL COMMENT '标签，逗号分隔',
  create_time DATETIME     NULL,
  update_time DATETIME     NULL,
  del_flag    TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未删除，1 已删除',
  KEY idx_course_id (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 知识库文档';

-- 预置几篇全局文档，保证首次演示时知识库不为空（重复执行不会插重）
INSERT INTO agent_knowledge_doc (course_id, title, content, tags, create_time, update_time, del_flag)
SELECT NULL,
       '主观题评分通用规范',
       '主观题按得分点给分，每个得分点独立判断是否命中。计算题遵循"步骤分优先"：公式/定理正确但结果错误，给步骤分；结果正确但缺少关键步骤，酌情扣分。简答题按关键词或要点命中数量给分，表述不清但意思正确可给部分分。评语必须写清"命中哪些点、缺哪些点、为什么扣分"，不能只给分数。',
       '评分规范,主观题,步骤分',
       NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM agent_knowledge_doc WHERE title = '主观题评分通用规范');

INSERT INTO agent_knowledge_doc (course_id, title, content, tags, create_time, update_time, del_flag)
SELECT NULL,
       '数学解答题常见扣分点',
       '数学解答题常见扣分点：1. 缺少必要文字说明或"由题意得"等逻辑衔接；2. 公式写对但代入错误；3. 计算过程跳步导致关键步骤缺失；4. 结果未化简、未写单位、未注明定义域或取值范围；5. 分类讨论不完整，漏掉边界情况；6. 使用未证明的结论。讲评时应优先展示标准解法与典型错解对比。',
       '数学,解答题,扣分点',
       NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM agent_knowledge_doc WHERE title = '数学解答题常见扣分点');

INSERT INTO agent_knowledge_doc (course_id, title, content, tags, create_time, update_time, del_flag)
SELECT NULL,
       '椭圆标准方程与几何性质',
       '椭圆标准方程 x²/a² + y²/b² = 1（a>b>0），焦点在 x 轴；若焦点在 y 轴则 x²/b² + y²/a² = 1。核心关系 a²=b²+c²，离心率 e=c/a（0<e<1）。常见错误：把 a、b 的位置写反；忘记 a>b>0 的条件；求离心率时把 c 与 b 混淆；写范围时遗漏端点。讲评建议：先画图确定焦点位置，再写方程，最后用 a²=b²+c² 校验。',
       '数学,椭圆,解析几何',
       NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM agent_knowledge_doc WHERE title = '椭圆标准方程与几何性质');

INSERT INTO agent_knowledge_doc (course_id, title, content, tags, create_time, update_time, del_flag)
SELECT NULL,
       '讲评课设计建议',
       '讲评课按"数据定位—典型错例—方法归纳—变式训练"四步组织：1. 先用得分率定位薄弱题，不逐题平均讲；2. 展示典型错解，让学生先找错因；3. 归纳可迁移的方法与易错点；4. 配 1~2 道变式题当堂检测。讲评材料应包含原题、标准答案、评分细则、班级得分率和高频错误，避免只报分数。',
       '讲评,学情分析,教学设计',
       NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM agent_knowledge_doc WHERE title = '讲评课设计建议');
