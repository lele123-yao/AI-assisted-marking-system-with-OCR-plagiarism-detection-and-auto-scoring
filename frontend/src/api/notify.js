import request from './request'

/**
 * 站内通知接口。
 *
 * 后端只返回"当前登录用户自己的"消息（Service 层强制带 user_id 条件），
 * 因此前端无需传用户标识。
 */

/** 当前用户消息分页 */
export function pageNotify(params) {
  return request.get('/notify/page', { params })
}

/** 未读消息数 */
export function getUnreadCount() {
  return request.get('/notify/unread-count')
}

/** 标记单条已读 */
export function readNotify(id) {
  return request.post(`/notify/read/${id}`)
}

/** 全部标记已读 */
export function readAllNotify() {
  return request.post('/notify/read-all')
}
