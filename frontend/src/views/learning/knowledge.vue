<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLearningStatistics } from '@/api/learning'
import { pagePapers } from '@/api/paper'
import EChart from '@/components/EChart.vue'
import PageHero from '@/components/PageHero.vue'

/** 知识点得分率图表：支持饼图 / 柱状图切换 */
const route = useRoute()
const papers = ref([])
const paperId = ref(null)
const stats = ref(null)
const loading = ref(false)
const chartType = ref('pie')

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
  paperId.value = route.query.paperId ? Number(route.query.paperId) : papers.value[0]?.id || null
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

/** 得分率统一转换成百分数（后端为 0~1 小数） */
function percentValue(value) {
  const num = Number(value)
  if (Number.isNaN(num)) return 0
  return Number((num <= 1 ? num * 100 : num).toFixed(1))
}

const knowledgeRows = computed(() =>
  (stats.value?.knowledgeStats || []).map((item) => ({
    ...item,
    percent: percentValue(item.scoreRate)
  }))
)

/** 概览统计：全部由已加载的知识点统计计算 */
const statItems = computed(() => {
  const rows = knowledgeRows.value
  const questionCount = rows.reduce((sum, row) => sum + Number(row.questionCount || 0), 0)
  const answerCount = rows.reduce((sum, row) => sum + Number(row.answerCount || 0), 0)
  const avgRate = rows.length
    ? (rows.reduce((sum, row) => sum + Number(row.percent || 0), 0) / rows.length).toFixed(1)
    : '-'
  return [
    {
      label: '知识点数',
      value: rows.length,
      icon: 'Notebook',
      color: '#409eff',
      bg: '#ecf5ff'
    },
    {
      label: '关联题目',
      value: questionCount,
      icon: 'Document',
      color: '#67c23a',
      bg: '#f0f9eb'
    },
    {
      label: '作答总数',
      value: answerCount,
      icon: 'Tickets',
      color: '#e6a23c',
      bg: '#fdf6ec'
    },
    {
      label: '平均得分率',
      value: avgRate === '-' ? '-' : `${avgRate}%`,
      icon: 'TrendCharts',
      color: '#9254de',
      bg: '#f9f0ff'
    }
  ]
})

/** 掌握度从低到高排序，优先暴露薄弱知识点 */
const sortedRows = computed(() => [...knowledgeRows.value].sort((a, b) => a.percent - b.percent))

const selectedPaperLabel = computed(
  () => papers.value.find((paper) => paper.id === paperId.value)?.paperTitle || '未选择试卷'
)

/** 得分率颜色：低于 60 红、低于 75 橙、其余蓝 */
function rankColor(value) {
  if (value < 60) return '#f56c6c'
  if (value < 75) return '#e6a23c'
  return '#5b6cff'
}

const chartOption = computed(() => {
  const rows = knowledgeRows.value
  if (chartType.value === 'pie') {
    return {
      tooltip: { trigger: 'item', formatter: '{b}<br/>得分率：{c}%' },
      legend: { bottom: 0, type: 'scroll' },
      series: [
        {
          name: '知识点得分率',
          type: 'pie',
          radius: ['42%', '68%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { formatter: '{b}\n{c}%' },
          data: rows.map((row) => ({ name: row.knowledgeName, value: row.percent }))
        }
      ]
    }
  }

  return {
    tooltip: { trigger: 'axis', valueFormatter: (value) => `${value}%` },
    grid: { left: 55, right: 24, top: 30, bottom: 80 },
    xAxis: {
      type: 'category',
      data: rows.map((row) => row.knowledgeName),
      axisLabel: { interval: 0, rotate: rows.length > 6 ? 30 : 0 }
    },
    yAxis: { type: 'value', max: 100, name: '得分率(%)' },
    series: [
      {
        type: 'bar',
        data: rows.map((row) => row.percent),
        barWidth: '52%',
        itemStyle: { color: '#5b6cff', borderRadius: [4, 4, 0, 0] },
        label: { show: true, position: 'top', formatter: '{c}%' }
      }
    ]
  }
})

onMounted(async () => {
  await loadPapers()
  loadStatistics()
})
</script>

<template>
  <div class="page-container">
    <PageHero
      title="知识点掌握"
      subtitle="按知识点汇总试卷的作答量与得分率，支持饼图、柱状图与明细三种视角对照查看。"
      icon="Notebook"
    >
      <template #actions>
        <el-button plain :loading="loading" @click="loadStatistics">刷新</el-button>
      </template>
    </PageHero>

    <!-- 概览横幅 + 控制 -->
    <section class="knowledge-hero">
      <div class="hero-main">
        <span class="hero-eyebrow">知识点掌握 · {{ selectedPaperLabel }}</span>
        <div class="hero-metrics">
          <div v-for="item in statItems" :key="item.label" class="hero-metric">
            <b>{{ item.value }}</b>
            <span>{{ item.label }}</span>
          </div>
        </div>
      </div>

      <div class="hero-actions">
        <el-select
          v-model="paperId"
          filterable
          class="paper-select"
          placeholder="选择试卷"
          @change="loadStatistics"
        >
          <el-option
            v-for="paper in papers"
            :key="paper.id"
            :label="`${paper.paperTitle}（${paper.paperNo}）`"
            :value="paper.id"
          />
        </el-select>
        <el-radio-group v-model="chartType" class="chart-toggle">
          <el-radio-button value="pie">饼图</el-radio-button>
          <el-radio-button value="bar">柱状图</el-radio-button>
        </el-radio-group>
      </div>
    </section>

    <div v-loading="loading" class="knowledge-grid">
      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="card-head">
            <span>知识点得分率分布</span>
            <span class="card-sub">按知识点统计平均得分率</span>
          </div>
        </template>
        <EChart v-if="knowledgeRows.length" :option="chartOption" height="420px" />
        <el-empty v-else description="暂无知识点统计数据" :image-size="90" />
      </el-card>

      <el-card shadow="never" class="rank-card">
        <template #header>
          <div class="card-head">
            <span>掌握度排行</span>
            <span class="card-sub">由低到高，优先关注前列</span>
          </div>
        </template>
        <div v-if="sortedRows.length" class="rank-list">
          <div
            v-for="(row, index) in sortedRows"
            :key="row.knowledgeName"
            class="rank-item"
          >
            <span class="rank-index" :class="{ weak: index < 3 }">{{ index + 1 }}</span>
            <div class="rank-main">
              <div class="rank-top">
                <span class="rank-name">{{ row.knowledgeName }}</span>
                <b :style="{ color: rankColor(row.percent) }">{{ row.percent }}%</b>
              </div>
              <el-progress
                :percentage="row.percent"
                :stroke-width="8"
                :show-text="false"
                :color="rankColor(row.percent)"
              />
              <div class="rank-meta">关联 {{ row.questionCount }} 题 · 作答 {{ row.answerCount }} 次</div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无知识点数据" :image-size="80" />
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

/* 知识点概览横幅 */
.knowledge-hero {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 30px;
  overflow: hidden;
  color: #fff;
  border: 1px solid rgba(140, 156, 255, 0.18);
  border-radius: 16px;
  background: var(--ai-gradient);
  box-shadow: 0 16px 36px rgba(30, 30, 100, 0.18);
}

.knowledge-hero::before {
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

.hero-main,
.hero-actions {
  position: relative;
  z-index: 1;
}

.hero-eyebrow {
  display: inline-flex;
  padding: 5px 13px;
  margin-bottom: 14px;
  font-size: 12px;
  color: #b6c1ff;
  background: rgba(126, 146, 255, 0.14);
  border: 1px solid rgba(126, 146, 255, 0.3);
  border-radius: 999px;
}

.hero-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 34px;
}

.hero-metric {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.hero-metric b {
  font-size: 24px;
  font-weight: 700;
  color: #eef1ff;
}

.hero-metric span {
  font-size: 12px;
  color: rgba(190, 198, 240, 0.65);
}

.hero-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.paper-select {
  width: 320px;
}

.hero-actions :deep(.el-select__wrapper) {
  background: rgba(255, 255, 255, 0.1);
  box-shadow: 0 0 0 1px rgba(150, 166, 255, 0.3) inset;
}

.hero-actions :deep(.el-select__placeholder),
.hero-actions :deep(.el-select__selected-item) {
  color: #e6eaff;
}

.chart-toggle :deep(.el-radio-button__inner) {
  color: #d6dcff;
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(160, 174, 255, 0.3);
}

.chart-toggle :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  color: #1a1c50;
  background: #cfd8ff;
  border-color: #cfd8ff;
  box-shadow: none;
}

/* 图表 + 排行 */
.knowledge-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 1fr);
  gap: 16px;
  align-items: start;
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

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 420px;
  padding-right: 4px;
  overflow-y: auto;
}

.rank-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.rank-index {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  font-size: 12px;
  font-weight: 600;
  color: #8b90ab;
  background: #f1f3fb;
  border-radius: 8px;
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
  gap: 10px;
  margin-bottom: 5px;
}

.rank-name {
  overflow: hidden;
  font-size: 13px;
  color: #3a3e5c;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-top b {
  font-size: 14px;
}

.rank-meta {
  margin-top: 5px;
  font-size: 11px;
  color: #a2a7bd;
}

@media (max-width: 1080px) {
  .knowledge-grid {
    grid-template-columns: 1fr;
  }

  .hero-actions {
    align-items: flex-start;
  }

  .paper-select {
    width: 100%;
  }
}
</style>
