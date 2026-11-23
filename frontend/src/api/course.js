import request from './request'

/**
 * 课程管理接口（管理员 / 教师）。
 * 后端：CourseController，路径前缀 /courses
 */

/** 分页查询课程 */
export function pageCourses(params) {
  return request.get('/courses/page', { params })
}

/** 新增课程 */
export function addCourse(data) {
  return request.post('/courses', data)
}

/** 编辑课程 */
export function updateCourse(id, data) {
  return request.put(`/courses/${id}`, data)
}

/** 归档课程（课程下有未归档试卷时后端拒绝） */
export function archiveCourse(id) {
  return request.put(`/courses/${id}/archive`)
}
