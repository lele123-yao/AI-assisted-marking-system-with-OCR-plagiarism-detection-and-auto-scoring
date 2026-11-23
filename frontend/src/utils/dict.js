/**
 * 业务字典与展示辅助。
 *
 * 后端多数接口返回数字状态码与描述文案，这里统一维护一份映射，
 * 避免每个页面各写一套 if/else 导致文案不一致。
 */

/** 试卷状态：0 草稿，1 待考试，2 阅卷中，3 已归档 */
export const PAPER_STATUS = {
  0: { label: '草稿', type: 'info' },
  1: { label: '待考试', type: 'warning' },
  2: { label: '阅卷中', type: 'primary' },
  3: { label: '已归档', type: 'success' }
}

/** 作答模式：1 线上作答，2 纸质线下阅卷 */
export const ANSWER_MODE = {
  1: { label: '线上作答', type: 'primary' },
  2: { label: '纸质线下阅卷', type: 'warning' }
}

/** 题型：1-8，0 表示通用（仅提示词模板使用） */
export const QUESTION_TYPE = {
  0: { label: '通用', type: 'info' },
  1: { label: '单选题', type: 'info' },
  2: { label: '多选题', type: 'info' },
  3: { label: '判断题', type: 'info' },
  4: { label: '填空题', type: 'success' },
  5: { label: '简答题', type: 'primary' },
  6: { label: '论述题', type: 'warning' },
  7: { label: '计算题', type: 'warning' },
  8: { label: '案例题', type: 'danger' }
}

/** 答卷状态（纸质/阅卷任务共用）：0 待录入，1 OCR识别中，2 AI批改中，3 待教师确认，4 失败，5 已归档 */
export const MARKING_STATUS = {
  0: { label: '待录入', type: 'info' },
  1: { label: 'OCR识别中', type: 'warning' },
  2: { label: 'AI批改中', type: 'primary' },
  3: { label: '待教师确认', type: 'warning' },
  4: { label: '阅卷失败', type: 'danger' },
  5: { label: '已确认归档', type: 'success' }
}

/** 复核状态：0 待复核，1 复核通过，2 已改分，3 复核驳回 */
export const REVIEW_STATUS = {
  0: { label: '待复核', type: 'info' },
  1: { label: '复核通过', type: 'success' },
  2: { label: '已改分', type: 'warning' },
  3: { label: '复核驳回', type: 'danger' }
}

/** 答卷来源 */
export const ANSWER_SOURCE = {
  ONLINE: { label: '线上作答', type: 'primary' },
  PAPER_SCAN: { label: '纸质扫描', type: 'warning' }
}

/** 取字典项，找不到时回退为“未知” */
export function dictItem(dict, value) {
  return dict[value] || { label: value ?? '未知', type: 'info' }
}

/** 取字典文案 */
export function dictLabel(dict, value) {
  return dictItem(dict, value).label
}

/** 百分比展示（后端返回 0~1 的小数） */
export function toPercent(value, digits = 1) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '-'
  return `${(Number(value) * 100).toFixed(digits)}%`
}

/** 时间展示：后端格式为 ISO 字符串或数组，这里只做空值兜底 */
export function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}
