<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { parsePaperFiles } from '@/api/paper'
import { QUESTION_TYPE, dictItem } from '@/utils/dict'

/**
 * 试卷素材解析面板（移动端共用：新增试卷、题目管理）。
 *
 * 交互分三段，避免"上传了却看不到结果"：
 * 1. 选文件：图片/PDF 分开两个 input（微信里 accept 混合会只弹相册），已选文件可预览、可删；
 * 2. 解析：OCR/PDF 文本 → 大模型或规则拆题，结果只进内存，不落库；
 * 3. 校对：逐题展示题干/题型/分值/参考答案/评分细则，可改可删可补，
 *    由父页面在确认时调 /questions/batch 一次性入库。
 *
 * 通过 defineExpose 暴露 files / parsed / questions / buildQuestions / reset 给父页面。
 */
const emit = defineEmits(['parsed'])

const imageInputRef = ref()
const pdfInputRef = ref()
const files = ref([])
const parsing = ref(false)
const parsed = ref(false)
const warnings = ref([])
const pageStatuses = ref([])
const progress = ref({ done: 0, total: 0, message: '' })
const questions = ref([])

const totalScore = computed(() =>
  questions.value.reduce((sum, item) => sum + (Number(item.score) || 0), 0)
)

const parseButtonText = computed(() => {
  if (parsing.value) {
    return progress.value.total
      ? `识别中 ${progress.value.done}/${progress.value.total}…`
      : '识别中…'
  }
  return parsed.value ? '重新解析' : '上传并解析'
})

function onFilesChange(event) {
  const picked = Array.from(event.target.files || [])
  files.value = [
    ...files.value,
    ...picked.map((file) => ({
      file,
      name: file.name,
      size: file.size,
      isImage: file.type.startsWith('image/'),
      url: file.type.startsWith('image/') ? URL.createObjectURL(file) : ''
    }))
  ]
  // input 值清空，允许重复选择同一个文件
  event.target.value = ''
  parsed.value = false
  warnings.value = []
  pageStatuses.value = []
}

function removeFile(index) {
  const target = files.value[index]
  if (target?.url) URL.revokeObjectURL(target.url)
  files.value.splice(index, 1)
  parsed.value = false
}

function normalizeQuestion(raw, index) {
  return {
    questionNo: raw.questionNo || String(index + 1),
    sectionName: raw.sectionName || '简答题',
    questionType: raw.questionType || 5,
    questionContent: raw.questionContent || '',
    standardAnswer: raw.standardAnswer || '',
    scoringRule: raw.scoringRule || '',
    score: raw.score ?? 10
  }
}

async function handleParse() {
  if (!files.value.length || parsing.value) {
    if (!files.value.length) ElMessage.warning('请先选择 PDF 或图片文件')
    return
  }
  parsing.value = true
  parsed.value = false
  warnings.value = []
  pageStatuses.value = []
  progress.value = { done: 0, total: files.value.length, message: '正在上传文件…' }
  try {
    const data = await parsePaperFiles(files.value.map((item) => item.file), (task) => {
      progress.value = {
        done: task?.done || 0,
        total: task?.total ?? files.value.length,
        message: task?.message || '正在识别…'
      }
    })
    warnings.value = data?.warnings || []
    pageStatuses.value = data?.pages || []
    questions.value = (data?.questions || []).map(normalizeQuestion)
    parsed.value = true
    emit('parsed', data)
    if (questions.value.length) {
      ElMessage.success(`识别到 ${questions.value.length} 道题，请逐题核对后再保存`)
    } else {
      ElMessage.warning('未识别到题目，可在下方手动补录')
    }
  } catch (error) {
    // HTTP 异常已由 request 拦截器提示，这里只补任务失败/轮询超时的提示
    const message = error?.message || '解析失败，请重试'
    warnings.value = [message]
    if (!error?.response) ElMessage.error(message)
  } finally {
    parsing.value = false
  }
}

function pageTagType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function pageStatusText(page) {
  if (page?.status === 'SUCCESS') return `已识别 ${page.chars || 0} 字`
  return page?.message || (page?.status === 'SKIPPED_DUPLICATE' ? '重复跳过' : '识别失败')
}

function addQuestion() {
  questions.value.push(normalizeQuestion({}, questions.value.length))
}

function removeQuestion(index) {
  questions.value.splice(index, 1)
}

/** 父页面保存时调用：过滤空题干，保留试卷原题号 */
function buildQuestions() {
  return questions.value
    .filter((item) => (item.questionContent || '').trim())
    .map((item, index) => ({
      questionNo: String(item.questionNo || index + 1),
      sectionName: item.sectionName || '简答题',
      questionType: item.questionType || 5,
      questionContent: item.questionContent.trim(),
      standardAnswer: item.standardAnswer || '',
      scoringRule: item.scoringRule || '',
      score: Number(item.score) || 10
    }))
}

function reset() {
  files.value.forEach((item) => item.url && URL.revokeObjectURL(item.url))
  files.value = []
  parsing.value = false
  parsed.value = false
  warnings.value = []
  pageStatuses.value = []
  progress.value = { done: 0, total: 0, message: '' }
  questions.value = []
  if (imageInputRef.value) imageInputRef.value.value = ''
  if (pdfInputRef.value) pdfInputRef.value.value = ''
}

defineExpose({ files, parsed, parsing, questions, buildQuestions, reset })
</script>

<template>
  <div class="parse-panel">
    <input
      ref="imageInputRef"
      type="file"
      multiple
      accept="image/*"
      class="file-input"
      @change="onFilesChange"
    />
    <input
      ref="pdfInputRef"
      type="file"
      multiple
      accept="application/pdf,.pdf"
      class="file-input"
      @change="onFilesChange"
    />

    <div class="upload-actions">
      <el-button plain @click="imageInputRef?.click()">拍照 / 相册</el-button>
      <el-button plain @click="pdfInputRef?.click()">选择 PDF 文件</el-button>
    </div>
    <p class="tip">
      支持 PDF 与图片（可多页）。微信里若只弹出「拍摄 / 从相册选择」，请点「选择 PDF 文件」；
      仍选不到时点右上角 ··· 用浏览器打开。
    </p>

    <!-- 已选文件预览：让人看得见自己传了什么 -->
    <div v-if="files.length" class="file-list">
      <div v-for="(item, index) in files" :key="index" class="file-item">
        <el-image
          v-if="item.isImage"
          :src="item.url"
          :preview-src-list="files.filter((f) => f.isImage).map((f) => f.url)"
          :initial-index="files.filter((f) => f.isImage).findIndex((f) => f.url === item.url)"
          fit="cover"
          class="thumb"
          preview-teleported
        />
        <span v-else class="thumb pdf">PDF</span>
        <span class="file-name">{{ item.name }}</span>
        <el-button link type="danger" size="small" @click="removeFile(index)">删除</el-button>
      </div>
    </div>

    <el-button
      type="primary"
      plain
      class="parse-btn"
      :loading="parsing"
      :disabled="!files.length"
      @click="handleParse"
    >
      {{ parseButtonText }}
    </el-button>

    <p v-if="parsing && progress.message" class="progress">{{ progress.message }}</p>

    <p v-for="(warning, index) in warnings" :key="index" class="warning">{{ warning }}</p>

    <!-- 逐文件状态：哪张成功、哪张失败、哪张被判为重复一目了然 -->
    <div v-if="pageStatuses.length" class="page-list">
      <div v-for="(page, index) in pageStatuses" :key="index" class="page-item">
        <span class="page-name">{{ page.fileName }}</span>
        <el-tag size="small" :type="pageTagType(page.status)">{{ pageStatusText(page) }}</el-tag>
      </div>
    </div>

    <!-- 解析结果校对区：识别不准就在这里改，无关文字整题删掉 -->
    <template v-if="parsed">
      <div class="result-head">
        <span>识别结果（可编辑）</span>
        <b>{{ questions.length }} 题 / {{ totalScore }} 分</b>
      </div>
      <p class="tip">
        卷头、注意事项等无关文字若被当成题目，直接点「删除本题」；题干、分值、参考答案都可改。
      </p>

      <article v-for="(item, index) in questions" :key="index" class="q-card">
        <div class="q-head">
          <span class="q-no">{{ item.questionNo || index + 1 }}</span>
          <el-select v-model="item.questionType" size="small" class="q-type">
            <el-option
              v-for="(meta, key) in QUESTION_TYPE"
              :key="key"
              :label="meta.label"
              :value="Number(key)"
              :disabled="Number(key) === 0"
            />
          </el-select>
          <el-input-number
            v-model="item.score"
            size="small"
            :min="0.5"
            :max="100"
            :precision="1"
            class="q-score"
          />
          <span class="unit">分</span>
          <el-button link type="danger" size="small" @click="removeQuestion(index)">
            删除本题
          </el-button>
        </div>

        <el-input v-model="item.sectionName" size="small" class="q-field" placeholder="所属大题，如 简答题" />
        <el-input
          v-model="item.questionContent"
          type="textarea"
          :rows="4"
          class="q-field"
          placeholder="题干"
        />
        <el-input
          v-model="item.standardAnswer"
          type="textarea"
          :rows="2"
          class="q-field"
          placeholder="参考答案（主观题必填，AI 评分依据）"
        />
        <el-input
          v-model="item.scoringRule"
          type="textarea"
          :rows="2"
          class="q-field"
          placeholder="评分细则 / 得分点，如：答出定义 2 分，举例 3 分"
        />
        <div class="q-type-tag">
          <el-tag size="small" effect="plain" :type="dictItem(QUESTION_TYPE, item.questionType).type">
            {{ dictItem(QUESTION_TYPE, item.questionType).label }}
          </el-tag>
        </div>
      </article>

      <el-button plain class="parse-btn" @click="addQuestion">+ 手动补一道题</el-button>
    </template>
  </div>
</template>

<style scoped>
.file-input {
  display: none;
}

.upload-actions {
  display: flex;
  gap: 10px;
}

.upload-actions .el-button {
  flex: 1;
  margin-left: 0;
}

.tip {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #9aa0b8;
}

.file-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  background: #f7f8fd;
  border-radius: 10px;
}

.thumb {
  flex-shrink: 0;
  width: 46px;
  height: 46px;
  overflow: hidden;
  border-radius: 8px;
}

.thumb.pdf {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #f56c6c;
  background: #fef0f0;
}

.file-name {
  flex: 1;
  overflow: hidden;
  font-size: 12px;
  color: #3a3e5c;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.parse-btn {
  width: 100%;
  margin-top: 12px;
}

.warning {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #e6a23c;
}

.progress {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--ai-primary);
}

.page-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}

.page-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px;
  font-size: 12px;
  background: #f7f8fd;
  border-radius: 8px;
}

.page-name {
  flex: 1;
  overflow: hidden;
  color: #3a3e5c;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 16px;
  margin-top: 16px;
  font-size: 14px;
  color: #23264a;
  border-top: 1px dashed #eef0fa;
}

.result-head b {
  font-size: 12px;
  color: var(--ai-primary);
}

.q-card {
  padding: 12px;
  margin-top: 12px;
  background: #f7f8fd;
  border-radius: 12px;
}

.q-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.q-no {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  font-size: 13px;
  font-weight: 700;
  color: var(--ai-primary);
  background: #eef1ff;
  border-radius: 8px;
}

.q-type {
  width: 96px;
}

.q-score {
  width: 104px;
}

.unit {
  font-size: 12px;
  color: #9aa0b8;
}

.q-field {
  margin-top: 8px;
}

.q-type-tag {
  margin-top: 8px;
  text-align: right;
}
</style>
