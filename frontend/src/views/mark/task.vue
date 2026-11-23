<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { pageMarkingTasks } from '@/api/mark'
import { pagePapers } from '@/api/paper'
import { useTable } from '@/composables/useTable'
import { MARKING_STATUS, dictItem, formatTime } from '@/utils/dict'

/**
 * AI 阅卷任务监控。
 *
 * 阅卷任务进度由答卷的 markingStatus 字段体现：
 * 待录入 → OCR 识别中 → AI 批改中 → 待教师确认 → 已确认归档，
 * 因此本页按阅卷状态聚合统计，并支持直接筛选任务明细。
 */
const route = useRoute()
const router = useRouter()
const papers = ref([])
const counts = ref({})
const autoRefresh = ref(false)
let timer = null

const { loading, list, total, query, load, search, reset, handleSizeChange, handlePageChange } =
  useTable(pageMarkingTasks, { paperId: '', keyword: '', markingStatus: '' })

// 状态卡片：图标、配色只用于展示，点击后按状态筛选下方明细
const STATUS_META = {
  0: { icon: 'Upload', color: '#909399', bg: '#f4f4f5' },
  1: { icon: 'Camera', color: '#e6a23c', bg: '#fdf6ec' },
  2: { icon: 'Cpu', color: '#409eff', bg: '#ecf5ff' },
  3: { icon: 'EditPen', color: '#9254de', bg: '#f9f0ff' },
  4: { icon: 'Warning', color: '#f56c6c', bg: '#fef0f0' },
  5: { icon: 'CircleCheck', color: '#67c23a', bg: '#f0f9eb' }
}

const statusCards = computed(() =>
  Object.keys(MARKING_STATUS).map((key) => {
    const status = Number(key)
    return {
      status,
      label: MARKING_STATUS[key].label,
      type: MARKING_STATUS[key].type,
      count: counts.value[status] ?? 0,
      ...STATUS_META[status]
    }
  })
)

// 整体进度：已确认归档 / 全部答卷
const totalAll = computed(() =>
  statusCards.value.reduce((sum, item) => sum + item.count, 0)
)
const archivedCount = computed(() => counts.value[5] ?? 0)
const progressPercent = computed(() =>
  totalAll.value ? Math.round((archivedCount.value / totalAll.value) * 100) : 0
)

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
}

/** 并行拉取各状态的数量（pageSize=1 只取 total） */
async function loadCounts() {
  const statuses = [0, 1, 2, 3, 4, 5]
  const results = await Promise.all(
    statuses.map((status) =>
      pageMarkingTasks({
        pageNum: 1,
        pageSize: 1,
        paperId: query.paperId || undefined,
        markingStatus: status
      }).catch(() => ({ total: 0 }))
    )
  )
  const map = {}
  statuses.forEach((status, index) => {
    map[status] = Number(results[index]?.total || 0)
  })
  counts.value = map
}

function refreshAll() {
  load()
  loadCounts()
}

/** 点击状态卡片：再次点击同一状态可取消筛选 */
function filterByStatus(status) {
  query.markingStatus = query.markingStatus === status ? '' : status
  query.pageNum = 1
  load()
}

function toggleAutoRefresh(value) {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (value) {
    timer = setInterval(refreshAll, 15000)
  }
}

function goDetail(row) {
  router.push(`/mark/detail/${row.id}`)
}

watch(autoRefresh, toggleAutoRefresh)

onMounted(() => {
  // 首页统计卡片通过 ?status=3 之类的参数跳转过来，直接带入筛选条件
  const status = Number(route.query.status)
  if (route.query.status !== undefined && MARKING_STATUS[status]) {
    query.markingStatus = status
  }
  loadPapers()
  refreshAll()
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="page-container">
    <!-- 顶部概览 -->
    <div class="hero">
      <div class="hero-main">
        <div class="hero-icon">
          <el-icon :size="28"><Cpu /></el-icon>
        </div>
        <div>
          <h2 class="hero-title">AI 阅卷任务监控</h2>
          <p class="hero-desc">
            按答卷阅卷状态实时聚合 OCR 识别、AI 批改与教师确认进度，点击下方状态卡片可快速筛选。
          </p>
        </div>
      </div>
      <div class="hero-actions">
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新(15s)"
          class="hero-switch"
        />
        <el-button type="primary" :loading="loading" @click="refreshAll">立即刷新</el-button>
      </div>
    </div>

    <!-- 状态统计 -->
    <div class="stat-grid">
      <div
        v-for="item in statusCards"
        :key="item.status"
        class="stat-card"
        :class="{ active: query.markingStatus === item.status }"
        :style="{ '--stage': item.color }"
        @click="filterByStatus(item.status)"
      >
        <div class="stat-icon" :style="{ color: item.color, backgroundColor: item.bg }">
          <el-icon :size="22"><component :is="item.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ item.count }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </div>
        <span class="stat-step">{{ String(item.status + 1).padStart(2, '0') }}</span>
      </div>
    </div>

    <!-- 整体进度 -->
    <el-card shadow="never" class="progress-card">
      <div class="progress-head">
        <div class="progress-title">
          <el-icon><TrendCharts /></el-icon>
          <span>整体阅卷进度</span>
        </div>
        <div class="progress-meta">
          已确认归档 <b>{{ archivedCount }}</b> / 共 <b>{{ totalAll }}</b> 份答卷
        </div>
        <div class="progress-percent">{{ progressPercent }}%</div>
      </div>
      <el-progress
        :percentage="progressPercent"
        :stroke-width="10"
        :show-text="false"
        :color="progressPercent === 100 ? '#67c23a' : '#409eff'"
      />
    </el-card>

    <!-- 任务明细 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">任务明细</span>
          <el-tag v-if="query.markingStatus !== ''" closable type="info" @close="filterByStatus(query.markingStatus)">
            已筛选：{{ dictItem(MARKING_STATUS, query.markingStatus).label }}
          </el-tag>
        </div>
      </template>

      <div class="toolbar">
        <el-select v-model="query.paperId" placeholder="选择试卷" clearable filterable style="width: 300px" @change="search">
          <el-option
            v-for="paper in papers"
            :key="paper.id"
            :label="`${paper.paperTitle}（${paper.paperNo}）`"
            :value="paper.id"
          />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="学号 / 姓名"
          clearable
          style="width: 180px"
          @keyup.enter="search"
        />
        <el-select v-model="query.markingStatus" placeholder="阅卷状态" clearable style="width: 160px">
          <el-option
            v-for="(item, key) in MARKING_STATUS"
            :key="key"
            :label="item.label"
            :value="Number(key)"
          />
        </el-select>
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="studentNo" label="学号" width="130" />
        <el-table-column prop="studentName" label="姓名" width="110" />
        <el-table-column prop="paperTitle" label="试卷" min-width="190" show-overflow-tooltip />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="dictItem(MARKING_STATUS, row.markingStatus).type">
              {{ row.markingStatusDesc || dictItem(MARKING_STATUS, row.markingStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="AI参考分" width="100">
          <template #default="{ row }">{{ row.aiReferenceScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="教师最终分" width="110">
          <template #default="{ row }">{{ row.teacherFinalScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="170">
          <template #default="{ row }">{{ formatTime(row.markTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无阅卷任务" :image-size="80" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 顶部渐变概览 */
.hero {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 28px;
  overflow: hidden;
  color: #fff;
  border: 1px solid rgba(140, 156, 255, 0.18);
  border-radius: 14px;
  background: var(--ai-gradient);
}

.hero::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: '';
  background-image:
    linear-gradient(rgba(160, 174, 255, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(160, 174, 255, 0.08) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(circle at 25% 30%, #000 0%, transparent 74%);
}

.hero::after {
  position: absolute;
  top: -60px;
  right: -40px;
  width: 220px;
  height: 220px;
  pointer-events: none;
  content: '';
  background:
    radial-gradient(circle at 40% 42%, rgba(124, 108, 255, 0.5), transparent 62%),
    radial-gradient(circle at 66% 60%, rgba(41, 210, 228, 0.28), transparent 60%);
}

.hero-main {
  display: flex;
  align-items: center;
  gap: 16px;
  z-index: 1;
}

.hero-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.14);
}

.hero-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.hero-desc {
  margin: 0;
  max-width: 640px;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.78);
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  z-index: 1;
}

.hero-switch :deep(.el-switch__label) {
  color: rgba(255, 255, 255, 0.85);
}

/* 状态统计卡片 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 16px 16px 18px;
  overflow: hidden;
  cursor: pointer;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

/* 左侧阶段色条，体现阅卷流水线 */
.stat-card::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  content: '';
  background: var(--stage);
  opacity: 0.55;
  transition: opacity 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgba(31, 45, 61, 0.08);
}

.stat-card.active {
  border-color: var(--ai-primary);
  box-shadow: 0 0 0 2px rgba(91, 108, 255, 0.15);
}

.stat-card.active::before {
  opacity: 1;
}

.stat-step {
  margin-left: auto;
  align-self: flex-start;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1px;
  color: #cdd3e8;
  transition: color 0.2s ease;
}

.stat-card.active .stat-step {
  color: var(--stage);
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 10px;
  flex-shrink: 0;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
  color: #303133;
}

.stat-label {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

/* 整体进度 */
.progress-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.progress-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.progress-meta {
  font-size: 13px;
  color: #909399;
}

.progress-meta b {
  color: #303133;
}

.progress-percent {
  margin-left: auto;
  font-size: 22px;
  font-weight: 600;
  color: #409eff;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
