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

/** 删除草稿试卷（连同题目与知识点关联一起清理） */
export function deletePaper(id) {
  return request.delete(`/papers/${id}`)
}

/** 修改试卷状态：1 待考试，2 阅卷中，3 已归档 */
export function changePaperStatus(id, status) {
  return request.put(`/papers/${id}/status`, null, { params: { status } })
}

/**
 * 上传试卷素材（PDF / 拍照图片）并解析题目。
 *
 * 后端已改为异步任务：提交只做文件上传与 PDF 取文本，秒级返回 taskId；
 * OCR 在后台逐张执行，这里每 2 秒轮询一次，成功后把最终结果（题目草稿）返回给调用方。
 * 调用方不用关心轮询细节，接口形态与原来的同步版本保持一致。
 *
 * @param {File[]} files 选择的文件
 * @param {(task: object) => void} [onProgress] 进度回调，参数为任务快照（status/done/total/message）
 */
export async function parsePaperFiles(files, onProgress) {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  const task = await request.post('/papers/parse/async', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
  const taskId = task?.taskId
  if (!taskId) throw new Error('解析任务创建失败')
  onProgress?.(task)

  const deadline = Date.now() + 5 * 60 * 1000
  while (Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, 2000))
    const snapshot = await request.get(`/papers/parse/async/${taskId}`)
    onProgress?.(snapshot)
    if (snapshot?.status === 'SUCCESS') return snapshot.result
    if (snapshot?.status === 'FAILED') throw new Error(snapshot.message || '解析失败')
  }
  throw new Error('解析超时，请减少图片数量后重试')
}
