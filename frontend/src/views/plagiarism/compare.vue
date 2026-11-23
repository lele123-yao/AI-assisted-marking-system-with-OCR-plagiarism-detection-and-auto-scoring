<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { generateFingerprints, generateReport } from '@/api/plagiarism'
import { pagePapers } from '@/api/paper'
import PageHero from '@/components/PageHero.vue'
import { formatTime } from '@/utils/dict'

/** 答卷相似度比对：先生成指纹，再生成报告 */
const route = useRoute()
const router = useRouter()
const papers = ref([])
const paperId = ref(null)
const report = ref(null)
const fpLoading = ref(false)
const reportLoading = ref(false)

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
  if (route.query.paperId) {
    paperId.value = Number(route.query.paperId)
  } else if (papers.value.length) {
    paperId.value = papers.value[0].id
  }
}

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
    await generateFingerprints(paperId.value)
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

function goReport() {
  router.push({ path: '/plagiarism/report', query: { paperId: paperId.value } })
}

const selectedPaper = computed(
  () => papers.value.find((item) => item.id === paperId.value) || null
)

/** 风险分布：高风险 / 可疑 / 正常，按比对对数估算占比 */
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
      title="答卷相似度比对"
      subtitle="执行顺序：生成指纹 → 生成报告。指纹基于 SimHash 计算答卷间相似度，未识别 OCR 的答卷会被跳过。"
      icon="Connection"
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
        <el-button v-if="report" plain @click="goReport">查看完整报告</el-button>
      </template>
    </PageHero>

    <!-- 试卷选择条 -->
    <div class="paper-bar">
      <span class="bar-label">比对试卷</span>
      <el-select v-model="paperId" filterable placeholder="请选择试卷" class="bar-select">
        <el-option
          v-for="paper in papers"
          :key="paper.id"
          :label="`${paper.paperTitle}（${paper.paperNo}）`"
          :value="paper.id"
        />
      </el-select>
      <span class="bar-tip">未识别 OCR 的答卷会自动跳过</span>
    </div>

    <div class="scan-layout">
      <!-- 左：两步执行 -->
      <section class="flow-card">
        <div class="flow-step">
          <div class="flow-head">
            <span class="flow-index">01</span>
            <div class="flow-title">
              <b>生成 SimHash 指纹</b>
              <span>为该试卷下所有答卷建立可比的指纹数据</span>
            </div>
          </div>
          <el-button
            type="primary"
            class="flow-btn"
            :loading="fpLoading"
            :disabled="!paperId"
            @click="handleFingerprint"
          >
            生成指纹
          </el-button>
        </div>

        <div class="flow-connector">
          <span class="connector-line" />
          <el-icon class="connector-icon" :size="16"><Bottom /></el-icon>
        </div>

        <div class="flow-step">
          <div class="flow-head">
            <span class="flow-index">02</span>
            <div class="flow-title">
              <b>生成抄袭报告</b>
              <span>两两比对指纹，输出高风险与可疑答卷对</span>
            </div>
          </div>
          <el-button
            type="success"
            class="flow-btn"
            :loading="reportLoading"
            :disabled="!paperId"
            @click="handleReport"
          >
            生成报告
          </el-button>
        </div>
      </section>

      <!-- 右：比对结果 -->
      <section class="result-card">
        <template v-if="report && riskProfile">
          <div class="result-head">
            <span class="result-eyebrow">比对结果概览</span>
            <el-tag v-if="riskProfile.high" type="danger" effect="plain">存在高风险</el-tag>
            <el-tag v-else-if="riskProfile.suspicious" type="warning" effect="plain">存在可疑</el-tag>
            <el-tag v-else type="success" effect="plain">未发现异常</el-tag>
          </div>

          <div class="risk-numbers">
            <div class="risk-number high">
              <b>{{ riskProfile.high }}</b>
              <span>高风险对</span>
            </div>
            <div class="risk-number warn">
              <b>{{ riskProfile.suspicious }}</b>
              <span>可疑对</span>
            </div>
            <div class="risk-number normal">
              <b>{{ riskProfile.normal }}</b>
              <span>正常对</span>
            </div>
          </div>

          <div class="risk-meter">
            <span class="meter-high" :style="{ width: `${riskProfile.highRate}%` }" />
            <span class="meter-warn" :style="{ width: `${riskProfile.suspiciousRate}%` }" />
          </div>
          <p class="meter-caption">
            共比对 {{ riskProfile.compared }} 对答卷，高风险占 {{ riskProfile.highRate }}%，可疑占
            {{ riskProfile.suspiciousRate }}%。
          </p>

          <div class="result-meta">
            <div class="meta-item">
              <b>{{ report.totalAnswerCount ?? 0 }}</b>
              <span>答卷总数</span>
            </div>
            <div class="meta-item">
              <b>{{ report.fingerprintedCount ?? 0 }}</b>
              <span>已生成指纹</span>
            </div>
            <div class="meta-item">
              <b>{{ report.skippedCount ?? 0 }}</b>
              <span>跳过（未识别）</span>
            </div>
          </div>

          <el-button v-if="report" class="result-btn" @click="goReport">
            查看完整报告与明细
          </el-button>
        </template>

        <div v-else class="result-empty">
          <div class="empty-icon">
            <el-icon :size="30"><Connection /></el-icon>
          </div>
          <h3>尚未生成比对报告</h3>
          <p>按左侧顺序依次执行「生成指纹」与「生成报告」，结果会在此处汇总展示。</p>
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

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

/* 试卷选择条 */
.paper-bar {
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

.bar-label {
  font-size: 13px;
  font-weight: 600;
  color: #2b2e45;
}

.bar-select {
  width: 340px;
}

.bar-tip {
  margin-left: auto;
  font-size: 12px;
  color: #9aa0b8;
}

/* 左右布局 */
.scan-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(320px, 0.95fr);
  gap: 16px;
  align-items: stretch;
}

.flow-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0;
  padding: 28px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.flow-step {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 20px;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-radius: 14px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.flow-step:hover {
  border-color: #d5dcfa;
  box-shadow: 0 10px 24px rgba(60, 70, 160, 0.08);
}

.flow-head {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.flow-index {
  flex-shrink: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #c4cbf0;
}

.flow-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.flow-title b {
  font-size: 15px;
  color: #23264a;
}

.flow-title span {
  font-size: 12px;
  line-height: 1.6;
  color: #9aa0b8;
}

.flow-btn {
  flex-shrink: 0;
  height: 38px;
  border-radius: 10px;
}

.flow-connector {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0 6px 34px;
  color: #c4cbf0;
}

.connector-line {
  width: 1px;
  height: 18px;
  background: repeating-linear-gradient(180deg, #dfe4f7 0 5px, transparent 5px 10px);
  transform: translateX(5px);
}

/* 结果卡 */
.result-card {
  display: flex;
  flex-direction: column;
  padding: 24px;
  color: #fff;
  background:
    radial-gradient(360px 200px at 100% 0%, rgba(154, 91, 255, 0.4), transparent 62%),
    linear-gradient(160deg, #1b1e52 0%, #2a2a72 55%, #3d358f 100%);
  border: 1px solid rgba(140, 156, 255, 0.2);
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(25, 28, 80, 0.24);
}

.result-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.result-eyebrow {
  font-size: 12px;
  color: #a9b4f2;
  letter-spacing: 1px;
}

.risk-numbers {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 22px 0 18px;
}

.risk-number {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(160, 174, 255, 0.16);
  border-radius: 12px;
}

.risk-number b {
  font-size: 24px;
  font-weight: 700;
  color: #f0f2ff;
}

.risk-number span {
  font-size: 12px;
  color: rgba(190, 198, 240, 0.65);
}

.risk-number.high b {
  color: #ff8a8a;
}

.risk-number.warn b {
  color: #ffc46b;
}

.risk-number.normal b {
  color: #7ce0b4;
}

.risk-meter {
  display: flex;
  width: 100%;
  height: 8px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 999px;
}

.meter-high {
  height: 100%;
  background: #f56c6c;
  transition: width 0.4s ease;
}

.meter-warn {
  height: 100%;
  background: #e6a23c;
  transition: width 0.4s ease;
}

.meter-caption {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: rgba(190, 198, 240, 0.7);
}

.result-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding-top: 18px;
  margin-top: auto;
  border-top: 1px solid rgba(160, 174, 255, 0.16);
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.meta-item b {
  font-size: 17px;
  font-weight: 700;
  color: #eef1ff;
}

.meta-item span {
  font-size: 11px;
  color: rgba(190, 198, 240, 0.62);
}

.result-btn {
  width: 100%;
  margin-top: 20px;
  color: #1a1c50;
  background: #cfd8ff;
  border: none;
  border-radius: 10px;
}

.result-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  text-align: center;
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 70px;
  height: 70px;
  margin-bottom: 16px;
  color: #b6c1ff;
  background: rgba(126, 146, 255, 0.14);
  border: 1px solid rgba(126, 146, 255, 0.3);
  border-radius: 20px;
}

.result-empty h3 {
  margin: 0 0 8px;
  font-size: 16px;
  color: #eef1ff;
}

.result-empty p {
  max-width: 300px;
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: rgba(190, 198, 240, 0.66);
}

@media (max-width: 1000px) {
  .scan-layout {
    grid-template-columns: 1fr;
  }
}
</style>
