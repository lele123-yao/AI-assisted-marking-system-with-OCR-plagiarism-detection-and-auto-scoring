/**
 * 设备与访问环境识别。
 *
 * 只用于前端路由分流与提示，不参与任何业务判断：
 * - 移动端自动进入 /m/** 移动版；
 * - 桌面端默认保持原有布局；
 * - 用户手动切换后写入 localStorage，刷新仍生效。
 */

const MOBILE_UA = /Android|iPhone|iPad|iPod|HarmonyOS|Windows Phone|Mobile/i

/** 是否微信内置浏览器 */
export function isWeChat() {
  return /MicroMessenger/i.test(navigator.userAgent)
}

/** 是否移动设备（UA 或窄屏） */
export function isMobileDevice() {
  if (MOBILE_UA.test(navigator.userAgent)) return true
  if (typeof window.matchMedia === 'function') {
    return window.matchMedia('(max-width: 768px)').matches
  }
  return window.innerWidth <= 768
}

/** 用户是否主动选择「电脑版」 */
export function preferDesktop() {
  return localStorage.getItem('PREFER_DESKTOP') === '1'
}

/** 记住版本选择：true 电脑版，false 手机版 */
export function setPreferDesktop(value) {
  localStorage.setItem('PREFER_DESKTOP', value ? '1' : '0')
}
