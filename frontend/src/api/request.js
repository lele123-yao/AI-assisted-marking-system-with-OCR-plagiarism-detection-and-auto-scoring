import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearAuth } from '@/utils/auth'
import { isMobileDevice, preferDesktop } from '@/utils/device'

/**
 * Axios 全局封装
 * - 请求拦截：自动携带 Authorization: Bearer {token}
 * - 响应拦截：统一解析后端 Result { code, msg, data }
 * - 异常处理：HTTP 异常 / 业务失败统一提示，401 自动清理登录态并回到登录页
 */
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000
})

/**
 * 401 时跳转登录页（避免 request <-> router 循环依赖，直接用 location 跳转）。
 * 移动端进入 /m/login，桌面端保持 /login。
 */
function redirectToLogin() {
  clearAuth()
  const mobile = isMobileDevice() && !preferDesktop()
  const inMobileVersion = window.location.pathname.startsWith('/m')
  const targetLogin = mobile || inMobileVersion ? '/m/login' : '/login'
  if (!window.location.pathname.startsWith('/login') && !window.location.pathname.startsWith('/m/login')) {
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.href = `${targetLogin}?redirect=${redirect}`
  }
}

// ==================== 请求拦截器 ====================
service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      // 后端 JwtAuthenticationFilter 只识别 "Bearer xxx" 格式
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ==================== 响应拦截器 ====================
service.interceptors.response.use(
  (response) => {
    const res = response.data

    // 文件流等非 Result 结构直接返回
    if (!res || typeof res.code === 'undefined') {
      return res
    }

    // 业务成功：直接返回 data，调用方无需再 res.data.data
    if (res.code === 200) {
      return res.data
    }

    // 未登录 / token 失效（部分场景后端仍返回 HTTP 200）
    if (res.code === 401) {
      ElMessage.error(res.msg || '登录已过期，请重新登录')
      redirectToLogin()
      return Promise.reject(new Error(res.msg || '登录已过期'))
    }

    ElMessage.error(res.msg || '请求失败')
    return Promise.reject(new Error(res.msg || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    const msg = error.response?.data?.msg

    if (status === 401) {
      ElMessage.error(msg || '登录已过期，请重新登录')
      redirectToLogin()
    } else if (status === 403) {
      ElMessage.error(msg || '没有访问权限')
    } else if (status === 429) {
      ElMessage.warning(msg || '操作过于频繁，请稍后再试')
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络或稍后重试')
    } else if (!error.response) {
      ElMessage.error('无法连接服务器，请确认后端已启动（http://localhost:8080）')
    } else {
      ElMessage.error(msg || `请求异常（HTTP ${status}）`)
    }

    return Promise.reject(error)
  }
)

export default service
