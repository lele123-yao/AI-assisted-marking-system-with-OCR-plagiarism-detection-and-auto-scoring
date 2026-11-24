<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { pagePaperAnswers } from '@/api/paperAnswer'
import { useMobileList } from '@/composables/useMobileList'
import { MARKING_STATUS, dictItem, formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileLoadMore from '@/components/m/MobileLoadMore.vue'

/** 答卷列表：按阅卷状态筛选，卡片进入详情 */
const router = useRouter()
const status = ref('')

const fetchPage = (params) => {
  const payload = { ...params }
  if (payload.markingStatus === '' || payload.markingStatus === null) delete payload.markingStatus
  return pagePaperAnswers(payload)
}

const { query, list, loading, finished, hasMore, load, loadMore, search } = useMobileList(fetchPage, {
  markingStatus: ''
})
const filters = [
  { value: '', label: '全部' },
  { value: 0, label: '待录入' },
  { value: 1, label: 'OCR中' },
  { value: 2, label: 'AI批改中' },
  { value: 3, label: '待确认' },
  { value: 4, label: '失败' },
  { value: 5, label: '已归档' }
]

const empty = computed(() => !loading.value && !list.value.length)

function pickStatus(value) {
  status.value = value
  query.markingStatus = value
  search()
}

onMounted(load)
</script>

<template>
  <div class="m-page">
    <MobileHero title="答卷列表" subtitle="查看全部答卷的阅卷状态与得分" icon="Tickets">
      <template #actions>
        <el-button size="small" plain :loading="loading" @click="load">刷新</el-button>
      </template>
    </MobileHero>

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

    <div v-loading="loading" class="list">
      <article
        v-for="row in list"
        :key="row.id"
        class="m-card item"
        @click="router.push(`/m/answer/detail/${row.id}`)"
      >
        <div class="m-row-between">
          <span class="student">{{ row.studentNo || '-' }} {{ row.studentName || '' }}</span>
          <el-tag :type="dictItem(MARKING_STATUS, row.markingStatus).type" size="small" effect="plain">
            {{ row.markingStatusDesc || dictItem(MARKING_STATUS, row.markingStatus).label }}
          </el-tag>
        </div>

        <h3 class="m-title">{{ row.paperTitle || '未知试卷' }}</h3>

        <div class="score-row">
          <span class="score">
            <b>{{ row.teacherFinalScore ?? row.aiReferenceScore ?? '--' }}</b>
            <em>分</em>
          </span>
          <span v-if="row.scoreModified" class="diff">AI {{ row.aiReferenceScore ?? '-' }} / 已改分</span>
          <span v-else class="diff">AI 参考分</span>
        </div>

        <div class="meta">
          <span>{{ row.studentClass || '未分班' }}</span>
          <span>{{ row.answerSourceDesc || row.answerSource || '-' }}</span>
          <span>{{ formatTime(row.createTime) }}</span>
        </div>
      </article>

      <MobileEmpty
        v-if="empty"
        description="暂无答卷"
        icon="Tickets"
        action-text="刷新"
        @action="load"
      />

      <MobileLoadMore
        :loading="loading"
        :finished="finished && list.length > 0"
        :empty="empty"
        @load="loadMore"
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

.item {
  cursor: pointer;
}

.item:active {
  transform: scale(0.995);
}

.student {
  font-size: 12px;
  color: #5c6180;
}

.item h3 {
  margin: 7px 0 8px;
  font-size: 15px;
}

.score-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 8px 12px;
  background: #f7f8ff;
  border-radius: 10px;
}

.score b {
  font-size: 21px;
  color: var(--ai-primary);
}

.score em {
  margin-left: 2px;
  font-size: 11px;
  font-style: normal;
  color: #8b90ab;
}

.diff {
  font-size: 11px;
  color: #8b90ab;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 9px;
  font-size: 11px;
  color: #9aa0b8;
}
</style>
