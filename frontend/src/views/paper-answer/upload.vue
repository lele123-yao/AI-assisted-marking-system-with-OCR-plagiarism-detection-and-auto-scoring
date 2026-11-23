<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pagePaperAnswers, triggerOcr, uploadPaperImage } from '@/api/paperAnswer'
import { pagePapers } from '@/api/paper'
import FileUploader from '@/components/FileUploader.vue'
import PageHero from '@/components/PageHero.vue'
import { MARKING_STATUS, dictItem, formatTime } from '@/utils/dict'

/** 答卷图片上传与 OCR 触发 */
const papers = ref([])
const answers = ref([])
const loading = ref(false)
const ocrLoadingId = ref(null)

const form = reactive({ paperId: null, studentNo: '', studentName: '' })

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
  if (!form.paperId && papers.value.length) form.paperId = papers.value[0].id
}

async function loadAnswers() {
  if (!form.paperId) {
    answers.value = []
    return
  }
  loading.value = true
  try {
    const data = await pagePaperAnswers({ pageNum: 1, pageSize: 100, paperId: form.paperId })
    answers.value = data?.records || []
  } finally {
    loading.value = false
  }
}

/** 上传回调：交给 FileUploader 调用 */
async function doUpload(file) {
  if (!form.paperId) throw new Error('请先选择试卷')
  if (!form.studentNo) throw new Error('请先填写学号')

  const result = await uploadPaperImage({
    paperId: form.paperId,
    studentNo: form.studentNo,
    studentName: form.studentName,
    file
  })
  // 上传后刷新列表，取到新答卷 ID 后再触发 OCR
  await loadAnswers()
  const row = answers.value.find((item) => item.studentNo === form.studentNo)
  if (row && result) {
    ElMessage.success('上传成功，可点击「触发OCR」开始识别')
  }
  return result
}

async function handleOcr(row) {
  try {
    await ElMessageBox.confirm(
      `确认为「${row.studentName || row.studentNo}」触发 OCR 识别？识别完成后将自动进入 AI 批改队列。`,
      'OCR 确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  ocrLoadingId.value = row.id
  try {
    await triggerOcr(row.id)
    ElMessage.success('OCR 任务已提交')
    loadAnswers()
  } finally {
    ocrLoadingId.value = null
  }
}

/** 状态计数：作为右侧面板顶部的统计芯片 */
const counts = computed(() => {
  const rows = answers.value || []
  return [
    { label: '答卷', value: rows.length, tone: 'blue' },
    { label: '已传扫描件', value: rows.filter((row) => row.hasImage).length, tone: 'green' },
    { label: '识别/批改中', value: rows.filter((row) => [0, 1, 2].includes(row.markingStatus)).length, tone: 'orange' },
    { label: '已归档', value: rows.filter((row) => row.markingStatus === 5).length, tone: 'purple' }
  ]
})

onMounted(async () => {
  await loadPapers()
  loadAnswers()
})
</script>

<template>
  <div class="page-container">
    <PageHero
      title="答卷图片上传"
      subtitle="选择试卷并填写考生学号后上传答卷扫描图片，上传成功后可在右侧列表触发 OCR 识别。"
      icon="Camera"
      tone="light"
    >
      <template #meta>
        <div class="hero-tags">
          <el-tag type="info" effect="plain">支持 jpg / jpeg / png / webp</el-tag>
          <el-tag type="warning" effect="plain">需先选试卷并填写学号</el-tag>
          <el-tag effect="plain">学号需与名单一致</el-tag>
        </div>
      </template>
    </PageHero>

    <div class="workbench">
      <!-- 左：上传工作区 -->
      <aside class="upload-card">
        <div class="upload-head">
          <span class="upload-eyebrow">上传工作区</span>
          <h3>录入一份答卷</h3>
          <p>填写考生信息后上传扫描件，识别与批改为异步任务。</p>
        </div>

        <el-form :model="form" label-position="top" class="upload-form">
          <el-form-item label="选择试卷">
            <el-select v-model="form.paperId" filterable class="full" @change="loadAnswers">
              <el-option
                v-for="paper in papers"
                :key="paper.id"
                :label="`${paper.paperTitle}（${paper.paperNo}）`"
                :value="paper.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="学号">
            <el-input v-model="form.studentNo" placeholder="必须与名单中的学号一致" />
          </el-form-item>
          <el-form-item label="姓名（选填）">
            <el-input v-model="form.studentName" placeholder="便于在列表中辨认" />
          </el-form-item>
        </el-form>

        <div class="drop-zone">
          <FileUploader
            :upload-fn="doUpload"
            accept=".jpg,.jpeg,.png,.webp"
            :disabled="!form.paperId || !form.studentNo"
            prompt="上传该考生的答卷扫描图片"
          />
          <p class="drop-tip">需先选择试卷并填写学号；上传成功的记录会出现在右侧列表。</p>
        </div>
      </aside>

      <!-- 右：答卷流水线 -->
      <section class="answer-panel">
        <div class="panel-head">
          <div>
            <span class="panel-eyebrow">本试卷答卷</span>
            <h3 class="panel-title">{{ answers.length }} 份记录</h3>
          </div>
          <el-button :loading="loading" plain size="small" @click="loadAnswers">刷新</el-button>
        </div>

        <div class="count-row">
          <div v-for="item in counts" :key="item.label" class="count-chip" :class="item.tone">
            <b>{{ item.value }}</b>
            <span>{{ item.label }}</span>
          </div>
        </div>

        <div v-loading="loading" class="answer-list">
          <article v-for="row in answers" :key="row.id" class="answer-row">
            <span class="row-avatar">{{ (row.studentName || row.studentNo || '?').slice(0, 1) }}</span>

            <div class="row-main">
              <div class="row-top">
                <b>{{ row.studentName || '未命名考生' }}</b>
                <span class="row-no">{{ row.studentNo }}</span>
              </div>
              <div class="row-tags">
                <el-tag :type="row.hasImage ? 'success' : 'info'" size="small" effect="plain">
                  {{ row.hasImage ? '已上传扫描件' : '未上传' }}
                </el-tag>
                <el-tag :type="dictItem(MARKING_STATUS, row.markingStatus).type" size="small">
                  {{ row.markingStatusDesc || dictItem(MARKING_STATUS, row.markingStatus).label }}
                </el-tag>
                <span class="row-time">{{ formatTime(row.createTime) }}</span>
              </div>
            </div>

            <div class="row-side">
              <div class="row-score">
                <b>{{ row.aiReferenceScore ?? '-' }}</b>
                <span>AI 参考分</span>
              </div>
              <el-button
                v-permission="'paper-answer:ocr'"
                link
                type="primary"
                :disabled="!row.hasImage || [1, 2].includes(row.markingStatus)"
                :loading="ocrLoadingId === row.id"
                @click="handleOcr(row)"
              >
                触发 OCR
              </el-button>
            </div>
          </article>

          <el-empty
            v-if="!loading && !answers.length"
            description="暂无答卷，请先导入名单或上传图片"
            :image-size="90"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.workbench {
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

/* 上传工作区 */
.upload-card {
  position: sticky;
  top: 16px;
  padding: 24px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.upload-head h3 {
  margin: 6px 0 6px;
  font-size: 18px;
  font-weight: 700;
  color: #23264a;
}

.upload-eyebrow {
  font-size: 12px;
  color: #9aa0b8;
  letter-spacing: 1px;
}

.upload-head p {
  margin: 0 0 20px;
  font-size: 12px;
  line-height: 1.8;
  color: #9aa0b8;
}

.upload-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.full {
  width: 100%;
}

.drop-zone {
  padding-top: 18px;
  border-top: 1px dashed #e9ecf7;
}

.drop-tip {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #9aa0b8;
}

/* 答卷流水线 */
.answer-panel {
  padding: 22px 24px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.panel-eyebrow {
  font-size: 12px;
  color: #9aa0b8;
}

.panel-title {
  margin: 4px 0 0;
  font-size: 18px;
  font-weight: 700;
  color: #23264a;
}

.count-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 16px 0 18px;
}

.count-chip {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 14px;
  background: #f7f8ff;
  border: 1px solid #eef1fa;
  border-radius: 12px;
}

.count-chip b {
  font-size: 18px;
  font-weight: 700;
  color: #23264a;
}

.count-chip span {
  font-size: 12px;
  color: #9aa0b8;
}

.count-chip.blue {
  border-left: 3px solid #5b6cff;
}

.count-chip.green {
  border-left: 3px solid #2fae7a;
}

.count-chip.orange {
  border-left: 3px solid #e6a23c;
}

.count-chip.purple {
  border-left: 3px solid #9a5bff;
}

.answer-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 560px;
  padding-right: 2px;
  overflow-y: auto;
}

.answer-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-radius: 14px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.answer-row:hover {
  border-color: #d5dcfa;
  box-shadow: 0 8px 20px rgba(60, 70, 160, 0.07);
}

.row-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(140deg, #5b6cff, #9a5bff);
  border-radius: 12px;
}

.row-main {
  flex: 1;
  min-width: 0;
}

.row-top {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.row-top b {
  font-size: 14px;
  color: #23264a;
}

.row-no {
  font-size: 12px;
  color: #9aa0b8;
}

.row-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.row-time {
  font-size: 12px;
  color: #a9aec4;
}

.row-side {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.row-score {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.row-score b {
  font-size: 17px;
  font-weight: 700;
  color: #5b6cff;
}

.row-score span {
  font-size: 11px;
  color: #9aa0b8;
}

@media (max-width: 1000px) {
  .workbench {
    grid-template-columns: 1fr;
  }

  .upload-card {
    position: static;
  }

  .count-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
