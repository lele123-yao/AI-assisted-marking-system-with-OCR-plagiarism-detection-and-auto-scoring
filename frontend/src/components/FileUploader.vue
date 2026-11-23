<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, UploadFilled } from '@element-plus/icons-vue'

/**
 * 文件上传组件。
 *
 * 通过上传函数把文件交给调用方（内部使用 FormData + axios），
 * 上传成功后把后端返回的对象键（MinIO objectKey）或地址通过 success 事件抛出。
 * 支持图片即时预览，适配后端返回 objectKey、前端无需自行拼接地址的场景。
 */
const props = defineProps({
  /** (file: File) => Promise<any> */
  uploadFn: { type: Function, required: true },
  /** 允许的扩展名，逗号分隔 */
  accept: { type: String, default: '.jpg,.jpeg,.png,.webp,.pdf' },
  /** 单文件大小上限（MB） */
  maxSize: { type: Number, default: 20 },
  disabled: { type: Boolean, default: false },
  prompt: { type: String, default: '点击或拖拽文件到此处上传' }
})

const emit = defineEmits(['success', 'error'])

const uploading = ref(false)
const fileName = ref('')
const previewUrl = ref('')
const result = ref(null)

const isImage = computed(() => /\.(jpe?g|png|webp)$/i.test(fileName.value))

/** 上传前校验类型与大小 */
function beforeUpload(file) {
  if (file.size > props.maxSize * 1024 * 1024) {
    ElMessage.warning(`文件大小不能超过 ${props.maxSize}MB`)
    return false
  }
  const allow = props.accept.split(',').map((item) => item.trim().toLowerCase())
  const ext = `.${(file.name.split('.').pop() || '').toLowerCase()}`
  if (allow.length && !allow.includes(ext)) {
    ElMessage.warning(`仅支持 ${props.accept} 格式`)
    return false
  }
  return true
}

/** 自定义上传：不依赖 el-upload 的 action，统一走 axios */
async function handleUpload(options) {
  uploading.value = true
  fileName.value = options.file.name
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = /\.(jpe?g|png|webp)$/i.test(options.file.name)
    ? URL.createObjectURL(options.file)
    : ''

  try {
    const data = await props.uploadFn(options.file)
    result.value = data
    emit('success', data)
    ElMessage.success('上传成功')
    options.onSuccess?.(data)
  } catch (error) {
    emit('error', error)
    options.onError?.(error)
  } finally {
    uploading.value = false
  }
}

onBeforeUnmount(() => {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})
</script>

<template>
  <div class="file-uploader">
    <el-upload
      drag
      :accept="accept"
      :disabled="disabled || uploading"
      :show-file-list="false"
      :before-upload="beforeUpload"
      :http-request="handleUpload"
    >
      <el-icon class="upload-icon"><UploadFilled /></el-icon>
      <div class="upload-text">{{ prompt }}</div>
      <div class="upload-tip">支持 {{ accept }}，单文件不超过 {{ maxSize }}MB</div>
    </el-upload>

    <div v-if="uploading" class="upload-status">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>正在上传 {{ fileName }}...</span>
    </div>

    <div v-else-if="result" class="upload-result">
      <el-image
        v-if="isImage && previewUrl"
        :src="previewUrl"
        :preview-src-list="[previewUrl]"
        fit="cover"
        class="preview"
      />
      <div class="result-text">
        <div class="file-name">{{ fileName }}</div>
        <div class="object-key">对象键：{{ result }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.file-uploader {
  width: 100%;
}

.upload-icon {
  font-size: 42px;
  color: #c0c4cc;
}

.upload-text {
  margin-top: 8px;
  color: #606266;
}

.upload-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.upload-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  color: #409eff;
}

.upload-result {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.preview {
  width: 96px;
  height: 96px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.result-text {
  font-size: 13px;
  color: #606266;
  word-break: break-all;
}

.file-name {
  font-weight: 600;
  margin-bottom: 4px;
}

.object-key {
  color: #909399;
}
</style>
