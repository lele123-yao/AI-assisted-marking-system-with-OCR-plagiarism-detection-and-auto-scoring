<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getGradeStatistics } from '@/api/grade'
import { pagePapers } from '@/api/paper'
import { QUESTION_TYPE, dictItem, toPercent } from '@/utils/dict'
import EChart from '@/components/EChart.vue'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileCard from '@/components/m/MobileCard.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileStatGrid from '@/components/m/MobileStatGrid.vue'

/** 成绩统计：指标卡 + 分数段柱状图 + 逐题得分率 + 薄弱题目 */
const route = useRoute()
const papers = ref([])
const paperId = ref(null)
const loading = ref(false)
const stats = ref(null)

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 50 })
  papers.value = data?.records || []
  const preset = Number(route.query.paperId)
  if (preset && papers.value.some((paper) => paper.id === preset)) {
    paperId.value = preset
  } else if (papers.value.length) {
    paperId.value = papers.value[0].id
  }
}

async function loadStatistics() {
  if (!paperId.value) {
    ElMessage.warning('请先选择试卷')
    return
  }
  loading.value = true
  try {
    stats.value = await getGradeStatistics(paperId.value)
  } finally {
    loading.value = false
  }
}

/** 及格率、得分率兼容 0~1 与 0~100 */
function rateText(value) {
  const num = Number(value)
  if (Number.isNaN(num)) return '-'
  return num <= 1 ? toPercent(num) : `${num.toFixed(1)}%`
}

function ratePercent(value) {
  const num = Number(value)
  if (Number.isNaN(num)) return 0
  return Math.round((num <= 1 ? num * 100 : num) * 10) / 10
}

function rateColor(value) {
  if (value < 60) return '#f56c6c'
  if (value < 75) return '#e6a23c'
  return '#5b6cff'
}

const hasData = computed(() => !!stats.value)
const buckets = computed(() => stats.value?.scoreDistribution || [])
const questionStats = computed(() => stats.value?.questionStats || [])

const metricItems = computed(() => {
  const data = stats.value
  return [
    { key: 'avg', label: '平均分', value: data?.avgScore ?? '-', icon: 'TrendCharts', tone: 'primary' },
    { key: 'max', label: '最高分', value: data?.maxScore ?? '-', icon: 'Top', tone: 'success' },
    { key: 'min', label: '最低分', value: data?.minScore ?? '-', icon: 'Bottom', tone: 'warning' },
    { key: 'pass', label: '及格率', value: rateText(data?.passRate), icon: 'CircleCheck', tone: 'success' },
    { key: 'total', label: '答卷总数', value: data?.totalCount ?? 0, icon: 'Tickets', tone: 'info' },
    { key: 'marked', label: '已出分', value: data?.markedCount ?? 0, icon: 'DocumentChecked', tone: 'primary' }
  ]
})

/** 薄弱题目：按得分率升序取前 6 题 */
const weakQuestions = computed(() =>
  [...questionStats.value]
    .map((row) => ({ ...row, percent: ratePercent(row.scoreRate) }))
    .sort((a, b) => a.percent - b.percent)
    .slice(0, 6)
)

const distributionOption = computed(() => {
  const rows = buckets.value
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 34, right: 12, top: 24, bottom: 28 },
    xAxis: {
      type: 'category',
      data: rows.map((item) => item.label),
      axisLine: { lineStyle: { color: '#d9deef' } },
      axisLabel: {
        color: '#7d829c',
        fontSize: 10,
        interval: 0,
        rotate: rows.length > 6 ? 30 : 0
      }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#eef1fa' } },
      axisLabel: { color: '#7d829c', fontSize: 10 }
    },
    series: [
      {
        name: '人数',
        type: 'bar',
        barWidth: '52%',
        data: rows.map((item) => item.count),
        itemStyle: {
          borderRadius: [5, 5, 0, 0],
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
        label: { show: true, position: 'top', fontSize: 10, color: '#5b6079' }
      }
    ]
  }
})

const questionOption = computed(() => {
  const rows = questionStats.value
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const item = params[0]
        const row = rows[item.dataIndex]
        return `第 ${row?.questionNo ?? ''} 题<br/>得分率：${item.value}%`
      }
    },
    grid: { left: 48, right: 42, top: 12, bottom: 18 },
    xAxis: {
      type: 'value',
      max: 100,
      axisLabel: { color: '#7d829c', fontSize: 10, formatter: '{value}%' },
      splitLine: { lineStyle: { color: '#eef1fa' } }
    },
    yAxis: {
      type: 'category',
      data: rows.map((row) => `第${row.questionNo}题`),
      axisLine: { lineStyle: { color: '#d9deef' } },
      axisLabel: { color: '#5b6079', fontSize: 10, width: 42, overflow: 'truncate' }
    },
    series: [
      {
        type: 'bar',
        barWidth: '55%',
        data: rows.map((row) => {
          const value = ratePercent(row.scoreRate)
          return { value, itemStyle: { color: rateColor(value), borderRadius: [0, 5, 5, 0] } }
        }),
        label: { show: true, position: 'right', fontSize: 10, formatter: '{c}%', color: '#5b6079' }
      }
    ]
  }
})

onMounted(async () => {
  await loadPapers()
  if (paperId.value) loadStatistics()
})
</script>

<template>
  <div class="m-page">
    <MobileHero title="成绩统计" subtitle="分数段、逐题得分率与薄弱题目一目了然" icon="TrendCharts">
      <template #actions>
        <el-button size="small" plain :loading="loading" @click="loadStatistics">刷新</el-button>
      </template>
    </MobileHero>

    <section class="m-card">
      <span class="m-label">选择试卷</span>
      <el-select
        v-model="paperId"
        filterable
        placeholder="请选择试卷"
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
      <p v-if="stats?.paperTitle" class="paper-tip">
        {{ stats.paperTitle }} · 总分 {{ stats.totalScore ?? '-' }} · 及格线 {{ stats.passScore ?? '-' }}
      </p>
    </section>

    <div v-loading="loading" class="body">
      <template v-if="hasData">
        <MobileStatGrid :items="metricItems" :columns="2" />

        <MobileCard title="分数段分布">
          <template #extra>
            <span class="m-sub">按人数统计</span>
          </template>
          <EChart v-if="buckets.length" :option="distributionOption" height="230px" />
          <MobileEmpty v-else description="暂无分布数据" icon="Histogram" />
        </MobileCard>

        <MobileCard title="逐题得分率">
          <template #extra>
            <span class="m-sub">共 {{ questionStats.length }} 题</span>
          </template>
          <EChart v-if="questionStats.length" :option="questionOption" height="240px" />
          <MobileEmpty v-else description="暂无逐题数据" icon="DataAnalysis" />
        </MobileCard>

        <MobileCard title="薄弱题目">
          <template #extra>
            <span class="m-sub">按得分率升序</span>
          </template>
          <div v-if="weakQuestions.length" class="weak-list">
            <div v-for="row in weakQuestions" :key="row.questionId" class="weak-item">
              <div class="weak-head">
                <b>第 {{ row.questionNo }} 题</b>
                <el-tag
                  size="small"
                  effect="plain"
                  :type="dictItem(QUESTION_TYPE, row.questionType).type"
                >
                  {{ row.questionType || dictItem(QUESTION_TYPE, row.questionType).label }}
                </el-tag>
                <span class="weak-rate" :style="{ color: rateColor(row.percent) }">{{ row.percent }}%</span>
              </div>
              <div class="weak-bar">
                <span :style="{ width: `${row.percent}%`, background: rateColor(row.percent) }" />
              </div>
              <div class="weak-meta">
                <span>平均 {{ row.avgScore ?? '-' }} / {{ row.fullScore ?? '-' }}</span>
                <span>作答 {{ row.answerCount ?? 0 }} 人</span>
                <span>满分 {{ row.fullMarkCount ?? 0 }} 人</span>
              </div>
            </div>
          </div>
          <MobileEmpty v-else description="暂无薄弱题目" icon="Warning" />
        </MobileCard>
      </template>

      <MobileEmpty
        v-else-if="!loading"
        description="暂无统计数据，请选择其他试卷或稍后再试"
        icon="TrendCharts"
        action-text="刷新"
        @action="loadStatistics"
      />
    </div>
  </div>
</template>

<style scoped>
.paper-select {
  width: 100%;
  margin-top: 8px;
}

.paper-tip {
  margin: 9px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: #9aa0b8;
}

.body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.weak-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.weak-item {
  padding: 10px 12px;
  background: #f8f9fe;
  border-radius: 10px;
}

.weak-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.weak-head b {
  font-size: 13px;
  color: #23264a;
}

.weak-rate {
  margin-left: auto;
  font-size: 15px;
  font-weight: 700;
}

.weak-bar {
  height: 5px;
  margin: 8px 0 6px;
  overflow: hidden;
  background: #eceffa;
  border-radius: 999px;
}

.weak-bar span {
  display: block;
  height: 100%;
  border-radius: 999px;
}

.weak-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 11px;
  color: #9aa0b8;
}
</style>
