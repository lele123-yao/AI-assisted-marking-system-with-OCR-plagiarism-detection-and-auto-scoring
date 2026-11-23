<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMarkingTaskDetail } from '@/api/mark'
import { listQuestionsByPaper } from '@/api/question'
import PageHero from '@/components/PageHero.vue'
import StatCards from '@/components/StatCards.vue'
import { chatWithAgent, reviewQuestionByAgent } from '@/api/agent'
import {
  QUESTION_TYPE,
  MARKING_STATUS,
  REVIEW_STATUS,
  dictItem,
  formatTime
} from '@/utils/dict'

/** AI 阅卷详情：原题、学生答案、AI 打分、教师最终分 */
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref(null)
const questions = ref([])

// ==================== AI 助教对话 ====================
const chatVisible = ref(false)
const chatLoading = ref(false)
const chatInput = ref('')
const chatMessages = ref([])

// ==================== 阅卷 Agent 复核 ====================
const reviewVisible = ref(false)
const reviewLoading = ref(false)
const reviewQuestion = ref(null)
const reviewResult = ref(null)

async function load() {
  loading.value = true
  try {
    detail.value = await getMarkingTaskDetail(route.params.id)
    if (detail.value?.paperId) {
      questions.value = (await listQuestionsByPaper(detail.value.paperId)) || []
    }
  } finally {
    loading.value = false
  }
}

/** 分数概览：全部取自详情接口已返回字段 */
const scoreStats = computed(() => {
  const data = detail.value
  return [
    {
      label: 'AI 参考分',
      value: data?.aiReferenceScore ?? '-',
      icon: 'Cpu',
      color: '#409eff',
      bg: '#ecf5ff'
    },
    {
      label: '教师最终分',
      value: data?.teacherFinalScore ?? '-',
      icon: 'CircleCheck',
      color: '#67c23a',
      bg: '#f0f9eb'
    },
    {
      label: '分差',
      value: data?.scoreModified ? (data?.scoreDiff ?? '-') : '-',
      icon: 'Warning',
      color: '#e6a23c',
      bg: '#fdf6ec',
      hint: data?.scoreModified ? '教师已调整 AI 分数' : '尚未改分'
    },
    {
      label: '题目数',
      value: questions.value.length,
      icon: 'Tickets',
      color: '#9254de',
      bg: '#f9f0ff'
    }
  ]
})

/** 逐题结果：题目 + AI 参考分/评语 + 教师最终分/评语 */
const questionResults = computed(() => detail.value?.questionResults || [])

/** 有题目但所有 AI 分都为空：通常是大模型未配置 Key 或异步任务尚未完成 */
const aiPending = computed(
  () =>
    questionResults.value.length > 0 &&
    questionResults.value.every((item) => item.aiScore === null || item.aiScore === undefined)
)

onMounted(load)

/** 打开 AI 助教抽屉，首次进入给一句引导 */
function openChat() {
  chatVisible.value = true
  if (!chatMessages.value.length) {
    chatMessages.value.push({
      role: 'assistant',
      content: '我可以基于当前答卷回答：某道题为什么扣分、评分依据是否合理、这道题该怎么讲评。'
    })
  }
}

/** 发送问题：把最近 6 轮对话回传给后端，服务端不存会话 */
async function sendChat() {
  const text = chatInput.value.trim()
  if (!text || chatLoading.value) {
    return
  }
  chatMessages.value.push({ role: 'user', content: text })
  chatInput.value = ''
  chatLoading.value = true
  try {
    const history = chatMessages.value
      .slice(0, -1)
      .slice(-6)
      .map((item) => ({ role: item.role, content: item.content }))
    const res = await chatWithAgent({
      answerId: Number(route.params.id),
      message: text,
      history
    })
    chatMessages.value.push({
      role: 'assistant',
      content: res?.reply || '（Agent 没有返回内容）',
      steps: res?.steps || []
    })
  } finally {
    chatLoading.value = false
  }
}

/** 对某道题发起 Agent 复核：先初评，再自检，两步结果都展示 */
async function reviewRow(row) {
  reviewQuestion.value = row
  reviewResult.value = null
  reviewVisible.value = true
  reviewLoading.value = true
  try {
    reviewResult.value = await reviewQuestionByAgent({
      answerId: Number(route.params.id),
      questionId: row.questionId
    })
  } finally {
    reviewLoading.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="page-container">
    <PageHero
      title="AI 阅卷详情"
      subtitle="查看学生答卷的原题、OCR 识别文本、AI 参考分与教师最终分的差异。"
      icon="DocumentChecked"
      tone="light"
    >
      <template #meta>
        <div v-if="detail" class="hero-meta">
          <el-tag effect="plain">{{ detail.paperTitle || '未命名试卷' }}</el-tag>
          <el-tag :type="dictItem(MARKING_STATUS, detail.markingStatus).type">
            {{ detail.markingStatusDesc || dictItem(MARKING_STATUS, detail.markingStatus).label }}
          </el-tag>
          <el-tag :type="dictItem(REVIEW_STATUS, detail.reviewStatus).type" effect="plain">
            {{ detail.reviewStatusDesc || dictItem(REVIEW_STATUS, detail.reviewStatus).label }}
          </el-tag>
        </div>
      </template>
      <template #actions>
        <el-button @click="router.back()">返回</el-button>
        <el-button type="primary" @click="load">刷新</el-button>
        <el-button type="warning" plain @click="openChat">AI 助教</el-button>
        <el-button
          v-permission="'paper-answer:score'"
          type="success"
          @click="router.push('/paper-answer/score')"
        >
          去录分
        </el-button>
      </template>
    </PageHero>

    <StatCards :items="scoreStats" :loading="loading" />

    <el-card shadow="never">
      <el-descriptions v-if="detail" :column="3" border>
        <el-descriptions-item label="学号">{{ detail.studentNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ detail.studentName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ detail.studentClass || '-' }}</el-descriptions-item>
        <el-descriptions-item label="试卷">{{ detail.paperTitle || '-' }}</el-descriptions-item>
        <el-descriptions-item label="阅卷状态">
          <el-tag :type="dictItem(MARKING_STATUS, detail.markingStatus).type">
            {{ detail.markingStatusDesc || dictItem(MARKING_STATUS, detail.markingStatus).label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="复核状态">
          <el-tag :type="dictItem(REVIEW_STATUS, detail.reviewStatus).type" effect="plain">
            {{ detail.reviewStatusDesc || dictItem(REVIEW_STATUS, detail.reviewStatus).label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ formatTime(detail.submitTime) }}</el-descriptions-item>
        <el-descriptions-item label="阅卷完成时间">{{ formatTime(detail.markTime) }}</el-descriptions-item>
        <el-descriptions-item label="失败原因">{{ detail.markingError || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 原题与学生答案 -->
    <el-card shadow="never">
      <template #header>原题与学生答案</template>
      <div v-for="question in questions" :key="question.id" class="question">
        <div class="question-head">
          <span class="question-no">第 {{ question.questionNo }} 题</span>
          <el-tag :type="dictItem(QUESTION_TYPE, question.questionType).type" effect="plain" size="small">
            {{ dictItem(QUESTION_TYPE, question.questionType).label }}
          </el-tag>
          <span class="question-score">{{ question.score }} 分</span>
        </div>
        <div class="question-content">{{ question.questionContent }}</div>
        <div class="answer-box">
          <div class="answer-label">学生答案（OCR 全文）</div>
          <pre v-if="detail?.ocrText" class="ocr-text">{{ detail.ocrText }}</pre>
          <el-empty v-else description="暂无 OCR 文本" :image-size="60" />
        </div>
        <div class="answer-box">
          <div class="answer-label">参考答案</div>
          <div class="reference">{{ question.standardAnswer || '-' }}</div>
        </div>
        <div class="answer-box">
          <div class="answer-label">评分细则</div>
          <div class="reference">{{ question.scoringRule || '-' }}</div>
        </div>
      </div>
      <el-empty v-if="!questions.length" description="暂无题目" :image-size="80" />
    </el-card>

    <!-- 逐题评语 -->
    <el-card shadow="never">
      <template #header>AI 逐题评语与教师复核</template>

      <el-alert
        v-if="aiPending"
        type="info"
        show-icon
        :closable="false"
        title="AI 逐题结果尚未生成"
        description="请先在【大模型设置】保存 API Key，答卷提交后异步阅卷任务才会给出逐题分数与评语。"
        class="tip-alert"
      />

      <el-table v-if="questionResults.length" :data="questionResults" border stripe>
        <el-table-column prop="questionNo" label="题号" width="80" />
        <el-table-column prop="questionContent" label="题干" min-width="220" show-overflow-tooltip />
        <el-table-column prop="fullScore" label="满分" width="80" />
        <el-table-column label="AI 参考分" width="100">
          <template #default="{ row }">{{ row.aiScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="AI 评语" min-width="260">
          <template #default="{ row }">{{ row.aiComment || '-' }}</template>
        </el-table-column>
        <el-table-column label="教师最终分" width="110">
          <template #default="{ row }">{{ row.finalScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="教师评语" min-width="220">
          <template #default="{ row }">{{ row.finalComment || '-' }}</template>
        </el-table-column>
        <el-table-column label="Agent" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="reviewRow(row)">AI 复核</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无逐题结果" :image-size="60" />
        </template>
      </el-table>
      <el-empty v-else description="暂无逐题结果，请先完成阅卷" :image-size="80" />
    </el-card>

    <!-- AI 助教：带当前答卷上下文的多轮对话 -->
    <el-drawer v-model="chatVisible" title="AI 助教" size="440px">
      <div class="chat-body">
        <div
          v-for="(message, index) in chatMessages"
          :key="index"
          :class="['chat-item', message.role]"
        >
          <div class="chat-bubble">{{ message.content }}</div>
          <el-collapse v-if="message.steps?.length" class="chat-steps">
            <el-collapse-item :title="`工具调用轨迹（${message.steps.length} 步）`">
              <div v-for="step in message.steps" :key="step.step" class="step-item">
                <div class="step-title">第 {{ step.step }} 步 · {{ step.tool }}</div>
                <div class="step-thought">{{ step.thought }}</div>
                <pre class="step-observation">{{ step.observation }}</pre>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
        <el-empty
          v-if="!chatMessages.length"
          description="可以问：第 15 题为什么扣了 3 分？"
          :image-size="60"
        />
      </div>
      <template #footer>
        <el-input
          v-model="chatInput"
          type="textarea"
          :rows="2"
          placeholder="例如：第 15 题为什么只给了 8 分？"
          @keyup.enter.exact="sendChat"
        />
        <el-button
          type="primary"
          :loading="chatLoading"
          style="width: 100%; margin-top: 8px"
          @click="sendChat"
        >
          发送
        </el-button>
      </template>
    </el-drawer>

    <!-- 阅卷 Agent：初评 -> 自检 -> 定稿 -->
    <el-dialog v-model="reviewVisible" title="阅卷 Agent 复核" width="720px">
      <div v-loading="reviewLoading" class="review-body">
        <el-descriptions v-if="reviewQuestion" :column="3" border>
          <el-descriptions-item label="题号">
            {{ reviewQuestion.questionNo }}
          </el-descriptions-item>
          <el-descriptions-item label="满分">
            {{ reviewQuestion.fullScore }}
          </el-descriptions-item>
          <el-descriptions-item label="当前 AI 分">
            {{ reviewQuestion.aiScore ?? '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="reviewResult">
          <el-alert
            type="info"
            show-icon
            :closable="false"
            title="初评后由同一个模型按评分细则自检，若发现分数与依据不一致会自动修正"
            class="review-tip"
          />
          <el-descriptions :column="2" border>
            <el-descriptions-item label="初评分">
              {{ reviewResult.draftScore }}
            </el-descriptions-item>
            <el-descriptions-item label="定稿分">
              {{ reviewResult.finalScore }}
            </el-descriptions-item>
            <el-descriptions-item label="初评依据" :span="2">
              {{ reviewResult.draftComment || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="自检后依据" :span="2">
              {{ reviewResult.finalComment || '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <el-collapse v-if="reviewResult.steps?.length" class="chat-steps">
            <el-collapse-item :title="`工具调用轨迹（${reviewResult.steps.length} 步）`">
              <div v-for="step in reviewResult.steps" :key="step.step" class="step-item">
                <div class="step-title">第 {{ step.step }} 步 · {{ step.tool }}</div>
                <div class="step-thought">{{ step.thought }}</div>
                <pre class="step-observation">{{ step.observation }}</pre>
              </div>
            </el-collapse-item>
          </el-collapse>
        </template>
        <el-empty
          v-else-if="!reviewLoading"
          description="Agent 未返回结果，请确认已配置大模型 Key"
          :image-size="60"
        />
      </div>
      <template #footer>
        <el-button @click="reviewVisible = false">关闭</el-button>
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

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.question {
  padding: 16px 0;
  border-bottom: 1px dashed #ebeef5;
}

.question:last-child {
  border-bottom: none;
}

.question-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.question-no {
  font-weight: 600;
}

.question-score {
  margin-left: auto;
  color: #f56c6c;
}

.question-content {
  margin-bottom: 12px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.answer-box {
  margin-bottom: 12px;
}

.answer-label {
  margin-bottom: 6px;
  font-size: 13px;
  color: #909399;
}

.ocr-text {
  margin: 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
}

.reference {
  padding: 10px 12px;
  background: #fafafa;
  border-radius: 6px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.tip-alert {
  margin-bottom: 12px;
}

/* ==================== AI 助教 / Agent 复核 ==================== */
.chat-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.chat-item.user {
  align-items: flex-end;
}

.chat-bubble {
  max-width: 100%;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f5f7fa;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-item.user .chat-bubble {
  background: #ecf5ff;
}

.chat-steps {
  margin-top: 4px;
}

.step-item {
  margin-bottom: 12px;
}

.step-title {
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 600;
}

.step-thought {
  margin-bottom: 6px;
  font-size: 12px;
  color: #909399;
}

.step-observation {
  margin: 0;
  padding: 8px;
  max-height: 180px;
  overflow: auto;
  background: #fafafa;
  border-radius: 6px;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.review-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-tip {
  margin: 0;
}
</style>
