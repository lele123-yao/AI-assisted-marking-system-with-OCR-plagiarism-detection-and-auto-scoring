<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMarkingTaskDetail } from '@/api/mark'
import { triggerOcr } from '@/api/paperAnswer'
import { useUserStore } from '@/store/user'
import { MARKING_STATUS, REVIEW_STATUS, dictItem, formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileStatGrid from '@/components/m/MobileStatGrid.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'

/** 移动端阅卷详情：状态流转、考生信息、分数、OCR 全文与扫描件 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const ocrLoading = ref(false)
const detail = ref(null)
const ocrExpanded = ref(false)

const steps = ['待录入', 'OCR 识别中', 'AI 批改中', '待教师确认', '已确认归档']

async function load() {
  loading.value = true
  try {
    detail.value = await getMarkingTaskDetail(route.params.id)
  } finally {
    loading.value = false
  }
}

/** 0~3 对应步骤索引，5 表示全部完成；失败（4）停在第 1 步并标红 */
const stepActive = computed(() => {
  const status = detail.value?.markingStatus
  if (status === 5) return steps.length
  if (status === 4) return 0
  return status ?? 0
})

const scoreStats = computed(() => [
  {
    key: 'ai',
    label: 'AI 参考分',
    value: detail.value?.aiReferenceScore ?? '-',
    icon: 'Cpu',
    tone: 'primary'
  },
  {
    key: 'final',
    label: '教师最终分',
    value: detail.value?.teacherFinalScore ?? '-',
    icon: 'CircleCheck',
    tone: 'success'
  },
  {
    key: 'diff',
    label: '分差',
    value: detail.value?.scoreModified ? detail.value?.scoreDiff ?? '-' : '—',
    icon: 'Sort',
    tone: 'warning'
  },
  {
    key: 'review',
    label: '复核状态',
    value: detail.value?.reviewStatusDesc || dictItem(REVIEW_STATUS, detail.value?.reviewStatus).label,
    icon: 'View',
    tone: 'info'
  }
])

const imageUrl = computed(() => detail.value?.paperImgAccessUrl || detail.value?.paperImgUrl || '')
const canReview = computed(() => userStore.hasPermission('grade:review'))

async function handleOcr() {
  try {
    await ElMessageBox.confirm(
      '重新触发 OCR 会覆盖原有识别结果，并重新进入 AI 批改流程，确认继续？',
      'OCR 确认',
      { type: 'warning' }
    )
  } catch {
    return
  }

  ocrLoading.value = true
  try {
    await triggerOcr(route.params.id)
    ElMessage.success('OCR 任务已提交')
    await load()
  } finally {
    ocrLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="m-page">
    <MobileHero
      :title="detail ? `${detail.studentNo || ''} ${detail.studentName || ''}`.trim() || '阅卷详情' : '阅卷详情'"
      :subtitle="detail?.paperTitle || '查看阅卷状态与识别结果'"
      icon="DocumentChecked"
    >
      <template #actions>
        <el-button size="small" plain :loading="loading" @click="load">刷新</el-button>
      </template>
    </MobileHero>

    <template v-if="detail">
      <div class="status-row">
        <el-tag :type="dictItem(MARKING_STATUS, detail.markingStatus).type" effect="plain">
          {{ detail.markingStatusDesc || dictItem(MARKING_STATUS, detail.markingStatus).label }}
        </el-tag>
        <el-tag :type="dictItem(REVIEW_STATUS, detail.reviewStatus).type" effect="plain">
          {{ detail.reviewStatusDesc || dictItem(REVIEW_STATUS, detail.reviewStatus).label }}
        </el-tag>
      </div>

      <section class="m-card">
        <div class="m-row-between">
          <span class="m-title">阅卷流程</span>
          <span class="m-sub">状态码 {{ detail.markingStatus ?? '-' }}</span>
        </div>
        <el-steps
          direction="vertical"
          :active="stepActive"
          :process-status="detail.markingStatus === 4 ? 'error' : 'process'"
          finish-status="success"
          class="steps"
        >
          <el-step v-for="step in steps" :key="step" :title="step" />
        </el-steps>
      </section>

      <MobileStatGrid :items="scoreStats" />

      <el-alert
        v-if="detail.markingError"
        type="error"
        show-icon
        :closable="false"
        :title="`阅卷失败原因：${detail.markingError}`"
      />

      <section class="m-card">
        <div class="info-row"><span>学号</span><b>{{ detail.studentNo || '-' }}</b></div>
        <div class="info-row"><span>姓名</span><b>{{ detail.studentName || '-' }}</b></div>
        <div class="info-row"><span>班级</span><b>{{ detail.studentClass || '-' }}</b></div>
        <div class="info-row"><span>试卷</span><b>{{ detail.paperTitle || '-' }}</b></div>
        <div class="info-row"><span>提交时间</span><b>{{ formatTime(detail.submitTime) }}</b></div>
        <div class="info-row"><span>阅卷完成</span><b>{{ formatTime(detail.markTime) }}</b></div>
      </section>

      <div class="actions">
        <el-button
          v-if="[0, 4].includes(detail.markingStatus)"
          type="warning"
          plain
          :loading="ocrLoading"
          @click="handleOcr"
        >
          重新触发 OCR
        </el-button>
        <el-button
          v-if="detail.markingStatus === 3"
          type="primary"
          @click="router.push('/m/paper-answer/score')"
        >
          去确认分数
        </el-button>
        <el-button v-if="canReview" plain @click="router.push('/m/grade/review')">
          去复核
        </el-button>
      </div>

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
          <el-button
            v-if="detail.ocrText"
            link
            size="small"
            @click="ocrExpanded = !ocrExpanded"
          >
            {{ ocrExpanded ? '收起' : '展开' }}
          </el-button>
        </div>
        <pre
          v-if="detail.ocrText"
          class="ocr m-clamp-6"
          :class="{ expanded: ocrExpanded }"
        >{{ detail.ocrText }}</pre>
        <MobileEmpty v-else description="尚无 OCR 识别结果" icon="Document" />
      </section>
    </template>
  </div>
</template>

<style scoped>
.status-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.steps {
  height: 280px;
  margin-top: 14px;
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

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.actions .el-button {
  flex: 1 1 120px;
  margin-left: 0;
}

.paper-image {
  width: 100%;
  max-height: 420px;
  margin-top: 10px;
  border-radius: 10px;
}

.ocr {
  padding: 12px;
  margin: 10px 0 0;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.8;
  color: #3a3e5c;
  word-break: break-word;
  background: #f7f8fd;
  border-radius: 10px;
}

.ocr.expanded {
  display: block;
  max-height: 60vh;
  overflow-y: auto;
}
</style>
