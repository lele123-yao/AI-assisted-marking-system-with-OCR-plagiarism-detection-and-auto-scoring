import { ref } from 'vue'
import { ElNotification } from 'element-plus'
import { getToken } from '@/utils/auth'
import { getUnreadCount } from '@/api/notify'
import { useUserStore } from '@/store/user'
import router from '@/router'

/**
 * 阅卷完成实时通知（模块级单例）。
 *
 * 为什么放在模块作用域而不是 Pinia：
 * - 桌面 Layout 与移动 Layout 只会挂载一个，但组件可能因路由切换重建；
 *   模块级单例能保证同一页面生命周期内只有一条 WebSocket 连接；
 * - 与 store 解耦，登出时由布局卸载调用 closeMarkNotify 主动关闭。
 *
 * 网络策略：
 * - 25 秒发送一次 ping（服务端回 pong），避免 Nginx/网关 60s 空闲超时切断连接；
 * - 断线按 2s、4s、8s… 指数退避重连，上限 30s，连上后重置；
 * - 用 eventId 去重，防止重连窗口内重复收到同一事件导致角标多加。
 *
 * 兜底：即使 WebSocket 不可用，站内信已落库，用户仍可通过铃铛列表（HTTP 接口）查看，
 * 因此这里刻意不做"补推"。
 */

const unreadCount = ref(0)
/** 最近收到的实时事件（仅内存展示用，完整历史走 HTTP 分页） */
const messages = ref([])
const connected = ref(false)

const MAX_LOCAL_MESSAGES = 20
/** 已处理事件ID，防止重复计数；超过上限整体清空即可（不需要精确淘汰） */
const seenEventIds = new Set()

let socket = null
let heartbeatTimer = null
let reconnectTimer = null
let retryCount = 0
let manualClose = false
let started = false

/** 拼接 WebSocket 地址：token 只能放 query（浏览器 WS 不支持自定义头） */
function buildWsUrl() {
  const token = getToken()
  const apiBase = import.meta.env.VITE_API_BASE_URL || '/api'
  let origin = ''
  if (/^https?:\/\//i.test(apiBase)) {
    // 生产环境显式配置了后端地址时，WS 也连同一台后端
    origin = new URL(apiBase).origin
  }
  const httpBase = origin || `${window.location.protocol}//${window.location.host}`
  const wsBase = httpBase.replace(/^http/i, 'ws')
  return `${wsBase}/ws/mark?token=${encodeURIComponent(token)}`
}

/** 未读数与服务端对齐（登录后、重连成功后调用） */
async function refreshUnreadCount() {
  try {
    const count = await getUnreadCount()
    unreadCount.value = Number(count) || 0
  } catch {
    // 接口异常已由 axios 拦截器提示，这里保持旧值，不影响连接
  }
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send('ping')
    }
  }, 25000)
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

function scheduleReconnect() {
  if (manualClose) return
  const delay = Math.min(2000 * 2 ** retryCount, 30000)
  retryCount += 1
  clearTimeout(reconnectTimer)
  reconnectTimer = setTimeout(connect, delay)
}

function connect() {
  const token = getToken()
  if (!token) return
  // 避免重复建连（CONNECTING/OPEN 都算已连）
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    return
  }
  try {
    socket = new WebSocket(buildWsUrl())
  } catch {
    scheduleReconnect()
    return
  }

  socket.onopen = () => {
    connected.value = true
    retryCount = 0
    startHeartbeat()
    // 断线期间可能有新消息落库，重连后立刻校准角标
    refreshUnreadCount()
  }

  socket.onmessage = (event) => {
    if (event.data === 'pong') return
    handleEvent(event.data)
  }

  socket.onclose = () => {
    connected.value = false
    stopHeartbeat()
    if (!manualClose) scheduleReconnect()
  }

  socket.onerror = () => {
    // 触发 close 后由 onclose 统一调度重连
    try {
      socket?.close()
    } catch {
      // 忽略
    }
  }
}

/** 处理一条下行事件 */
function handleEvent(raw) {
  let payload
  try {
    payload = JSON.parse(raw)
  } catch {
    return
  }
  if (!payload || payload.type !== 'MARK_COMPLETED') return

  if (payload.eventId) {
    if (seenEventIds.has(payload.eventId)) return
    if (seenEventIds.size > 200) seenEventIds.clear()
    seenEventIds.add(payload.eventId)
  }

  messages.value = [payload, ...messages.value].slice(0, MAX_LOCAL_MESSAGES)
  unreadCount.value += 1

  ElNotification({
    title: payload.title || '阅卷完成',
    message: payload.content || '',
    type: 'success',
    duration: 8000,
    onClick: () => {
      if (payload.bizId) router.push(resolveNotifyTarget(payload))
    }
  })
}

/**
 * 点击消息后的跳转目标（事件里的 bizId 是答卷ID，不是阅卷任务ID）：
 * - 教师/管理员（有 answer:view）→ 答卷详情；
 * - 学生 → 我的答卷（移动端有单份答卷路由，桌面端回成绩列表）。
 */
export function resolveNotifyTarget(event) {
  const bizId = event?.bizId
  const mobile = window.location.pathname.startsWith('/m')
  const userStore = useUserStore()
  if (userStore.hasPermission('answer:view')) {
    return mobile ? `/m/answer/detail/${bizId}` : `/answer/detail/${bizId}`
  }
  return mobile ? `/m/student/answer/${bizId}` : '/student/scores'
}

/** 初始化连接（幂等，可安全地在每个挂载的铃铛组件里调用） */
export function initMarkNotify() {
  if (started) return
  started = true
  manualClose = false
  refreshUnreadCount()
  connect()
}

/** 主动关闭（退出登录 / 布局卸载） */
export function closeMarkNotify() {
  manualClose = true
  started = false
  clearTimeout(reconnectTimer)
  reconnectTimer = null
  stopHeartbeat()
  if (socket) {
    // 先置空回调，避免关闭动作触发重连
    socket.onclose = null
    socket.onerror = null
    socket.onmessage = null
    socket.onopen = null
    try {
      socket.close()
    } catch {
      // 忽略
    }
    socket = null
  }
  connected.value = false
  retryCount = 0
}

/** 供组件使用的组合式入口 */
export function useMarkNotify() {
  return {
    unreadCount,
    messages,
    connected,
    initMarkNotify,
    closeMarkNotify,
    refreshUnreadCount
  }
}
