import request from './request'

/**
 * 抄袭检测接口（教师 / 管理员）。
 * 后端：PlagiarismController，路径前缀 /plagiarism
 */

/** 为试卷下答卷批量生成 SimHash 指纹 */
export function generateFingerprints(paperId) {
  return request.post(`/plagiarism/papers/${paperId}/fingerprints`)
}

/** 生成或刷新试卷级抄袭报告 */
export function generateReport(paperId) {
  return request.post(`/plagiarism/papers/${paperId}/report`)
}

/** 查询试卷级抄袭报告（优先读缓存） */
export function getReport(paperId) {
  return request.get(`/plagiarism/papers/${paperId}/report`)
}

/** 查询单份答卷的查重报告 */
export function getAnswerReport(answerId) {
  return request.get(`/plagiarism/answers/${answerId}`)
}
