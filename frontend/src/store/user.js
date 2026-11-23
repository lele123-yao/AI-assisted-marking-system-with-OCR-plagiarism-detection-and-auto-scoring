import { defineStore } from 'pinia'
import { login as loginApi } from '@/api/auth'
import { derivePermissions } from '@/config/permission'
import {
  getToken,
  setToken,
  getAuthCache,
  setAuthCache,
  clearAuth
} from '@/utils/auth'

/**
 * 用户状态：token、用户信息、角色列表、权限标识列表
 * 状态持久化在 localStorage，刷新页面不丢失。
 */
export const useUserStore = defineStore('user', {
  state: () => {
    const cache = getAuthCache()
    return {
      token: getToken(),
      userInfo: cache.userInfo,
      roles: cache.roles, // 角色编码：ADMIN / TEACHER / STUDENT
      permissions: cache.permissions // 按钮/菜单权限标识
    }
  },

  getters: {
    isLogin: (state) => !!state.token,
    nickname: (state) => state.userInfo?.nickname || state.userInfo?.username || '',
    roleNames: (state) =>
      (state.userInfo?.roles || []).map((role) => role.roleName || role.roleCode)
  },

  actions: {
    /**
     * 登录：调用后端 /auth/login，保存 token 与用户信息
     * @param {{username: string, password: string}} form
     */
    async login(form) {
      const data = await loginApi(form)
      const user = data?.user || null

      this.token = data?.token || ''
      this.userInfo = user
      this.roles = (user?.roles || []).map((role) => role.roleCode)

      // 后端若返回 permissions 则优先使用，否则按角色映射
      const backendPermissions = user?.permissions
      this.permissions =
        Array.isArray(backendPermissions) && backendPermissions.length
          ? backendPermissions
          : derivePermissions(this.roles)

      this.persist()
      return data
    },

    /** 退出登录：后端无独立登出接口，清除本地登录态即可（旧 token 会在重新登录时被挤掉） */
    logout() {
      this.token = ''
      this.userInfo = null
      this.roles = []
      this.permissions = []
      clearAuth()
    },

    /** 是否拥有某个权限标识（'*' 表示全部权限） */
    hasPermission(permission) {
      if (!permission) return true
      return this.permissions.includes('*') || this.permissions.includes(permission)
    },

    /** 是否拥有某个角色 */
    hasRole(roleCode) {
      return this.roles.includes(roleCode)
    },

    /** 持久化登录态 */
    persist() {
      setToken(this.token)
      setAuthCache({
        userInfo: this.userInfo,
        roles: this.roles,
        permissions: this.permissions
      })
    }
  }
})
