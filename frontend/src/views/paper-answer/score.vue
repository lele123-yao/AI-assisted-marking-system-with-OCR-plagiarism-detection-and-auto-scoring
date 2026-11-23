<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getPaperAnswerDetail, pagePaperAnswers, saveTeacherScore } from '@/api/paperAnswer'
import { pagePapers } from '@/api/paper'
import { listQuestionsByPaper } from '@/api/question'
import PageHero from '@/components/PageHero.vue'
import { QUESTION_TYPE, dictItem, formatTime } from '@/utils/dict'

/** 教师分数录入：选试卷 → 选答卷 → 逐题录分 → 提交确认 */
const papers = ref([])
const answers = ref([])
const questions = ref([])
const detail = ref(null)

const form = reactive({ paperId: null, answerId: null })
const scores = reactive({})
/** 教师逐题评语，提交时与分数一起保存到 exam_answer_item.final_comment */
const comments = reactive({})
/** 逐题结果（AI 参考分/评语 + 教师最终分/评语），按 questionId 索引 */
const resultMap = ref({})
const loading = ref(false)
const submitting = ref(false)
const answerLoading = ref(false)

/** 自动合计总分（后端要求 teacherFinalScore 等于明细之和） */
const totalScore = computed(() =>
  questions.value.reduce((sum, question) => sum + (Number(scores[question.id]) || 0), 0)
)

const paperTotalScore = computed(() =>
  Number(detail.value?.paperTotalScore || questions.value.reduce((sum, q) => sum + Number(q.score || 0), 0))
)

const selectedPaper = computed(() => papers.value.find((item) => item.id === form.paperId) || null)

/** 录分进度：已给分的题目数 */
const gradedCount = computed(
  () => questions.value.filter((question) => Number(scores[question.id]) > 0).length
)

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
}

async function loadAnswers() {
  form.answerId = null
  detail.value = null
  questions.value = []
  if (!form.paperId) return
  loading.value = true
  try {
    const data = await pagePaperAnswers({ pageNum: 1, pageSize: 100, paperId: form.paperId })
    answers.value = data?.records || []
  } finally {
    loading.value = false
  }
}

async function loadAnswerDetail() {
  if (!form.answerId) return
  answerLoading.value = true
  try {
    detail.value = await getPaperAnswerDetail(form.answerId)
    questions.value = (await listQuestionsByPaper(detail.value?.paperId || form.paperId)) || []

    // 逐题结果按 questionId 建索引，用于回填 AI 参考分/评语与教师已保存的结果
    resultMap.value = {}
    ;(detail.value?.questionResults || []).forEach((item) => {
      resultMap.value[item.questionId] = item
    })

    Object.keys(scores).forEach((key) => delete scores[key])
    Object.keys(comments).forEach((key) => delete comments[key])
    questions.value.forEach((question) => {
      const result = resultMap.value[question.id]
      // 已有教师最终分则回填，方便教师在此基础上修改；否则从 0 开始
      scores[question.id] = result?.finalScore !== null && result?.finalScore !== undefined
        ? Number(result.finalScore)
        : 0
      comments[question.id] = result?.finalComment || ''
    })

    // 已存在教师最终分时给出提示，避免误覆盖
    if (detail.value?.teacherFinalScore !== null && detail.value?.teacherFinalScore !== undefined) {
      ElMessage.warning('该答卷已有教师最终分，再次提交将覆盖原成绩')
    }
  } finally {
    answerLoading.value = false
  }
}

async function submit() {
  if (submitting.value) return
  if (!form.answerId) {
    ElMessage.warning('请先选择答卷')
    return
  }
  if (!questions.value.length) {
    ElMessage.warning('该试卷暂无题目，无法录分')
    return
  }

  // 逐题校验不得超过该题满分
  for (const question of questions.value) {
    const score = Number(scores[question.id]) || 0
    if (score < 0 || score > Number(question.score)) {
      ElMessage.warning(`第 ${question.questionNo} 题得分需在 0 到 ${question.score} 之间`)
      return
    }
  }

  submitting.value = true
  try {
    await saveTeacherScore(form.answerId, {
      items: questions.value.map((question) => ({
        questionId: question.id,
        score: Number(scores[question.id]) || 0,
        comment: comments[question.id] || ''
      })),
      teacherFinalScore: totalScore.value
    })
    ElMessage.success('成绩已保存')
  } finally {
    submitting.value = false
  }
}

onMounted(loadPapers)
</script>

<template>
  <div class="page-container">
    <PageHero
      title="教师录分"
      subtitle="先选择试卷与考生答卷，再按题录入得分；系统自动合计总分，教师可直接采纳 AI 参考分。"
      icon="EditPen"
      tone="light"
    >
      <template #meta>
        <div class="hero-tags">
          <el-tag v-if="selectedPaper" effect="plain">
            {{ selectedPaper.paperTitle }}（{{ selectedPaper.paperNo }}）
          </el-tag>
          <el-tag v-if="detail?.studentNo" type="success" effect="plain">
            {{ detail.studentNo }} {{ detail.studentName || '' }}
          </el-tag>
          <el-tag v-if="detail" type="info" effect="plain">
            AI 参考分 {{ detail.aiReferenceScore ?? '-' }}
          </el-tag>
        </div>
      </template>
    </PageHero>

    <div class="grade-workspace">
      <!-- 左：答卷选择 -->
      <aside class="picker">
        <div class="picker-head">
          <span class="picker-eyebrow">答卷队列</span>
          <h3>{{ answers.length }} 位考生</h3>
        </div>

        <el-select
          v-model="form.paperId"
          filterable
          placeholder="请选择试卷"
          class="full mb-12"
          @change="loadAnswers"
        >
          <el-option
            v-for="paper in papers"
            :key="paper.id"
            :label="`${paper.paperTitle}（${paper.paperNo}）`"
            :value="paper.id"
          />
        </el-select>

        <div v-loading="loading" class="answer-list">
          <button
            v-for="item in answers"
            :key="item.id"
            class="answer-option"
            :class="{ active: form.answerId === item.id }"
            @click="form.answerId = item.id; loadAnswerDetail()"
          >
            <span class="option-avatar">{{ (item.studentName || item.studentNo || '?').slice(0, 1) }}</span>
            <span class="option-main">
              <b>{{ item.studentName || '未命名考生' }}</b>
              <span>{{ item.studentNo }}</span>
            </span>
            <span class="option-score">
              <b>{{ item.aiReferenceScore ?? '-' }}</b>
              <span>AI 分</span>
            </span>
          </button>

          <el-empty
            v-if="!loading && !answers.length"
            description="请先选择试卷"
            :image-size="70"
          />
        </div>
      </aside>

      <!-- 右：评分单 -->
      <section v-loading="answerLoading" class="sheet">
        <template v-if="detail">
          <!-- 考生信息条 -->
          <div class="student-bar">
            <span class="student-avatar">{{ (detail.studentName || detail.studentNo || '?').slice(0, 1) }}</span>
            <div class="student-main">
              <b>{{ detail.studentName || '未命名考生' }}</b>
              <span>
                {{ detail.studentNo }}
                <em v-if="detail.studentClass">· {{ detail.studentClass }}</em>
                <em>· {{ detail.markingStatusDesc || '待确认' }}</em>
              </span>
            </div>
            <div class="student-metrics">
              <div class="sm">
                <b>{{ detail.aiReferenceScore ?? '-' }}</b>
                <span>AI 参考分</span>
              </div>
              <div class="sm">
                <b>{{ detail.teacherFinalScore ?? '-' }}</b>
                <span>已有最终分</span>
              </div>
            </div>
            <el-link
              v-if="detail.paperImgAccessUrl"
              :href="detail.paperImgAccessUrl"
              target="_blank"
              type="primary"
              class="scan-link"
            >
              查看扫描件
            </el-link>
            <span class="submit-time">提交 {{ formatTime(detail.submitTime) }}</span>
          </div>

          <!-- 逐题卡片 -->
          <div class="question-cards">
            <article
              v-for="question in questions"
              :key="question.id"
              class="question-card"
            >
              <div class="q-head">
                <span class="q-no">{{ question.questionNo }}</span>
                <el-tag :type="dictItem(QUESTION_TYPE, question.questionType).type" size="small" effect="plain">
                  {{ dictItem(QUESTION_TYPE, question.questionType).label }}
                </el-tag>
                <span class="q-full">满分 {{ question.score }}</span>
                <span class="q-ai">AI 参考 {{ resultMap[question.id]?.aiScore ?? '-' }}</span>
              </div>

              <p class="q-content">{{ question.questionContent }}</p>

              <div v-if="resultMap[question.id]?.aiComment" class="q-ai-comment">
                <span class="ai-label">AI 评语</span>
                <p>{{ resultMap[question.id].aiComment }}</p>
              </div>

              <div class="q-input-row">
                <div class="q-score">
                  <label>得分</label>
                  <el-input-number
                    v-model="scores[question.id]"
                    :min="0"
                    :max="Number(question.score)"
                    :precision="1"
                    size="small"
                    controls-position="right"
                  />
                </div>
                <div class="q-comment">
                  <label>教师评语</label>
                  <el-input
                    v-model="comments[question.id]"
                    type="textarea"
                    :autosize="{ minRows: 1, maxRows: 3 }"
                    size="small"
                    maxlength="1000"
                    placeholder="可修改 AI 评语"
                  />
                </div>
              </div>
            </article>

            <el-empty v-if="!questions.length" description="该试卷暂无题目" :image-size="80" />
          </div>

          <!-- 底部合计 -->
          <div class="sheet-footer">
            <div class="footer-progress">
              <span>录分进度 {{ gradedCount }} / {{ questions.length }} 题</span>
              <div class="progress-bar">
                <span
                  class="progress-fill"
                  :style="{ width: `${questions.length ? (gradedCount / questions.length) * 100 : 0}%` }"
                />
              </div>
            </div>
            <div class="footer-total">
              <span>合计 <b>{{ totalScore }}</b> / {{ paperTotalScore }} 分</span>
              <el-button
                v-permission="'paper-answer:score'"
                type="primary"
                :loading="submitting"
                @click="submit"
              >
                提交成绩
              </el-button>
            </div>
          </div>
        </template>

        <div v-else class="sheet-placeholder">
          <div class="placeholder-icon">
            <el-icon :size="30"><EditPen /></el-icon>
          </div>
          <h3>选择一份答卷开始录分</h3>
          <p>左侧选择试卷与考生后，这里会展示逐题评分单，并自动合计教师最终分。</p>
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

.grade-workspace {
  display: grid;
  grid-template-columns: minmax(280px, 330px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

/* 左侧队列 */
.picker {
  position: sticky;
  top: 16px;
  padding: 22px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.picker-eyebrow {
  font-size: 12px;
  color: #9aa0b8;
  letter-spacing: 1px;
}

.picker-head h3 {
  margin: 4px 0 16px;
  font-size: 17px;
  font-weight: 700;
  color: #23264a;
}

.full {
  width: 100%;
}

.mb-12 {
  margin-bottom: 14px;
}

.answer-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 520px;
  overflow-y: auto;
}

.answer-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-radius: 12px;
  transition: all 0.2s ease;
}

.answer-option:hover {
  border-color: #d5dcfa;
}

.answer-option.active {
  background: linear-gradient(90deg, #eef1ff, #f7f3ff);
  border-color: #b9c4fb;
  box-shadow: 0 6px 16px rgba(91, 108, 255, 0.12);
}

.option-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(140deg, #5b6cff, #9a5bff);
  border-radius: 10px;
}

.option-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.option-main b {
  overflow: hidden;
  font-size: 13px;
  color: #2b2e45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.option-main span {
  font-size: 11px;
  color: #9aa0b8;
}

.option-score {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
  gap: 1px;
}

.option-score b {
  font-size: 14px;
  color: #5b6cff;
}

.option-score span {
  font-size: 10px;
  color: #a9aec4;
}

/* 右侧评分单 */
.sheet {
  padding: 22px 24px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.student-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  margin-bottom: 18px;
  background: #f7f8ff;
  border: 1px solid #eef1fa;
  border-radius: 14px;
}

.student-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(140deg, #5b6cff, #9a5bff);
  border-radius: 12px;
}

.student-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 120px;
}

.student-main b {
  font-size: 15px;
  color: #23264a;
}

.student-main span {
  font-size: 12px;
  color: #9aa0b8;
}

.student-main em {
  font-style: normal;
}

.student-metrics {
  display: flex;
  gap: 22px;
  margin-left: auto;
}

.sm {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.sm b {
  font-size: 17px;
  font-weight: 700;
  color: #5b6cff;
}

.sm span {
  font-size: 11px;
  color: #9aa0b8;
}

.scan-link {
  font-size: 13px;
}

.submit-time {
  font-size: 11px;
  color: #a9aec4;
}

/* 逐题卡片 */
.question-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-card {
  padding: 16px 18px;
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

.q-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 26px;
  padding: 0 8px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(140deg, #5b6cff, #9a5bff);
  border-radius: 8px;
}

.q-full,
.q-ai {
  font-size: 12px;
  color: #9aa0b8;
}

.q-ai {
  margin-left: auto;
}

.q-content {
  margin: 0 0 12px;
  font-size: 14px;
  line-height: 1.8;
  color: #33364f;
}

.q-ai-comment {
  padding: 10px 12px;
  margin-bottom: 12px;
  background: #f2f4ff;
  border-radius: 10px;
}

.ai-label {
  font-size: 11px;
  font-weight: 600;
  color: #5b6cff;
}

.q-ai-comment p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #5c6280;
  white-space: pre-wrap;
}

.q-input-row {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.q-score,
.q-comment {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.q-score label,
.q-comment label {
  font-size: 12px;
  color: #9aa0b8;
}

/* 底部合计 */
.sheet-footer {
  position: sticky;
  bottom: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  margin-top: 18px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 14px;
  box-shadow: 0 -6px 20px rgba(40, 50, 120, 0.06);
}

.footer-progress {
  flex: 1;
  min-width: 200px;
  font-size: 12px;
  color: #9aa0b8;
}

.progress-bar {
  height: 6px;
  margin-top: 8px;
  overflow: hidden;
  background: #eef1fa;
  border-radius: 999px;
}

.progress-fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #5b6cff, #9a5bff);
  border-radius: 999px;
  transition: width 0.3s ease;
}

.footer-total {
  display: flex;
  align-items: center;
  gap: 18px;
  font-size: 13px;
  color: #5c6280;
}

.footer-total b {
  font-size: 20px;
  font-weight: 700;
  color: #f56c6c;
}

/* 空态 */
.sheet-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 460px;
  text-align: center;
}

.placeholder-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 70px;
  height: 70px;
  margin-bottom: 16px;
  color: #fff;
  background: linear-gradient(150deg, #5b6cff, #9a5bff);
  border-radius: 20px;
  box-shadow: 0 16px 30px rgba(91, 108, 255, 0.28);
}

.sheet-placeholder h3 {
  margin: 0 0 8px;
  font-size: 16px;
  color: #23264a;
}

.sheet-placeholder p {
  max-width: 360px;
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: #8b90ab;
}

@media (max-width: 1000px) {
  .grade-workspace {
    grid-template-columns: 1fr;
  }

  .picker {
    position: static;
  }

  .q-input-row {
    grid-template-columns: 1fr;
  }
}
</style>
