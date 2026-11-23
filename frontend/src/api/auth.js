import request from './request'

/**
 * 认证相关接口
 * 登录接口已在后端白名单中放行，无需 token。
 */
export function login(data) {
  // 返回 LoginVO：{ token, tokenType, expiresIn, user }
  return request.post('/auth/login', data)
}
