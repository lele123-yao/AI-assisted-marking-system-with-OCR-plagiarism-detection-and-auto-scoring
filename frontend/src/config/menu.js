/**
 * 菜单与路由配置。
 *
 * menuTree：侧边栏菜单树（支持分组），分组本身可以设置 permission，
 *           子菜单也会按自身的 meta.permission 二次过滤。
 * menuRoutes：扁平化后的路由记录，供 router/index.js 直接展开到 Layout 下。
 *
 * meta.hidden = true 的路由（如详情页）不进入菜单，但仍可访问。
 */
export const menuTree = [
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    meta: { title: '首页', icon: 'HomeFilled', permission: 'dashboard:view' }
  },
  // ==================== 大模型设置（管理员 / 教师，仅用于阅卷侧配置 Key） ====================
  {
    path: '/llm/setting',
    name: 'LlmSetting',
    component: () => import('@/views/setting/llm.vue'),
    meta: { title: '大模型设置', icon: 'Key', permission: 'llm:setting' }
  },

  // ==================== 系统管理（仅管理员） ====================
  {
    path: '/system',
    meta: { title: '系统管理', icon: 'Setting', permission: 'system:user:view' },
    children: [
      {
        path: '/system/user',
        name: 'SystemUser',
        component: () => import('@/views/system/user/index.vue'),
        meta: { title: '用户管理', icon: 'User', permission: 'system:user:view' }
      },
      {
        path: '/system/role',
        name: 'SystemRole',
        component: () => import('@/views/system/role/index.vue'),
        meta: { title: '角色管理', icon: 'Avatar', permission: 'system:role:view' }
      },
      {
        path: '/system/menu',
        name: 'SystemMenu',
        component: () => import('@/views/system/menu/index.vue'),
        meta: { title: '菜单权限', icon: 'Menu', permission: 'system:menu:view' }
      }
    ]
  },

  // ==================== 教学资源（教师 / 管理员） ====================
  {
    path: '/teaching',
    meta: { title: '教学资源', icon: 'Reading', permission: 'course:view' },
    children: [
      {
        path: '/course',
        name: 'Course',
        component: () => import('@/views/course/index.vue'),
        meta: { title: '课程管理', icon: 'Notebook', permission: 'course:view' }
      },
      {
        path: '/class',
        name: 'Class',
        component: () => import('@/views/class/index.vue'),
        meta: { title: '班级管理', icon: 'School', permission: 'class:view' }
      },
      {
        path: '/paper',
        name: 'Paper',
        component: () => import('@/views/paper/index.vue'),
        meta: { title: '试卷管理', icon: 'Document', permission: 'paper:view' }
      },
      {
        path: '/question',
        name: 'Question',
        component: () => import('@/views/question/index.vue'),
        meta: { title: '主观题目', icon: 'EditPen', permission: 'question:view' }
      },
      {
        path: '/prompt',
        name: 'Prompt',
        component: () => import('@/views/prompt/index.vue'),
        meta: { title: 'Prompt模板', icon: 'MagicStick', permission: 'prompt:view' }
      }
    ]
  },

  // ==================== 答卷管理（学生看到在线作答，教师看到列表） ====================
  {
    path: '/answer-group',
    meta: { title: '答卷管理', icon: 'DocumentCopy' },
    children: [
      {
        path: '/answer/submit',
        name: 'AnswerSubmit',
        component: () => import('@/views/answer/submit.vue'),
        meta: { title: '在线作答', icon: 'EditPen', permission: 'answer:submit' }
      },
      {
        path: '/answer/list',
        name: 'AnswerList',
        component: () => import('@/views/answer/list.vue'),
        meta: { title: '答卷列表', icon: 'List', permission: 'answer:view' }
      }
    ]
  },

  // ==================== 我的学习（学生） ====================
  {
    path: '/my-learning',
    meta: { title: '我的学习', icon: 'Reading', permission: 'student:paper' },
    children: [
      {
        path: '/student/papers',
        name: 'StudentPapers',
        component: () => import('@/views/student/papers.vue'),
        meta: { title: '我的试卷', icon: 'Document', permission: 'student:paper' }
      },
      {
        path: '/student/scores',
        name: 'StudentScores',
        component: () => import('@/views/student/scores.vue'),
        meta: { title: '我的成绩', icon: 'Trophy', permission: 'student:score' }
      }
    ]
  },

  // ==================== 纸质辅助批改 ====================
  {
    path: '/paper-work',
    meta: { title: '纸质辅助批改', icon: 'Files', permission: 'paper-answer:view' },
    children: [
      {
        path: '/paper-answer/roster',
        name: 'PaperRoster',
        component: () => import('@/views/paper-answer/roster.vue'),
        meta: { title: '名单导入', icon: 'Upload', permission: 'paper-answer:import' }
      },
      {
        path: '/paper-answer/upload',
        name: 'PaperUpload',
        component: () => import('@/views/paper-answer/upload.vue'),
        meta: { title: '图片上传/OCR', icon: 'Picture', permission: 'paper-answer:image' }
      },
      {
        path: '/paper-answer/score',
        name: 'PaperScore',
        component: () => import('@/views/paper-answer/score.vue'),
        meta: { title: '教师录分', icon: 'Edit', permission: 'paper-answer:score' }
      }
    ]
  },

  // ==================== AI 异步阅卷 ====================
  {
    path: '/ai-mark',
    meta: { title: 'AI异步阅卷', icon: 'Cpu', permission: 'mark:view' },
    children: [
      {
        path: '/mark/task',
        name: 'MarkTask',
        component: () => import('@/views/mark/task.vue'),
        meta: { title: '任务监控', icon: 'Monitor', permission: 'mark:view' }
      }
    ]
  },

  // ==================== AI Agent（RAG 知识库） ====================
  {
    path: '/agent-group',
    meta: { title: 'AI Agent', icon: 'MagicStick', permission: 'mark:view' },
    children: [
      {
        path: '/agent/knowledge',
        name: 'AgentKnowledge',
        component: () => import('@/views/agent/knowledge.vue'),
        meta: { title: '知识库管理', icon: 'Collection', permission: 'mark:view' }
      }
    ]
  },

  // ==================== 抄袭检测 ====================
  {
    path: '/plagiarism-group',
    meta: { title: '抄袭检测', icon: 'Warning', permission: 'plagiarism:view' },
    children: [
      {
        path: '/plagiarism/compare',
        name: 'PlagiarismCompare',
        component: () => import('@/views/plagiarism/compare.vue'),
        meta: { title: '相似度比对', icon: 'CopyDocument', permission: 'plagiarism:view' }
      },
      {
        path: '/plagiarism/report',
        name: 'PlagiarismReport',
        component: () => import('@/views/plagiarism/report.vue'),
        meta: { title: '抄袭报告', icon: 'DataAnalysis', permission: 'plagiarism:view' }
      }
    ]
  },

  // ==================== 成绩管理 ====================
  {
    path: '/grade-group',
    meta: { title: '成绩管理', icon: 'Trophy', permission: 'grade:view' },
    children: [
      {
        path: '/grade/review',
        name: 'GradeReview',
        component: () => import('@/views/grade/review.vue'),
        meta: { title: '成绩复核', icon: 'Stamp', permission: 'grade:review' }
      },
      {
        path: '/grade/statistics',
        name: 'GradeStatistics',
        component: () => import('@/views/grade/statistics.vue'),
        meta: { title: '成绩统计', icon: 'PieChart', permission: 'grade:view' }
      },
      {
        path: '/grade/export',
        name: 'GradeExport',
        component: () => import('@/views/grade/export.vue'),
        meta: { title: '成绩导出', icon: 'Download', permission: 'grade:export' }
      }
    ]
  },

  // ==================== 学情分析 ====================
  {
    path: '/learning-group',
    meta: { title: '学情分析', icon: 'TrendCharts', permission: 'learning:view' },
    children: [
      {
        path: '/learning/report',
        name: 'LearningReport',
        component: () => import('@/views/learning/report.vue'),
        meta: { title: '学情报表', icon: 'DataBoard', permission: 'learning:view' }
      },
      {
        path: '/learning/knowledge',
        name: 'LearningKnowledge',
        component: () => import('@/views/learning/knowledge.vue'),
        meta: { title: '知识点得分率', icon: 'PieChart', permission: 'learning:view' }
      },
      {
        path: '/learning/advice',
        name: 'LearningAdvice',
        component: () => import('@/views/learning/advice.vue'),
        meta: { title: 'AI讲评建议', icon: 'ChatDotRound', permission: 'learning:view' }
      },
      {
        path: '/learning/ppt',
        name: 'LearningPpt',
        component: () => import('@/views/learning/ppt.vue'),
        meta: { title: 'PPT生成下载', icon: 'Slides', permission: 'learning:ppt' }
      }
    ]
  }
]

/** 详情类路由：不进入菜单，但必须注册 */
export const hiddenRoutes = [
  {
    path: '/answer/detail/:id',
    name: 'AnswerDetail',
    component: () => import('@/views/answer/detail.vue'),
    meta: { title: '答卷详情', permission: 'answer:view', hidden: true }
  },
  {
    path: '/mark/detail/:id',
    name: 'MarkDetail',
    component: () => import('@/views/mark/detail.vue'),
    meta: { title: 'AI阅卷详情', permission: 'mark:view', hidden: true }
  },
  {
    // 权限演示页仅保留给管理员内部查看，普通角色不再出现在菜单中
    path: '/demo/permission',
    name: 'PermissionDemo',
    component: () => import('@/views/demo/permission.vue'),
    meta: { title: '权限演示', permission: 'system:menu:view', hidden: true }
  }
]

/** 扁平化路由：分组只用于菜单展示，路由仍直接挂在 Layout 下 */
export const menuRoutes = [
  ...menuTree.flatMap((item) => (item.children?.length ? item.children : [item])),
  ...hiddenRoutes
]
