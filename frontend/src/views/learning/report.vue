<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLearningStatistics } from '@/api/learning'
import { pagePapers } from '@/api/paper'
import EChart from '@/components/EChart.vue'
import { QUESTION_TYPE, dictItem, toPercent } from '@/utils/dict'

/** 学情报表：整体指标、班级对比、高频错题 */
const router = useRouter()
const papers = ref([])
const paperId = ref(null)
const loading = ref(false)
const stats = ref(null)
const activeTab = ref('class')

const selectedPaper = computed(() => papers.value.find((item) => item.id === paperId.value) || null)

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
  if (!paperId.value && papers.value.length) paperId.value = papers.value[0].id
}

async function loadStatistics() {
  if (!paperId.value) {
    ElMessage.warning('请先选择试卷')
    return
  }
  loading.value = true
  try {
    stats.value = await getLearningStatistics(paperId.value)
  } finally {
    loading.value = false
  }
}

/** 兼容 0~1 与 0~100 */
function rateText(value) {
  const num = Number(value)
  if (Number.isNaN(num)) return '-'
  return num <= 1 ? toPercent(num) : `${num.toFixed(1)}%`
}

/** 转为图表/进度需要的 0~100 数值 */
function ratePercent(value) {
  const num = Number(value)
  if (Number.isNaN(num)) return 0
  return Math.round((num <= 1 ? num * 100 : num) * 10) / 10
}

function goKnowledge() {
  router.push({ path: '/learning/knowledge', query: { paperId: paperId.value } })
}

function goAdvice() {
  router.push({ path: '/learning/advice', query: { paperId: paperId.value } })
}

/** 班级平均分柱状 + 及格率折线 */
const classOption = computed(() => {
  const rows = stats.value?.classMetrics || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, icon: 'roundRect', textStyle: { color: '#7d829c' } },
    grid: { left: 48, right: 56, top: 46, bottom: 34 },
    xAxis: {
      type: 'category',
      data: rows.map((row) => row.className),
      axisLine: { lineStyle: { color: '#d9deef' } },
      axisLabel: { color: '#7d829c', interval: 0, rotate: rows.length > 5 ? 20 : 0 }
    },
    yAxis: [
      {
        type: 'value',
        name: '平均分',
        splitLine: { lineStyle: { color: '#eef1fa' } },
        axisLabel: { color: '#7d829c' }
      },
      {
        type: 'value',
        name: '及格率(%)',
        max: 100,
        splitLine: { show: false },
        axisLabel: { color: '#7d829c', formatter: '{value}%' }
      }
    ],
    series: [
      {
        name: '平均分',
        type: 'bar',
        barWidth: '42%',
        data: rows.map((row) => row.avgScore),
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: '#7a8bff' },
              { offset: 1, color: '#5b6cff' }
            ]
          }
        },
        label: { show: true, position: 'top', color: '#5b6079' }
      },
      {
        name: '及格率',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 7,
        data: rows.map((row) => ratePercent(row.passRate)),
        lineStyle: { width: 3, color: '#9a5bff' },
        itemStyle: { color: '#9a5bff' }
      }
    ]
  }
})

/** 高频错题得分率横向条形图（低分率红色预警） */
const weakOption = computed(() => {
  const rows = [...(stats.value?.weakQuestions || [])].slice(0, 8).reverse()
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const item = params[0]
        return `第 ${item.name} 题<br/>得分率：${item.value}%`
      }
    },
    grid: { left: 62, right: 60, top: 20, bottom: 24 },
    xAxis: {
      type: 'value',
      max: 100,
      axisLabel: { color: '#7d829c', formatter: '{value}%' },
      splitLine: { lineStyle: { color: '#eef1fa' } }
    },
    yAxis: {
      type: 'category',
      data: rows.map((row) => String(row.questionNo)),
      axisLine: { lineStyle: { color: '#d9deef' } },
      axisLabel: { color: '#5b6079' }
    },
    series: [
      {
        type: 'bar',
        barWidth: '52%',
        data: rows.map((row) => {
          const value = ratePercent(row.scoreRate)
          const color = value < 60 ? '#f56c6c' : value < 75 ? '#e6a23c' : '#5b6cff'
          return { value, itemStyle: { color, borderRadius: [0, 6, 6, 0] } }
        }),
        label: { show: true, position: 'right', formatter: '{c}%', color: '#5b6079' }
      }
    ]
  }
})

const summaryItems = computed(() => [
  { label: '答卷总数', value: stats.value?.totalCount ?? 0 },
  { label: '已出分', value: stats.value?.markedCount ?? 0 },
  { label: '及格率', value: rateText(stats.value?.passRate) },
  { label: '优秀率', value: rateText(stats.value?.excellentRate) }
])

onMounted(async () => {
  await loadPapers()
  loadStatistics()
})
</script>

<template>
  <div class="page-container">
    <!-- 学情概览横幅 -->
    <section class="summary-hero">
      <div class="summary-main">
        <div class="summary-eyebrow">
          <span class="pulse-dot" />
          学情概览 · {{ selectedPaper?.paperTitle || '请选择试卷' }}
        </div>
        <div class="summary-score">
          <span class="score-value">{{ stats?.avgScore ?? '-' }}</span>
          <span class="score-unit">平均分</span>
        </div>
        <div class="summary-meta">
          <div v-for="item in summaryItems" :key="item.label" class="meta-item">
            <b>{{ item.value }}</b>
            <span>{{ item.label }}</span>
          </div>
        </div>
      </div>

      <div class="summary-actions">
        <el-select
          v-model="paperId"
          filterable
          placeholder="选择试卷"
          class="paper-select"
          @change="loadStatistics"
        >
          <el-option
            v-for="paper in papers"
            :key="paper.id"
            :label="`${paper.paperTitle}（${paper.paperNo}）`"
            :value="paper.id"
          />
        </el-select>
        <div class="action-row">
          <el-button class="ghost-btn" @click="loadStatistics">刷新报表</el-button>
          <el-button class="ghost-btn" @click="goKnowledge">知识点得分率</el-button>
          <el-button v-permission="'learning:generate'" type="primary" @click="goAdvice">
            生成AI讲评
          </el-button>
        </div>
      </div>
    </section>

    <div v-loading="loading" class="report-body">
      <!-- 图表区 -->
      <section class="chart-grid">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-head">
              <span>班级平均分与及格率</span>
              <span class="card-sub">柱状为平均分，折线为及格率</span>
            </div>
          </template>
          <EChart v-if="stats?.classMetrics?.length" :option="classOption" height="330px" />
          <el-empty v-else description="暂无班级数据" :image-size="80" />
        </el-card>

        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-head">
              <span>高频错题得分率</span>
              <span class="card-sub">低于 60% 标红预警</span>
            </div>
          </template>
          <EChart v-if="stats?.weakQuestions?.length" :option="weakOption" height="330px" />
          <el-empty v-else description="暂无错题数据" :image-size="80" />
        </el-card>
      </section>

      <!-- 明细：Tab 切换 -->
      <el-card shadow="never">
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane label="班级对比" name="class">
            <el-table :data="stats?.classMetrics || []" border stripe>
              <el-table-column prop="className" label="班级" min-width="150" />
              <el-table-column prop="studentCount" label="人数" width="90" />
              <el-table-column prop="markedCount" label="已出分" width="90" />
              <el-table-column prop="avgScore" label="平均分" width="90" />
              <el-table-column prop="maxScore" label="最高分" width="90" />
              <el-table-column prop="minScore" label="最低分" width="90" />
              <el-table-column prop="passCount" label="及格数" width="90" />
              <el-table-column label="及格率" width="100">
                <template #default="{ row }">{{ rateText(row.passRate) }}</template>
              </el-table-column>
              <el-table-column prop="excellentCount" label="优秀数" width="90" />
              <el-table-column label="优秀率" width="100">
                <template #default="{ row }">{{ rateText(row.excellentRate) }}</template>
              </el-table-column>
              <template #empty>
                <el-empty description="暂无班级数据" :image-size="80" />
              </template>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="高频错题" name="weak">
            <el-table :data="stats?.weakQuestions || []" border stripe>
              <el-table-column prop="questionNo" label="题号" width="90" />
              <el-table-column label="题型" width="100">
                <template #default="{ row }">
                  <el-tag :type="dictItem(QUESTION_TYPE, row.questionType).type" effect="plain">
                    {{ dictItem(QUESTION_TYPE, row.questionType).label }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="fullScore" label="满分" width="80" />
              <el-table-column prop="answerCount" label="作答数" width="90" />
              <el-table-column prop="avgScore" label="平均分" width="90" />
              <el-table-column label="得分率" width="100">
                <template #default="{ row }">{{ rateText(row.scoreRate) }}</template>
              </el-table-column>
              <el-table-column prop="wrongCount" label="错误数" width="90" />
              <el-table-column label="错误率" width="100">
                <template #default="{ row }">{{ rateText(row.wrongRate) }}</template>
              </el-table-column>
              <template #empty>
                <el-empty description="暂无错题数据" :image-size="80" />
              </template>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ==================== 学情概览横幅 ==================== */
.summary-hero {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  overflow: hidden;
  color: #fff;
  border: 1px solid rgba(140, 156, 255, 0.18);
  border-radius: 16px;
  background: var(--ai-gradient);
  box-shadow: 0 18px 40px rgba(30, 30, 100, 0.2);
}

.summary-hero::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: '';
  background-image:
    linear-gradient(rgba(160, 174, 255, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(160, 174, 255, 0.08) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: radial-gradient(circle at 22% 30%, #000 0%, transparent 76%);
}

.summary-main,
.summary-actions {
  position: relative;
  z-index: 1;
}

.summary-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 13px;
  margin-bottom: 14px;
  font-size: 12px;
  color: #b6c1ff;
  background: rgba(126, 146, 255, 0.14);
  border: 1px solid rgba(126, 146, 255, 0.3);
  border-radius: 999px;
}

.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #5cf0c8;
  box-shadow: 0 0 10px #5cf0c8;
}

.summary-score {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.score-value {
  font-size: 46px;
  font-weight: 700;
  line-height: 1;
  background: linear-gradient(96deg, #ffffff 0%, #c3ceff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.score-unit {
  font-size: 14px;
  color: rgba(200, 208, 245, 0.7);
}

.summary-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 34px;
  padding-top: 18px;
  margin-top: 18px;
  border-top: 1px solid rgba(140, 156, 255, 0.16);
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-item b {
  font-size: 20px;
  font-weight: 700;
  color: #eef1ff;
}

.meta-item span {
  font-size: 12px;
  color: rgba(190, 198, 240, 0.6);
}

.summary-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-end;
}

.paper-select {
  width: 320px;
}

.summary-actions :deep(.el-select__wrapper) {
  background: rgba(255, 255, 255, 0.1);
  box-shadow: 0 0 0 1px rgba(150, 166, 255, 0.3) inset;
}

.summary-actions :deep(.el-select__placeholder),
.summary-actions :deep(.el-select__selected-item) {
  color: #e6eaff;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.ghost-btn {
  color: #d6dcff;
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(160, 174, 255, 0.3);
}

.ghost-btn:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.16);
  border-color: rgba(160, 174, 255, 0.5);
}

/* ==================== 图表与明细 ==================== */
.report-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 16px;
}

.card-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #2b2e45;
}

.card-sub {
  font-size: 12px;
  font-weight: 400;
  color: #9aa0b8;
}

.detail-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}

.detail-tabs :deep(.el-tabs__item) {
  font-size: 14px;
}

@media (max-width: 1080px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }

  .summary-actions {
    align-items: flex-start;
  }

  .paper-select {
    width: 100%;
  }
}
</style>
