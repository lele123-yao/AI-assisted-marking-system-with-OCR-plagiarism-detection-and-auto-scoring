import request from './request'

/**
 * 题目管理接口（管理员 / 教师）。
 * 后端：QuestionController，路径前缀 /questions
 */

/** 查询试卷下全部题目 */
export function listQuestionsByPaper(paperId) {
  return request.get(`/questions/paper/${paperId}`)
}

/** 新增题目 */
export function addQuestion(data) {
  return request.post('/questions', data)
}

/** 编辑题目 */
export function updateQuestion(id, data) {
  return request.put(`/questions/${id}`, data)
}

/** 删除题目（后端会同步回写试卷总分与题数） */
export function deleteQuestion(id) {
  return request.delete(`/questions/${id}`)
}

/** 维护题目知识点：整体覆盖 */
export function assignKnowledge(id, knowledgeIds) {
  return request.put(`/questions/${id}/knowledge`, { knowledgeIds })
}

/**
 * 批量保存试卷题目（整体替换，仅草稿可调用）。
 * 上传解析后教师确认题目时使用；返回 { questionCount, totalScore }。
 */
export function batchSaveQuestions(data) {
  return request.post('/questions/batch', data)
}
