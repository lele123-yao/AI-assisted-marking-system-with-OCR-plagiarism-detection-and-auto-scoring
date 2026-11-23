import request from './request'

/**
 * Agent 接口（后端 AgentController，路径前缀 /agent）。
 *
 * Agent 一次请求会串行调用多次大模型，耗时可能超过全局 30 秒超时，
 * 因此这里单独放大 timeout，避免前端先断开而后端还在跑。
 */

/** 教师助手对话：带当前答卷上下文，返回回答 + 工具调用轨迹 */
export function chatWithAgent(data) {
  return request.post('/agent/chat', data, { timeout: 180000 })
}

/** 阅卷 Agent 复核单题：返回初评、自检后的定稿与调用轨迹 */
export function reviewQuestionByAgent(data) {
  return request.post('/agent/marking/review', data, { timeout: 180000 })
}

/** 查询知识库文档（courseId 可选） */
export function listKnowledge(courseId) {
  return request.get('/agent/knowledge', { params: { courseId } })
}

/** 新增知识库文档 */
export function saveKnowledge(data) {
  return request.post('/agent/knowledge', data)
}

/** 编辑知识库文档，后端会同步重建向量 */
export function updateKnowledge(id, data) {
  return request.put(`/agent/knowledge/${id}`, data)
}

/** 删除知识库文档（逻辑删除并清理向量） */
export function deleteKnowledge(id) {
  return request.delete(`/agent/knowledge/${id}`)
}

/**
 * 重建全部文档的向量索引。
 * 未配置 embedding 时返回 0，检索仍可走纯 BM25，不算失败。
 */
export function reindexKnowledge() {
  return request.post('/agent/knowledge/reindex', null, { timeout: 120000 })
}
