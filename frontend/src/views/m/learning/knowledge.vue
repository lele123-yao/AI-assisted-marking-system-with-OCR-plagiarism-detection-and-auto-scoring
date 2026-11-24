<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLearningStatistics } from '@/api/learning'
import { pagePapers } from '@/api/paper'
import EChart from '@/components/EChart.vue'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileCard from '@/components/m/MobileCard.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileStatGrid from '@/components/m/MobileStatGrid.vue'

/** 知识点掌握：环形图 + 得分率排行，点击知识点展开明细 */
const route = useRoute()
const papers = ref([])
const paperId = ref(null)
const loading = ref(false)
const stats = ref(null)
const expandedKey = ref('')

const chartColors = ['#5b6cff', '#9a5bff', '#2fae7a', '#e6a23c', '#f56c6c', '#38b2cf', '#8c9eff', '#b07cff']

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
    expandedKey.value = ''
  } finally {
    loading.value = false
  }
}

function percentValue(value) {
  const num = Number(value)
  if (Number.isNaN(num)) return 0
  return Number((num <= 1 ? num * 100 : num).toFixed(1))
}

function rateColor(value) {
  if (value < 60) return '#f56c6c'
  if (value < 75) return '#e6a23c'
  return '#5b6cff'
}

const knowledgeRows = computed(() =>
  (stats.value?.knowledgeStats || []).map((row) => ({
    ...row,
    percent: percentValue(row.scoreRate)
  }))
)

/** 掌握度由低到高，优先暴露薄弱知识点 */
const sortedRows = computed(() => [...knowledgeRows.value].sort((a, b) => a.percent - b.percent))

const summaryItems = computed(() => {
  const rows = knowledgeRows.value
  const questionCount = rows.reduce((sum, row) => sum + Number(row.questionCount || 0), 0)
  const answerCount = rows.reduce((sum, row) => sum + Number(row.answerCount || 0), 0)
  const avgRate = rows.length
    ? (rows.reduce((sum, row) => sum + row.percent, 0) / rows.length).toFixed(1)
    : '-'
  return [
    { key: 'k', label: '知识点数', value: rows.length, icon: 'Notebook', tone: 'primary' },
    { key: 'q', label: '关联题目', value: questionCount, icon: 'Document', tone: 'success' },
    { key: 'a', label: '作答总数', value: answerCount, icon: 'Tickets', tone: 'warning' },
    {
      key: 'r',
      label: '平均得分率',
      value: avgRate === '-' ? '-' : `${avgRate}%`,
      icon: 'TrendCharts',
      tone: 'primary'
    }
  ]
})

function rowKey(row) {
  return String(row.knowledgeId ?? row.knowledgeName)
}

function toggle(row) {
  const key = rowKey(row)
  expandedKey.value = expandedKey.value === key ? '' : key
}

const chartOption = computed(() => {
  const rows = knowledgeRows.value
  return {
    tooltip: { trigger: 'item', formatter: '{b}<br/>得分率：{c}%' },
    legend: {
      bottom: 0,
      type: 'scroll',
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { fontSize: 10, color: '#7d829c' }
    },
    series: [
      {
        name: '知识点得分率',
        type: 'pie',
        radius: ['44%', '68%'],
        center: ['50%', '42%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        labelLine: { show: false },
        emphasis: {
          label: { show: true, position: 'center', fontSize: 13, fontWeight: 600 }
        },
        data: rows.map((row, index) => ({
          name: row.knowledgeName,
          value: row.percent,
          itemStyle: { color: chartColors[index % chartColors.length] }
        }))
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
    <MobileHero title="知识点得分率" subtitle="按知识点查看掌握度，优先关注得分率偏低项" icon="Notebook">
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
    </section>

    <div v-loading="loading" class="body">
      <template v-if="knowledgeRows.length">
        <MobileStatGrid :items="summaryItems" :columns="2" />

        <MobileCard title="得分率分布">
          <template #extra>
            <span class="m-sub">共 {{ knowledgeRows.length }} 个知识点</span>
          </template>
          <EChart :option="chartOption" height="240px" />
        </MobileCard>

        <MobileCard title="掌握度排行">
          <template #extra>
            <span class="m-sub">由低到高</span>
          </template>
          <div class="rank-list">
            <div
              v-for="(row, index) in sortedRows"
              :key="rowKey(row)"
              class="rank-item"
              :class="{ expanded: expandedKey === rowKey(row) }"
              @click="toggle(row)"
            >
              <span class="rank-index" :class="{ weak: index < 3 }">{{ index + 1 }}</span>
              <div class="rank-main">
                <div class="rank-top">
                  <span class="rank-name">{{ row.knowledgeName }}</span>
                  <b :style="{ color: rateColor(row.percent) }">{{ row.percent }}%</b>
                  <el-icon class="rank-arrow" :size="12">
                    <ArrowDown v-if="expandedKey !== rowKey(row)" />
                    <ArrowUp v-else />
                  </el-icon>
                </div>
                <div class="rank-bar">
                  <span :style="{ width: `${row.percent}%`, background: rateColor(row.percent) }" />
                </div>
                <div class="rank-meta">关联 {{ row.questionCount ?? 0 }} 题 · 作答 {{ row.answerCount ?? 0 }} 次</div>

                <div v-if="expandedKey === rowKey(row)" class="rank-detail">
                  <div><span>累计满分</span><b>{{ row.fullScore ?? '-' }}</b></div>
                  <div><span>平均得分</span><b>{{ row.avgScore ?? '-' }}</b></div>
                  <div><span>作答人次</span><b>{{ row.answerCount ?? 0 }}</b></div>
                  <div><span>关联题目</span><b>{{ row.questionCount ?? 0 }}</b></div>
                </div>
              </div>
            </div>
          </div>
        </MobileCard>
      </template>

      <MobileEmpty
        v-else-if="!loading"
        description="暂无知识点统计数据，请选择其他试卷"
        icon="Notebook"
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

.body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rank-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px;
  background: #fafbff;
  border: 1px solid #f0f2fb;
  border-radius: 10px;
  transition: border-color 0.16s ease;
}

.rank-item.expanded {
  background: #f6f7ff;
  border-color: #c9d0ff;
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
  background: #eef1fa;
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
  align-items: center;
  gap: 6px;
}

.rank-name {
  overflow: hidden;
  font-size: 13px;
  color: #3a3e5c;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-top b {
  margin-left: auto;
  font-size: 13px;
}

.rank-arrow {
  color: #b0b5cc;
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

.rank-detail {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding-top: 9px;
  margin-top: 9px;
  border-top: 1px dashed #e6e9f7;
}

.rank-detail div {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #8b90ab;
}

.rank-detail b {
  font-weight: 600;
  color: #3a3e5c;
}
</style>
