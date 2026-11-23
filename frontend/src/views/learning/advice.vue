<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { generateAnalysis } from '@/api/learning'
import { pagePapers } from '@/api/paper'
import PageHero from '@/components/PageHero.vue'
import { formatTime } from '@/utils/dict'

/** AI 讲评建议：左侧生成控制台 + 右侧讲评正文 */
const route = useRoute()
const router = useRouter()
const papers = ref([])
const paperId = ref(null)
const report = ref(null)
const generating = ref(false)

const form = reactive({ reportTitle: '' })

const selectedPaper = computed(() => papers.value.find((item) => item.id === paperId.value) || null)

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
  paperId.value = route.query.paperId ? Number(route.query.paperId) : papers.value[0]?.id || null
  syncTitle()
}

function syncTitle() {
  if (!form.reportTitle) {
    form.reportTitle = selectedPaper.value ? `${selectedPaper.value.paperTitle} 学情讲评` : ''
  }
}

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

function goPpt() {
  router.push({
    path: '/learning/ppt',
    query: { paperId: paperId.value, reportId: report.value?.id }
  })
}

/** 正文概览：字数、段落数、生成时间 */
const docStats = computed(() => {
  const text = report.value?.analysisText || ''
  return {
    chars: text.length,
    paragraphs: text.split(/\n+/).filter((item) => item.trim()).length
  }
})

onMounted(loadPapers)
</script>

<template>
  <div class="page-container">
    <PageHero
      title="AI 讲评建议"
      subtitle="选择试卷后由大模型生成教学讲评正文，梳理共性问题与讲评思路，可继续制作讲评 PPT。"
      icon="ChatDotRound"
      tone="light"
    >
      <template #meta>
        <div class="hero-tags">
          <el-tag v-if="selectedPaper" type="info" effect="plain">
            {{ selectedPaper.paperTitle }}
          </el-tag>
          <el-tag v-else type="info" effect="plain">未选择试卷</el-tag>
          <el-tag :type="report ? 'success' : 'info'" effect="plain">
            {{ report ? report.statusDesc || '讲评已生成' : '尚未生成' }}
          </el-tag>
        </div>
      </template>
    </PageHero>

    <div class="advice-layout">
      <!-- 左：生成控制台 -->
      <aside class="control-card">
        <div class="control-step">
          <span class="step-index">1</span>
          <div class="step-body">
            <label class="step-label">选择试卷</label>
            <el-select v-model="paperId" filterable class="full" @change="syncTitle">
              <el-option
                v-for="paper in papers"
                :key="paper.id"
                :label="`${paper.paperTitle}（${paper.paperNo}）`"
                :value="paper.id"
              />
            </el-select>
          </div>
        </div>

        <div class="control-step">
          <span class="step-index">2</span>
          <div class="step-body">
            <label class="step-label">报告标题（可选）</label>
            <el-input v-model="form.reportTitle" placeholder="默认使用「试卷名 学情讲评」" />
          </div>
        </div>

        <div class="control-step">
          <span class="step-index">3</span>
          <div class="step-body">
            <label class="step-label">生成讲评</label>
            <el-button
              v-permission="'learning:generate'"
              type="primary"
              class="generate-btn"
              :loading="generating"
              @click="handleGenerate"
            >
              {{ report ? '重新生成讲评建议' : '生成 AI 讲评建议' }}
            </el-button>
            <el-button v-if="report" class="ppt-btn" @click="goPpt">继续生成讲评 PPT</el-button>
          </div>
        </div>

        <el-alert
          type="info"
          show-icon
          :closable="false"
          class="control-alert"
          title="报告由大模型生成，耗时可能较长"
          description="刷新页面后需重新生成才能再次查看正文，生成的报告记录与 PPT 仍保存在服务端。"
        />
      </aside>

      <!-- 右：讲评正文 -->
      <section v-loading="generating" class="doc-panel">
        <template v-if="report">
          <div class="doc-head">
            <div>
              <span class="doc-eyebrow">学情讲评正文</span>
              <h2 class="doc-title">{{ report.reportTitle || '学情讲评建议' }}</h2>
            </div>
            <div class="doc-badges">
              <el-tag v-if="report.statusDesc" type="success" effect="plain">
                {{ report.statusDesc }}
              </el-tag>
              <span class="doc-time">{{ formatTime(report.createTime) }}</span>
            </div>
          </div>

          <div class="doc-stats">
            <div class="doc-stat">
              <b>{{ docStats.chars }}</b>
              <span>正文字数</span>
            </div>
            <div class="doc-stat">
              <b>{{ docStats.paragraphs }}</b>
              <span>段落数</span>
            </div>
            <div class="doc-stat">
              <b>{{ selectedPaper?.paperTitle || '-' }}</b>
              <span>关联试卷</span>
            </div>
          </div>

          <el-alert
            v-if="report.errorMsg"
            type="error"
            show-icon
            :closable="false"
            :title="`生成失败：${report.errorMsg}`"
            class="mb-16"
          />

          <pre v-if="report.analysisText" class="analysis">{{ report.analysisText }}</pre>
          <el-empty v-else description="暂无讲评正文" :image-size="80" />
        </template>

        <div v-else class="doc-placeholder">
          <div class="placeholder-icon">
            <el-icon :size="34"><ChatDotRound /></el-icon>
          </div>
          <h3>还没有生成讲评建议</h3>
          <p>在左侧选择试卷并点击「生成 AI 讲评建议」，大模型会结合答卷统计与知识点得分率输出教学讲评。</p>
          <ul class="placeholder-list">
            <li>共性问题归纳</li>
            <li>重点错因剖析</li>
            <li>课堂讲评思路</li>
          </ul>
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

.advice-layout {
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

/* 控制台 */
.control-card {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 24px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.control-step {
  display: flex;
  gap: 14px;
}

.step-index {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  margin-top: 2px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(140deg, #5b6cff, #9a5bff);
  border-radius: 9px;
}

.step-body {
  display: flex;
  flex-direction: column;
  gap: 9px;
  flex: 1;
  min-width: 0;
}

.step-label {
  font-size: 13px;
  font-weight: 600;
  color: #2b2e45;
}

.full {
  width: 100%;
}

.generate-btn {
  width: 100%;
  height: 42px;
  background: linear-gradient(96deg, #5b6cff, #8a5bff);
  border: none;
  border-radius: 10px;
  box-shadow: 0 12px 26px rgba(91, 108, 255, 0.28);
}

.ppt-btn {
  width: 100%;
  margin-left: 0;
  border-radius: 10px;
}

.control-alert {
  border-radius: 12px;
}

.control-alert :deep(.el-alert__description) {
  margin-top: 6px;
  line-height: 1.7;
}

/* 正文面板 */
.doc-panel {
  min-height: 520px;
  padding: 26px 30px 30px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.doc-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 18px;
  border-bottom: 1px solid #eef1fa;
}

.doc-eyebrow {
  font-size: 12px;
  color: #9aa0b8;
  letter-spacing: 1px;
}

.doc-title {
  margin: 6px 0 0;
  font-size: 20px;
  font-weight: 700;
  color: #23264a;
}

.doc-badges {
  display: flex;
  align-items: center;
  gap: 12px;
}

.doc-time {
  font-size: 12px;
  color: #9aa0b8;
}

.doc-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 18px 0 20px;
}

.doc-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  background: #f7f8ff;
  border: 1px solid #eef1fa;
  border-radius: 12px;
}

.doc-stat b {
  overflow: hidden;
  font-size: 18px;
  font-weight: 700;
  color: #23264a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-stat span {
  font-size: 12px;
  color: #9aa0b8;
}

.analysis {
  padding: 22px 24px;
  margin: 0;
  font-family: inherit;
  font-size: 14px;
  line-height: 2;
  color: #33364f;
  word-break: break-word;
  white-space: pre-wrap;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-radius: 14px;
}

.mb-16 {
  margin-bottom: 16px;
}

/* 空态 */
.doc-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 460px;
  text-align: center;
}

.placeholder-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 78px;
  height: 78px;
  margin-bottom: 18px;
  color: #fff;
  background: linear-gradient(150deg, #5b6cff, #9a5bff);
  border-radius: 22px;
  box-shadow: 0 16px 30px rgba(91, 108, 255, 0.3);
}

.doc-placeholder h3 {
  margin: 0 0 10px;
  font-size: 17px;
  color: #23264a;
}

.doc-placeholder p {
  max-width: 380px;
  margin: 0 0 22px;
  font-size: 13px;
  line-height: 1.9;
  color: #8b90ab;
}

.placeholder-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.placeholder-list li {
  padding: 6px 14px;
  font-size: 12px;
  color: #5b6cff;
  background: #eef1ff;
  border-radius: 999px;
}

@media (max-width: 1000px) {
  .advice-layout {
    grid-template-columns: 1fr;
  }

  .control-card {
    position: static;
  }

  .doc-stats {
    grid-template-columns: 1fr;
  }
}
</style>
