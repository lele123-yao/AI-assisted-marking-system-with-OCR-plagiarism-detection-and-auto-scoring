import request from './request'

/**
 * 纸质答卷批改接口（教师 / 管理员）。
 * 后端：PaperAnswerController，路径前缀 /paper-answers
 */

/** 分页查询纸质答卷（只返回 PAPER_SCAN 来源） */
export function pagePaperAnswers(params) {
  return request.get('/paper-answers/page', { params })
}

/** 纸质答卷详情（OCR 全文、签名图片地址、AI 参考分、教师最终分） */
export function getPaperAnswerDetail(answerId) {
  return request.get(`/paper-answers/${answerId}`)
}

/**
 * 导入考生名单 Excel。
 * 后端使用 @ModelAttribute + @RequestPart("file")，因此业务参数与文件都放 FormData。
 * @param {{paperId: number, defaultClass?: string, file: File}} payload
 */
export function importRoster({ paperId, defaultClass, file }) {
  const formData = new FormData()
  formData.append('paperId', paperId)
  if (defaultClass) formData.append('defaultClass', defaultClass)
  formData.append('file', file)
  return request.post('/paper-answers/roster/import', formData)
}

/**
 * 上传答卷扫描图片，返回图片对象键。
 * @param {{paperId: number, studentNo: string, studentName?: string, file: File}} payload
 */
export function uploadPaperImage({ paperId, studentNo, studentName, file }) {
  const formData = new FormData()
  formData.append('paperId', paperId)
  formData.append('studentNo', studentNo)
  if (studentName) formData.append('studentName', studentName)
  formData.append('file', file)
  return request.post('/paper-answers/image', formData)
}

/** 触发 OCR 识别任务 */
export function triggerOcr(answerId) {
  return request.post(`/paper-answers/${answerId}/ocr`)
}

/**
 * 教师手动录入逐题得分与最终分。
 * @param {number} answerId
 * @param {{items: Array<{questionId: number, score: number}>, teacherFinalScore: number}} data
 */
export function saveTeacherScore(answerId, data) {
  return request.post(`/paper-answers/${answerId}/score`, data)
}
