import request from './request'

/**
 * 学生端接口。
 * 后端：StudentPortalController，路径前缀 /student
 */

/** 我的试卷（已下发到本人班级或指定给本人） */
export function myPapers() {
  return request.get('/student/papers')
}

/** 我的成绩 */
export function myScores() {
  return request.get('/student/scores')
}
