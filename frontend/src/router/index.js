import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/index.vue'
import MobileLayout from '@/layout/mobile/MobileLayout.vue'
import { menuRoutes } from '@/config/menu'
import { useUserStore } from '@/store/user'
import { isMobileDevice, preferDesktop } from '@/utils/device'

/**
 * 路由表
 * - /login、/register 为公开页面
 * - 桌面业务菜单路由统一挂在 Layout 下
 * - /m/** 为移动端（微信可访问）页面，挂在 MobileLayout 下
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

  // ==================== 移动端 H5 ====================
  {
    path: '/m/login',
    name: 'MobileLogin',
    component: () => import('@/views/m/login.vue'),
    meta: { title: '登录', public: true, hidden: true }
  },
  {
    path: '/m/register',
    name: 'MobileRegister',
    component: () => import('@/views/m/register.vue'),
    meta: { title: '注册', public: true, hidden: true }
  },
  {
    path: '/m',
    component: MobileLayout,
    redirect: '/m/home',
    children: [
      {
        path: 'home',
        name: 'MobileHome',
        component: () => import('@/views/m/home.vue'),
        meta: { title: '首页', permission: 'dashboard:view' }
      },
      {
        path: 'menu',
        name: 'MobileMenu',
        component: () => import('@/views/m/menu.vue'),
        meta: { title: '全部功能' }
      },
      {
        path: 'profile',
        name: 'MobileProfile',
        component: () => import('@/views/m/profile.vue'),
        meta: { title: '我的' }
      },
      {
        path: 'course',
        name: 'MobileCourse',
        component: () => import('@/views/m/course/index.vue'),
        meta: { title: '课程管理', permission: 'course:view' }
      },
      {
        path: 'class',
        name: 'MobileClass',
        component: () => import('@/views/m/class/index.vue'),
        meta: { title: '班级管理', permission: 'class:view' }
      },
      {
        path: 'paper',
        name: 'MobilePaper',
        component: () => import('@/views/m/paper/index.vue'),
        meta: { title: '试卷管理', permission: 'paper:view' }
      },
      {
        path: 'question',
        name: 'MobileQuestion',
        component: () => import('@/views/m/question/index.vue'),
        meta: { title: '主观题目', permission: 'question:view' }
      },
      {
        path: 'prompt',
        name: 'MobilePrompt',
        component: () => import('@/views/m/prompt/index.vue'),
        meta: { title: 'Prompt模板', permission: 'prompt:view' }
      },
      {
        path: 'answer/submit',
        name: 'MobileAnswerSubmit',
        component: () => import('@/views/m/answer/submit.vue'),
        meta: { title: '在线作答', permission: 'answer:submit' }
      },
      {
        path: 'answer/list',
        name: 'MobileAnswerList',
        component: () => import('@/views/m/answer/list.vue'),
        meta: { title: '答卷列表', permission: 'answer:view' }
      },
      {
        path: 'answer/detail/:id',
        name: 'MobileAnswerDetail',
        component: () => import('@/views/m/answer/detail.vue'),
        meta: { title: '答卷详情', permission: 'answer:view', hidden: true }
      },
      {
        path: 'student/papers',
        name: 'MobileStudentPapers',
        component: () => import('@/views/m/student/papers.vue'),
        meta: { title: '我的试卷', permission: 'student:paper' }
      },
      {
        path: 'student/scores',
        name: 'MobileStudentScores',
        component: () => import('@/views/m/student/scores.vue'),
        meta: { title: '我的成绩', permission: 'student:score' }
      },
      {
        path: 'student/answer/:id',
        name: 'MobileStudentAnswerDetail',
        component: () => import('@/views/m/answer/detail.vue'),
        meta: { title: '我的答卷', permission: 'student:score' }
      },
      {
        path: 'paper-answer/roster',
        name: 'MobilePaperRoster',
        component: () => import('@/views/m/paper-answer/roster.vue'),
        meta: { title: '名单导入', permission: 'paper-answer:import' }
      },
      {
        path: 'paper-answer/upload',
        name: 'MobilePaperUpload',
        component: () => import('@/views/m/paper-answer/upload.vue'),
        meta: { title: '图片上传/OCR', permission: 'paper-answer:image' }
      },
      {
        path: 'paper-answer/score',
        name: 'MobilePaperScore',
        component: () => import('@/views/m/paper-answer/score.vue'),
        meta: { title: '教师录分', permission: 'paper-answer:score' }
      },
      {
        path: 'mark/task',
        name: 'MobileMarkTask',
        component: () => import('@/views/m/mark/task.vue'),
        meta: { title: '任务监控', permission: 'mark:view' }
      },
      {
        path: 'mark/detail/:id',
        name: 'MobileMarkDetail',
        component: () => import('@/views/m/mark/detail.vue'),
        meta: { title: '阅卷详情', permission: 'mark:view', hidden: true }
      },
      {
        path: 'agent/knowledge',
        name: 'MobileAgentKnowledge',
        component: () => import('@/views/m/agent/knowledge.vue'),
        meta: { title: '知识库管理', permission: 'mark:view' }
      },
      {
        path: 'plagiarism/compare',
        name: 'MobilePlagiarismCompare',
        component: () => import('@/views/m/plagiarism/compare.vue'),
        meta: { title: '相似度比对', permission: 'plagiarism:view' }
      },
      {
        path: 'plagiarism/report',
        name: 'MobilePlagiarismReport',
        component: () => import('@/views/m/plagiarism/report.vue'),
        meta: { title: '抄袭报告', permission: 'plagiarism:view' }
      },
      {
        path: 'grade/review',
        name: 'MobileGradeReview',
        component: () => import('@/views/m/grade/review.vue'),
        meta: { title: '成绩复核', permission: 'grade:review' }
      },
      {
        path: 'grade/statistics',
        name: 'MobileGradeStatistics',
        component: () => import('@/views/m/grade/statistics.vue'),
        meta: { title: '成绩统计', permission: 'grade:view' }
      },
      {
        path: 'grade/export',
        name: 'MobileGradeExport',
        component: () => import('@/views/m/grade/export.vue'),
        meta: { title: '成绩导出', permission: 'grade:export' }
      },
      {
        path: 'learning/report',
        name: 'MobileLearningReport',
        component: () => import('@/views/m/learning/report.vue'),
        meta: { title: '学情报表', permission: 'learning:view' }
      },
      {
        path: 'learning/knowledge',
        name: 'MobileLearningKnowledge',
        component: () => import('@/views/m/learning/knowledge.vue'),
        meta: { title: '知识点得分率', permission: 'learning:view' }
      },
      {
        path: 'learning/advice',
        name: 'MobileLearningAdvice',
        component: () => import('@/views/m/learning/advice.vue'),
        meta: { title: 'AI讲评建议', permission: 'learning:view' }
      },
      {
        path: 'learning/ppt',
        name: 'MobileLearningPpt',
        component: () => import('@/views/m/learning/ppt.vue'),
        meta: { title: 'PPT生成下载', permission: 'learning:ppt' }
      },
      {
        path: 'llm/setting',
        name: 'MobileLlmSetting',
        component: () => import('@/views/m/setting/llm.vue'),
        meta: { title: '大模型设置', permission: 'llm:setting' }
      },
      {
        path: 'system/user',
        name: 'MobileSystemUser',
        component: () => import('@/views/m/system/user.vue'),
        meta: { title: '用户管理', permission: 'system:user:view' }
      },
      {
        path: 'system/role',
        name: 'MobileSystemRole',
        component: () => import('@/views/m/system/role.vue'),
        meta: { title: '角色管理', permission: 'system:role:view' }
      },
      {
        path: 'system/menu',
        name: 'MobileSystemMenu',
        component: () => import('@/views/m/system/menu.vue'),
        meta: { title: '菜单权限', permission: 'system:menu:view' }
      },
      {
        path: '403',
        name: 'MobileForbidden',
        component: () => import('@/views/m/error/403.vue'),
        meta: { title: '无访问权限', hidden: true }
      },
      {
        path: '404',
        name: 'MobileNotFound',
        component: () => import('@/views/m/error/404.vue'),
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
  routes,
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { top: 0 }
  }
})

// ==================== 全局前置守卫 ====================
router.beforeEach((to) => {
  const userStore = useUserStore()
  const mobile = isMobileDevice()
  const mobileUrl = to.path.startsWith('/m')
  // 用户主动选择电脑版后，移动设备也不再强制跳移动端
  const useMobile = mobile && !preferDesktop()

  // 1. 移动端未手动切换时，统一进入 /m 路由树
  if (useMobile && !mobileUrl) {
    return {
      path: to.path === '/' ? '/m/home' : `/m${to.path}`,
      query: to.query,
      hash: to.hash,
      replace: true
    }
  }

  // 动态设置浏览器标题
  const baseTitle = import.meta.env.VITE_APP_TITLE || '智能阅卷系统'
  document.title = to.meta.title ? `${to.meta.title} - ${baseTitle}` : baseTitle

  // 2. 公开页面：已登录访问登录/注册时回到首页
  if (to.meta.public) {
    return userStore.isLogin ? { path: useMobile || mobileUrl ? '/m/home' : '/', replace: true } : true
  }

  // 3. 未登录一律跳转登录页，并记录来源地址
  if (!userStore.isLogin) {
    return {
      path: useMobile || mobileUrl ? '/m/login' : '/login',
      query: { redirect: to.fullPath }
    }
  }

  // 4. 页面级权限校验
  if (to.meta.permission && !userStore.hasPermission(to.meta.permission)) {
    return { path: mobileUrl ? '/m/403' : '/403' }
  }

  return true
})

export default router
