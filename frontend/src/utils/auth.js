/**
 * 本地缓存工具：token 与登录用户信息统一从这里读写。
 * 单独抽出的目的：Axios 拦截器只依赖本地缓存，不依赖 Pinia，避免 store <-> request 循环引用。
 */
const TOKEN_KEY = 'ai_marking_token'
const USER_KEY = 'ai_marking_user'
const ROLES_KEY = 'ai_marking_roles'
const PERMISSIONS_KEY = 'ai_marking_permissions'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token || '')
}

/** 读取缓存的 JSON，解析失败时返回兜底值 */
function readJSON(key, fallback) {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : fallback
  } catch {
    return fallback
  }
}

export function getAuthCache() {
  return {
    userInfo: readJSON(USER_KEY, null),
    roles: readJSON(ROLES_KEY, []),
    permissions: readJSON(PERMISSIONS_KEY, [])
  }
}

export function setAuthCache({ userInfo, roles, permissions }) {
  localStorage.setItem(USER_KEY, JSON.stringify(userInfo ?? null))
  localStorage.setItem(ROLES_KEY, JSON.stringify(roles ?? []))
  localStorage.setItem(PERMISSIONS_KEY, JSON.stringify(permissions ?? []))
}

/** 清空全部登录态（退出登录 / token 失效时调用） */
export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(ROLES_KEY)
  localStorage.removeItem(PERMISSIONS_KEY)
}
