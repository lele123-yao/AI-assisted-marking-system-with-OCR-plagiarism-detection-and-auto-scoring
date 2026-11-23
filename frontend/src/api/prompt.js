import request from './request'

/**
 * AI 评分提示词模板接口（管理员 / 教师）。
 * 后端：PromptTemplateController，路径前缀 /prompt-templates
 */

/** 分页查询模板 */
export function pageTemplates(params) {
  return request.get('/prompt-templates/page', { params })
}

/** 新增模板 */
export function createTemplate(data) {
  return request.post('/prompt-templates', data)
}

/** 编辑模板（后端自动版本号 +1） */
export function updateTemplate(id, data) {
  return request.put(`/prompt-templates/${id}`, data)
}

/** 删除模板（被题目引用时后端拒绝） */
export function deleteTemplate(id) {
  return request.delete(`/prompt-templates/${id}`)
}
