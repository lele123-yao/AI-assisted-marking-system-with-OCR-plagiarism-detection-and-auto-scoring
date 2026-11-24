<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { submitOnlineAnswer } from '@/api/answer'
import { myPapers } from '@/api/student'
import { listQuestionsByPaper } from '@/api/question'
import { QUESTION_TYPE, dictItem, formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'

/** 学生在线作答：选择线上试卷 → 逐题作答 → 一次性提交 */
const route = useRoute()
const router = useRouter()

const papers = ref([])
const paperId = ref(null)
const questions = ref([])
const loading = ref(false)
const submitting = ref(false)
const answers = reactive({})

const selectedPaper = computed(
  () => papers.value.find((item) => item.id === paperId.value) || null
)

const answeredCount = computed(
  () => questions.value.filter((item) => (answers[item.id] || '').trim()).length
)

const inExamWindow = computed(() => {
  const paper = selectedPaper.value
  if (!paper?.startTime || !paper?.endTime) return true
  const now = Date.now()
  return now >= new Date(paper.startTime).getTime() && now <= new Date(paper.endTime).getTime()
})

async function loadPapers() {
  const data = await myPapers()
  papers.value = (Array.isArray(data) ? data : [])
    .filter((paper) => paper.answerMode === 1 && !paper.answered)
    .map((paper) => ({ ...paper, id: paper.paperId }))

  const preset = Number(route.query.paperId)
  if (preset && papers.value.some((paper) => paper.id === preset)) {
    paperId.value = preset
  } else if (papers.value.length) {
    paperId.value = papers.value[0].id
  }
}

async function loadQuestions() {
  Object.keys(answers).forEach((key) => delete answers[key])
  if (!paperId.value) {
    questions.value = []
    return
  }
  loading.value = true
  try {
    questions.value = (await listQuestionsByPaper(paperId.value)) || []
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (submitting.value) return
  if (!paperId.value) {
    ElMessage.warning('请先选择试卷')
    return
  }
  if (!inExamWindow.value) {
    ElMessage.warning('当前不在考试时间范围内，无法提交')
    return
  }
  if (!answeredCount.value) {
    ElMessage.warning('请至少作答一道题')
    return
  }

  try {
    await ElMessageBox.confirm(
      `共 ${questions.value.length} 题，已作答 ${answeredCount.value} 题，提交后不可修改。确认提交？`,
      '提交确认',
      { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '继续作答' }
    )
  } catch {
    return
  }

  const content = {}
  questions.value.forEach((question) => {
    const value = (answers[question.id] || '').trim()
    if (value) content[question.questionNo] = value
  })

  submitting.value = true
  try {
    await submitOnlineAnswer({ paperId: paperId.value, answerContent: JSON.stringify(content) })
    ElMessage.success('提交成功，请等待阅卷')
    router.replace('/m/student/papers')
  } finally {
    submitting.value = false
  }
}

watch(paperId, loadQuestions)

onMounted(async () => {
  await loadPapers()
  loadQuestions()
})
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="在线作答"
      subtitle="提交后不可修改，答卷将进入 OCR 识别与 AI 阅卷流程"
      icon="EditPen"
    >
      <template #actions>
        <el-button size="small" plain :loading="loading" @click="loadQuestions">刷新</el-button>
      </template>
    </MobileHero>

    <section v-if="papers.length" class="m-card">
      <span class="m-label">选择试卷</span>
      <el-select v-model="paperId" placeholder="请选择线上试卷" class="paper-select">
        <el-option
          v-for="paper in papers"
          :key="paper.id"
          :label="`${paper.paperTitle}（${paper.paperNo}）`"
          :value="paper.id"
        />
      </el-select>

      <div v-if="selectedPaper" class="summary">
        <div><b>{{ selectedPaper.totalScore ?? '-' }}</b><span>卷面总分</span></div>
        <div><b>{{ questions.length }}</b><span>题目数</span></div>
        <div><b>{{ answeredCount }}</b><span>已作答</span></div>
        <div><b>{{ Math.max(questions.length - answeredCount, 0) }}</b><span>未作答</span></div>
      </div>

      <div v-if="selectedPaper?.startTime" class="window" :class="{ error: !inExamWindow }">
        <el-icon><Clock /></el-icon>
        <span>{{ inExamWindow ? '考试时间' : '不在考试时间' }}</span>
        <small>{{ formatTime(selectedPaper.startTime) }} 至 {{ formatTime(selectedPaper.endTime) }}</small>
      </div>
    </section>

    <div v-loading="loading" class="questions">
      <article v-for="(question, index) in questions" :key="question.id" class="m-card question">
        <div class="q-head">
          <span class="q-index">{{ index + 1 }}</span>
          <span class="q-title">第 {{ question.questionNo }} 题</span>
          <el-tag :type="dictItem(QUESTION_TYPE, question.questionType).type" size="small" effect="plain">
            {{ dictItem(QUESTION_TYPE, question.questionType).label }}
          </el-tag>
          <b class="q-score">{{ question.score }} 分</b>
        </div>

        <p class="q-content">{{ question.questionContent }}</p>

        <el-input
          v-model="answers[question.id]"
          type="textarea"
          :rows="4"
          maxlength="5000"
          show-word-limit
          placeholder="请输入作答内容"
        />
      </article>

      <MobileEmpty
        v-if="!loading && !questions.length"
        :description="papers.length ? '该试卷暂无题目' : '暂无可作答的线上试卷'"
        icon="EditPen"
        action-text="返回我的试卷"
        @action="router.replace('/m/student/papers')"
      />
    </div>

    <div v-if="questions.length" class="m-bottom-bar">
      <span class="m-total">已作答 <b>{{ answeredCount }}</b> / {{ questions.length }}</span>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">提交答卷</el-button>
    </div>
  </div>
</template>

<style scoped>
.paper-select {
  width: 100%;
  margin-top: 8px;
}

.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  padding: 12px 0;
  margin-top: 10px;
  text-align: center;
  border-top: 1px dashed #f0f2fb;
}

.summary b {
  display: block;
  font-size: 18px;
  color: #23264a;
}

.summary span {
  font-size: 11px;
  color: #9aa0b8;
}

.window {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 12px;
  font-size: 12px;
  color: #5c6180;
  background: #f4f6ff;
  border-radius: 10px;
}

.window small {
  margin-left: auto;
  color: #8b90ab;
}

.window.error {
  color: #f56c6c;
  background: #fef0f0;
}

.questions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100px;
}

.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.q-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ai-primary);
  background: #eef1ff;
  border-radius: 7px;
}

.q-title {
  font-size: 14px;
  font-weight: 600;
  color: #23264a;
}

.q-score {
  margin-left: auto;
  font-size: 12px;
  color: #8b90ab;
}

.q-content {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.75;
  color: #3a3e5c;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
