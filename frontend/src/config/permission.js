/**
 * 角色 -> 前端权限标识映射。
 *
 * 后端登录接口当前只返回角色信息（user.roles[].roleCode），尚未返回按钮级权限码。
 * 因此这里按角色映射出前端权限标识；后续后端在登录响应中补充 permissions 字段后，
 * user store 会优先使用后端返回值，无需改动本文件。
 */
export const ROLE_PERMISSIONS = {
  // 管理员拥有全部权限（'*' 为通配符）
  ADMIN: ['*'],

  // 教师：可管理教学资源、答卷批改、成绩与学情，但不含系统管理
  TEACHER: [
    'dashboard:view',
    // 大模型 API Key 配置（阅卷侧）
    'llm:setting',
    // 课程 / 班级 / 试卷 / 题目 / 提示词
    'course:view', 'course:add', 'course:edit', 'course:archive',
    'class:view', 'class:add', 'class:edit', 'class:delete', 'assignment:assign',
    'paper:view', 'paper:add', 'paper:edit', 'paper:status',
    'question:view', 'question:add', 'question:edit', 'question:delete',
    'prompt:view', 'prompt:add', 'prompt:edit', 'prompt:delete',
    // 答卷（教师端只读查询）
    'answer:view',
    // 纸质辅助批改
    'paper-answer:view', 'paper-answer:import', 'paper-answer:image',
    'paper-answer:ocr', 'paper-answer:score',
    // AI 阅卷任务监控
    'mark:view',
    // 抄袭检测
    'plagiarism:view',
    // 成绩复核与统计
    'grade:view', 'grade:review', 'grade:export',
    // 学情分析与 PPT
    'learning:view', 'learning:generate', 'learning:ppt'
  ],

  // 学生：在线作答 + 我的试卷 / 我的成绩
  STUDENT: ['dashboard:view', 'answer:submit', 'student:paper', 'student:score']
}

/** 角色编码 -> 中文名称（仅用于展示） */
export const ROLE_LABELS = {
  ADMIN: '管理员',
  TEACHER: '教师',
  STUDENT: '学生'
}

/** 根据角色列表合并计算权限标识 */
export function derivePermissions(roles = []) {
  const permissions = new Set()
  roles.forEach((role) => {
    const list = ROLE_PERMISSIONS[role] || []
    list.forEach((item) => permissions.add(item))
  })
  return Array.from(permissions)
}
