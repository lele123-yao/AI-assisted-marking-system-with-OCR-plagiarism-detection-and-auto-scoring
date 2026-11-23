<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { pagePaperAnswers } from '@/api/paperAnswer'
import { pagePapers } from '@/api/paper'
import { listQuestionsByPaper } from '@/api/question'
import { reviewGrade } from '@/api/grade'
import PageHero from '@/components/PageHero.vue'
import { MARKING_STATUS, REVIEW_STATUS, dictItem, formatTime } from '@/utils/dict'

/** 成绩复核：通过 / 改分 / 驳回 */
const papers = ref([])
const answers = ref([])
const paperId = ref(null)
const reviewFilter = ref('')
const loading = ref(false)

const dialogVisible = ref(false)
const submitting = ref(false)
const current = ref(null)
const selectedAnswer = ref(null)
const questions = ref([])
const form = reactive({ reviewAction: 1, reviewRemark: '' })
const itemScores = reactive({})

const filteredAnswers = computed(() => {
  if (reviewFilter.value === '') return answers.value
  return answers.value.filter((item) => item.reviewStatus === reviewFilter.value)
})

/** 当前所选试卷标题，答卷 VO 未返回试卷名时作为兜底展示 */
const currentPaperTitle = computed(
  () => papers.value.find((paper) => paper.id === paperId.value)?.paperTitle || ''
)

/** 列表刷新后丢弃已不在结果集中的选中项，避免右侧展示过期数据 */
watch(filteredAnswers, (rows) => {
  if (selectedAnswer.value && !rows.some((item) => item.id === selectedAnswer.value.id)) {
    selectedAnswer.value = null
  }
})

const totalScore = computed(() =>
  questions.value.reduce((sum, question) => sum + (Number(itemScores[question.id]) || 0), 0)
)

/** 复核概览：按当前已加载答卷统计，点击卡片可快捷筛选状态 */
const reviewStats = computed(() => {
  const list = answers.value
  const countBy = (status) => list.filter((item) => item.reviewStatus === status).length
  return [
    {
      label: '当前页答卷',
      value: list.length,
      icon: 'Tickets',
      color: '#409eff',
      bg: '#ecf5ff',
      hint: '最多加载 100 条'
    },
    {
      label: '待复核',
      value: countBy(0),
      icon: 'Clock',
      color: '#e6a23c',
      bg: '#fdf6ec',
      clickable: true,
      filterValue: 0
    },
    {
      label: '复核通过',
      value: countBy(1),
      icon: 'CircleCheck',
      color: '#67c23a',
      bg: '#f0f9eb',
      hint: `含改分 ${countBy(2)} 份`,
      clickable: true,
      filterValue: 1
    },
    {
      label: '复核驳回',
      value: countBy(3),
      icon: 'CircleClose',
      color: '#f56c6c',
      bg: '#fef0f0',
      clickable: true,
      filterValue: 3
    }
  ]
})

/** 点击统计卡快捷切换复核状态筛选 */
function handleStatSelect(item) {
  if (item.filterValue === undefined) return
  reviewFilter.value = item.filterValue
}

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
}

/**
 * 答卷列表 VO 暂未返回复核状态，这里按「已归档 + 最终分是否等于 AI 分」推导：
 * 已归档且改过分 → 已改分；已归档且未改分 → 复核通过；其余仍为待复核。
 * 后端补上 reviewStatus 后会自动使用接口值，推导只作为兜底。
 */
function normalizeReviewStatus(row) {
  if (row.reviewStatus !== null && row.reviewStatus !== undefined) return row
  let reviewStatus = 0
  if (row.markingStatus === 5) {
    const ai = row.aiReferenceScore
    const final = row.teacherFinalScore
    const changed = ai !== null && ai !== undefined && final !== null && final !== undefined &&
      Number(ai) !== Number(final)
    reviewStatus = changed ? 2 : 1
  }
  return { ...row, reviewStatus }
}

async function loadAnswers() {
  answers.value = []
  if (!paperId.value) return
  loading.value = true
  try {
    const data = await pagePaperAnswers({ pageNum: 1, pageSize: 100, paperId: paperId.value })
    answers.value = (data?.records || []).map(normalizeReviewStatus)
  } finally {
    loading.value = false
  }
}

async function openReview(row) {
  current.value = row
  form.reviewAction = 1
  form.reviewRemark = ''
  dialogVisible.value = true
  questions.value = (await listQuestionsByPaper(paperId.value)) || []
  Object.keys(itemScores).forEach((key) => delete itemScores[key])
  questions.value.forEach((question) => {
    itemScores[question.id] = 0
  })
}

async function submit() {
  if (submitting.value) return
  if (form.reviewAction === 3 && !form.reviewRemark.trim()) {
    ElMessage.warning('驳回时必须填写复核意见')
    return
  }

  const payload = {
    answerId: current.value.id,
    reviewAction: form.reviewAction,
    reviewRemark: form.reviewRemark
  }

  // 改分：必须提交逐题明细，最终分等于明细之和
  if (form.reviewAction === 2) {
    if (!questions.value.length) {
      ElMessage.warning('该试卷暂无题目，无法改分')
      return
    }
    for (const question of questions.value) {
      const score = Number(itemScores[question.id]) || 0
      if (score < 0 || score > Number(question.score)) {
        ElMessage.warning(`第 ${question.questionNo} 题得分需在 0 到 ${question.score} 之间`)
        return
      }
    }
    payload.finalScore = totalScore.value
    payload.items = questions.value.map((question) => ({
      questionId: question.id,
      score: Number(itemScores[question.id]) || 0
    }))
  }

  submitting.value = true
  try {
    await reviewGrade(payload)
    ElMessage.success('复核已提交')
    dialogVisible.value = false
    loadAnswers()
  } finally {
    submitting.value = false
  }
}

onMounted(loadPapers)
</script>

<template>
  <div class="page-container">
    <PageHero
      title="成绩复核"
      subtitle="教师确认 AI 参考分：可复核通过、逐题改分或驳回重批，复核通过后成绩才最终生效。"
      icon="Stamp"
    >
      <template #actions>
        <el-button :loading="loading" plain @click="loadAnswers">刷新</el-button>
      </template>
    </PageHero>

    <el-card v-loading="loading" shadow="never" class="workspace">
      <!-- 顶部紧凑状态条 -->
      <div class="summary-strip">
        <div
          v-for="item in reviewStats"
          :key="item.label"
          class="summary-item"
          :class="{
            clickable: item.clickable,
            active: item.clickable && reviewFilter === item.filterValue
          }"
          @click="item.clickable && handleStatSelect(item)"
        >
          <span class="summary-icon" :style="{ color: item.color, backgroundColor: item.bg }">
            <el-icon :size="15"><component :is="item.icon" /></el-icon>
          </span>
          <span class="summary-value">{{ item.value }}</span>
          <span class="summary-label">{{ item.label }}</span>
          <span v-if="item.hint" class="summary-hint">{{ item.hint }}</span>
        </div>
      </div>

      <!-- 试卷与复核状态筛选 -->
      <div class="workspace-toolbar">
        <span class="label">选择试卷：</span>
        <el-select v-model="paperId" filterable placeholder="请选择试卷" style="width: 340px" @change="loadAnswers">
          <el-option
            v-for="paper in papers"
            :key="paper.id"
            :label="`${paper.paperTitle}（${paper.paperNo}）`"
            :value="paper.id"
          />
        </el-select>
        <el-select v-model="reviewFilter" placeholder="复核状态" clearable style="width: 150px">
          <el-option
            v-for="(item, key) in REVIEW_STATUS"
            :key="key"
            :label="item.label"
            :value="Number(key)"
          />
        </el-select>
      </div>

      <div class="split">
        <!-- 左栏：待复核答卷队列 -->
        <aside class="queue">
          <div class="queue-head">
            <span class="queue-title">答卷队列</span>
            <span class="queue-count">{{ filteredAnswers.length }} 份</span>
          </div>

          <el-empty v-if="!filteredAnswers.length" description="暂无答卷数据" :image-size="70" />
          <div v-else class="queue-list">
            <div
              v-for="row in filteredAnswers"
              :key="row.id"
              class="queue-item"
              :class="{ active: selectedAnswer?.id === row.id }"
              @click="selectedAnswer = row"
            >
              <div class="queue-top">
                <span class="queue-name">
                  {{ row.studentName || '未命名' }}
                  <em>{{ row.studentNo }}</em>
                </span>
                <el-tag size="small" :type="dictItem(REVIEW_STATUS, row.reviewStatus).type">
                  {{ row.reviewStatusDesc || dictItem(REVIEW_STATUS, row.reviewStatus).label }}
                </el-tag>
              </div>
              <div class="queue-paper">{{ row.paperTitle || currentPaperTitle || '未关联试卷' }}</div>
              <div class="queue-bottom">
                <el-tag size="small" effect="plain" :type="dictItem(MARKING_STATUS, row.markingStatus).type">
                  {{ row.markingStatusDesc || dictItem(MARKING_STATUS, row.markingStatus).label }}
                </el-tag>
                <span class="queue-score">
                  AI <b>{{ row.aiReferenceScore ?? '-' }}</b>
                  · 最终 <b>{{ row.teacherFinalScore ?? '-' }}</b>
                </span>
              </div>
            </div>
          </div>
        </aside>

        <!-- 右栏：所选答卷摘要 -->
        <section class="detail">
          <template v-if="selectedAnswer">
            <div class="detail-head">
              <div>
                <h3 class="detail-title">{{ selectedAnswer.studentName || '未命名' }}</h3>
                <p class="detail-sub">
                  {{ selectedAnswer.studentNo }}
                  <span v-if="selectedAnswer.studentClass">· {{ selectedAnswer.studentClass }}</span>
                </p>
              </div>
              <el-button
                v-permission="'grade:review'"
                type="primary"
                @click="openReview(selectedAnswer)"
              >
                开始复核
              </el-button>
            </div>

            <el-descriptions :column="2" size="small" border class="meta">
              <el-descriptions-item label="试卷" :span="2">
                {{ selectedAnswer.paperTitle || currentPaperTitle || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="AI 参考分">
                <span class="score-strong">{{ selectedAnswer.aiReferenceScore ?? '-' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="教师最终分">
                <span class="score-strong">{{ selectedAnswer.teacherFinalScore ?? '-' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="阅卷状态">
                <el-tag size="small" :type="dictItem(MARKING_STATUS, selectedAnswer.markingStatus).type">
                  {{ selectedAnswer.markingStatusDesc || dictItem(MARKING_STATUS, selectedAnswer.markingStatus).label }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="复核状态">
                <el-tag size="small" :type="dictItem(REVIEW_STATUS, selectedAnswer.reviewStatus).type">
                  {{ selectedAnswer.reviewStatusDesc || dictItem(REVIEW_STATUS, selectedAnswer.reviewStatus).label }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="复核人">
                {{ selectedAnswer.reviewerName || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="复核时间">
                {{ formatTime(selectedAnswer.reviewTime) }}
              </el-descriptions-item>
              <el-descriptions-item label="提交时间" :span="2">
                {{ formatTime(selectedAnswer.createTime) }}
              </el-descriptions-item>
            </el-descriptions>
          </template>
          <div v-else class="detail-empty">
            <el-empty description="请选择左侧答卷查看复核信息" :image-size="90" />
          </div>
        </section>
      </div>
    </el-card>

    <!-- 复核弹窗 -->
    <el-dialog v-model="dialogVisible" title="成绩复核" width="720px" destroy-on-close>
      <el-descriptions v-if="current" :column="3" border class="mb-16">
        <el-descriptions-item label="学号">{{ current.studentNo }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ current.studentName }}</el-descriptions-item>
        <el-descriptions-item label="AI参考分">{{ current.aiReferenceScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="教师最终分">{{ current.teacherFinalScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="复核状态">
          {{ current.reviewStatusDesc || dictItem(REVIEW_STATUS, current.reviewStatus).label }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-width="100px">
        <el-form-item label="复核动作">
          <el-radio-group v-model="form.reviewAction">
            <el-radio :value="1">复核通过</el-radio>
            <el-radio :value="2">改分</el-radio>
            <el-radio :value="3">驳回重批</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 改分：逐题录入 -->
        <template v-if="form.reviewAction === 2">
          <el-table :data="questions" border size="small" max-height="300">
            <el-table-column prop="questionNo" label="题号" width="80" />
            <el-table-column prop="questionContent" label="题干" min-width="220" show-overflow-tooltip />
            <el-table-column prop="score" label="满分" width="80" />
            <el-table-column label="得分" width="130">
              <template #default="{ row }">
                <el-input-number
                  v-model="itemScores[row.id]"
                  :min="0"
                  :max="Number(row.score)"
                  :precision="1"
                  size="small"
                  style="width: 110px"
                />
              </template>
            </el-table-column>
          </el-table>
          <div class="total">合计：{{ totalScore }} 分</div>
        </template>

        <el-form-item label="复核意见" class="mt-16">
          <el-input
            v-model="form.reviewRemark"
            type="textarea"
            :rows="3"
            maxlength="256"
            show-word-limit
            :placeholder="form.reviewAction === 3 ? '驳回时必填' : '选填'"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交复核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.workspace :deep(.el-card__body) {
  padding: 0;
}

/* 顶部紧凑状态条 */
.summary-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  background: var(--ai-gradient-soft);
  border-bottom: 1px solid var(--ai-border);
}

.summary-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 14px 5px 6px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 999px;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.summary-item.clickable {
  cursor: pointer;
}

.summary-item.clickable:hover {
  border-color: #c9d0ff;
  box-shadow: 0 4px 12px rgba(91, 108, 255, 0.12);
}

.summary-item.active {
  background: #eef1ff;
  border-color: var(--ai-primary);
  box-shadow: 0 0 0 2px rgba(91, 108, 255, 0.14);
}

.summary-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  flex-shrink: 0;
}

.summary-value {
  font-size: 16px;
  font-weight: 600;
  color: #2b2e45;
}

.summary-label {
  font-size: 12px;
  color: #8a90ad;
}

.summary-hint {
  padding-left: 6px;
  font-size: 11px;
  color: #b0b5cc;
  border-left: 1px solid #e4e7f5;
}

.workspace-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--ai-border);
}

.label {
  color: #606266;
}

/* 左右分栏 */
.split {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 20px;
  padding: 16px 20px 20px;
}

.queue {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-right: 20px;
  border-right: 1px solid var(--ai-border);
}

.queue-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 2px;
}

.queue-title {
  font-size: 13px;
  font-weight: 600;
  color: #2b2e45;
}

.queue-count {
  font-size: 12px;
  color: #8a90ad;
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 560px;
  min-height: 200px;
  overflow-y: auto;
  padding-right: 4px;
}

.queue-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 10px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.queue-item:hover {
  transform: translateY(-1px);
  border-color: #c9d0ff;
  box-shadow: 0 6px 16px rgba(91, 108, 255, 0.1);
}

.queue-item.active {
  background: #f6f7ff;
  border-color: var(--ai-primary);
  box-shadow: 0 0 0 2px rgba(91, 108, 255, 0.14);
}

.queue-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.queue-name {
  font-size: 14px;
  font-weight: 600;
  color: #2b2e45;
}

.queue-name em {
  margin-left: 6px;
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
  color: #8a90ad;
}

.queue-paper {
  overflow: hidden;
  font-size: 12px;
  color: #8a90ad;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue-bottom {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.queue-score {
  font-size: 12px;
  color: #6b72a0;
}

.queue-score b {
  color: #2b2e45;
}

/* 右栏：答卷摘要 */
.detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.detail-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.detail-title {
  margin: 0 0 4px;
  font-size: 18px;
  color: #2b2e45;
}

.detail-sub {
  margin: 0;
  font-size: 12px;
  color: #8a90ad;
}

.meta :deep(.el-descriptions__label) {
  width: 92px;
}

.score-strong {
  font-size: 15px;
  font-weight: 600;
  color: var(--ai-primary);
}

.detail-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
}

.mb-16 {
  margin-bottom: 16px;
}

.mt-16 {
  margin-top: 16px;
}

.total {
  margin-top: 10px;
  text-align: right;
  font-weight: 600;
  color: #f56c6c;
}

@media (max-width: 1100px) {
  .split {
    grid-template-columns: 1fr;
  }

  .queue {
    padding-right: 0;
    padding-bottom: 16px;
    border-right: none;
    border-bottom: 1px solid var(--ai-border);
  }

  .queue-list {
    max-height: 340px;
  }
}
</style>
