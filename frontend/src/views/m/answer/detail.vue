<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPaperAnswerDetail } from '@/api/paperAnswer'
import { listQuestionsByPaper } from '@/api/question'
import { getMyAnswerDetail } from '@/api/student'
import { useUserStore } from '@/store/user'
import { MARKING_STATUS, QUESTION_TYPE, REVIEW_STATUS, dictItem, formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileStatGrid from '@/components/m/MobileStatGrid.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'

/** 答卷详情：考生信息、逐题作答与评分、扫描件、OCR 全文 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const detail = ref(null)
const questions = ref([])
const ocrExpanded = ref(false)

/** 学生从「我的试卷 / 我的成绩」进入时走学生专属接口，教师端走阅卷接口 */
const isStudentView = computed(() => route.path.startsWith('/m/student/answer'))

async function load() {
  loading.value = true
  try {
    detail.value = isStudentView.value
      ? await getMyAnswerDetail(route.params.id)
      : await getPaperAnswerDetail(route.params.id)
    if (detail.value?.paperId) {
      questions.value = (await listQuestionsByPaper(detail.value.paperId)) || []
    }
  } finally {
    loading.value = false
  }
}

/** 逐题明细：优先用后端返回的 questionResults，缺失时退回试卷原题 */
const questionRows = computed(() => {
  const results = detail.value?.questionResults || []
  if (results.length) {
    return results
  }
  return questions.value.map((question) => ({
    questionId: question.id,
    questionNo: question.questionNo,
    questionType: question.questionType,
    questionContent: question.questionContent,
    standardAnswer: question.standardAnswer,
    fullScore: question.score
  }))
})

/** 学生作答是否完整（有任一题作答即视为作答过） */
const hasStudentAnswer = computed(() => questionRows.value.some((row) => row.studentAnswer))

const stats = computed(() => [
  { key: 'final', label: '教师最终分', value: detail.value?.teacherFinalScore ?? '-', icon: 'CircleCheck', tone: 'success' },
  { key: 'ai', label: 'AI 参考分', value: detail.value?.aiReferenceScore ?? '-', icon: 'DataLine', tone: 'primary' },
  {
    key: 'diff',
    label: '分差',
    value: detail.value?.scoreModified ? detail.value?.scoreDiff ?? '-' : '—',
    icon: 'Sort',
    tone: 'warning'
  },
  { key: 'count', label: '题目数', value: questions.value.length, icon: 'Tickets', tone: 'info' }
])

const imageUrl = computed(() => detail.value?.paperImgAccessUrl || detail.value?.paperImgUrl || '')

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="m-page">
    <MobileHero
      :title="detail ? `${detail.studentNo || ''} ${detail.studentName || ''}`.trim() || '答卷详情' : '答卷详情'"
      :subtitle="detail?.paperTitle || '查看答卷信息与阅卷结果'"
      icon="Tickets"
    >
      <template #actions>
        <el-button size="small" plain :loading="loading" @click="load">刷新</el-button>
      </template>
    </MobileHero>

    <div v-if="detail" class="status-row">
      <el-tag :type="dictItem(MARKING_STATUS, detail.markingStatus).type" effect="plain">
        {{ detail.markingStatusDesc || dictItem(MARKING_STATUS, detail.markingStatus).label }}
      </el-tag>
      <el-tag :type="dictItem(REVIEW_STATUS, detail.reviewStatus).type" effect="plain">
        {{ detail.reviewStatusDesc || dictItem(REVIEW_STATUS, detail.reviewStatus).label }}
      </el-tag>
      <el-button
        v-if="userStore.hasPermission('grade:review') && detail.markingStatus === 3"
        size="small"
        type="primary"
        plain
        @click="router.push('/m/grade/review')"
      >
        去复核
      </el-button>
    </div>

    <MobileStatGrid v-if="detail" :items="stats" />

    <el-alert
      v-if="detail?.markingError"
      type="error"
      show-icon
      :closable="false"
      :title="`阅卷失败原因：${detail.markingError}`"
    />

    <section v-if="detail" class="m-card">
      <div class="info-row"><span>学号</span><b>{{ detail.studentNo || '-' }}</b></div>
      <div class="info-row"><span>姓名</span><b>{{ detail.studentName || '-' }}</b></div>
      <div class="info-row"><span>班级</span><b>{{ detail.studentClass || '-' }}</b></div>
      <div class="info-row"><span>试卷</span><b>{{ detail.paperTitle || '-' }}</b></div>
      <div class="info-row"><span>答卷来源</span><b>{{ detail.answerSourceDesc || detail.answerSource || '-' }}</b></div>
      <div class="info-row"><span>导入批次</span><b>{{ detail.importBatchNo || '-' }}</b></div>
      <div class="info-row"><span>阅卷完成</span><b>{{ formatTime(detail.markTime) }}</b></div>
    </section>

    <section v-if="imageUrl" class="m-card">
      <div class="m-row-between">
        <span class="m-title">答卷扫描件</span>
        <span class="m-sub">点击查看大图</span>
      </div>
      <el-image
        :src="imageUrl"
        :preview-src-list="[imageUrl]"
        fit="contain"
        class="paper-image"
        preview-teleported
      />
    </section>

    <section class="m-card">
      <div class="m-row-between">
        <span class="m-title">OCR 识别全文</span>
        <el-button v-if="detail?.ocrText" link size="small" @click="ocrExpanded = !ocrExpanded">
          {{ ocrExpanded ? '收起' : '展开' }}
        </el-button>
      </div>
      <pre v-if="detail?.ocrText" class="ocr" :class="{ expanded: ocrExpanded }">{{ detail.ocrText }}</pre>
      <MobileEmpty v-else description="尚无 OCR 识别结果" icon="Document" />
    </section>

    <section class="m-card">
      <div class="m-row-between">
        <span class="m-title">逐题作答与评分</span>
        <span class="m-sub">{{ questionRows.length }} 题</span>
      </div>
      <el-alert
        v-if="!hasStudentAnswer && questionRows.length"
        type="info"
        :closable="false"
        show-icon
        class="answer-tip"
        title="本卷为纸质扫描阅卷，逐题作答请查看上方 OCR 全文与扫描件"
      />
      <div class="question-list">
        <div v-for="row in questionRows" :key="row.questionId || row.questionNo" class="question">
          <div class="q-head">
            <span class="q-no">第 {{ row.questionNo }} 题</span>
            <el-tag v-if="row.questionType" :type="dictItem(QUESTION_TYPE, row.questionType).type" size="small" effect="plain">
              {{ dictItem(QUESTION_TYPE, row.questionType).label }}
            </el-tag>
            <b>满分 {{ row.fullScore ?? '-' }}</b>
          </div>
          <p v-if="row.questionContent" class="q-content">{{ row.questionContent }}</p>

          <div v-if="row.studentAnswer" class="q-block student">
            <span class="q-label">考生作答</span>
            <p>{{ row.studentAnswer }}</p>
          </div>
          <div v-else-if="!detail?.ocrText" class="q-block muted">暂无该题作答记录</div>

          <div v-if="row.aiScore !== null && row.aiScore !== undefined" class="q-block ai">
            <span class="q-label">AI 参考分</span>
            <b>{{ row.aiScore }} / {{ row.fullScore ?? '-' }}</b>
            <p v-if="row.aiComment">{{ row.aiComment }}</p>
          </div>

          <div v-if="row.finalScore !== null && row.finalScore !== undefined" class="q-block final">
            <span class="q-label">最终得分</span>
            <b>{{ row.finalScore }} / {{ row.fullScore ?? '-' }}</b>
            <p v-if="row.finalComment">{{ row.finalComment }}</p>
          </div>

          <p v-if="row.standardAnswer" class="q-answer">
            <span>参考答案</span>{{ row.standardAnswer }}
          </p>
        </div>
        <MobileEmpty v-if="!questionRows.length" description="暂无题目" icon="Document" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.status-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 9px 0;
  font-size: 13px;
  border-bottom: 1px dashed #f0f2fb;
}

.info-row:last-child {
  border-bottom: none;
}

.info-row span {
  flex-shrink: 0;
  color: #8b90ab;
}

.info-row b {
  font-weight: 500;
  color: #3a3e5c;
  text-align: right;
  word-break: break-all;
}

.paper-image {
  width: 100%;
  max-height: 420px;
  margin-top: 10px;
  border-radius: 10px;
}

.ocr {
  max-height: 132px;
  padding: 12px;
  margin: 0;
  overflow: hidden;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.8;
  color: #3a3e5c;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f7f8fd;
  border-radius: 10px;
}

.ocr.expanded {
  max-height: 60vh;
  overflow-y: auto;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 10px;
}

.question {
  padding: 12px;
  background: #f8f9fe;
  border-radius: 11px;
}

.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.q-no {
  font-size: 13px;
  font-weight: 600;
  color: #23264a;
}

.q-head b {
  margin-left: auto;
  font-size: 12px;
  color: #8b90ab;
}

.q-content {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #3a3e5c;
  white-space: pre-wrap;
}

.q-answer {
  padding: 8px 10px;
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #5c6180;
  background: #fff;
  border-radius: 8px;
}

.q-answer span {
  margin-right: 6px;
  color: var(--ai-primary);
}

.answer-tip {
  margin-top: 10px;
}

.q-block {
  padding: 9px 11px;
  margin-top: 8px;
  background: #fff;
  border-radius: 9px;
}

.q-block p {
  margin: 5px 0 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: #3a3e5c;
  white-space: pre-wrap;
  word-break: break-word;
}

.q-block b {
  font-size: 13px;
  color: #23264a;
}

.q-label {
  display: inline-block;
  margin-right: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #8b90ab;
}

.q-block.ai {
  border-left: 3px solid var(--ai-primary);
}

.q-block.final {
  border-left: 3px solid #34c759;
}

.q-block.muted {
  font-size: 12px;
  color: #9aa0bb;
}
</style>
