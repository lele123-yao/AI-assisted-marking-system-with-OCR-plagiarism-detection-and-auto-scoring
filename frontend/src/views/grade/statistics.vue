<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getGradeStatistics } from '@/api/grade'
import { pagePapers } from '@/api/paper'
import EChart from '@/components/EChart.vue'
import PageHero from '@/components/PageHero.vue'
import { QUESTION_TYPE, dictItem, toPercent } from '@/utils/dict'

/** 成绩统计图表：指标卡 + 分数段柱状图 + 出分进度 + 逐题统计 */
const papers = ref([])
const paperId = ref(null)
const loading = ref(false)
const stats = ref(null)

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
    stats.value = await getGradeStatistics(paperId.value)
  } finally {
    loading.value = false
  }
}

/** 分数段分布柱状图 */
const distributionOption = computed(() => {
  const buckets = stats.value?.scoreDistribution || []
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 50, right: 24, top: 40, bottom: 40 },
    xAxis: {
      type: 'category',
      data: buckets.map((item) => item.label),
      axisLine: { lineStyle: { color: '#d9deef' } },
      axisLabel: { color: '#7d829c' }
    },
    yAxis: {
      type: 'value',
      name: '人数',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#eef1fa' } },
      axisLabel: { color: '#7d829c' }
    },
    series: [
      {
        name: '人数',
        type: 'bar',
        barWidth: '52%',
        data: buckets.map((item) => item.count),
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
      }
    ]
  }
})

/** 出分进度环形图 */
const statusOption = computed(() => {
  const marked = Number(stats.value?.markedCount || 0)
  const unmarked = Number(stats.value?.unmarkedCount || 0)
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, icon: 'circle', textStyle: { color: '#7d829c' } },
    series: [
      {
        type: 'pie',
        radius: ['58%', '78%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: false,
        itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 8 },
        label: { show: false },
        labelLine: { show: false },
        emphasis: {
          label: { show: true, position: 'center', fontSize: 18, fontWeight: 600 }
        },
        data: [
          { value: marked, name: '已出分', itemStyle: { color: '#5b6cff' } },
          { value: unmarked, name: '未出分', itemStyle: { color: '#e4e8f6' } }
        ]
      }
    ]
  }
})

/** 及格率/优秀率兼容 0~1 与 0~100 */
function rateText(value) {
  const num = Number(value)
  if (Number.isNaN(num)) return '-'
  return num <= 1 ? toPercent(num) : `${num.toFixed(1)}%`
}

/** 转为 el-progress 需要的 0~100 数值 */
function ratePercent(value) {
  const num = Number(value)
  if (Number.isNaN(num)) return 0
  return Math.round((num <= 1 ? num * 100 : num) * 10) / 10
}

const passPercent = computed(() => ratePercent(stats.value?.passRate))
const excellentPercent = computed(() => ratePercent(stats.value?.excellentRate))

/** 指标卡数据：全部来自统计接口 */
const metricStats = computed(() => {
  const data = stats.value
  return [
    { label: '答卷总数', value: data?.totalCount ?? 0, icon: 'Tickets', color: '#5b6cff', bg: '#eef1ff' },
    { label: '已出分', value: data?.markedCount ?? 0, icon: 'CircleCheck', color: '#2fae7a', bg: '#e9f8f1' },
    { label: '未出分', value: data?.unmarkedCount ?? 0, icon: 'Clock', color: '#e6a23c', bg: '#fdf6ec' },
    { label: '平均分', value: data?.avgScore ?? '-', icon: 'TrendCharts', color: '#7a5bff', bg: '#f3efff' },
    { label: '最高分', value: data?.maxScore ?? '-', icon: 'Top', color: '#f56c6c', bg: '#fef0f0' },
    { label: '最低分', value: data?.minScore ?? '-', icon: 'Bottom', color: '#8b90ab', bg: '#f4f5fa' }
  ]
})

onMounted(async () => {
  await loadPapers()
  loadStatistics()
})
</script>

<template>
  <div class="page-container">
    <PageHero
      title="成绩统计"
      subtitle="按试卷汇总答卷出分情况、平均分与分数段分布，并展示逐题得分率便于定位薄弱题目。"
      icon="TrendCharts"
    >
      <template #actions>
        <el-button :loading="loading" plain @click="loadStatistics">刷新统计</el-button>
      </template>
    </PageHero>

    <!-- 试卷选择条 -->
    <el-card shadow="never" class="control-bar">
      <div class="control-inner">
        <div class="control-label">
          <el-icon><Document /></el-icon>
          <span>选择试卷</span>
        </div>
        <el-select v-model="paperId" filterable style="width: 380px" @change="loadStatistics">
          <el-option
            v-for="paper in papers"
            :key="paper.id"
            :label="`${paper.paperTitle}（${paper.paperNo}）`"
            :value="paper.id"
          />
        </el-select>
        <span class="control-hint">统计口径：当前试卷下全部已提交答卷</span>
      </div>
    </el-card>

    <div v-loading="loading" class="dashboard-body">
      <!-- 指标条 -->
      <section class="metric-grid">
        <article v-for="item in metricStats" :key="item.label" class="metric-card">
          <div class="metric-icon" :style="{ color: item.color, backgroundColor: item.bg }">
            <el-icon :size="20"><component :is="item.icon" /></el-icon>
          </div>
          <div class="metric-info">
            <div class="metric-value">{{ item.value }}</div>
            <div class="metric-label">{{ item.label }}</div>
          </div>
        </article>
      </section>

      <!-- 图表区 -->
      <section class="chart-grid">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-head">
              <span>分数段分布</span>
              <span class="card-sub">按总分区间统计人数</span>
            </div>
          </template>
          <EChart v-if="stats?.scoreDistribution?.length" :option="distributionOption" height="340px" />
          <el-empty v-else description="暂无分布数据" :image-size="80" />
        </el-card>

        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-head">
              <span>出分进度</span>
              <span class="card-sub">已出分 / 未出分</span>
            </div>
          </template>
          <EChart v-if="stats" :option="statusOption" height="200px" />
          <el-empty v-else description="暂无出分数据" :image-size="70" />
          <div class="rate-row">
            <div class="rate-item">
              <el-progress type="dashboard" :percentage="passPercent" :width="104" color="#5b6cff" />
              <div class="rate-label">及格率 {{ rateText(stats?.passRate) }}</div>
            </div>
            <div class="rate-item">
              <el-progress type="dashboard" :percentage="excellentPercent" :width="104" color="#9a5bff" />
              <div class="rate-label">优秀率 {{ rateText(stats?.excellentRate) }}</div>
            </div>
          </div>
        </el-card>
      </section>

      <!-- 逐题统计 -->
      <el-card shadow="never">
        <template #header>
          <div class="card-head">
            <span>逐题得分统计</span>
            <span class="card-sub">按题号列出平均分、得分率与满分人数</span>
          </div>
        </template>
        <el-table :data="stats?.questionStats || []" border stripe>
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
          <el-table-column prop="maxScore" label="最高分" width="90" />
          <el-table-column prop="minScore" label="最低分" width="90" />
          <el-table-column label="得分率" width="100">
            <template #default="{ row }">{{ rateText(row.scoreRate) }}</template>
          </el-table-column>
          <el-table-column prop="fullMarkCount" label="满分人数" width="100" />
          <template #empty>
            <el-empty description="暂无逐题统计" :image-size="80" />
          </template>
        </el-table>
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

/* 试卷选择条 */
.control-bar {
  background: linear-gradient(120deg, #ffffff 0%, #f7f8ff 100%);
}

.control-inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
}

.control-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #3a3e5c;
}

.control-label .el-icon {
  color: #5b6cff;
}

.control-hint {
  margin-left: auto;
  font-size: 12px;
  color: #a2a7bd;
}

.dashboard-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 指标条 */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 12px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.metric-card:hover {
  box-shadow: 0 10px 24px rgba(44, 52, 120, 0.08);
  transform: translateY(-2px);
}

.metric-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
}

.metric-value {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
  color: #23264a;
}

.metric-label {
  margin-top: 2px;
  font-size: 12px;
  color: #8b90ab;
}

/* 图表区 */
.chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(300px, 1fr);
  gap: 16px;
  align-items: stretch;
}

.chart-card {
  display: flex;
  flex-direction: column;
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

.rate-row {
  display: flex;
  justify-content: space-around;
  padding-top: 6px;
  margin-top: auto;
  border-top: 1px dashed #eef1fa;
}

.rate-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.rate-label {
  font-size: 13px;
  color: #5b6079;
}

@media (max-width: 1280px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1080px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .control-hint {
    margin-left: 0;
  }
}
</style>
