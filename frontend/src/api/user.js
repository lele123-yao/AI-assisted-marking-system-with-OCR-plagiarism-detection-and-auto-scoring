import request from './request'

/**
 * 用户管理接口（仅管理员）。
 * 后端：UserController，路径前缀 /system/users
 */

/** 分页查询用户 */
export function pageUsers(params) {
  return request.get('/system/users/page', { params })
}

/** 新增用户（可同时绑定角色） */
export function addUser(data) {
  return request.post('/system/users', data)
}

/** 启用 / 禁用用户：status 1 启用，0 禁用 */
export function updateUserStatus(id, status) {
  return request.put(`/system/users/${id}/status`, null, { params: { status } })
}

/** 重置密码 */
export function resetUserPassword(id, newPassword) {
  return request.put(`/system/users/${id}/password`, { newPassword })
}

/** 分配角色：整体覆盖，传空数组表示清空 */
export function assignUserRoles(id, roleIds) {
  return request.put(`/system/users/${id}/roles`, { roleIds })
}
