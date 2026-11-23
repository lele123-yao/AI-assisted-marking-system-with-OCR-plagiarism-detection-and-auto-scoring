<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { myPapers } from '@/api/student'
import PageHero from '@/components/PageHero.vue'
import { ANSWER_MODE, PAPER_STATUS, dictItem, formatTime } from '@/utils/dict'

/** 学生端：老师下发给我的试卷，按作答状态分组展示 */
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

function goAnswer(row) {
  router.push({ path: '/answer/submit', query: { paperId: row.paperId } })
}

function canAnswer(row) {
  return row.answerMode === 1 && !row.answered
}

/** 状态分组的计数与筛选 */
const groups = computed(() => [
  { key: 'all', label: '全部试卷', value: list.value.length },
  { key: 'pending', label: '待作答', value: list.value.filter((row) => canAnswer(row)).length },
  { key: 'done', label: '已提交', value: list.value.filter((row) => row.answered).length },
  { key: 'offline', label: '线下作答', value: list.value.filter((row) => row.answerMode === 2).length }
])

const filteredList = computed(() => {
  const rows = list.value
  if (filter.value === 'pending') return rows.filter((row) => canAnswer(row))
  if (filter.value === 'done') return rows.filter((row) => row.answered)
  if (filter.value === 'offline') return rows.filter((row) => row.answerMode === 2)
  return rows
})

/** 卡片上的状态文案与色板 */
function statusMeta(row) {
  if (row.answered) return { label: '已提交', tone: 'done' }
  if (row.answerMode === 1) return { label: '待作答', tone: 'pending' }
  return { label: '线下作答', tone: 'offline' }
}

/** 截止时间紧迫度：72 小时内提示 */
function deadlineTone(row) {
  if (!row.deadline) return ''
  const diff = new Date(row.deadline).getTime() - Date.now()
  if (diff < 0) return 'expired'
  if (diff < 72 * 3600 * 1000) return 'urgent'
  return ''
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <PageHero
      title="我的试卷"
      subtitle="仅显示老师下发给本人（或本人所在班级）且未截止的试卷，可直接进入线上作答。"
      icon="Document"
    >
      <template #actions>
        <el-button :loading="loading" plain class="hero-btn" @click="load">刷新</el-button>
      </template>
    </PageHero>

    <!-- 状态分组筛选 -->
    <div class="filter-bar">
      <button
        v-for="item in groups"
        :key="item.key"
        class="filter-chip"
        :class="{ active: filter === item.key }"
        @click="filter = item.key"
      >
        <span>{{ item.label }}</span>
        <b>{{ item.value }}</b>
      </button>
      <span class="filter-tip">共 {{ filteredList.length }} 份试卷</span>
    </div>

    <div v-loading="loading" class="paper-grid">
      <article
        v-for="row in filteredList"
        :key="row.paperId"
        class="paper-card"
        :class="`tone-${statusMeta(row).tone}`"
      >
        <div class="paper-top">
          <span class="course-chip">{{ row.courseName || '未归属课程' }}</span>
          <span class="status-dot" :class="`dot-${statusMeta(row).tone}`">
            {{ statusMeta(row).label }}
          </span>
        </div>

        <h3 class="paper-title">{{ row.paperTitle }}</h3>
        <p class="paper-no">{{ row.paperNo }}</p>

        <div class="paper-tags">
          <el-tag
            :type="dictItem(ANSWER_MODE, row.answerMode).type"
            size="small"
            effect="plain"
          >
            {{ dictItem(ANSWER_MODE, row.answerMode).label }}
          </el-tag>
          <el-tag v-if="row.totalScore" size="small" type="info" effect="plain">
            满分 {{ row.totalScore }}
          </el-tag>
          <el-tag size="small" effect="plain" :type="row.source === 'CLASS' ? 'primary' : 'warning'">
            {{ row.source === 'CLASS' ? '班级下发' : '指定本人' }}
          </el-tag>
        </div>

        <div class="paper-meta">
          <div class="meta-row">
            <span class="meta-label">考试时间</span>
            <span class="meta-value">
              {{ row.startTime ? formatTime(row.startTime) : '-' }}
            </span>
          </div>
          <div class="meta-row">
            <span class="meta-label">截止时间</span>
            <span class="meta-value" :class="deadlineTone(row)">
              {{ row.deadline ? formatTime(row.deadline) : '不限' }}
            </span>
          </div>
        </div>

        <div class="paper-footer">
          <el-button v-if="canAnswer(row)" type="primary" class="answer-btn" @click="goAnswer(row)">
            去作答
          </el-button>
          <span v-else-if="row.answered" class="footer-state done">已完成，等待阅卷</span>
          <span v-else-if="row.answerMode === 2" class="footer-state offline">按老师要求线下完成</span>
          <el-tag v-else :type="dictItem(PAPER_STATUS, row.paperStatus).type" effect="plain">
            {{ dictItem(PAPER_STATUS, row.paperStatus).label }}
          </el-tag>
        </div>
      </article>

      <el-empty
        v-if="!loading && !filteredList.length"
        class="empty-block"
        description="当前分组暂无试卷"
        :image-size="90"
      />
    </div>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 状态筛选条 */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 14px;
  box-shadow: 0 6px 18px rgba(40, 50, 120, 0.04);
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 13px;
  color: #5c6280;
  cursor: pointer;
  background: #f6f7fd;
  border: 1px solid transparent;
  border-radius: 999px;
  transition: all 0.2s ease;
}

.filter-chip b {
  font-size: 13px;
  color: #2b2e45;
}

.filter-chip:hover {
  color: var(--ai-primary);
  border-color: #d9defb;
}

.filter-chip.active {
  color: #fff;
  background: linear-gradient(96deg, #5b6cff, #8a5bff);
  box-shadow: 0 8px 18px rgba(91, 108, 255, 0.28);
}

.filter-chip.active b {
  color: #fff;
}

.filter-tip {
  margin-left: auto;
  font-size: 12px;
  color: #9aa0b8;
}

/* 试卷卡片网格 */
.paper-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  min-height: 120px;
}

.paper-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 20px 22px 18px;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 16px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
}

.paper-card::before {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 4px;
  content: '';
  background: #dfe4f7;
}

.paper-card.tone-pending::before {
  background: linear-gradient(90deg, #5b6cff, #9a5bff);
}

.paper-card.tone-done::before {
  background: linear-gradient(90deg, #2fae7a, #6ad3a5);
}

.paper-card.tone-offline::before {
  background: #c8cde0;
}

.paper-card:hover {
  border-color: #cdd5fb;
  box-shadow: 0 18px 38px rgba(60, 70, 160, 0.12);
  transform: translateY(-3px);
}

.paper-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.course-chip {
  padding: 3px 10px;
  overflow: hidden;
  font-size: 12px;
  color: #5b6cff;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: #eef1ff;
  border-radius: 999px;
}

.status-dot {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
}

.dot-pending {
  color: #5b6cff;
}

.dot-done {
  color: #2fae7a;
}

.dot-offline {
  color: #9aa0b8;
}

.paper-title {
  margin: 2px 0 0;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.45;
  color: #23264a;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.paper-no {
  margin: 0;
  font-size: 12px;
  color: #9aa0b8;
  letter-spacing: 0.4px;
}

.paper-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.paper-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 0;
  margin-top: 2px;
  border-top: 1px dashed #e9ecf7;
  border-bottom: 1px dashed #e9ecf7;
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}

.meta-label {
  color: #9aa0b8;
}

.meta-value {
  color: #5c6280;
}

.meta-value.urgent {
  font-weight: 600;
  color: #e6a23c;
}

.meta-value.expired {
  color: #f56c6c;
}

.paper-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-height: 34px;
  margin-top: auto;
}

.answer-btn {
  width: 100%;
  height: 38px;
  background: linear-gradient(96deg, #5b6cff, #8a5bff);
  border: none;
  border-radius: 10px;
  box-shadow: 0 10px 22px rgba(91, 108, 255, 0.28);
}

.footer-state {
  font-size: 12px;
}

.footer-state.done {
  color: #2fae7a;
}

.footer-state.offline {
  color: #9aa0b8;
}

.empty-block {
  grid-column: 1 / -1;
  padding: 40px 0;
  background: #fff;
  border: 1px dashed var(--ai-border);
  border-radius: 16px;
}
</style>
