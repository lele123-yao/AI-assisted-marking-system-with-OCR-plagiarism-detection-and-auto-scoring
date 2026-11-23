import request from './request'

/**
 * 试卷管理接口（管理员 / 教师）。
 * 后端：PaperController，路径前缀 /papers
 */

/** 分页查询试卷 */
export function pagePapers(params) {
  return request.get('/papers/page', { params })
}

/** 新增试卷（创建后固定为草稿） */
export function createPaper(data) {
  return request.post('/papers', data)
}

/** 编辑试卷（仅草稿可编辑） */
export function updatePaper(id, data) {
  return request.put(`/papers/${id}`, data)
}

/** 修改试卷状态：1 待考试，2 阅卷中，3 已归档 */
export function changePaperStatus(id, status) {
  return request.put(`/papers/${id}/status`, null, { params: { status } })
}

/**
 * 上传试卷素材（PDF / 拍照图片）并解析题目。
 * 文件上传到 MinIO，后端返回 pdfUrl / imageUrls / 题目草稿。
 * 解析同步调用 OCR/大模型，超时单独放宽到 180 秒。
 */
export function parsePaperFiles(files) {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  return request.post('/papers/parse', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000
  })
}
