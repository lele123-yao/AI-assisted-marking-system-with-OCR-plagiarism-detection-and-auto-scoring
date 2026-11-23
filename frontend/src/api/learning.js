import request from './request'

/**
 * 学情智能分析接口（教师 / 管理员，学生卡片除外）。
 * 后端：LearningAnalysisController，路径前缀 /learning
 */

/** 试卷班级学情统计（整体指标、班级对比、高频错题、知识点得分率） */
export function getLearningStatistics(paperId) {
  return request.get(`/learning/papers/${paperId}/statistics`)
}

/**
 * 触发 AI 生成教学讲评建议。
 * @param {{paperId: number, reportTitle?: string}} data
 */
export function generateAnalysis(data) {
  return request.post('/learning/reports', data)
}

/** 生成讲评 PPT 并返回短时效下载地址 */
export function generatePpt(reportId) {
  return request.post(`/learning/reports/${reportId}/ppt`)
}

/** 学生个人学情卡片 */
export function getStudentCard(paperId, studentId) {
  return request.get(`/learning/papers/${paperId}/students/${studentId}/card`)
}
