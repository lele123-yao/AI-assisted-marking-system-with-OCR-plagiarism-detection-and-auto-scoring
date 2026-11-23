import request from './request'

/**
 * 班级管理接口（管理员 / 教师）。
 * 后端：ClassController，路径前缀 /class
 */

/** 分页查询班级（教师只返回自己的班级） */
export function pageClasses(params) {
  return request.get('/class/page', { params })
}

/** 新增班级 */
export function addClass(data) {
  return request.post('/class', data)
}

/** 编辑班级 */
export function updateClass(id, data) {
  return request.put(`/class/${id}`, data)
}

/** 删除班级（同时移出班级学生关系） */
export function removeClass(id) {
  return request.delete(`/class/${id}`)
}

/** 班级下拉选项 */
export function listClassOptions() {
  return request.get('/class/options')
}

/** 班级学生列表 */
export function listClassStudents(classId) {
  return request.get(`/class/${classId}/students`)
}

/** 学生候选：onlyAvailable=true 只返回未入班学生 */
export function listStudentOptions(params = {}) {
  return request.get('/class/student-options', { params })
}

/** 批量添加学生 */
export function addClassStudents(classId, studentIds) {
  return request.post(`/class/${classId}/students`, { studentIds })
}

/** 将学生移出班级 */
export function removeClassStudent(classId, studentId) {
  return request.delete(`/class/${classId}/students/${studentId}`)
}

/**
 * Excel 名单导入班级。
 * Excel 表头：学号、姓名；autoCreateUser=true 时自动创建缺失的学生账号（初始密码为学号后 6 位）。
 */
export function importClassStudents(classId, file, autoCreateUser) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('autoCreateUser', autoCreateUser)
  return request.post(`/class/${classId}/students/import`, formData)
}
