import request from './request'

/**
 * 答卷相关接口。
 * 后端：AnswerController，路径前缀 /answers
 * 说明：后端只有提交与附件上传两个接口，没有线上答卷查询接口。
 *       列表与详情请使用 paperAnswer.js 中的纸质答卷接口。
 */

/**
 * 提交线上答卷。
 * @param {{paperId: number, answerContent: string}} data
 *        answerContent 为按题号组织的 JSON 字符串，如 {"1":"...","2":"..."}
 */
export function submitOnlineAnswer(data) {
  return request.post('/answers/online', data)
}

/**
 * 上传答卷附件，返回 MinIO 对象键（objectKey）。
 * 注意：不要手动设置 Content-Type，浏览器会自动补 multipart 边界。
 */
export function uploadAnswerFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/answers/upload', formData)
}
