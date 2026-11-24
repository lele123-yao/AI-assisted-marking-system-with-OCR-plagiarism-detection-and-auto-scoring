<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getPaperAnswerDetail, pagePaperAnswers, saveTeacherScore } from '@/api/paperAnswer'
import { listQuestionsByPaper } from '@/api/question'
import { useMobileList } from '@/composables/useMobileList'
import { MARKING_STATUS, QUESTION_TYPE, dictItem } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileLoadMore from '@/components/m/MobileLoadMore.vue'

/** 移动端教师录分：答卷队列 + 逐题得分，底部吸底显示合计 */
const fetchAnswers = (params) => {
  const payload = { ...params }
  if (payload.markingStatus === '' || payload.markingStatus === null) delete payload.markingStatus
  return pagePaperAnswers(payload)
}

// 默认优先展示待教师确认（3）的答卷
const { query, list, loading, finished, load, refresh, loadMore, search } = useMobileList(
  fetchAnswers,
  { markingStatus: 3 }
)

const status = ref(3)
const selected = ref(null)
const detail = ref(null)
const questions = ref([])
const resultMap = ref({})
const scores = reactive({})
const scoreLoading = ref(false)
const saving = ref(false)

const filters = [
  { value: 3, label: '待确认' },
  { value: '', label: '全部' }
]

const totalScore = computed(() =>
  questions.value.reduce((sum, question) => sum + (Number(scores[question.id]) || 0), 0)
)

const paperTotalScore = computed(() =>
  questions.value.reduce((sum, question) => sum + Number(question.score || 0), 0)
)

function pickStatus(value) {
  status.value = value
  query.markingStatus = value
  search()
}

async function selectAnswer(row) {
  selected.value = row
  detail.value = null
  questions.value = []
  resultMap.value = {}
  Object.keys(scores).forEach((key) => delete scores[key])
  scoreLoading.value = true
  try {
    detail.value = await getPaperAnswerDetail(row.id)
    const paperId = detail.value?.paperId
    questions.value = paperId ? (await listQuestionsByPaper(paperId)) || [] : []

    // 逐题结果按 questionId 建索引，用于回填教师分数与展示 AI 参考分
    const map = {}
    ;(detail.value?.questionResults || []).forEach((item) => {
      map[item.questionId] = item
    })
    resultMap.value = map

    questions.value.forEach((question) => {
      const result = map[question.id]
      scores[question.id] = result?.finalScore !== null && result?.finalScore !== undefined
        ? Number(result.finalScore)
        : 0
    })
  } finally {
    scoreLoading.value = false
  }
}

async function save() {
  if (saving.value) return
  if (!selected.value) {
    ElMessage.warning('请先选择答卷')
    return
  }
  if (!questions.value.length) {
    ElMessage.warning('该试卷暂无题目，无法录分')
    return
  }

  // 逐题不得超过该题满分
  for (const question of questions.value) {
    const score = Number(scores[question.id]) || 0
    if (score < 0 || score > Number(question.score)) {
      ElMessage.warning(`第 ${question.questionNo} 题得分需在 0 到 ${question.score} 之间`)
      return
    }
  }
  if (totalScore.value > paperTotalScore.value) {
    ElMessage.warning(`合计得分不能超过试卷满分 ${paperTotalScore.value}`)
    return
  }

  saving.value = true
  try {
    await saveTeacherScore(selected.value.id, {
      items: questions.value.map((question) => ({
        questionId: question.id,
        score: Number(scores[question.id]) || 0
      })),
      teacherFinalScore: totalScore.value
    })
    ElMessage.success('成绩已保存')
    await refresh()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="教师录分"
      subtitle="按题录入得分，系统自动合计教师最终分，可直接对照 AI 参考分"
      icon="EditPen"
    >
      <template #actions>
        <el-button size="small" plain :loading="loading" @click="refresh">刷新</el-button>
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

    <div v-loading="loading" class="answer-list">
      <article
        v-for="row in list"
        :key="row.id"
        class="m-card answer"
        :class="{ active: selected?.id === row.id }"
        @click="selectAnswer(row)"
      >
        <div class="m-row-between">
          <span class="student">{{ row.studentNo || '-' }} {{ row.studentName || '' }}</span>
          <el-tag :type="dictItem(MARKING_STATUS, row.markingStatus).type" size="small" effect="plain">
            {{ row.markingStatusDesc || dictItem(MARKING_STATUS, row.markingStatus).label }}
          </el-tag>
        </div>
        <div class="m-row-between answer-foot">
          <span class="m-sub">{{ row.studentClass || '未分班' }}</span>
          <span class="score">AI {{ row.aiReferenceScore ?? '-' }} / 最终 {{ row.teacherFinalScore ?? '-' }}</span>
        </div>
      </article>

      <MobileEmpty
        v-if="!loading && !list.length"
        description="当前分组暂无答卷"
        icon="Tickets"
        action-text="刷新"
        @action="refresh"
      />

      <MobileLoadMore
        :loading="loading"
        :finished="finished && list.length > 0"
        :empty="!loading && !list.length"
        @load="loadMore"
      />
    </div>

    <section v-if="selected" v-loading="scoreLoading" class="m-card sheet">
      <div class="m-row-between">
        <span class="m-title">{{ selected.studentName || '未命名考生' }} · {{ selected.studentNo }}</span>
        <el-tag
          v-if="detail?.aiReferenceScore !== null && detail?.aiReferenceScore !== undefined"
          type="primary"
          effect="plain"
          size="small"
        >
          AI 参考 {{ detail.aiReferenceScore }}
        </el-tag>
      </div>
      <p v-if="detail?.paperTitle" class="m-sub sheet-paper">{{ detail.paperTitle }}</p>

      <div v-if="questions.length" class="questions">
        <div v-for="question in questions" :key="question.id" class="question">
          <div class="q-head">
            <span class="q-no">第 {{ question.questionNo }} 题</span>
            <el-tag :type="dictItem(QUESTION_TYPE, question.questionType).type" size="small" effect="plain">
              {{ dictItem(QUESTION_TYPE, question.questionType).label }}
            </el-tag>
            <span class="q-ai">AI {{ resultMap[question.id]?.aiScore ?? '-' }} / 满分 {{ question.score }}</span>
          </div>
          <p class="q-content">{{ question.questionContent }}</p>
          <div class="q-score">
            <span class="m-label">得分</span>
            <el-input-number
              v-model="scores[question.id]"
              :min="0"
              :max="Number(question.score)"
              :precision="1"
              size="small"
              controls-position="right"
            />
          </div>
        </div>
      </div>
      <MobileEmpty v-else description="该试卷暂无题目" icon="EditPen" />

      <div class="m-bottom-bar">
        <span class="m-total">合计 <b>{{ totalScore }}</b> / {{ paperTotalScore }} 分</span>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!questions.length"
          @click="save"
        >
          保存成绩
        </el-button>
      </div>
    </section>

    <MobileEmpty
      v-else
      description="点击上方答卷开始逐题录分"
      icon="EditPen"
    />
  </div>
</template>

<style scoped>
.answer-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 80px;
}

.answer {
  cursor: pointer;
}

.answer.active {
  border-color: #b9c4fb;
  box-shadow: 0 0 0 2px rgba(91, 108, 255, 0.14);
}

.student {
  font-size: 13px;
  font-weight: 600;
  color: #23264a;
}

.answer-foot {
  margin-top: 8px;
}

.score {
  font-size: 11px;
  color: #8b90ab;
}

.sheet-paper {
  margin: 6px 0 0;
}

.questions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.question {
  padding: 12px;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-radius: 11px;
}

.q-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
}

.q-no {
  font-size: 13px;
  font-weight: 600;
  color: #23264a;
}

.q-ai {
  margin-left: auto;
  font-size: 11px;
  color: #8b90ab;
}

.q-content {
  margin: 8px 0 10px;
  font-size: 13px;
  line-height: 1.7;
  color: #3a3e5c;
  white-space: pre-wrap;
  word-break: break-word;
}

.q-score {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 10px;
  border-top: 1px dashed #eef1fa;
}

.q-score :deep(.el-input-number) {
  width: 132px;
}

.sheet .m-bottom-bar {
  margin-top: 14px;
  margin-bottom: -14px;
}
</style>
