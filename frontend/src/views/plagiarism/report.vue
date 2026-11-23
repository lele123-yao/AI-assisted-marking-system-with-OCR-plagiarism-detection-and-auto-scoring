<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { generateReport, getAnswerReport, getReport } from '@/api/plagiarism'
import { pagePapers } from '@/api/paper'
import PageHero from '@/components/PageHero.vue'
import { formatTime } from '@/utils/dict'

/** 抄袭报告：风险总览 + 疑似答卷卡片 + 相似答卷对 */
const route = useRoute()
const papers = ref([])
const paperId = ref(null)
const report = ref(null)
const loading = ref(false)
const generating = ref(false)

const answerDialog = ref(false)
const answerReport = ref(null)
const answerLoading = ref(false)

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
  if (route.query.paperId) {
    paperId.value = Number(route.query.paperId)
    loadReport()
  }
}

async function loadReport() {
  if (!paperId.value) {
    ElMessage.warning('请先选择试卷')
    return
  }
  loading.value = true
  try {
    report.value = await getReport(paperId.value)
  } catch {
    // 报告不存在时后端会返回业务异常，这里保留空态
    report.value = null
  } finally {
    loading.value = false
  }
}

async function handleGenerate() {
  if (!paperId.value) {
    ElMessage.warning('请先选择试卷')
    return
  }
  generating.value = true
  try {
    report.value = await generateReport(paperId.value)
    ElMessage.success('报告已生成')
  } finally {
    generating.value = false
  }
}

async function openAnswerReport(row) {
  answerDialog.value = true
  answerReport.value = null
  answerLoading.value = true
  try {
    answerReport.value = await getAnswerReport(row.answerId)
  } finally {
    answerLoading.value = false
  }
}

/** 相似度兼容 0~1 与 0~100 两种返回 */
function formatSimilarity(value) {
  const num = Number(value)
  if (Number.isNaN(num)) return '-'
  return num <= 1 ? `${(num * 100).toFixed(1)}%` : `${num.toFixed(1)}%`
}

/** 风险等级标签颜色 */
function riskTag(level) {
  const value = String(level ?? '').toUpperCase()
  if (value.includes('HIGH') || value === '3' || value.includes('高')) return 'danger'
  if (value.includes('MEDIUM') || value === '2' || value.includes('中')) return 'warning'
  return 'info'
}

function riskTone(level) {
  const tag = riskTag(level)
  return tag === 'danger' ? 'high' : tag === 'warning' ? 'warn' : 'low'
}

const selectedPaper = computed(
  () => papers.value.find((item) => item.id === paperId.value) || null
)

/** 风险分布条：报告字段计算占比 */
const riskProfile = computed(() => {
  const data = report.value
  if (!data) return null
  const high = Number(data.highRiskPairCount || 0)
  const suspicious = Number(data.suspiciousPairCount || 0)
  const compared = Number(data.comparedPairCount || 0)
  const normal = Math.max(compared - high - suspicious, 0)
  const base = compared || 1
  return {
    high,
    suspicious,
    normal,
    compared,
    highRate: Math.round((high / base) * 100),
    suspiciousRate: Math.round((suspicious / base) * 100)
  }
})

onMounted(loadPapers)
</script>

<template>
  <div class="page-container">
    <PageHero
      title="抄袭检测报告"
      subtitle="汇总 SimHash 相似度比对结果，标记高风险与可疑答卷对，可下钻查看单份答卷的查重明细。"
      icon="DataAnalysis"
      tone="light"
    >
      <template #meta>
        <div v-if="selectedPaper || report" class="hero-meta">
          <el-tag v-if="selectedPaper" effect="plain">
            {{ selectedPaper.paperTitle }}（{{ selectedPaper.paperNo }}）
          </el-tag>
          <el-tag v-if="report" type="info" effect="plain">
            生成时间：{{ formatTime(report.generatedTime) }}
          </el-tag>
        </div>
      </template>
      <template #actions>
        <el-button type="success" :loading="generating" @click="handleGenerate">重新生成</el-button>
      </template>
    </PageHero>

    <!-- 查询条 -->
    <div class="query-bar">
      <span class="query-label">报告试卷</span>
      <el-select v-model="paperId" filterable placeholder="请选择试卷" class="query-select">
        <el-option
          v-for="paper in papers"
          :key="paper.id"
          :label="`${paper.paperTitle}（${paper.paperNo}）`"
          :value="paper.id"
        />
      </el-select>
      <el-button type="primary" :loading="loading" @click="loadReport">查询报告</el-button>
      <span class="query-tip">报告不存在时需先在「相似度比对」中生成</span>
    </div>

    <template v-if="report && riskProfile">
      <!-- 风险总览 -->
      <section class="risk-board">
        <div class="board-head">
          <div>
            <span class="board-eyebrow">风险总览</span>
            <h3 class="board-title">{{ selectedPaper?.paperTitle || '抄袭检测报告' }}</h3>
          </div>
          <el-tag v-if="riskProfile.high" type="danger" effect="dark">高风险 {{ riskProfile.high }} 对</el-tag>
          <el-tag v-else-if="riskProfile.suspicious" type="warning" effect="dark">
            可疑 {{ riskProfile.suspicious }} 对
          </el-tag>
          <el-tag v-else type="success" effect="dark">未发现异常</el-tag>
        </div>

        <div class="board-metrics">
          <div class="board-metric">
            <b>{{ report.totalAnswerCount ?? 0 }}</b>
            <span>答卷总数</span>
          </div>
          <div class="board-metric">
            <b>{{ report.fingerprintedCount ?? 0 }}</b>
            <span>已生成指纹</span>
          </div>
          <div class="board-metric">
            <b>{{ riskProfile.compared }}</b>
            <span>比对对数</span>
          </div>
          <div class="board-metric">
            <b>{{ report.skippedCount ?? 0 }}</b>
            <span>跳过（未识别）</span>
          </div>
        </div>

        <div class="board-meter">
          <span class="m-high" :style="{ width: `${riskProfile.highRate}%` }" />
          <span class="m-warn" :style="{ width: `${riskProfile.suspiciousRate}%` }" />
        </div>
        <div class="board-legend">
          <span><i class="dot-high" />高风险 {{ riskProfile.high }}</span>
          <span><i class="dot-warn" />可疑 {{ riskProfile.suspicious }}</span>
          <span><i class="dot-low" />正常 {{ riskProfile.normal }}</span>
        </div>
      </section>

      <!-- 疑似答卷卡片墙 -->
      <section class="suspect-section">
        <div class="section-head">
          <span class="section-title">疑似抄袭答卷</span>
          <span class="section-sub">共 {{ report.suspects?.length || 0 }} 份，点击查看逐份明细</span>
        </div>

        <div v-if="report.suspects?.length" class="suspect-grid">
          <article
            v-for="row in report.suspects"
            :key="row.answerId"
            class="suspect-card"
            :class="riskTone(row.riskLevel)"
          >
            <div class="suspect-top">
              <span class="avatar">{{ (row.studentName || row.studentNo || '?').slice(0, 1) }}</span>
              <div class="suspect-id">
                <b>{{ row.studentName || '未知考生' }}</b>
                <span>{{ row.studentNo }}</span>
              </div>
              <el-tag :type="riskTag(row.riskLevel)" size="small">
                {{ row.riskLevelDesc || row.riskLevel || '-' }}
              </el-tag>
            </div>

            <div class="suspect-sim">
              <b>{{ formatSimilarity(row.maxSimilarity) }}</b>
              <span>最高相似度</span>
            </div>

            <div class="suspect-foot">
              <span>相似答卷 {{ row.similarCount ?? 0 }} 份</span>
              <el-button link type="primary" @click="openAnswerReport(row)">查看明细</el-button>
            </div>
          </article>
        </div>

        <el-empty v-else description="未发现疑似答卷" :image-size="90" />
      </section>

      <!-- 相似答卷对 -->
      <el-card shadow="never" class="pairs-card">
        <template #header>
          <div class="section-head">
            <span class="section-title">相似答卷对</span>
            <span class="section-sub">按相似度排序，汉明距离越小越相似</span>
          </div>
        </template>
        <el-table :data="report.pairs || []" border stripe>
          <el-table-column label="答卷A" min-width="170">
            <template #default="{ row }">
              {{ row.studentNoA }} {{ row.studentNameA || '' }}
            </template>
          </el-table-column>
          <el-table-column label="答卷B" min-width="170">
            <template #default="{ row }">
              {{ row.studentNoB }} {{ row.studentNameB || '' }}
            </template>
          </el-table-column>
          <el-table-column prop="hammingDistance" label="汉明距离" width="110" />
          <el-table-column label="相似度" width="110">
            <template #default="{ row }">{{ formatSimilarity(row.similarity) }}</template>
          </el-table-column>
          <el-table-column label="风险等级" width="120">
            <template #default="{ row }">
              <el-tag :type="riskTag(row.riskLevel)">
                {{ row.riskLevelDesc || row.riskLevel || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无相似答卷对" :image-size="80" />
          </template>
        </el-table>
      </el-card>
    </template>

    <div v-else v-loading="loading" class="empty-panel">
      <el-empty description="暂无抄袭报告，请选择试卷并查询，或先生成报告" :image-size="90" />
    </div>

    <!-- 单份答卷明细 -->
    <el-dialog v-model="answerDialog" title="答卷查重明细" width="820px">
      <div v-loading="answerLoading">
        <el-descriptions v-if="answerReport" :column="3" border class="mb-16">
          <el-descriptions-item label="学号">{{ answerReport.studentNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ answerReport.studentName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="比对份数">{{ answerReport.comparedCount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="最高相似度">
            {{ formatSimilarity(answerReport.maxSimilarity) }}
          </el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="riskTag(answerReport.riskLevel)">
              {{ answerReport.riskLevelDesc || answerReport.riskLevel || '-' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-table :data="answerReport?.similarAnswers || []" border size="small">
          <el-table-column prop="studentNo" label="对方学号" width="140" />
          <el-table-column prop="studentName" label="对方姓名" width="110" />
          <el-table-column prop="hammingDistance" label="汉明距离" width="110" />
          <el-table-column label="相似度" width="110">
            <template #default="{ row }">{{ formatSimilarity(row.similarity) }}</template>
          </el-table-column>
          <el-table-column label="风险等级" width="120">
            <template #default="{ row }">
              <el-tag :type="riskTag(row.riskLevel)">
                {{ row.riskLevelDesc || row.riskLevel || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无相似答卷" :image-size="60" />
          </template>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

/* 查询条 */
.query-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 14px;
  box-shadow: 0 6px 18px rgba(40, 50, 120, 0.04);
}

.query-label {
  font-size: 13px;
  font-weight: 600;
  color: #2b2e45;
}

.query-select {
  width: 320px;
}

.query-tip {
  margin-left: auto;
  font-size: 12px;
  color: #9aa0b8;
}

/* 风险总览 */
.risk-board {
  padding: 24px 28px;
  color: #fff;
  background:
    radial-gradient(420px 220px at 100% 0%, rgba(154, 91, 255, 0.38), transparent 62%),
    linear-gradient(120deg, #221c4f 0%, #332a7d 55%, #4a3fa8 100%);
  border: 1px solid rgba(140, 156, 255, 0.2);
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(25, 28, 80, 0.22);
}

.board-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.board-eyebrow {
  font-size: 12px;
  color: #a9b4f2;
  letter-spacing: 1px;
}

.board-title {
  margin: 6px 0 0;
  font-size: 19px;
  font-weight: 700;
  color: #f0f2ff;
}

.board-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 20px 0 18px;
}

.board-metric {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(160, 174, 255, 0.16);
  border-radius: 12px;
}

.board-metric b {
  font-size: 21px;
  font-weight: 700;
  color: #eef1ff;
}

.board-metric span {
  font-size: 12px;
  color: rgba(190, 198, 240, 0.65);
}

.board-meter {
  display: flex;
  width: 100%;
  height: 8px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 999px;
}

.m-high {
  height: 100%;
  background: #f56c6c;
}

.m-warn {
  height: 100%;
  background: #e6a23c;
}

.board-legend {
  display: flex;
  gap: 20px;
  margin-top: 12px;
  font-size: 12px;
  color: rgba(190, 198, 240, 0.75);
}

.board-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.board-legend i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot-high {
  background: #f56c6c;
}

.dot-warn {
  background: #e6a23c;
}

.dot-low {
  background: #7ce0b4;
}

/* 疑似答卷 */
.suspect-section {
  padding: 22px 24px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.section-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #23264a;
}

.section-sub {
  font-size: 12px;
  color: #9aa0b8;
}

.suspect-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.suspect-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-left: 3px solid #c8cde0;
  border-radius: 14px;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.suspect-card:hover {
  box-shadow: 0 12px 26px rgba(60, 70, 160, 0.1);
}

.suspect-card.high {
  border-left-color: #f56c6c;
}

.suspect-card.warn {
  border-left-color: #e6a23c;
}

.suspect-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
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

.suspect-id {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.suspect-id b {
  overflow: hidden;
  font-size: 14px;
  color: #23264a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.suspect-id span {
  font-size: 12px;
  color: #9aa0b8;
}

.suspect-sim {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.suspect-sim b {
  font-size: 24px;
  font-weight: 700;
  color: #5b6cff;
}

.suspect-card.high .suspect-sim b {
  color: #f56c6c;
}

.suspect-card.warn .suspect-sim b {
  color: #e6a23c;
}

.suspect-sim span {
  font-size: 12px;
  color: #9aa0b8;
}

.suspect-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  font-size: 12px;
  color: #9aa0b8;
  border-top: 1px dashed #e9ecf7;
}

.pairs-card :deep(.el-card__header) {
  padding-bottom: 14px;
}

.empty-panel {
  padding: 20px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
}

.mb-16 {
  margin-bottom: 16px;
}

@media (max-width: 900px) {
  .board-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
