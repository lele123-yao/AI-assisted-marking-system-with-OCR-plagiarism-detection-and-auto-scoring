import request from './request'

/**
 * 试卷下发接口（管理员 / 教师）。
 * 后端：PaperAssignmentController，路径前缀 /paper-assignment
 */

/** 下发试卷：classId 与 studentIds 可同时传，deadline 可空 */
export function assignPaper(data) {
  return request.post('/paper-assignment', data)
}

/** 查看某试卷的下发记录 */
export function pagePaperAssignments(paperId) {
  return request.get(`/paper-assignment/paper/${paperId}`)
}

/** 撤销下发 */
export function cancelAssignment(id) {
  return request.delete(`/paper-assignment/${id}`)
}
