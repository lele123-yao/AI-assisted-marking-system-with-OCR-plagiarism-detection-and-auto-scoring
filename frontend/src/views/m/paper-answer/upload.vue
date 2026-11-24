<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pagePaperAnswers, triggerOcr, uploadPaperImage } from '@/api/paperAnswer'
import { pagePapers } from '@/api/paper'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'

/**
 * 移动端：拍照 / 相册上传答卷扫描件。
 * 上传接口只返回图片对象键，答卷 ID 需回查答卷列表获得，再用于触发 OCR。
 */
const route = useRoute()

const papers = ref([])
const form = reactive({ paperId: null, studentNo: '', studentName: '' })
const records = ref([])
const uploading = ref(false)
const ocrLoadingKey = ref(null)
const cameraInput = ref(null)
const albumInput = ref(null)
let seq = 0

const ready = computed(
  () => !!form.paperId && !!form.studentNo.trim() && !uploading.value
)

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 50 })
  papers.value = data?.records || []
  const preset = Number(route.query.paperId)
  if (preset && papers.value.some((item) => item.id === preset)) {
    form.paperId = preset
  }
}

function openCamera() {
  cameraInput.value?.click()
}

function openAlbum() {
  albumInput.value?.click()
}

function handleFileChange(event) {
  const picked = event.target.files?.[0]
  event.target.value = ''
  if (!picked) return
  if (!/\.(jpe?g|png|webp)$/i.test(picked.name)) {
    ElMessage.warning('仅支持 jpg / jpeg / png / webp 图片')
    return
  }
  doUpload(picked)
}

/** 回查答卷 ID：按试卷 + 学号匹配最新的一条记录 */
async function findAnswerId(paperId, studentNo) {
  const data = await pagePaperAnswers({ pageNum: 1, pageSize: 100, paperId })
  const row = (data?.records || []).find((item) => item.studentNo === studentNo)
  return row?.id ?? null
}

async function doUpload(file) {
  if (uploading.value) {
    ElMessage.warning('请等待当前文件上传完成')
    return
  }
  if (!form.paperId) {
    ElMessage.warning('请先选择试卷')
    return
  }
  const studentNo = form.studentNo.trim()
  if (!studentNo) {
    ElMessage.warning('请先填写学号')
    return
  }

  const paperId = form.paperId
  const studentName = form.studentName.trim()
  records.value.unshift({
    key: ++seq,
    fileName: file.name,
    url: URL.createObjectURL(file),
    status: 'uploading',
    answerId: null,
    ocrTriggered: false,
    message: ''
  })
  const record = records.value[0]

  uploading.value = true
  try {
    await uploadPaperImage({ paperId, studentNo, studentName, file })
    record.status = 'success'
    ElMessage.success('上传成功')
    // 上传成功后清空考生信息，避免连续上传串号
    form.studentNo = ''
    form.studentName = ''

    try {
      record.answerId = await findAnswerId(paperId, studentNo)
      record.message = record.answerId ? '已关联答卷，可触发 OCR' : '上传成功，未匹配到答卷记录'
    } catch {
      record.answerId = null
      record.message = '上传成功，答卷 ID 查询失败，可稍后重试'
    }
  } catch {
    record.status = 'failed'
    record.message = '上传失败，请重新选择图片'
  } finally {
    uploading.value = false
  }
}

function statusMeta(record) {
  if (record.status === 'uploading') return { label: '上传中', type: 'warning' }
  if (record.status === 'failed') return { label: '上传失败', type: 'danger' }
  if (record.ocrTriggered) return { label: '已触发 OCR', type: 'primary' }
  return { label: '上传成功', type: 'success' }
}

async function handleOcr(record) {
  if (!record.answerId || record.ocrTriggered || ocrLoadingKey.value) return
  try {
    await ElMessageBox.confirm(
      `确认为「${record.fileName}」触发 OCR 识别？识别完成后将自动进入 AI 批改队列。`,
      'OCR 确认',
      { type: 'warning' }
    )
  } catch {
    return
  }

  ocrLoadingKey.value = record.key
  try {
    await triggerOcr(record.answerId)
    record.ocrTriggered = true
    record.message = 'OCR 任务已提交'
    ElMessage.success('OCR 任务已提交')
  } finally {
    ocrLoadingKey.value = null
  }
}

onMounted(loadPapers)

// 页面卸载时释放本地上传预览地址
onBeforeUnmount(() => {
  records.value.forEach((record) => URL.revokeObjectURL(record.url))
})
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="图片上传"
      subtitle="拍照或选择相册图片上传答卷，上传成功后可触发 OCR 识别"
      icon="Camera"
    >
      <template #actions>
        <el-tag type="info" effect="plain">一次一份</el-tag>
      </template>
    </MobileHero>

    <section class="m-card">
      <div class="m-col">
        <div class="field">
          <span class="m-label">选择试卷</span>
          <el-select v-model="form.paperId" filterable placeholder="请选择试卷" class="full">
            <el-option
              v-for="paper in papers"
              :key="paper.id"
              :label="`${paper.paperTitle}（${paper.paperNo}）`"
              :value="paper.id"
            />
          </el-select>
        </div>

        <div class="field">
          <span class="m-label">学号</span>
          <el-input v-model="form.studentNo" placeholder="必须与名单中的学号一致" clearable />
        </div>

        <div class="field">
          <span class="m-label">姓名（选填）</span>
          <el-input v-model="form.studentName" placeholder="便于在上传记录中辨认" clearable />
        </div>

        <div class="capture-row">
          <el-button type="primary" :disabled="!ready" :loading="uploading" @click="openCamera">
            <el-icon><Camera /></el-icon>
            拍照上传
          </el-button>
          <el-button plain :disabled="!ready" :loading="uploading" @click="openAlbum">
            <el-icon><Picture /></el-icon>
            相册上传
          </el-button>
        </div>
        <input
          ref="cameraInput"
          class="file-input"
          type="file"
          accept="image/*"
          capture="environment"
          @change="handleFileChange"
        />
        <input
          ref="albumInput"
          class="file-input"
          type="file"
          accept="image/*"
          @change="handleFileChange"
        />

        <p class="hint">一次上传一份；上传成功后会自动清空学号与姓名，避免连续上传串号。</p>
      </div>
    </section>

    <section class="m-card">
      <div class="m-row-between">
        <span class="m-title">上传记录</span>
        <span class="m-sub">{{ records.length }} 份</span>
      </div>

      <div class="record-list">
        <article v-for="record in records" :key="record.key" class="record">
          <img :src="record.url" class="thumb" alt="答卷缩略图" />
          <div class="record-main">
            <div class="m-row-between">
              <span class="record-name m-ellipsis">{{ record.fileName }}</span>
              <el-tag :type="statusMeta(record).type" size="small" effect="plain">
                {{ statusMeta(record).label }}
              </el-tag>
            </div>
            <span class="m-sub">{{ record.message || '正在上传…' }}</span>
            <el-button
              v-if="record.status === 'success' && record.answerId"
              link
              type="primary"
              size="small"
              class="ocr-btn"
              :loading="ocrLoadingKey === record.key"
              :disabled="record.ocrTriggered"
              @click="handleOcr(record)"
            >
              {{ record.ocrTriggered ? '已触发 OCR' : '触发 OCR' }}
            </el-button>
          </div>
        </article>

        <MobileEmpty v-if="!records.length" description="还没有上传记录" icon="Camera" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.full {
  width: 100%;
}

.capture-row {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}

.capture-row .el-button {
  flex: 1;
  margin-left: 0;
}

.file-input {
  display: none;
}

.hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: #9aa0b8;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.record {
  display: flex;
  gap: 12px;
  padding: 10px;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-radius: 12px;
}

.thumb {
  flex-shrink: 0;
  width: 62px;
  height: 62px;
  object-fit: cover;
  background: #eef1ff;
  border-radius: 9px;
}

.record-main {
  display: flex;
  flex-direction: column;
  gap: 5px;
  flex: 1;
  min-width: 0;
}

.record-name {
  font-size: 13px;
  color: #23264a;
}

.record-main .el-tag {
  flex-shrink: 0;
}

.ocr-btn {
  align-self: flex-start;
  height: auto;
  padding: 0;
  margin-top: 2px;
}
</style>
