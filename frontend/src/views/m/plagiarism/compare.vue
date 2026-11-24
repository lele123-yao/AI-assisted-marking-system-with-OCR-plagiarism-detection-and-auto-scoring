<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { generateFingerprints, generateReport, getAnswerReport } from '@/api/plagiarism'
import { pagePapers } from '@/api/paper'
import { formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileSheet from '@/components/m/MobileSheet.vue'

/** 移动端答卷相似度比对：生成指纹 → 生成报告 → 下钻单份明细 */
const route = useRoute()
const router = useRouter()

const papers = ref([])
const paperId = ref(null)
const papersLoading = ref(false)
const report = ref(null)
const fingerprintResult = ref(null)
const fpLoading = ref(false)
const reportLoading = ref(false)

const answerVisible = ref(false)
const answerLoading = ref(false)
const answerReport = ref(null)

async function loadPapers() {
  papersLoading.value = true
  try {
    const data = await pagePapers({ pageNum: 1, pageSize: 50 })
    papers.value = data?.records || []
    if (route.query.paperId) {
      paperId.value = Number(route.query.paperId)
    } else if (papers.value.length) {
      paperId.value = papers.value[0].id
    }
  } finally {
    papersLoading.value = false
  }
}

const selectedPaper = computed(
  () => papers.value.find((item) => item.id === paperId.value) || null
)

function onPaperChange() {
  report.value = null
  fingerprintResult.value = null
}

/** 相似度兼容 0~1 与 0~100 两种返回 */
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

function riskTag(level) {
  const value = String(level ?? '').toUpperCase()
  if (value.includes('HIGH')) return 'danger'
  if (value.includes('SUSPICIOUS') || value.includes('MEDIUM')) return 'warning'
  return 'success'
}

const fingerprintItems = computed(() => {
  const data = fingerprintResult.value
  if (!data) return []
  return [
    { label: '答卷总数', value: data.totalCount ?? 0 },
    { label: '新生成', value: data.generatedCount ?? 0 },
    { label: '已有指纹', value: data.existingCount ?? 0 },
    { label: '跳过', value: data.skippedCount ?? 0 }
  ]
})

const skippedPreview = computed(() => (fingerprintResult.value?.skippedItems || []).slice(0, 5))

const pairs = computed(() => report.value?.pairs || [])

const pairStats = computed(() => {
  const values = pairs.value
    .map((row) => Number(row.similarity))
    .filter((value) => !Number.isNaN(value))
  const max = values.length ? Math.max(...values) : null
  const avg = values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null
  return { max, avg }
})

/** 风险分布：与桌面 compare.vue 口径一致，按比对对数估算占比 */
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

async function handleFingerprint() {
  if (!paperId.value) {
    ElMessage.warning('请先选择试卷')
    return
  }
  try {
    await ElMessageBox.confirm(
      '将为该试卷下所有已识别答卷生成 SimHash 指纹，重复生成会覆盖旧指纹。是否继续？',
      '生成指纹',
      { type: 'warning' }
    )
  } catch {
    return
  }
  fpLoading.value = true
  try {
    fingerprintResult.value = await generateFingerprints(paperId.value)
    ElMessage.success('指纹生成完成，可继续生成抄袭报告')
  } finally {
    fpLoading.value = false
  }
}

async function handleReport() {
  if (!paperId.value) {
    ElMessage.warning('请先选择试卷')
    return
  }
  reportLoading.value = true
  try {
    report.value = await generateReport(paperId.value)
    ElMessage.success('抄袭报告已生成')
  } finally {
    reportLoading.value = false
  }
}

async function openAnswer(row) {
  answerVisible.value = true
  answerReport.value = null
  answerLoading.value = true
  try {
    answerReport.value = await getAnswerReport(row.answerIdA)
  } finally {
    answerLoading.value = false
  }
}

function goReport() {
  router.push({ path: '/m/plagiarism/report', query: { paperId: paperId.value } })
}

onMounted(loadPapers)
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="相似度比对"
      subtitle="执行顺序：生成指纹 → 生成报告。指纹基于 SimHash 计算，未识别 OCR 的答卷会跳过。"
      icon="Connection"
    >
      <template #actions>
        <el-button v-if="report" size="small" plain @click="goReport">查看报告</el-button>
      </template>
    </MobileHero>

    <!-- 试卷选择 -->
    <section class="m-card">
      <div class="m-label">比对试卷</div>
      <el-select
        v-model="paperId"
        filterable
        placeholder="请选择试卷"
        class="paper-select"
        :loading="papersLoading"
        @change="onPaperChange"
      >
        <el-option
          v-for="paper in papers"
          :key="paper.id"
          :label="`${paper.paperTitle}（${paper.paperNo}）`"
          :value="paper.id"
        />
      </el-select>
      <p class="m-sub tip">未识别 OCR 的答卷会自动跳过。</p>
    </section>

    <!-- 步骤 1：生成指纹 -->
    <section class="m-card step">
      <div class="step-head">
        <span class="step-index">01</span>
        <div>
          <b>生成 SimHash 指纹</b>
          <p class="m-sub">为试卷下所有已识别答卷建立可比对指纹，答卷较多时可能耗时数十秒。</p>
        </div>
      </div>
      <el-button
        type="primary"
        class="step-btn"
        :loading="fpLoading"
        :disabled="!paperId"
        @click="handleFingerprint"
      >
        生成指纹
      </el-button>

      <div v-if="fingerprintResult" class="fp-summary">
        <div class="mini-grid">
          <div v-for="item in fingerprintItems" :key="item.label" class="mini-stat">
            <b>{{ item.value }}</b>
            <span>{{ item.label }}</span>
          </div>
        </div>
        <div v-if="skippedPreview.length" class="skipped">
          <span class="m-label">跳过明细</span>
          <p v-for="item in skippedPreview" :key="item.answerId" class="skipped-row">
            {{ item.studentNo || item.answerId }}：{{ item.reason }}
          </p>
          <p v-if="fingerprintResult.skippedItems?.length > skippedPreview.length" class="m-sub">
            仅展示前 {{ skippedPreview.length }} 条
          </p>
        </div>
      </div>
    </section>

    <!-- 步骤 2：生成报告 -->
    <section class="m-card step">
      <div class="step-head">
        <span class="step-index">02</span>
        <div>
          <b>生成抄袭报告</b>
          <p class="m-sub">两两比对指纹，输出高风险与可疑答卷对，答卷较多时可能耗时较久。</p>
        </div>
      </div>
      <el-button
        type="success"
        class="step-btn"
        :loading="reportLoading"
        :disabled="!paperId"
        @click="handleReport"
      >
        生成报告
      </el-button>
    </section>

    <!-- 结果概览 -->
    <template v-if="report && riskProfile">
      <section class="m-card result">
        <div class="m-row-between">
          <div>
            <span class="m-label">比对结果概览</span>
            <h3 class="m-title">{{ selectedPaper?.paperTitle || report.paperTitle || '抄袭报告' }}</h3>
          </div>
          <el-tag v-if="riskProfile.high" type="danger" effect="plain">存在高风险</el-tag>
          <el-tag v-else-if="riskProfile.suspicious" type="warning" effect="plain">存在可疑</el-tag>
          <el-tag v-else type="success" effect="plain">未发现异常</el-tag>
        </div>

        <div class="risk-grid">
          <div class="risk-item high">
            <b>{{ riskProfile.high }}</b>
            <span>高风险对</span>
          </div>
          <div class="risk-item warn">
            <b>{{ riskProfile.suspicious }}</b>
            <span>可疑对</span>
          </div>
          <div class="risk-item">
            <b>{{ formatSimilarity(pairStats.max) }}</b>
            <span>最高相似度</span>
          </div>
          <div class="risk-item">
            <b>{{ formatSimilarity(pairStats.avg) }}</b>
            <span>平均相似度</span>
          </div>
        </div>

        <div class="meter">
          <span class="meter-high" :style="{ width: `${riskProfile.highRate}%` }" />
          <span class="meter-warn" :style="{ width: `${riskProfile.suspiciousRate}%` }" />
        </div>
        <p class="meter-caption">
          共比对 {{ riskProfile.compared }} 对答卷，高风险占 {{ riskProfile.highRate }}%，可疑占
          {{ riskProfile.suspiciousRate }}%。
        </p>

        <div class="result-meta">
          <span>答卷总数 {{ report.totalAnswerCount ?? 0 }}</span>
          <span>已生成指纹 {{ report.fingerprintedCount ?? 0 }}</span>
          <span>跳过 {{ report.skippedCount ?? 0 }}</span>
          <span v-if="report.generatedTime">生成于 {{ formatTime(report.generatedTime) }}</span>
        </div>
      </section>

      <section class="list-block">
        <div class="m-row-between">
          <span class="m-title">相似答卷对</span>
          <span class="m-sub">点击查看单份明细</span>
        </div>

        <div class="pairs">
          <article
            v-for="row in pairs"
            :key="`${row.answerIdA}-${row.answerIdB}`"
            class="m-card pair"
            @click="openAnswer(row)"
          >
            <div class="pair-head">
              <span class="pair-name">{{ row.studentNameA || '未知' }} · {{ row.studentNoA }}</span>
              <el-icon class="pair-arrow"><ArrowRight /></el-icon>
              <span class="pair-name">{{ row.studentNameB || '未知' }} · {{ row.studentNoB }}</span>
              <el-tag :type="riskTag(row.riskLevel)" size="small" effect="plain">
                {{ row.riskLevelDesc || row.riskLevel || '-' }}
              </el-tag>
            </div>

            <div class="bar">
              <span
                class="bar-fill"
                :class="riskTag(row.riskLevel)"
                :style="{ width: `${percentOf(row.similarity)}%` }"
              />
            </div>

            <div class="pair-meta">
              <span>相似度 {{ formatSimilarity(row.similarity) }}</span>
              <span>汉明距离 {{ row.hammingDistance ?? '-' }}</span>
              <span>相同特征位 {{ row.hammingDistance == null ? '-' : 64 - Number(row.hammingDistance) }}</span>
            </div>
          </article>

          <MobileEmpty v-if="!pairs.length" description="未发现相似答卷对" icon="Connection" />
        </div>
      </section>
    </template>

    <section v-else class="m-card">
      <MobileEmpty
        description="尚未生成比对报告，请先选择试卷并依次生成指纹、生成报告"
        icon="Connection"
      />
    </section>

    <!-- 单份答卷明细 -->
    <MobileSheet
      v-model="answerVisible"
      title="答卷查重明细"
      confirm-text="关闭"
      height="78%"
      @confirm="answerVisible = false"
    >
      <div v-loading="answerLoading" class="answer-detail">
        <template v-if="answerReport">
          <div class="detail-head">
            <div>
              <b>{{ answerReport.studentName || '未知考生' }}</b>
              <p class="m-sub">{{ answerReport.studentNo || '-' }}</p>
            </div>
            <el-tag :type="riskTag(answerReport.riskLevel)" effect="plain">
              {{ answerReport.riskLevelDesc || answerReport.riskLevel || '无风险' }}
            </el-tag>
          </div>

          <ul class="detail-list">
            <li><span>比对份数</span><b>{{ answerReport.comparedCount ?? '-' }}</b></li>
            <li><span>最高相似度</span><b>{{ formatSimilarity(answerReport.maxSimilarity) }}</b></li>
          </ul>

          <div class="m-row-between">
            <span class="m-title">相似答卷</span>
            <span class="m-sub">{{ answerReport.similarAnswers?.length || 0 }} 份</span>
          </div>

          <div class="similar-list">
            <div
              v-for="item in answerReport.similarAnswers || []"
              :key="item.answerId"
              class="similar-item"
            >
              <div class="m-row-between">
                <span>{{ item.studentName || '未知' }} · {{ item.studentNo }}</span>
                <el-tag :type="riskTag(item.riskLevel)" size="small" effect="plain">
                  {{ formatSimilarity(item.similarity) }}
                </el-tag>
              </div>
              <div class="bar">
                <span
                  class="bar-fill"
                  :class="riskTag(item.riskLevel)"
                  :style="{ width: `${percentOf(item.similarity)}%` }"
                />
              </div>
              <p class="m-sub">
                汉明距离 {{ item.hammingDistance ?? '-' }} · 相同特征位
                {{ item.hammingDistance == null ? '-' : 64 - Number(item.hammingDistance) }}
              </p>
            </div>

            <MobileEmpty
              v-if="!answerReport.similarAnswers?.length"
              description="暂无相似答卷"
              icon="Connection"
            />
          </div>
        </template>

        <MobileEmpty v-else-if="!answerLoading" description="暂无明细" icon="Connection" />
      </div>
    </MobileSheet>
  </div>
</template>

<style scoped>
.paper-select {
  width: 100%;
  margin-top: 6px;
}

.tip {
  margin: 8px 0 0;
}

.step-head {
  display: flex;
  gap: 12px;
}

.step-index {
  flex-shrink: 0;
  font-size: 19px;
  font-weight: 700;
  color: #c4cbf0;
}

.step-head b {
  font-size: 15px;
  color: #23264a;
}

.step-head p {
  margin: 4px 0 0;
  line-height: 1.6;
}

.step-btn {
  width: 100%;
  margin-top: 12px;
}

.fp-summary {
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px dashed #eef1fa;
}

.mini-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.mini-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 4px;
  background: #f8f9ff;
  border-radius: 10px;
}

.mini-stat b {
  font-size: 17px;
  color: #23264a;
}

.mini-stat span {
  margin-top: 2px;
  font-size: 11px;
  color: #8b90ab;
}

.skipped {
  margin-top: 10px;
}

.skipped-row {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #5c6180;
}

.result h3 {
  margin: 4px 0 0;
  font-size: 16px;
}

.risk-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.risk-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 11px 13px;
  background: #f8f9ff;
  border-radius: 12px;
}

.risk-item b {
  font-size: 20px;
  color: #5b6cff;
}

.risk-item span {
  font-size: 11px;
  color: #8b90ab;
}

.risk-item.high b {
  color: #f56c6c;
}

.risk-item.warn b {
  color: #e6a23c;
}

.meter {
  display: flex;
  width: 100%;
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

.meter-caption {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.7;
  color: #8b90ab;
}

.result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 10px;
  margin-top: 10px;
  font-size: 11px;
  color: #9aa0b8;
  border-top: 1px dashed #eef1fa;
}

.list-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pairs {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pair:active {
  transform: scale(0.995);
}

.pair-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.pair-name {
  font-size: 13px;
  font-weight: 500;
  color: #2f3247;
}

.pair-arrow {
  color: #c6cce4;
}

.pair-head .el-tag {
  margin-left: auto;
}

.bar {
  height: 6px;
  margin: 10px 0 7px;
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
  flex-wrap: wrap;
  gap: 10px;
  font-size: 11px;
  color: #8b90ab;
}

.answer-detail {
  min-height: 120px;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.detail-head b {
  font-size: 15px;
  color: #23264a;
}

.detail-head p {
  margin: 4px 0 0;
}

.detail-list {
  padding: 0;
  margin: 12px 0;
  list-style: none;
  border-top: 1px dashed #eef1fa;
}

.detail-list li {
  display: flex;
  justify-content: space-between;
  padding: 9px 0;
  font-size: 13px;
  color: #8b90ab;
  border-bottom: 1px dashed #eef1fa;
}

.detail-list b {
  font-weight: 500;
  color: #2f3247;
}

.similar-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 10px;
}

.similar-item {
  padding: 10px 12px;
  background: #f8f9ff;
  border-radius: 10px;
}

.similar-item .bar {
  margin: 8px 0 4px;
}

.similar-item p {
  margin: 0;
}
</style>
