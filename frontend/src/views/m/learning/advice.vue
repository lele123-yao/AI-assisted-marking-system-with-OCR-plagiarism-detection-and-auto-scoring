<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { generateAnalysis } from '@/api/learning'
import { pagePapers } from '@/api/paper'
import { formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'

/** AI 讲评建议：选择试卷生成讲评正文，文档式展示并支持复制全文 */
const route = useRoute()
const papers = ref([])
const paperId = ref(null)
const report = ref(null)
const generating = ref(false)

const form = reactive({ reportTitle: '' })

const selectedPaper = computed(() => papers.value.find((item) => item.id === paperId.value) || null)

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 50 })
  papers.value = data?.records || []
  const preset = Number(route.query.paperId)
  if (preset && papers.value.some((paper) => paper.id === preset)) {
    paperId.value = preset
  } else if (papers.value.length) {
    paperId.value = papers.value[0].id
  }
  syncTitle()
}

function syncTitle() {
  form.reportTitle = selectedPaper.value ? `${selectedPaper.value.paperTitle} 学情讲评` : ''
}

const paragraphs = computed(() =>
  (report.value?.analysisText || '')
    .split(/\n+/)
    .map((text) => text.trim())
    .filter(Boolean)
)

/** 识别「一、」「（一）」等小标题，方便手机端分段阅读 */
function isHeading(text) {
  return /^[（(]?[一二三四五六七八九十]+[）)、.．]/.test(text) || /^#+\s/.test(text)
}

const docStats = computed(() => ({
  chars: report.value?.analysisText?.length || 0,
  paragraphs: paragraphs.value.length
}))

async function handleGenerate() {
  if (generating.value) return
  if (!paperId.value) {
    ElMessage.warning('请先选择试卷')
    return
  }
  generating.value = true
  try {
    report.value = await generateAnalysis({
      paperId: paperId.value,
      reportTitle: form.reportTitle || undefined
    })
    ElMessage.success('讲评建议已生成')
  } finally {
    generating.value = false
  }
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

async function copyText(text) {
  if (!text) {
    ElMessage.warning('暂无可复制内容')
    return
  }
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      fallbackCopy(text)
    }
    ElMessage.success('已复制全文')
  } catch {
    try {
      fallbackCopy(text)
      ElMessage.success('已复制全文')
    } catch {
      ElMessage.error('复制失败，请手动选择文本复制')
    }
  }
}

onMounted(loadPapers)
</script>

<template>
  <div class="m-page">
    <MobileHero title="AI 讲评建议" subtitle="大模型结合答卷统计生成教学讲评正文" icon="ChatDotRound" />

    <section class="m-card">
      <div class="step">
        <span class="step-index">1</span>
        <span class="step-label">选择试卷</span>
      </div>
      <el-select
        v-model="paperId"
        filterable
        placeholder="请选择试卷"
        class="paper-select"
        @change="syncTitle"
      >
        <el-option
          v-for="paper in papers"
          :key="paper.id"
          :label="`${paper.paperTitle}（${paper.paperNo}）`"
          :value="paper.id"
        />
      </el-select>

      <div class="step step-gap">
        <span class="step-index">2</span>
        <span class="step-label">报告标题（可选）</span>
      </div>
      <el-input v-model="form.reportTitle" placeholder="默认使用「试卷名 学情讲评」" />

      <el-button
        type="primary"
        class="generate-btn"
        :loading="generating"
        :disabled="!paperId"
        @click="handleGenerate"
      >
        {{ report ? '重新生成讲评建议' : '生成 AI 讲评建议' }}
      </el-button>
      <p class="tip">报告由大模型生成，通常需要 30 秒以上，请保持页面打开。</p>
    </section>

    <el-alert
      v-if="generating"
      type="warning"
      show-icon
      :closable="false"
      title="正在生成讲评建议，预计 30 秒以上"
      description="大模型分析耗时较长，请勿重复点击或关闭页面。"
    />

    <section class="m-card doc">
      <template v-if="report">
        <div class="doc-head">
          <div class="doc-title-wrap">
            <span class="doc-eyebrow">学情讲评正文</span>
            <h2 class="doc-title">{{ report.reportTitle || form.reportTitle || '学情讲评建议' }}</h2>
          </div>
          <el-tag v-if="report.statusDesc" type="success" size="small" effect="plain">
            {{ report.statusDesc }}
          </el-tag>
        </div>

        <div class="doc-meta">
          <span>{{ formatTime(report.createTime) }}</span>
          <span>正文 {{ docStats.chars }} 字</span>
          <span>{{ docStats.paragraphs }} 段</span>
        </div>

        <el-alert
          v-if="report.errorMsg"
          type="error"
          show-icon
          :closable="false"
          :title="`生成失败：${report.errorMsg}`"
        />

        <div v-if="paragraphs.length" class="doc-body">
          <p
            v-for="(text, index) in paragraphs"
            :key="index"
            :class="{ heading: isHeading(text) }"
          >
            {{ text }}
          </p>
        </div>
        <MobileEmpty v-else description="暂无讲评正文" icon="ChatDotRound" />

        <el-button
          v-if="paragraphs.length"
          class="copy-btn"
          plain
          @click="copyText(report.analysisText)"
        >
          <el-icon class="btn-icon"><CopyDocument /></el-icon>
          复制全文
        </el-button>
      </template>

      <MobileEmpty
        v-else
        description="还没有生成讲评建议，请选择试卷后点击生成"
        icon="ChatDotRound"
      />
    </section>
  </div>
</template>

<style scoped>
.step {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-gap {
  margin-top: 14px;
}

.step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(140deg, #5b6cff, #9a5bff);
  border-radius: 7px;
}

.step-label {
  font-size: 13px;
  font-weight: 600;
  color: #2b2e45;
}

.paper-select {
  width: 100%;
  margin-top: 8px;
}

.generate-btn {
  width: 100%;
  height: 42px;
  margin-top: 14px;
  color: #fff;
  background: linear-gradient(96deg, #5b6cff, #8a5bff);
  border: none;
  border-radius: 10px;
  box-shadow: 0 12px 24px rgba(91, 108, 255, 0.26);
}

.tip {
  margin: 9px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: #9aa0b8;
}

.doc {
  padding: 16px 14px;
}

.doc-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eef1fa;
}

.doc-title-wrap {
  min-width: 0;
}

.doc-eyebrow {
  font-size: 10px;
  letter-spacing: 1px;
  color: #9aa0b8;
}

.doc-title {
  margin: 4px 0 0;
  font-size: 17px;
  font-weight: 700;
  line-height: 1.4;
  color: #23264a;
}

.doc-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 10px 0 12px;
  font-size: 11px;
  color: #9aa0b8;
}

.doc-body {
  padding: 14px 12px;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-radius: 12px;
}

.doc-body p {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.95;
  color: #33364f;
  word-break: break-word;
  white-space: pre-wrap;
}

.doc-body p:last-child {
  margin-bottom: 0;
}

.doc-body p.heading {
  margin-top: 14px;
  font-size: 14px;
  font-weight: 600;
  color: #23264a;
}

.copy-btn {
  width: 100%;
  margin-top: 12px;
}

.btn-icon {
  margin-right: 5px;
}
</style>
