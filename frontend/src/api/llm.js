import request from './request'

/**
 * 大模型 Key 配置接口（BYOK：用户自带 Key）。
 * 后端不会回传完整 Key，只返回脱敏信息。
 */

/** 查询当前用户是否已配置 Key（返回 { configured, maskedKey, apiUrl, model, expireAt }） */
export function getLlmCredential() {
  return request.get('/llm/credential')
}

/** 保存当前用户的 Key（apiUrl / model 可空，留空用默认 DeepSeek） */
export function saveLlmCredential(data) {
  return request.post('/llm/credential', data)
}

/** 清除当前用户的 Key */
export function removeLlmCredential() {
  return request.delete('/llm/credential')
}

/** 测试当前 Key 是否可用（会真实发起一次极短调用） */
export function testLlmCredential() {
  return request.post('/llm/credential/test')
}
