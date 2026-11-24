<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { myPapers } from '@/api/student'
import { ANSWER_MODE, dictItem, formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'

/** 学生端：我的试卷，按作答状态筛选 */
const router = useRouter()
const loading = ref(false)
const list = ref([])
const filter = ref('all')

async function load() {
  loading.value = true
  try {
    list.value = (await myPapers()) || []
  } finally {
    loading.value = false
  }
}

const canAnswer = (row) => row.answerMode === 1 && !row.answered

const groups = computed(() => [
  { key: 'all', label: '全部', value: list.value.length },
  { key: 'pending', label: '待作答', value: list.value.filter(canAnswer).length },
  { key: 'done', label: '已提交', value: list.value.filter((row) => row.answered).length },
  { key: 'offline', label: '线下', value: list.value.filter((row) => row.answerMode === 2).length }
])

const filtered = computed(() => {
  if (filter.value === 'pending') return list.value.filter(canAnswer)
  if (filter.value === 'done') return list.value.filter((row) => row.answered)
  if (filter.value === 'offline') return list.value.filter((row) => row.answerMode === 2)
  return list.value
})

function statusMeta(row) {
  if (row.answered) return { label: '已提交', tone: 'done' }
  if (row.answerMode === 1) return { label: '待作答', tone: 'pending' }
  return { label: '线下作答', tone: 'offline' }
}

function deadlineTone(row) {
  if (!row.deadline) return ''
  const diff = new Date(row.deadline).getTime() - Date.now()
  if (diff < 0) return 'expired'
  if (diff < 72 * 3600 * 1000) return 'urgent'
  return ''
}

function goAnswer(row) {
  router.push({ path: '/m/answer/submit', query: { paperId: row.paperId } })
}

onMounted(load)
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="我的试卷"
      subtitle="仅显示下发给本人的试卷，可直接进入线上作答"
      icon="Document"
    >
      <template #actions>
        <el-button size="small" plain :loading="loading" @click="load">刷新</el-button>
      </template>
    </MobileHero>

    <div class="m-chips">
      <button
        v-for="item in groups"
        :key="item.key"
        type="button"
        class="m-chip"
        :class="{ active: filter === item.key }"
        @click="filter = item.key"
      >
        {{ item.label }}
        <b>{{ item.value }}</b>
      </button>
    </div>

    <div v-loading="loading" class="list">
      <article v-for="row in filtered" :key="row.paperId" class="m-card paper">
        <div class="m-row-between">
          <span class="course">{{ row.courseName || '未归属课程' }}</span>
          <span class="status" :class="statusMeta(row).tone">{{ statusMeta(row).label }}</span>
        </div>

        <h3 class="m-title">{{ row.paperTitle }}</h3>
        <p class="paper-no">{{ row.paperNo }}</p>

        <div class="tags">
          <el-tag :type="dictItem(ANSWER_MODE, row.answerMode).type" size="small" effect="plain">
            {{ dictItem(ANSWER_MODE, row.answerMode).label }}
          </el-tag>
          <el-tag v-if="row.totalScore" size="small" type="info" effect="plain">
            满分 {{ row.totalScore }}
          </el-tag>
          <el-tag size="small" effect="plain" :type="row.source === 'CLASS' ? 'primary' : 'warning'">
            {{ row.source === 'CLASS' ? '班级下发' : '指定本人' }}
          </el-tag>
        </div>

        <div class="meta">
          <div><span>考试时间</span><b>{{ row.startTime ? formatTime(row.startTime) : '-' }}</b></div>
          <div :class="deadlineTone(row)">
            <span>截止时间</span><b>{{ row.deadline ? formatTime(row.deadline) : '不限' }}</b>
          </div>
        </div>

        <div class="footer">
          <el-button v-if="canAnswer(row)" type="primary" size="small" @click="goAnswer(row)">
            去作答
          </el-button>
          <el-button
            v-else-if="row.answered && row.answerId"
            type="primary"
            size="small"
            plain
            @click="router.push(`/m/student/answer/${row.answerId}`)"
          >
            查看答卷
          </el-button>
          <span v-else-if="row.answered" class="state done">已完成，等待阅卷</span>
          <span v-else class="state offline">按老师要求线下完成</span>
        </div>
      </article>

      <MobileEmpty
        v-if="!loading && !filtered.length"
        description="当前分组暂无试卷"
        icon="Document"
        action-text="刷新"
        @action="load"
      />
    </div>
  </div>
</template>

<style scoped>
.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.course {
  font-size: 12px;
  color: var(--ai-primary);
}

.status {
  padding: 2px 9px;
  font-size: 11px;
  border-radius: 999px;
}

.status.pending {
  color: #e6a23c;
  background: #fdf6ec;
}

.status.done {
  color: #4caf50;
  background: #f0f9eb;
}

.status.offline {
  color: #8b90ab;
  background: #f4f6fc;
}

.paper h3 {
  margin: 9px 0 3px;
  font-size: 16px;
  line-height: 1.4;
}

.paper-no {
  margin: 0;
  font-size: 12px;
  color: #9aa0b8;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.meta {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 10px 0;
  margin-top: 6px;
  border-top: 1px dashed #f0f2fb;
}

.meta div {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #8b90ab;
}

.meta b {
  font-weight: 500;
  color: #3a3e5c;
}

.meta .urgent b {
  color: #e6a23c;
}

.meta .expired b {
  color: #f56c6c;
}

.footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}

.state {
  font-size: 12px;
}

.state.done {
  color: #4caf50;
}

.state.offline {
  color: #9aa0b8;
}
</style>
