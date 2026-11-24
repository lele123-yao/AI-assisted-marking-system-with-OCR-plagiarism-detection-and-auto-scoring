<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { generateAnalysis, generatePpt } from '@/api/learning'
import { pagePapers } from '@/api/paper'
import { isWeChat } from '@/utils/device'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileCard from '@/components/m/MobileCard.vue'

/** 讲评 PPT：先生成讲评报告取得 reportId，再生成 PPT 并下载 */
const route = useRoute()
const papers = ref([])
const paperId = ref(null)
const reportId = ref(null)
const generatingReport = ref(false)
const generatingPpt = ref(false)
const ppt = ref(null)

const form = reactive({ reportTitle: '' })

const selectedPaper = computed(() => papers.value.find((item) => item.id === paperId.value) || null)

const downloadUrl = computed(() => ppt.value?.downloadUrl || ppt.value?.pptFileUrl || '')

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 50 })
  papers.value = data?.records || []
  const preset = Number(route.query.paperId)
  if (preset && papers.value.some((paper) => paper.id === preset)) {
    paperId.value = preset
  } else if (papers.value.length) {
    paperId.value = papers.value[0].id
  }
  if (route.query.reportId) reportId.value = Number(route.query.reportId)
  syncTitle()
}

function syncTitle() {
  form.reportTitle = selectedPaper.value ? `${selectedPaper.value.paperTitle} 学情讲评` : ''
}

/** 切换试卷后旧报告与课件不再匹配，重置状态 */
function handlePaperChange() {
  reportId.value = null
  ppt.value = null
  syncTitle()
}

async function handleGenerateReport() {
  if (generatingReport.value) return
  if (!paperId.value) {
    ElMessage.warning('请先选择试卷')
    return
  }
  generatingReport.value = true
  try {
    const report = await generateAnalysis({
      paperId: paperId.value,
      reportTitle: form.reportTitle || undefined
    })
    reportId.value = report?.id || null
    ppt.value = null
    if (!reportId.value) {
      ElMessage.error('报告生成成功但未返回报告ID，请稍后重试')
      return
    }
    ElMessage.success('讲评报告已生成，可继续生成 PPT')
  } finally {
    generatingReport.value = false
  }
}

async function handleGeneratePpt() {
  if (generatingPpt.value) return
  if (!reportId.value) {
    ElMessage.warning('请先生成讲评报告')
    return
  }
  generatingPpt.value = true
  try {
    ppt.value = await generatePpt(reportId.value)
    ElMessage.success('PPT 已生成')
  } finally {
    generatingPpt.value = false
  }
}

/** 微信内无法直接下载，提示在浏览器中打开 */
async function ensureNotWeChat() {
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

async function openPpt() {
  if (!downloadUrl.value) {
    ElMessage.warning('暂无可下载地址')
    return
  }
  if (!(await ensureNotWeChat())) return
  const opened = window.open(downloadUrl.value, '_blank', 'noopener')
  if (!opened) window.location.href = downloadUrl.value
}

/** 复制失败时回退到 textarea + execCommand */
function fallbackCopy(text) {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const ok = document.execCommand('copy')
  textarea.remove()
  if (!ok) throw new Error('copy failed')
}

async function copyLink() {
  if (!downloadUrl.value) {
    ElMessage.warning('暂无可复制的下载链接')
    return
  }
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(downloadUrl.value)
    } else {
      fallbackCopy(downloadUrl.value)
    }
    ElMessage.success('下载链接已复制')
  } catch {
    try {
      fallbackCopy(downloadUrl.value)
      ElMessage.success('下载链接已复制')
    } catch {
      ElMessage.error('复制失败，请手动选择链接复制')
    }
  }
}

onMounted(loadPapers)
</script>

<template>
  <div class="m-page">
    <MobileHero title="讲评 PPT" subtitle="先由报告生成课件，再下载带临时签名的 PPTX" icon="Present" />

    <!-- 步骤一：生成讲评报告 -->
    <MobileCard title="生成讲评报告">
      <template #extra>
        <el-tag :type="reportId ? 'success' : 'info'" size="small" effect="plain">
          {{ reportId ? '已完成' : '待执行' }}
        </el-tag>
      </template>

      <span class="m-label">选择试卷</span>
      <el-select
        v-model="paperId"
        filterable
        placeholder="请选择试卷"
        class="paper-select"
        @change="handlePaperChange"
      >
        <el-option
          v-for="paper in papers"
          :key="paper.id"
          :label="`${paper.paperTitle}（${paper.paperNo}）`"
          :value="paper.id"
        />
      </el-select>

      <span class="m-label block-gap">报告标题（可选）</span>
      <el-input v-model="form.reportTitle" placeholder="默认使用「试卷名 学情讲评」" />

      <el-button
        type="primary"
        class="action-btn"
        :loading="generatingReport"
        :disabled="!paperId"
        @click="handleGenerateReport"
      >
        {{ reportId ? '重新生成讲评报告' : '生成讲评报告' }}
      </el-button>

      <p class="tip">
        报告由大模型基于答卷统计生成，通常需要 30 秒以上，请保持页面打开。
        <template v-if="reportId">当前报告ID：{{ reportId }}</template>
      </p>
    </MobileCard>

    <el-alert
      v-if="generatingReport"
      type="warning"
      show-icon
      :closable="false"
      title="正在生成讲评报告，预计 30 秒以上"
      description="请勿重复点击或关闭页面。"
    />

    <!-- 步骤二：生成并下载 PPT -->
    <MobileCard title="生成讲评 PPT">
      <template #extra>
        <el-tag :type="ppt ? 'success' : 'info'" size="small" effect="plain">
          {{ ppt ? '已生成' : '待执行' }}
        </el-tag>
      </template>

      <el-button
        type="primary"
        class="action-btn no-margin"
        :loading="generatingPpt"
        :disabled="!reportId"
        @click="handleGeneratePpt"
      >
        {{ ppt ? '重新生成 PPT' : '生成 PPT' }}
      </el-button>
      <p class="tip">
        {{ reportId ? '报告已就绪，可开始制作课件。' : '请先完成步骤一，生成报告后此按钮才可用。' }}
      </p>

      <div v-if="ppt" class="ppt-result">
        <div class="ppt-file">
          <span class="file-icon">PPTX</span>
          <div class="file-info">
            <b>{{ ppt.fileName || '学情分析报告.pptx' }}</b>
            <span>{{ ppt.reportTitle || form.reportTitle || selectedPaper?.paperTitle }}</span>
          </div>
        </div>

        <div class="ppt-actions">
          <el-button type="success" class="download-btn" @click="openPpt">
            <el-icon class="btn-icon"><Download /></el-icon>
            下载 PPT
          </el-button>
          <el-button class="copy-btn" plain @click="copyLink">
            <el-icon class="btn-icon"><CopyDocument /></el-icon>
            复制下载链接
          </el-button>
        </div>
        <p class="tip">下载地址为临时签名地址，请尽快下载或复制保存。</p>
      </div>
    </MobileCard>

    <MobileCard title="课件说明">
      <ul class="notes">
        <li>课件基于讲评报告自动编排，涵盖得分概况、知识点掌握与典型错题。</li>
        <li>报告与 PPT 均保存在服务端，可稍后重新生成下载链接。</li>
        <li>微信内无法直接下载，请复制链接到浏览器打开。</li>
      </ul>
    </MobileCard>
  </div>
</template>

<style scoped>
.paper-select {
  width: 100%;
  margin-top: 8px;
}

.block-gap {
  display: block;
  margin-top: 14px;
}

.action-btn {
  width: 100%;
  height: 42px;
  margin-top: 14px;
  color: #fff;
  background: linear-gradient(96deg, #5b6cff, #8a5bff);
  border: none;
  border-radius: 10px;
  box-shadow: 0 12px 24px rgba(91, 108, 255, 0.26);
}

.action-btn.no-margin {
  margin-top: 0;
}

.tip {
  margin: 9px 0 0;
  font-size: 11px;
  line-height: 1.7;
  color: #9aa0b8;
}

.ppt-result {
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px dashed #e9ecf7;
}

.ppt-file {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  background: #f7f8ff;
  border-radius: 10px;
}

.file-icon {
  flex-shrink: 0;
  padding: 7px 9px;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(150deg, #f0803c, #e8563c);
  border-radius: 7px;
}

.file-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.file-info b {
  overflow: hidden;
  font-size: 13px;
  color: #23264a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-info span {
  overflow: hidden;
  font-size: 11px;
  color: #9aa0b8;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ppt-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.download-btn,
.copy-btn {
  width: 100%;
  margin-left: 0;
}

.btn-icon {
  margin-right: 5px;
}

.notes {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding-left: 16px;
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: #8b90ab;
}
</style>
