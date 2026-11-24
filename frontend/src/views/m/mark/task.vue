<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { pageMarkingTasks } from '@/api/mark'
import { useMobileList } from '@/composables/useMobileList'
import { MARKING_STATUS, dictItem, formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileLoadMore from '@/components/m/MobileLoadMore.vue'

/** 移动端 AI 阅卷任务监控：按答卷状态筛选，卡片进入阅卷详情 */
const route = useRoute()
const router = useRouter()

const preset = Number(route.query.status)
const hasPreset = route.query.status !== undefined && route.query.status !== '' && !!MARKING_STATUS[preset]

const fetchTasks = (params) => {
  const payload = { ...params }
  if (payload.markingStatus === '' || payload.markingStatus === null) delete payload.markingStatus
  return pageMarkingTasks(payload)
}

const { query, list, loading, finished, load, loadMore, search } = useMobileList(fetchTasks, {
  markingStatus: hasPreset ? preset : ''
})

const status = ref(hasPreset ? preset : '')
const total = computed(() => list.value.length)

const filters = computed(() => [
  { value: '', label: '全部' },
  ...Object.keys(MARKING_STATUS).map((key) => ({
    value: Number(key),
    label: MARKING_STATUS[key].label
  }))
])

/** 当前已加载列表内各状态数量，不额外请求接口 */
const statusCards = computed(() => {
  const counts = { 0: 0, 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 }
  list.value.forEach((row) => {
    if (row.markingStatus in counts) counts[row.markingStatus] += 1
  })
  return Object.keys(MARKING_STATUS).map((key) => ({
    value: Number(key),
    short: MARKING_STATUS[key].label.replace('识别中', '').replace('教师', ''),
    label: MARKING_STATUS[key].label,
    count: counts[key] || 0
  }))
})

function pickStatus(value) {
  status.value = value
  query.markingStatus = value
  search()
}

function goDetail(row) {
  router.push(`/m/mark/detail/${row.id}`)
}

onMounted(load)
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="任务监控"
      subtitle="按阅卷状态查看 OCR、AI 批改与教师确认进度"
      icon="Cpu"
    >
      <template #actions>
        <el-button size="small" plain :loading="loading" @click="load">刷新</el-button>
      </template>
    </MobileHero>

    <section class="m-card summary-card">
      <div class="m-row-between">
        <span class="m-title">当前页状态</span>
        <span class="m-sub">已加载 {{ total }} 条</span>
      </div>
      <div class="summary-grid">
        <button
          v-for="item in statusCards"
          :key="item.value"
          type="button"
          class="summary-item"
          :class="{ active: status === item.value }"
          @click="pickStatus(item.value)"
        >
          <b>{{ item.count }}</b>
          <span>{{ item.short }}</span>
        </button>
      </div>
    </section>

    <div class="m-chips">
      <button
        v-for="item in filters"
        :key="item.label"
        type="button"
        class="m-chip"
        :class="{ active: status === item.value }"
        @click="pickStatus(item.value)"
      >
        {{ item.label }}
      </button>
    </div>

    <div v-loading="loading" class="task-list">
      <article v-for="row in list" :key="row.id" class="m-card task" @click="goDetail(row)">
        <div class="m-row-between">
          <span class="student">{{ row.studentNo || '-' }} {{ row.studentName || '' }}</span>
          <el-tag :type="dictItem(MARKING_STATUS, row.markingStatus).type" size="small" effect="plain">
            {{ row.markingStatusDesc || dictItem(MARKING_STATUS, row.markingStatus).label }}
          </el-tag>
        </div>

        <h3 class="m-title task-paper">{{ row.paperTitle || '未知试卷' }}</h3>

        <div class="m-row-between task-foot">
          <span class="m-sub">AI 参考分 {{ row.aiReferenceScore ?? '-' }}</span>
          <span class="m-sub">{{ formatTime(row.markTime || row.createTime) }}</span>
        </div>
      </article>

      <MobileEmpty
        v-if="!loading && !list.length"
        description="暂无阅卷任务"
        icon="Cpu"
        action-text="刷新"
        @action="load"
      />

      <MobileLoadMore
        :loading="loading"
        :finished="finished && list.length > 0"
        :empty="!loading && !list.length"
        @load="loadMore"
      />
    </div>
  </div>
</template>

<style scoped>
.summary-card {
  padding-bottom: 10px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 6px;
  text-align: center;
  background: #f8f9ff;
  border: 1px solid #eef1fa;
  border-radius: 10px;
}

.summary-item b {
  font-size: 17px;
  font-weight: 700;
  color: #23264a;
}

.summary-item span {
  overflow: hidden;
  font-size: 11px;
  color: #8b90ab;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-item.active {
  background: #eef1ff;
  border-color: #b9c4fb;
}

.summary-item.active b {
  color: var(--ai-primary);
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 80px;
}

.task {
  cursor: pointer;
}

.task:active {
  transform: scale(0.995);
}

.student {
  font-size: 12px;
  color: #5c6180;
}

.task-paper {
  margin: 8px 0 10px;
  font-size: 15px;
}

.task-foot {
  padding-top: 8px;
  border-top: 1px dashed #f0f2fb;
}
</style>
