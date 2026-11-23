import request from './request'

/**
 * AI 异步阅卷任务相关接口。
 *
 * 后端没有独立的 MarkTaskController，mark_task 表仅由 MQ 消费者内部使用，
 * 任务进度通过答卷的 markingStatus 字段体现。因此这里复用纸质答卷接口：
 * - 任务列表 = 按 markingStatus 筛选的答卷分页
 * - 任务详情 = 答卷详情
 * 不要臆造 /mark-tasks 路径。
 */

/** 阅卷任务列表（基于答卷状态） */
export function pageMarkingTasks(params) {
  return request.get('/paper-answers/page', { params })
}

/** 阅卷任务详情（答卷详情，含 OCR 文本与 AI 参考分） */
export function getMarkingTaskDetail(answerId) {
  return request.get(`/paper-answers/${answerId}`)
}
