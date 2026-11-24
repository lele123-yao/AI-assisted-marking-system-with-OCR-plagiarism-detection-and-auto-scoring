<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLearningStatistics } from '@/api/learning'
import { pagePapers } from '@/api/paper'
import { toPercent } from '@/utils/dict'
import EChart from '@/components/EChart.vue'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileCard from '@/components/m/MobileCard.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileStatGrid from '@/components/m/MobileStatGrid.vue'

/** 学情报表：整体指标 + 班级对比 + 知识点排行 + 高频错题 */
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
    stats.value = await getLearningStatistics(paperId.value)
  } finally {
    loading.value = false
  }
}

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

const metricItems = computed(() => {
  const data = stats.value
  return [
    { key: 'avg', label: '平均分', value: data?.avgScore ?? '-', icon: 'TrendCharts', tone: 'primary' },
    { key: 'total', label: '答卷总数', value: data?.totalCount ?? 0, icon: 'Tickets', tone: 'info' },
    { key: 'pass', label: '及格率', value: rateText(data?.passRate), icon: 'CircleCheck', tone: 'success' },
    { key: 'excellent', label: '优秀率', value: rateText(data?.excellentRate), icon: 'Top', tone: 'warning' }
  ]
})

const classRows = computed(() => stats.value?.classMetrics || [])

const knowledgeRows = computed(() =>
  [...(stats.value?.knowledgeStats || [])]
    .map((row) => ({ ...row, percent: ratePercent(row.scoreRate) }))
    .sort((a, b) => a.percent - b.percent)
)

const weakRows = computed(() =>
  [...(stats.value?.weakQuestions || [])].slice(0, 6).map((row) => ({
    ...row,
    percent: ratePercent(row.scoreRate),
    wrongPercent: ratePercent(row.wrongRate)
  }))
)

const classOption = computed(() => {
  const rows = classRows.value
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 8, textStyle: { fontSize: 10 } },
    grid: { left: 34, right: 34, top: 36, bottom: 40 },
    xAxis: {
      type: 'category',
      data: rows.map((row) => row.className),
      axisLine: { lineStyle: { color: '#d9deef' } },
      axisLabel: {
        color: '#7d829c',
        fontSize: 10,
        interval: 0,
        rotate: rows.length > 4 ? 30 : 0
      }
    },
    yAxis: [
      {
        type: 'value',
        name: '平均分',
        nameTextStyle: { fontSize: 10 },
        splitLine: { lineStyle: { color: '#eef1fa' } },
        axisLabel: { color: '#7d829c', fontSize: 10 }
      },
      {
        type: 'value',
        name: '及格率',
        max: 100,
        nameTextStyle: { fontSize: 10 },
        splitLine: { show: false },
        axisLabel: { color: '#7d829c', fontSize: 10, formatter: '{value}%' }
      }
    ],
    series: [
      {
        name: '平均分',
        type: 'bar',
        barWidth: '45%',
        data: rows.map((row) => row.avgScore),
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
        }
      },
      {
        name: '及格率',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 5,
        data: rows.map((row) => ratePercent(row.passRate)),
        lineStyle: { width: 2, color: '#9a5bff' },
        itemStyle: { color: '#9a5bff' }
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
    <MobileHero title="学情报表" subtitle="班级对比、知识点掌握与高频错题" icon="DataAnalysis">
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
        {{ stats.paperTitle }} · 已出分 {{ stats.markedCount ?? 0 }} / {{ stats.totalCount ?? 0 }}
      </p>
    </section>

    <div v-loading="loading" class="body">
      <template v-if="hasData">
        <MobileStatGrid :items="metricItems" :columns="2" />

        <MobileCard title="班级对比">
          <template #extra>
            <span class="m-sub">柱状平均分 · 折线及格率</span>
          </template>
          <EChart v-if="classRows.length" :option="classOption" height="240px" />
          <MobileEmpty v-else description="暂无班级数据" icon="DataAnalysis" />
        </MobileCard>

        <MobileCard title="知识点得分率">
          <template #extra>
            <span class="m-sub">由低到高</span>
          </template>
          <div v-if="knowledgeRows.length" class="rank-list">
            <div v-for="(row, index) in knowledgeRows" :key="row.knowledgeId || row.knowledgeName" class="rank-item">
              <span class="rank-index" :class="{ weak: index < 3 }">{{ index + 1 }}</span>
              <div class="rank-main">
                <div class="rank-top">
                  <span class="rank-name">{{ row.knowledgeName }}</span>
                  <b :style="{ color: rateColor(row.percent) }">{{ row.percent }}%</b>
                </div>
                <div class="rank-bar">
                  <span :style="{ width: `${row.percent}%`, background: rateColor(row.percent) }" />
                </div>
                <div class="rank-meta">关联 {{ row.questionCount ?? 0 }} 题 · 作答 {{ row.answerCount ?? 0 }} 次</div>
              </div>
            </div>
          </div>
          <MobileEmpty v-else description="暂无知识点数据" icon="Notebook" />
        </MobileCard>

        <MobileCard title="高频错题">
          <template #extra>
            <span class="m-sub">得分率低于 60%</span>
          </template>
          <div v-if="weakRows.length" class="weak-list">
            <div v-for="row in weakRows" :key="row.questionId" class="weak-item">
              <div class="weak-head">
                <b>第 {{ row.questionNo }} 题</b>
                <span class="weak-type">{{ row.questionType || '-' }}</span>
                <span class="weak-rate" :style="{ color: rateColor(row.percent) }">{{ row.percent }}%</span>
              </div>
              <div class="weak-meta">
                <span>满分 {{ row.fullScore ?? '-' }}</span>
                <span>平均 {{ row.avgScore ?? '-' }}</span>
                <span>低分 {{ row.wrongCount ?? 0 }} 人（{{ row.wrongPercent }}%）</span>
              </div>
            </div>
          </div>
          <MobileEmpty v-else description="暂无高频错题" icon="Warning" />
        </MobileCard>
      </template>

      <MobileEmpty
        v-else-if="!loading"
        description="暂无学情数据，请选择其他试卷或稍后再试"
        icon="DataAnalysis"
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
  color: #9aa0b8;
}

.body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 13px;
}

.rank-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.rank-index {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  font-size: 11px;
  font-weight: 600;
  color: #8b90ab;
  background: #f1f3fb;
  border-radius: 7px;
}

.rank-index.weak {
  color: #fff;
  background: linear-gradient(135deg, #f56c6c, #ff9a8b);
}

.rank-main {
  flex: 1;
  min-width: 0;
}

.rank-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.rank-name {
  overflow: hidden;
  font-size: 13px;
  color: #3a3e5c;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-top b {
  font-size: 13px;
}

.rank-bar {
  height: 5px;
  margin: 6px 0 5px;
  overflow: hidden;
  background: #eef1fb;
  border-radius: 999px;
}

.rank-bar span {
  display: block;
  height: 100%;
  border-radius: 999px;
}

.rank-meta {
  font-size: 11px;
  color: #a2a7bd;
}

.weak-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
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

.weak-type {
  padding: 1px 7px;
  font-size: 10px;
  color: #6b72a0;
  background: #eef1ff;
  border-radius: 999px;
}

.weak-rate {
  margin-left: auto;
  font-size: 15px;
  font-weight: 700;
}

.weak-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 5px;
  font-size: 11px;
  color: #9aa0b8;
}
</style>
