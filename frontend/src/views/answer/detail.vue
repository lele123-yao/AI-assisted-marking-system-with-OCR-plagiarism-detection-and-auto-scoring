<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPaperAnswerDetail } from '@/api/paperAnswer'
import { listQuestionsByPaper } from '@/api/question'
import PageHero from '@/components/PageHero.vue'
import StatCards from '@/components/StatCards.vue'
import { QUESTION_TYPE, MARKING_STATUS, REVIEW_STATUS, dictItem, formatTime } from '@/utils/dict'

/** 答卷详情：学生信息、扫描件、OCR 全文、试卷原题 */
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref(null)
const questions = ref([])

async function load() {
  loading.value = true
  try {
    detail.value = await getPaperAnswerDetail(route.params.id)
    if (detail.value?.paperId) {
      questions.value = (await listQuestionsByPaper(detail.value.paperId)) || []
    }
  } finally {
    loading.value = false
  }
}

/** 概览统计：全部取自答卷详情，未评阅时显示占位 */
const stats = computed(() => [
  {
    label: '教师最终分',
    value: detail.value?.teacherFinalScore ?? '-',
    icon: 'CircleCheck',
    color: '#67c23a',
    bg: '#f0f9eb',
    hint: '教师确认成绩'
  },
  {
    label: 'AI 参考分',
    value: detail.value?.aiReferenceScore ?? '-',
    icon: 'DataLine',
    color: '#409eff',
    bg: '#ecf5ff',
    hint: '模型初评'
  },
  {
    label: '分差',
    value: detail.value?.scoreModified ? detail.value?.scoreDiff : '—',
    icon: 'Sort',
    color: '#e6a23c',
    bg: '#fdf6ec',
    hint: '教师改分后'
  },
  {
    label: '题目数',
    value: questions.value.length,
    icon: 'Tickets',
    color: '#9254de',
    bg: '#f9f0ff'
  }
])

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page-container">
    <PageHero
      title="答卷详情"
      subtitle="查看单个考生的扫描答卷、OCR 识别全文、AI 参考分与教师最终分，并对照试卷原题核对作答情况。"
      icon="Tickets"
      tone="light"
    >
      <template #meta>
        <div class="hero-tags">
          <el-tag v-if="detail?.studentNo" effect="plain">
            {{ detail.studentNo }} {{ detail.studentName || '' }}
          </el-tag>
          <el-tag v-if="detail" :type="dictItem(MARKING_STATUS, detail.markingStatus).type">
            {{ detail.markingStatusDesc || dictItem(MARKING_STATUS, detail.markingStatus).label }}
          </el-tag>
          <el-tag v-if="detail" :type="dictItem(REVIEW_STATUS, detail.reviewStatus).type" effect="plain">
            {{ detail.reviewStatusDesc || dictItem(REVIEW_STATUS, detail.reviewStatus).label }}
          </el-tag>
          <el-tag v-if="detail?.markTime" type="info" effect="plain">
            阅卷完成 {{ formatTime(detail.markTime) }}
          </el-tag>
        </div>
      </template>
      <template #actions>
        <el-button @click="router.back()">返回</el-button>
        <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHero>

    <StatCards :items="stats" :loading="loading" />

    <el-card shadow="never">
      <el-descriptions v-if="detail" :column="3" border>
        <el-descriptions-item label="学号">{{ detail.studentNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ detail.studentName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ detail.studentClass || '-' }}</el-descriptions-item>
        <el-descriptions-item label="试卷">{{ detail.paperTitle || '-' }}</el-descriptions-item>
        <el-descriptions-item label="答卷来源">
          {{ detail.answerSourceDesc || detail.answerSource || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="导入批次">{{ detail.importBatchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="AI参考分">{{ detail.aiReferenceScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="教师最终分">{{ detail.teacherFinalScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="分差">
          {{ detail.scoreModified ? detail.scoreDiff : '-' }}
        </el-descriptions-item>
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
        <el-descriptions-item label="阅卷完成时间">{{ formatTime(detail.markTime) }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="detail?.markingError"
        type="error"
        show-icon
        :closable="false"
        :title="`阅卷失败原因：${detail.markingError}`"
        class="mt-16"
      />
    </el-card>

    <!-- 扫描件预览 -->
    <el-card v-if="detail?.paperImgAccessUrl || detail?.paperImgUrl" shadow="never">
      <template #header>答卷扫描件</template>
      <el-image
        :src="detail.paperImgAccessUrl || detail.paperImgUrl"
        :preview-src-list="[detail.paperImgAccessUrl || detail.paperImgUrl]"
        fit="contain"
        class="paper-image"
      />
    </el-card>

    <!-- OCR 全文 -->
    <el-card shadow="never">
      <template #header>OCR 识别全文</template>
      <pre v-if="detail?.ocrText" class="ocr-text">{{ detail.ocrText }}</pre>
      <el-empty v-else description="尚无 OCR 识别结果" :image-size="80" />
    </el-card>

    <!-- 试卷原题 -->
    <el-card shadow="never">
      <template #header>试卷题目</template>
      <el-table :data="questions" border>
        <el-table-column prop="questionNo" label="题号" width="90" />
        <el-table-column label="题型" width="100">
          <template #default="{ row }">
            <el-tag :type="dictItem(QUESTION_TYPE, row.questionType).type" effect="plain">
              {{ dictItem(QUESTION_TYPE, row.questionType).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="questionContent" label="题干" min-width="320" show-overflow-tooltip />
        <el-table-column prop="score" label="分值" width="80" />
        <el-table-column prop="standardAnswer" label="参考答案" min-width="240" show-overflow-tooltip />
        <template #empty>
          <el-empty description="暂无题目" :image-size="80" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* hero 内的提示标签 */
.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.paper-image {
  width: 100%;
  max-height: 560px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
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

.mt-16 {
  margin-top: 16px;
}
</style>
