import request from './request'

/**
 * 成绩复核、统计与导出接口（教师 / 管理员）。
 * 后端：GradeController，路径前缀 /grades
 */

/**
 * 成绩复核。
 * @param {{answerId: number, reviewAction: 1|2|3, finalScore?: number, items?: Array, reviewRemark?: string}} data
 *        reviewAction：1 通过，2 改分，3 驳回重批
 */
export function reviewGrade(data) {
  return request.post('/grades/reviews', data)
}

/** 试卷成绩统计（分数段、逐题统计等） */
export function getGradeStatistics(paperId) {
  return request.get(`/grades/papers/${paperId}/statistics`)
}

/**
 * 导出成绩单。
 * 后端直接返回 xlsx 文件流（非 Result），因此 responseType 必须为 blob。
 * request.js 响应拦截器会原样返回 Blob。
 */
export function exportGrades(paperId) {
  return request.get(`/grades/papers/${paperId}/export`, { responseType: 'blob' })
}
