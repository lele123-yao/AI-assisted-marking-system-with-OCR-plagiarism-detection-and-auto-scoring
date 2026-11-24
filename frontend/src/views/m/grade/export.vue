<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportGrades } from '@/api/grade'
import { pagePapers } from '@/api/paper'
import { downloadBlob } from '@/utils/download'
import { isWeChat } from '@/utils/device'
import { ANSWER_MODE, dictItem } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'

/** Excel 成绩导出：选择试卷后下载 xlsx 成绩单 */
const papers = ref([])
const paperId = ref(null)
const exporting = ref(false)

const selectedPaper = computed(() => papers.value.find((item) => item.id === paperId.value) || null)

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 50 })
  papers.value = data?.records || []
  if (!paperId.value && papers.value.length) paperId.value = papers.value[0].id
}

/** 微信内置浏览器无法直接下载文件，先提示在浏览器中打开 */
async function ensureDownloadable() {
  if (!isWeChat()) return true
  try {
    await ElMessageBox.confirm(
      '微信内无法直接下载，请点右上角 ··· 选择在浏览器中打开后重试',
      '下载提示',
      { type: 'warning', confirmButtonText: '我知道了', cancelButtonText: '取消' }
    )
  } catch {
    return false
  }
  return false
}

function todayStamp() {
  const date = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}`
}

async function handleExport() {
  if (exporting.value) return
  if (!paperId.value) {
    ElMessage.warning('请先选择试卷')
    return
  }
  if (!(await ensureDownloadable())) return

  exporting.value = true
  try {
    const blob = await exportGrades(paperId.value)
    if (!blob || !blob.size) {
      ElMessage.error('导出失败：文件内容为空')
      return
    }
    const paperNo = selectedPaper.value?.paperNo || selectedPaper.value?.paperTitle || '成绩单'
    downloadBlob(blob, `${paperNo}_成绩单_${todayStamp()}.xlsx`)
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

onMounted(loadPapers)
</script>

<template>
  <div class="m-page">
    <MobileHero title="成绩导出" subtitle="按试卷导出 Excel 成绩单，包含 AI 参考分与最终分" icon="Download">
      <template #actions>
        <el-button size="small" plain :loading="exporting" @click="handleExport">导出</el-button>
      </template>
    </MobileHero>

    <section class="m-card">
      <span class="m-label">选择试卷</span>
      <el-select v-model="paperId" filterable placeholder="请选择试卷" class="paper-select">
        <el-option
          v-for="paper in papers"
          :key="paper.id"
          :label="`${paper.paperTitle}（${paper.paperNo}）`"
          :value="paper.id"
        />
      </el-select>
    </section>

    <div v-if="selectedPaper" class="m-card file-card">
      <div class="file-badge">
        <el-icon :size="26"><Document /></el-icon>
        <span>XLSX</span>
      </div>
      <h3 class="file-name">{{ selectedPaper.paperTitle }}</h3>
      <p class="file-desc">
        包含学号、姓名、班级、AI 参考分、教师最终分与复核状态，可直接用 Excel / WPS 打开。
      </p>

      <div class="info-list">
        <div class="info-row">
          <span>试卷编号</span>
          <b>{{ selectedPaper.paperNo || '-' }}</b>
        </div>
        <div class="info-row">
          <span>作答模式</span>
          <b>{{ dictItem(ANSWER_MODE, selectedPaper.answerMode).label }}</b>
        </div>
        <div class="info-row">
          <span>试卷总分</span>
          <b>{{ selectedPaper.totalScore ?? '-' }}</b>
        </div>
        <div class="info-row">
          <span>题目数</span>
          <b>{{ selectedPaper.questionCount ?? '-' }}</b>
        </div>
        <div class="info-row">
          <span>文件名</span>
          <b class="filename">{{ selectedPaper.paperNo || '成绩单' }}_成绩单_{{ todayStamp() }}.xlsx</b>
        </div>
      </div>

      <el-button
        type="primary"
        class="export-btn"
        :loading="exporting"
        :disabled="!paperId"
        @click="handleExport"
      >
        <el-icon class="btn-icon"><Download /></el-icon>
        导出成绩单
      </el-button>
      <p class="tip">微信内无法直接下载，请在浏览器中打开本页后操作。</p>
    </div>

    <MobileEmpty
      v-else
      description="暂无可导出的试卷"
      icon="Document"
      action-text="刷新"
      @action="loadPapers"
    />
  </div>
</template>

<style scoped>
.paper-select {
  width: 100%;
  margin-top: 8px;
}

.file-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 22px 16px 18px;
  text-align: center;
  background:
    radial-gradient(280px 160px at 100% 0%, rgba(154, 91, 255, 0.12), transparent 62%),
    #fff;
}

.file-badge {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  width: 64px;
  height: 74px;
  color: #fff;
  background: linear-gradient(150deg, #5b6cff, #9a5bff);
  border-radius: 12px 12px 12px 4px;
  box-shadow: 0 12px 24px rgba(88, 100, 255, 0.28);
}

.file-badge span {
  font-size: 9px;
  letter-spacing: 1px;
  opacity: 0.85;
}

.file-name {
  margin: 14px 0 6px;
  font-size: 16px;
  font-weight: 600;
  color: #23264a;
}

.file-desc {
  margin: 0 0 14px;
  font-size: 12px;
  line-height: 1.7;
  color: #8b90ab;
}

.info-list {
  width: 100%;
  padding: 4px 0;
  margin-bottom: 16px;
  border-top: 1px dashed #eef1fa;
}

.info-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  font-size: 12px;
  border-bottom: 1px dashed #f2f4fb;
}

.info-row:last-child {
  border-bottom: none;
}

.info-row span {
  flex-shrink: 0;
  color: #8b90ab;
}

.info-row b {
  font-weight: 500;
  color: #3a3e5c;
  text-align: right;
  word-break: break-all;
}

.info-row b.filename {
  color: var(--ai-primary);
}

.export-btn {
  width: 100%;
  height: 44px;
  color: #fff;
  background: linear-gradient(96deg, #5b6cff, #8a5bff);
  border: none;
  border-radius: 10px;
  box-shadow: 0 12px 24px rgba(91, 108, 255, 0.26);
}

.btn-icon {
  margin-right: 6px;
}

.tip {
  margin: 10px 0 0;
  font-size: 11px;
  color: #b0b5c9;
}
</style>
