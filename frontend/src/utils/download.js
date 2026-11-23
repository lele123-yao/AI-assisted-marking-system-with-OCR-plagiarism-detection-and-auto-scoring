/**
 * 文件下载工具。
 *
 * 1. downloadBlob：用于后端直接返回文件流的接口（如成绩 Excel 导出）。
 * 2. downloadByUrl：用于 MinIO 预签名地址、PPT/图片等可直接访问的 URL。
 */

/** 通过 Blob 触发浏览器下载 */
export function downloadBlob(blob, filename = 'download.xlsx') {
  if (!blob) return
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  // 释放内存
  window.URL.revokeObjectURL(url)
}

/** 通过 URL 触发下载（跨域地址会退化为新窗口打开） */
export function downloadByUrl(url, filename = '') {
  if (!url) return
  const link = document.createElement('a')
  link.href = url
  if (filename) link.download = filename
  link.target = '_blank'
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
}
