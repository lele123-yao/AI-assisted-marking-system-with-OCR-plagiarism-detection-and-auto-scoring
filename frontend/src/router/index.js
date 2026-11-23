import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/index.vue'
import { menuRoutes } from '@/config/menu'
import { useUserStore } from '@/store/user'

/**
 * 路由表
 * - /login 为公开页面
 * - 业务菜单路由统一挂在 Layout 下
 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true, hidden: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/Register.vue'),
    meta: { title: '注册', public: true, hidden: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      ...menuRoutes,
      {
        path: '/403',
        name: 'Forbidden',
        component: () => import('@/views/error/403.vue'),
        meta: { title: '无访问权限', hidden: true }
      },
      {
        path: '/404',
        name: 'NotFound',
        component: () => import('@/views/error/404.vue'),
        meta: { title: '页面不存在', hidden: true }
      }
    ]
  },
  {
    // 兜底：未匹配的地址统一跳 404
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// ==================== 全局前置守卫 ====================
router.beforeEach((to) => {
  const userStore = useUserStore()

  // 动态设置浏览器标题
  const baseTitle = import.meta.env.VITE_APP_TITLE || '智能阅卷系统'
  document.title = to.meta.title ? `${to.meta.title} - ${baseTitle}` : baseTitle

  // 公开页面：已登录访问登录页时重定向到首页
  if (to.meta.public) {
    return userStore.isLogin && to.path === '/login' ? { path: '/' } : true
  }

  // 未登录一律跳转登录页，并记录来源地址
  if (!userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 页面级权限校验
  if (to.meta.permission && !userStore.hasPermission(to.meta.permission)) {
    return { path: '/403' }
  }

  return true
})

export default router
