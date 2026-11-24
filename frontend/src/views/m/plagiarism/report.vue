<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getReport } from '@/api/plagiarism'
import { pagePapers } from '@/api/paper'
import { formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'

/** 移动端抄袭检测报告：风险总览 + 疑似卷对 + 展开比对明细 */
const route = useRoute()
const router = useRouter()

const papers = ref([])
const paperId = ref(null)
const papersLoading = ref(false)
const loading = ref(false)
const report = ref(null)
const expandedKey = ref('')

async function loadPapers() {
  papersLoading.value = true
  try {
    const data = await pagePapers({ pageNum: 1, pageSize: 50 })
    papers.value = data?.records || []
    if (route.query.paperId) {
      paperId.value = Number(route.query.paperId)
      loadReport()
    }
  } finally {
    papersLoading.value = false
  }
}

async function loadReport() {
  if (!paperId.value) {
    ElMessage.warning('请先选择试卷')
    return
  }
  loading.value = true
  expandedKey.value = ''
  try {
    report.value = await getReport(paperId.value)
  } catch {
    // 报告不存在时后端返回业务异常，这里保留空态
    report.value = null
  } finally {
    loading.value = false
  }
}

const selectedPaper = computed(
  () => papers.value.find((item) => item.id === paperId.value) || null
)

const pairs = computed(() => report.value?.pairs || [])

function formatSimilarity(value) {
  if (value === null || value === undefined || value === '') return '-'
  const num = Number(value)
  if (Number.isNaN(num)) return '-'
  return num <= 1 ? `${(num * 100).toFixed(1)}%` : `${num.toFixed(1)}%`
}

function percentOf(value) {
  const num = Number(value)
  if (Number.isNaN(num)) return 0
  return Math.min(100, num <= 1 ? num * 100 : num)
}

/** 风险等级：HIGH 红、SUSPICIOUS 橙、其余绿 */
function riskTag(level) {
  const value = String(level ?? '').toUpperCase()
  if (value.includes('HIGH')) return 'danger'
  if (value.includes('SUSPICIOUS') || value.includes('MEDIUM')) return 'warning'
  return 'success'
}

function riskTone(level) {
  const tag = riskTag(level)
  return tag === 'danger' ? 'high' : tag === 'warning' ? 'warn' : 'low'
}

function sameBits(row) {
  if (row.hammingDistance === null || row.hammingDistance === undefined) return '-'
  return 64 - Number(row.hammingDistance)
}

const stats = computed(() => {
  const values = pairs.value
    .map((row) => Number(row.similarity))
    .filter((value) => !Number.isNaN(value))
  const avg = values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null
  const max = values.length ? Math.max(...values) : null
  return {
    avg,
    max,
    high: Number(report.value?.highRiskPairCount || 0),
    suspicious: Number(report.value?.suspiciousPairCount || 0),
    compared: Number(report.value?.comparedPairCount || 0)
  }
})

const avgTone = computed(() => {
  const value = percentOf(stats.value.avg)
  if (stats.value.avg === null) return 'low'
  if (value >= 80) return 'high'
  if (value >= 60) return 'warn'
  return 'low'
})

function overallTag() {
  if (stats.value.high) return 'danger'
  if (stats.value.suspicious) return 'warning'
  return 'success'
}

function overallLabel() {
  if (stats.value.high) return `高风险 ${stats.value.high} 对`
  if (stats.value.suspicious) return `可疑 ${stats.value.suspicious} 对`
  return '未发现异常'
}

function goCompare() {
  router.push({ path: '/m/plagiarism/compare', query: { paperId: paperId.value || undefined } })
}

function toggle(row) {
  const key = `${row.answerIdA}-${row.answerIdB}`
  expandedKey.value = expandedKey.value === key ? '' : key
}

onMounted(loadPapers)
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="抄袭检测报告"
      subtitle="查看试卷级相似度总览与疑似卷对，点击卷对可展开比对明细。"
      icon="DataAnalysis"
    >
      <template #actions>
        <el-button size="small" plain :loading="loading" @click="loadReport">刷新</el-button>
      </template>
    </MobileHero>

    <section class="m-card">
      <div class="m-label">报告试卷</div>
      <el-select
        v-model="paperId"
        filterable
        placeholder="请选择试卷"
        class="paper-select"
        :loading="papersLoading"
      >
        <el-option
          v-for="paper in papers"
          :key="paper.id"
          :label="`${paper.paperTitle}（${paper.paperNo}）`"
          :value="paper.id"
        />
      </el-select>
      <el-button type="primary" class="query-btn" :loading="loading" @click="loadReport">
        查询报告
      </el-button>
    </section>

    <div v-loading="loading" class="report-body">
      <template v-if="report">
        <!-- 风险总览 -->
        <section class="m-card risk-board">
          <div class="m-row-between">
            <div>
              <span class="m-label">风险总览</span>
              <h3 class="m-title">{{ selectedPaper?.paperTitle || report.paperTitle || '抄袭报告' }}</h3>
              <p v-if="report.generatedTime" class="m-sub">
                生成时间 {{ formatTime(report.generatedTime) }}
              </p>
            </div>
            <el-tag :type="overallTag()" effect="dark">{{ overallLabel() }}</el-tag>
          </div>

          <div class="metrics">
            <div class="metric" :class="avgTone">
              <b>{{ formatSimilarity(stats.avg) }}</b>
              <span>平均相似度</span>
            </div>
            <div class="metric high">
              <b>{{ stats.high }}</b>
              <span>高风险对</span>
            </div>
            <div class="metric warn">
              <b>{{ formatSimilarity(stats.max) }}</b>
              <span>最高相似对</span>
            </div>
            <div class="metric low">
              <b>{{ stats.suspicious }}</b>
              <span>可疑对</span>
            </div>
          </div>

          <div class="meter">
            <span class="meter-high" :style="{ width: `${stats.compared ? Math.round((stats.high / stats.compared) * 100) : 0}%` }" />
            <span class="meter-warn" :style="{ width: `${stats.compared ? Math.round((stats.suspicious / stats.compared) * 100) : 0}%` }" />
          </div>

          <div class="overview-meta">
            <span>答卷总数 {{ report.totalAnswerCount ?? 0 }}</span>
            <span>已生成指纹 {{ report.fingerprintedCount ?? 0 }}</span>
            <span>比对对数 {{ stats.compared }}</span>
            <span>跳过 {{ report.skippedCount ?? 0 }}</span>
          </div>
        </section>

        <!-- 疑似卷对 -->
        <section class="pairs-block">
          <div class="m-row-between">
            <span class="m-title">疑似卷对</span>
            <span class="m-sub">共 {{ pairs.length }} 对，点击展开</span>
          </div>

          <article
            v-for="row in pairs"
            :key="`${row.answerIdA}-${row.answerIdB}`"
            class="m-card pair"
            :class="riskTone(row.riskLevel)"
            @click="toggle(row)"
          >
            <div class="pair-head">
              <div class="student">
                <b>{{ row.studentNameA || '未知考生' }}</b>
                <span>{{ row.studentNoA || '-' }}</span>
              </div>
              <el-icon class="pair-arrow"><ArrowRight /></el-icon>
              <div class="student">
                <b>{{ row.studentNameB || '未知考生' }}</b>
                <span>{{ row.studentNoB || '-' }}</span>
              </div>
              <el-tag :type="riskTag(row.riskLevel)" size="small" effect="plain">
                {{ row.riskLevelDesc || row.riskLevel || '-' }}
              </el-tag>
            </div>

            <div class="sim-row">
              <span>相似度</span>
              <b>{{ formatSimilarity(row.similarity) }}</b>
            </div>
            <div class="bar">
              <span
                class="bar-fill"
                :class="riskTag(row.riskLevel)"
                :style="{ width: `${percentOf(row.similarity)}%` }"
              />
            </div>
            <div class="pair-meta">
              <span>相同特征位 {{ sameBits(row) }}</span>
              <span>汉明距离 {{ row.hammingDistance ?? '-' }}</span>
              <el-icon class="expand-icon" :class="{ open: expandedKey === `${row.answerIdA}-${row.answerIdB}` }">
                <ArrowDown />
              </el-icon>
            </div>

            <div
              v-if="expandedKey === `${row.answerIdA}-${row.answerIdB}`"
              class="detail"
              @click.stop
            >
              <div class="detail-row"><span>相似度</span><b>{{ formatSimilarity(row.similarity) }}</b></div>
              <div class="detail-row"><span>汉明距离</span><b>{{ row.hammingDistance ?? '-' }}</b></div>
              <div class="detail-row"><span>相同特征位（SimHash）</span><b>{{ sameBits(row) }}</b></div>
              <div class="detail-row">
                <span>风险等级</span>
                <el-tag :type="riskTag(row.riskLevel)" size="small">
                  {{ row.riskLevelDesc || row.riskLevel || '-' }}
                </el-tag>
              </div>
              <p class="detail-note">
                当前比对基于 SimHash 指纹，报告仅返回相似度与汉明距离，不含原文片段文本。
              </p>
            </div>
          </article>

          <MobileEmpty
            v-if="!pairs.length"
            description="未发现相似答卷对"
            icon="DataAnalysis"
          />
        </section>
      </template>

      <section v-else class="m-card">
        <MobileEmpty
          description="暂无抄袭报告，请先到「相似度比对」生成"
          icon="DataAnalysis"
          action-text="去相似度比对"
          @action="goCompare"
        />
      </section>
    </div>
  </div>
</template>

<style scoped>
.paper-select {
  width: 100%;
  margin-top: 6px;
}

.query-btn {
  width: 100%;
  margin-top: 10px;
}

.report-body {
  min-height: 120px;
}

.report-body > * + * {
  margin-top: 14px;
}

.risk-board h3 {
  margin: 4px 0 0;
  font-size: 16px;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 11px 13px;
  background: #f8f9ff;
  border-radius: 12px;
}

.metric b {
  font-size: 20px;
  color: #5b6cff;
}

.metric span {
  font-size: 11px;
  color: #8b90ab;
}

.metric.high b {
  color: #f56c6c;
}

.metric.warn b {
  color: #e6a23c;
}

.metric.low b {
  color: #67c23a;
}

.meter {
  display: flex;
  height: 7px;
  margin-top: 14px;
  overflow: hidden;
  background: #eef1fb;
  border-radius: 999px;
}

.meter-high {
  height: 100%;
  background: #f56c6c;
}

.meter-warn {
  height: 100%;
  background: #e6a23c;
}

.overview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 10px;
  margin-top: 12px;
  font-size: 11px;
  color: #9aa0b8;
  border-top: 1px dashed #eef1fa;
}

.pairs-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pair {
  border-left: 3px solid #c8cde0;
}

.pair.high {
  border-left-color: #f56c6c;
}

.pair.warn {
  border-left-color: #e6a23c;
}

.pair.low {
  border-left-color: #67c23a;
}

.pair:active {
  transform: scale(0.995);
}

.pair-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.student {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.student b {
  font-size: 13px;
  color: #23264a;
}

.student span {
  font-size: 11px;
  color: #9aa0b8;
}

.pair-arrow {
  color: #c6cce4;
}

.pair-head .el-tag {
  margin-left: auto;
}

.sim-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 12px;
  font-size: 12px;
  color: #8b90ab;
}

.sim-row b {
  font-size: 18px;
  color: #f56c6c;
}

.pair.warn .sim-row b {
  color: #e6a23c;
}

.pair.low .sim-row b {
  color: #67c23a;
}

.bar {
  height: 6px;
  margin: 7px 0 8px;
  overflow: hidden;
  background: #eef1fb;
  border-radius: 999px;
}

.bar-fill {
  display: block;
  height: 100%;
  background: #5b6cff;
  border-radius: 999px;
  transition: width 0.3s ease;
}

.bar-fill.danger {
  background: #f56c6c;
}

.bar-fill.warning {
  background: #e6a23c;
}

.bar-fill.success {
  background: #67c23a;
}

.pair-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 11px;
  color: #8b90ab;
}

.expand-icon {
  margin-left: auto;
  color: #c6cce4;
  transition: transform 0.2s ease;
}

.expand-icon.open {
  transform: rotate(180deg);
}

.detail {
  padding-top: 10px;
  margin-top: 10px;
  border-top: 1px dashed #eef1fa;
}

.detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 0;
  font-size: 12px;
  color: #8b90ab;
}

.detail-row b {
  font-weight: 500;
  color: #2f3247;
}

.detail-note {
  padding: 9px 10px;
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.7;
  color: #9aa0b8;
  background: #f8f9ff;
  border-radius: 8px;
}
</style>
