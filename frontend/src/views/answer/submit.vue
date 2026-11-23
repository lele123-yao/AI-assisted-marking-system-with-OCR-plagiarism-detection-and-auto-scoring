<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { submitOnlineAnswer } from '@/api/answer'
import { myPapers } from '@/api/student'
import { listQuestionsByPaper } from '@/api/question'
import PageHero from '@/components/PageHero.vue'
import { QUESTION_TYPE, dictItem, formatTime } from '@/utils/dict'

/** 学生在线作答：选择线上试卷 → 逐题作答 → 组装 JSON 提交 */
const route = useRoute()
const papers = ref([])
const paperId = ref(null)
const questions = ref([])
const loading = ref(false)
const submitting = ref(false)

// 答案以题目主键为 key，提交时再转换成以题号为 key 的 JSON
const answers = reactive({})

const selectedPaper = computed(() => papers.value.find((item) => item.id === paperId.value) || null)

const answeredCount = computed(
  () => questions.value.filter((item) => (answers[item.id] || '').trim()).length
)

/** 是否在允许作答时间窗内（后端会再次校验） */
const inExamWindow = computed(() => {
  const paper = selectedPaper.value
  if (!paper?.startTime || !paper?.endTime) return true
  const now = Date.now()
  return now >= new Date(paper.startTime).getTime() && now <= new Date(paper.endTime).getTime()
})

/** 作答进度文字 */
const progressText = computed(() => `${answeredCount.value} / ${questions.value.length}`)

async function loadPapers() {
  // 只加载老师下发给本人、线上作答且尚未提交的试卷
  const data = await myPapers()
  papers.value = (Array.isArray(data) ? data : [])
    .filter((paper) => paper.answerMode === 1 && !paper.answered)
    .map((paper) => ({ ...paper, id: paper.paperId }))

  const preset = Number(route.query.paperId)
  if (preset && papers.value.some((paper) => paper.id === preset)) {
    paperId.value = preset
  } else if (!paperId.value && papers.value.length) {
    paperId.value = papers.value[0].id
  }
}

async function loadQuestions() {
  if (!paperId.value) {
    questions.value = []
    return
  }
  loading.value = true
  try {
    questions.value = (await listQuestionsByPaper(paperId.value)) || []
    Object.keys(answers).forEach((key) => delete answers[key])
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
      `共 ${questions.value.length} 题，已作答 ${answeredCount.value} 题。提交后不可修改，确认提交？`,
      '提交确认',
      { type: 'warning' }
    )
  } catch {
    return
  }

  // 按题号组织 JSON，例如 {"1":"答案一","2":"答案二"}
  const content = {}
  questions.value.forEach((question) => {
    const value = (answers[question.id] || '').trim()
    if (value) content[question.questionNo] = value
  })

  submitting.value = true
  try {
    await submitOnlineAnswer({ paperId: paperId.value, answerContent: JSON.stringify(content) })
    ElMessage.success('提交成功，请等待阅卷')
    Object.keys(answers).forEach((key) => delete answers[key])
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
  <div class="page-container">
    <PageHero
      title="在线作答"
      subtitle="选择老师下发的线上试卷，逐题填写答案后一次性提交；提交后不可修改，答卷将进入 OCR 识别与 AI 阅卷流程。"
      icon="EditPen"
      tone="light"
    >
      <template #meta>
        <div class="hero-tags">
          <el-tag v-if="selectedPaper" type="info" effect="plain">
            总分 {{ selectedPaper.totalScore ?? '-' }} 分 / {{ selectedPaper.duration ?? '-' }} 分钟
          </el-tag>
          <el-tag v-if="questions.length" type="success" effect="plain">
            已作答 {{ answeredCount }} / {{ questions.length }}
          </el-tag>
        </div>
      </template>
    </PageHero>

    <div class="exam-layout">
      <!-- 左：试卷信息与题号导航 -->
      <aside class="paper-side">
        <span class="side-eyebrow">作答试卷</span>
        <el-select v-model="paperId" filterable placeholder="请选择线上试卷" class="full">
          <el-option
            v-for="paper in papers"
            :key="paper.id"
            :label="`${paper.paperTitle}（${paper.paperNo}）`"
            :value="paper.id"
          />
        </el-select>

        <div v-if="selectedPaper" class="paper-summary">
          <h3>{{ selectedPaper.paperTitle }}</h3>
          <p class="paper-no">{{ selectedPaper.paperNo }}</p>

          <div class="summary-grid">
            <div class="sg">
              <b>{{ selectedPaper.totalScore ?? '-' }}</b>
              <span>卷面总分</span>
            </div>
            <div class="sg">
              <b>{{ questions.length }}</b>
              <span>题目数</span>
            </div>
            <div class="sg">
              <b>{{ answeredCount }}</b>
              <span>已作答</span>
            </div>
            <div class="sg">
              <b>{{ Math.max(questions.length - answeredCount, 0) }}</b>
              <span>未作答</span>
            </div>
          </div>

          <div class="summary-window" :class="{ error: !inExamWindow }">
            <span class="window-label">{{ inExamWindow ? '考试时间' : '不在考试时间' }}</span>
            <span>{{ formatTime(selectedPaper.startTime) }} 至 {{ formatTime(selectedPaper.endTime) }}</span>
          </div>
        </div>

        <div v-if="questions.length" class="number-grid">
          <span class="group-title">题号导航</span>
          <div class="numbers">
            <span
              v-for="question in questions"
              :key="question.id"
              class="q-dot"
              :class="{ done: (answers[question.id] || '').trim() }"
            >
              {{ question.questionNo }}
            </span>
          </div>
          <span class="group-tip">实心表示已填写答案</span>
        </div>

        <el-button plain class="full refresh-btn" :loading="loading" @click="loadQuestions">
          刷新题目
        </el-button>
      </aside>

      <!-- 右：作答区 -->
      <section class="sheet">
        <div class="sheet-head">
          <div>
            <span class="sheet-eyebrow">作答区</span>
            <h3>{{ questions.length ? '逐题填写答案' : '暂无题目' }}</h3>
          </div>
          <span class="sheet-progress">已作答 {{ progressText }}</span>
        </div>

        <div v-loading="loading" class="question-list">
          <article v-for="(question, index) in questions" :key="question.id" class="question-card">
            <div class="q-head">
              <span class="q-index">{{ index + 1 }}</span>
              <span class="q-title">第 {{ question.questionNo }} 题</span>
              <el-tag :type="dictItem(QUESTION_TYPE, question.questionType).type" effect="plain" size="small">
                {{ dictItem(QUESTION_TYPE, question.questionType).label }}
              </el-tag>
              <span class="q-score">{{ question.score }} 分</span>
            </div>

            <p class="q-content">{{ question.questionContent }}</p>

            <el-input
              v-model="answers[question.id]"
              type="textarea"
              :rows="4"
              maxlength="5000"
              show-word-limit
              placeholder="请输入你的答案"
            />
          </article>

          <el-empty
            v-if="!loading && !questions.length"
            description="该试卷暂无题目或尚未选择试卷"
            :image-size="90"
          />
        </div>

        <div class="submit-bar">
          <div class="submit-meta">
            <span>已作答 {{ progressText }}</span>
            <span v-if="!inExamWindow" class="warn">当前不在考试时间范围内，提交会被后端拒绝</span>
          </div>
          <el-button
            v-permission="'answer:submit'"
            type="primary"
            class="submit-btn"
            :loading="submitting"
            @click="handleSubmit"
          >
            提交答卷
          </el-button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.exam-layout {
  display: grid;
  grid-template-columns: minmax(270px, 320px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

/* 左侧试卷信息 */
.paper-side {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 22px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.side-eyebrow {
  font-size: 12px;
  color: #9aa0b8;
  letter-spacing: 1px;
}

.full {
  width: 100%;
}

.paper-summary {
  padding: 16px;
  background: #f7f8ff;
  border: 1px solid #eef1fa;
  border-radius: 14px;
}

.paper-summary h3 {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  color: #23264a;
}

.paper-no {
  margin: 0 0 14px;
  font-size: 12px;
  color: #9aa0b8;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.sg {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sg b {
  font-size: 17px;
  font-weight: 700;
  color: #23264a;
}

.sg span {
  font-size: 11px;
  color: #9aa0b8;
}

.summary-window {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-top: 12px;
  margin-top: 14px;
  font-size: 12px;
  color: #5c6280;
  border-top: 1px dashed #e0e5f4;
}

.summary-window .window-label {
  font-size: 11px;
  font-weight: 600;
  color: #2fae7a;
}

.summary-window.error .window-label {
  color: #f56c6c;
}

.number-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.group-title {
  font-size: 12px;
  font-weight: 600;
  color: #5c6280;
}

.numbers {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.q-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 28px;
  padding: 0 6px;
  font-size: 12px;
  color: #8b90ab;
  background: #f4f6fc;
  border: 1px solid #e6eaf6;
  border-radius: 8px;
}

.q-dot.done {
  color: #fff;
  background: linear-gradient(140deg, #5b6cff, #9a5bff);
  border-color: transparent;
}

.group-tip {
  font-size: 11px;
  color: #a9aec4;
}

.refresh-btn {
  height: 36px;
}

/* 右侧作答区 */
.sheet {
  padding: 22px 24px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.sheet-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-bottom: 1px solid #eef1fa;
}

.sheet-eyebrow {
  font-size: 12px;
  color: #9aa0b8;
}

.sheet-head h3 {
  margin: 4px 0 0;
  font-size: 18px;
  font-weight: 700;
  color: #23264a;
}

.sheet-progress {
  font-size: 12px;
  color: #5b6cff;
  background: #eef1ff;
  padding: 5px 12px;
  border-radius: 999px;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.question-card {
  padding: 18px 20px;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-radius: 14px;
}

.q-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.q-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(140deg, #5b6cff, #9a5bff);
  border-radius: 8px;
}

.q-title {
  font-size: 14px;
  font-weight: 600;
  color: #23264a;
}

.q-score {
  margin-left: auto;
  font-size: 13px;
  font-weight: 600;
  color: #f56c6c;
}

.q-content {
  margin: 0 0 12px;
  font-size: 14px;
  line-height: 1.8;
  color: #33364f;
  white-space: pre-wrap;
}

.submit-bar {
  position: sticky;
  bottom: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 18px;
  margin-top: 20px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 14px;
  box-shadow: 0 -6px 20px rgba(40, 50, 120, 0.06);
}

.submit-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: #9aa0b8;
}

.submit-meta .warn {
  color: #f56c6c;
}

.submit-btn {
  height: 42px;
  padding: 0 28px;
  background: linear-gradient(96deg, #5b6cff, #8a5bff);
  border: none;
  border-radius: 10px;
  box-shadow: 0 12px 26px rgba(91, 108, 255, 0.28);
}

@media (max-width: 1000px) {
  .exam-layout {
    grid-template-columns: 1fr;
  }

  .paper-side {
    position: static;
  }
}
</style>
