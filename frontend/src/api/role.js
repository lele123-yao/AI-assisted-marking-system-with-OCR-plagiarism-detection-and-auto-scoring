import request from './request'

/**
 * 角色管理接口（仅管理员）。
 * 后端：RoleController，路径前缀 /system/roles
 */

/** 角色列表，status 可空 */
export function listRoles(params = {}) {
  return request.get('/system/roles/list', { params })
}

/** 新增角色 */
export function createRole(data) {
  return request.post('/system/roles', data)
}

/** 修改角色 */
export function updateRole(id, data) {
  return request.put(`/system/roles/${id}`, data)
}

/** 删除角色（角色仍被用户绑定时后端会拒绝） */
export function deleteRole(id) {
  return request.delete(`/system/roles/${id}`)
}
