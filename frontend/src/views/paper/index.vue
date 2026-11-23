<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changePaperStatus, createPaper, pagePapers, parsePaperFiles, updatePaper } from '@/api/paper'
import { batchSaveQuestions, listQuestionsByPaper } from '@/api/question'
import { pageCourses } from '@/api/course'
import { listClassOptions, listStudentOptions } from '@/api/class'
import { assignPaper } from '@/api/assignment'
import PageHero from '@/components/PageHero.vue'
import { useTable } from '@/composables/useTable'
import { ANSWER_MODE, PAPER_STATUS, dictItem, formatTime } from '@/utils/dict'

/** 试卷管理：支持线上作答与纸质线下阅卷两种模式 */
const { loading, list, total, query, load, search, reset, handleSizeChange, handlePageChange } =
  useTable(pagePapers, { keyword: '', courseId: '', status: '', answerMode: '' })

/** 看板统计：试卷总数取后端 total，各状态数量按当前页数据计算 */
const stats = computed(() => [
  {
    label: '试卷总数',
    value: total.value,
    icon: 'Document',
    color: '#409eff',
    bg: '#ecf5ff',
    hint: '点击查看全部',
    clickable: true,
    key: 'all'
  },
  {
    label: '草稿',
    value: list.value.filter((row) => row.status === 0).length,
    icon: 'EditPen',
    color: '#e6a23c',
    bg: '#fdf6ec',
    hint: '当前页 · 点击筛选',
    clickable: true,
    key: 'draft'
  },
  {
    label: '阅卷中',
    value: list.value.filter((row) => row.status === 2).length,
    icon: 'Loading',
    color: '#67c23a',
    bg: '#f0f9eb',
    hint: '当前页 · 点击筛选',
    clickable: true,
    key: 'marking'
  },
  {
    label: '已归档',
    value: list.value.filter((row) => row.status === 3).length,
    icon: 'FolderOpened',
    color: '#909399',
    bg: '#f4f4f5',
    hint: '当前页 · 点击筛选',
    clickable: true,
    key: 'archived'
  }
])

/** 点击统计卡片时按状态筛选试卷，总数卡片则清空状态条件 */
const STATUS_BY_KEY = { draft: 0, marking: 2, archived: 3 }

function onStatSelect(item) {
  query.status = STATUS_BY_KEY[item.key] ?? ''
  search()
}

/** 概览条目高亮：与当前筛选状态保持一致 */
function isStatActive(item) {
  if (item.key === 'all') return query.status === ''
  return query.status === STATUS_BY_KEY[item.key]
}

// 课程下拉
const courses = ref([])
async function loadCourses() {
  const data = await pageCourses({ pageNum: 1, pageSize: 100 })
  courses.value = data?.records || []
}

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({
  paperNo: '',
  paperTitle: '',
  courseId: null,
  answerMode: 1,
  passScore: null,
  duration: null,
  startTime: '',
  endTime: '',
  aiMarking: 1,
  description: '',
  pdfUrl: '',
  imageUrl: ''
})

// ==================== 试卷素材与题目解析 ====================
/** el-upload 的文件列表（auto-upload=false，点击"识别题目"时才统一上传） */
const pdfFileList = ref([])
const imageFileList = ref([])
const parsing = ref(false)
/** 本次选择的文件是否已完成"上传并识别"；未上传就提交会导致素材缺失 */
const filesParsed = ref(false)
const parseWarnings = ref([])
const extractedText = ref('')
/** 题目草稿：解析结果 + 教师手动增删改，最终以这里为准提交 */
const questionRows = ref([])
/** 题目是否有改动；编辑已有试卷时只有改动才整体替换，避免无谓重建题目ID */
const questionsDirty = ref(false)

const hasSelectedFiles = computed(() =>
  [...pdfFileList.value, ...imageFileList.value].some((file) => file.raw)
)

const questionTotalScore = computed(() =>
  questionRows.value.reduce((sum, row) => sum + (Number(row.score) || 0), 0)
)

function markQuestionsDirty() {
  questionsDirty.value = true
}

function addQuestionRow() {
  questionRows.value.push({
    questionNo: String(questionRows.value.length + 1),
    sectionName: '简答题',
    questionType: 5,
    questionContent: '',
    score: 10,
    standardAnswer: '',
    scoringRule: ''
  })
  markQuestionsDirty()
}

function removeQuestionRow(index) {
  questionRows.value.splice(index, 1)
  markQuestionsDirty()
}

function handleFileChange() {
  filesParsed.value = false
}

/** 调后端上传素材并解析题目：文件落 MinIO，返回题目草稿与提示 */
async function handleParse() {
  const files = [...pdfFileList.value, ...imageFileList.value]
    .map((file) => file.raw)
    .filter(Boolean)
  if (!files.length) {
    ElMessage.warning('请先选择 PDF 或图片文件')
    return
  }
  parsing.value = true
  try {
    const data = await parsePaperFiles(files)
    filesParsed.value = true
    if (data?.pdfUrl) form.pdfUrl = data.pdfUrl
    if (data?.imageUrls?.length) form.imageUrl = JSON.stringify(data.imageUrls)
    parseWarnings.value = data?.warnings || []
    extractedText.value = data?.extractedText || ''

    const parsed = data?.questions || []
    if (parsed.length) {
      questionRows.value = parsed.map((item, index) => ({
        questionNo: item.questionNo || String(index + 1),
        sectionName: item.sectionName || '简答题',
        questionType: item.questionType || 5,
        questionContent: item.questionContent || '',
        score: Number(item.score) || 10,
        standardAnswer: item.standardAnswer || '',
        scoringRule: item.scoringRule || ''
      }))
      markQuestionsDirty()
      ElMessage.success(`识别到 ${parsed.length} 道题，请核对并补充参考答案与评分细则`)
    } else {
      ElMessage.warning('未识别到题目，请手动录入')
    }
  } finally {
    parsing.value = false
  }
}

/** 提交前校验题目草稿，错误信息直接定位到具体题号 */
function validateQuestionRows() {
  const seen = new Set()
  for (let index = 0; index < questionRows.value.length; index++) {
    const row = questionRows.value[index]
    const questionNo = String(row.questionNo || '').trim()
    if (!questionNo) return `第 ${index + 1} 行题号不能为空`
    if (seen.has(questionNo)) return `题号重复：${questionNo}`
    seen.add(questionNo)
    if (!row.questionContent || !row.questionContent.trim()) return `第 ${questionNo} 题题干不能为空`
    if (!(Number(row.score) > 0)) return `第 ${questionNo} 题分值必须大于 0`
    if ([5, 6, 7, 8].includes(row.questionType)) {
      if (!row.standardAnswer?.trim()) return `第 ${questionNo} 题为主观题，必须填写参考答案`
      if (!row.scoringRule?.trim()) return `第 ${questionNo} 题为主观题，必须填写评分细则`
    }
  }
  return ''
}

/** 重置弹窗里的素材、题目与提示 */
function resetDialogState() {
  pdfFileList.value = []
  imageFileList.value = []
  filesParsed.value = false
  parseWarnings.value = []
  extractedText.value = ''
  questionRows.value = []
  questionsDirty.value = false
}

function handlePdfExceed() {
  ElMessage.warning('一次最多上传 1 个 PDF')
}

function handleImageExceed() {
  ElMessage.warning('一次最多上传 9 张图片')
}

/** 线上模式必须设置考试时间；收卷必须晚于开考 */
const rules = {
  paperNo: [{ required: true, message: '请输入试卷编号', trigger: 'blur' }],
  paperTitle: [{ required: true, message: '请输入试卷标题', trigger: 'blur' }],
  courseId: [{ required: true, message: '请选择所属课程', trigger: 'change' }],
  answerMode: [{ required: true, message: '请选择作答模式', trigger: 'change' }],
  startTime: [
    {
      validator: (rule, value, callback) => {
        if (form.answerMode === 1 && !value) return callback(new Error('线上作答必须设置开考时间'))
        callback()
      },
      trigger: 'change'
    }
  ],
  endTime: [
    {
      validator: (rule, value, callback) => {
        if (form.answerMode === 1 && !value) return callback(new Error('线上作答必须设置收卷时间'))
        if (value && form.startTime && new Date(value) <= new Date(form.startTime)) {
          return callback(new Error('收卷时间必须晚于开考时间'))
        }
        callback()
      },
      trigger: 'change'
    }
  ]
}

function openCreate() {
  editingId.value = null
  resetDialogState()
  Object.assign(form, {
    paperNo: '',
    paperTitle: '',
    courseId: null,
    answerMode: 1,
    passScore: null,
    duration: null,
    startTime: '',
    endTime: '',
    aiMarking: 1,
    description: '',
    pdfUrl: '',
    imageUrl: ''
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function openEdit(row) {
  editingId.value = row.id
  resetDialogState()
  Object.assign(form, {
    paperNo: row.paperNo,
    paperTitle: row.paperTitle,
    courseId: row.courseId,
    answerMode: row.answerMode,
    passScore: row.passScore,
    duration: row.duration,
    startTime: row.startTime || '',
    endTime: row.endTime || '',
    aiMarking: row.aiMarking ?? 1,
    description: row.description || '',
    pdfUrl: row.pdfUrl || '',
    imageUrl: row.imageUrl || ''
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()

  // 回填已录入的题目，便于继续增删改；未改动时不会触发整体替换
  try {
    const questions = await listQuestionsByPaper(row.id)
    questionRows.value = (questions || []).map((item, index) => ({
      questionNo: item.questionNo || String(index + 1),
      sectionName: item.sectionName || '简答题',
      questionType: item.questionType || 5,
      questionContent: item.questionContent || '',
      score: Number(item.score) || 10,
      standardAnswer: item.standardAnswer || '',
      scoringRule: item.scoringRule || ''
    }))
  } catch {
    questionRows.value = []
  }
  questionsDirty.value = false
}

async function submit() {
  if (submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const questionError = validateQuestionRows()
  if (questionError) {
    ElMessage.warning(questionError)
    return
  }

  if (hasSelectedFiles.value && !filesParsed.value) {
    ElMessage.warning('已选择文件但尚未上传，请先点击「上传并识别题目」')
    return
  }

  submitting.value = true
  try {
    const payload = { ...form }
    let paperId = editingId.value
    const isCreate = !paperId

    if (paperId) {
      await updatePaper(paperId, payload)
    } else {
      paperId = await createPaper(payload)
      // 先记住新试卷ID：万一存题失败，再次点击保存会走编辑而不是重复建卷
      editingId.value = paperId
    }

    // 新建时只要有题目就保存；编辑时只有题目改动才整体替换
    const needSaveQuestions = isCreate ? questionRows.value.length > 0 : questionsDirty.value
    if (needSaveQuestions) {
      await batchSaveQuestions({
        paperId,
        questions: questionRows.value.map((row) => ({
          questionNo: String(row.questionNo).trim(),
          sectionName: row.sectionName,
          questionType: row.questionType,
          questionContent: row.questionContent,
          score: row.score,
          standardAnswer: row.standardAnswer,
          scoringRule: row.scoringRule
        }))
      })
      questionsDirty.value = false
    }

    ElMessage.success(
      isCreate ? `创建成功，当前为草稿，共 ${questionRows.value.length} 题` : '保存成功'
    )
    dialogVisible.value = false
    load()
  } catch {
    if (editingId.value) {
      ElMessage.warning('试卷信息已保存，但题目未保存成功，请修正题目后再次点击保存')
    }
  } finally {
    submitting.value = false
  }
}

/** 状态推进：0 草稿 → 1 待考试 → 2 阅卷中 → 3 已归档 */
const nextStatusMap = { 0: 1, 1: 2, 2: 3 }
const nextStatusText = { 0: '发布（待考试）', 1: '进入阅卷', 2: '归档' }

async function handleNextStatus(row) {
  const target = nextStatusMap[row.status]
  if (!target) return
  try {
    await ElMessageBox.confirm(
      `确认将试卷「${row.paperTitle}」${nextStatusText[row.status]}？状态只能单向前进。`,
      '状态变更',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await changePaperStatus(row.id, target)
  ElMessage.success('状态已更新')
  load()
}

// ==================== 下发试卷 ====================
const assignDialogVisible = ref(false)
const assignSubmitting = ref(false)
const currentPaper = ref(null)
const classOptions = ref([])
const allStudents = ref([])
const assignForm = reactive({
  classId: null,
  studentIds: [],
  deadline: ''
})

async function openAssign(row) {
  currentPaper.value = row
  Object.assign(assignForm, { classId: null, studentIds: [], deadline: '' })
  assignDialogVisible.value = true
  const [classes, students] = await Promise.all([
    listClassOptions().catch(() => []),
    listStudentOptions({ onlyAvailable: false }).catch(() => [])
  ])
  classOptions.value = classes || []
  allStudents.value = students || []
}

async function submitAssign() {
  if (!assignForm.classId && !assignForm.studentIds.length) {
    ElMessage.warning('请选择班级或至少指定一名学生')
    return
  }
  assignSubmitting.value = true
  try {
    const count = await assignPaper({
      paperId: currentPaper.value.id,
      classId: assignForm.classId,
      studentIds: assignForm.studentIds,
      deadline: assignForm.deadline || null
    })
    ElMessage.success(`下发成功，新增 ${count} 条记录`)
    assignDialogVisible.value = false
  } finally {
    assignSubmitting.value = false
  }
}

onMounted(() => {
  load()
  loadCourses()
})
</script>

<template>
  <div class="page-container">
    <PageHero
      title="试卷管理"
      subtitle="按「草稿 → 待考试 → 阅卷中 → 已归档」单向推进试卷状态；支持线上作答与纸质线下阅卷，线上模式必须设置开考与收卷时间。"
      icon="Document"
    >
      <template #actions>
        <el-button v-permission="'paper:add'" type="primary" @click="openCreate">新增试卷</el-button>
      </template>
    </PageHero>

    <!-- 左筛选 / 右列表 -->
    <div class="paper-layout">
      <aside class="filter-panel">
        <el-card shadow="never" class="filter-card">
          <template #header>
            <span class="panel-title">筛选试卷</span>
          </template>
          <el-form label-position="top" class="filter-form" @submit.prevent>
            <el-form-item label="关键词">
              <el-input
                v-model="query.keyword"
                placeholder="标题 / 试卷编号"
                clearable
                @keyup.enter="search"
              />
            </el-form-item>
            <el-form-item label="所属课程">
              <el-select v-model="query.courseId" placeholder="全部课程" clearable style="width: 100%">
                <el-option
                  v-for="course in courses"
                  :key="course.id"
                  :label="course.courseName"
                  :value="course.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="作答模式">
              <el-select v-model="query.answerMode" placeholder="全部模式" clearable style="width: 100%">
                <el-option label="线上作答" :value="1" />
                <el-option label="纸质线下阅卷" :value="2" />
              </el-select>
            </el-form-item>
            <el-form-item label="试卷状态">
              <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 100%">
                <el-option
                  v-for="(item, key) in PAPER_STATUS"
                  :key="key"
                  :label="item.label"
                  :value="Number(key)"
                />
              </el-select>
            </el-form-item>
            <div class="filter-actions">
              <el-button type="primary" :loading="loading" @click="search">查询</el-button>
              <el-button @click="reset">重置</el-button>
            </div>
          </el-form>
        </el-card>

        <el-card shadow="never" class="overview-card">
          <template #header>
            <span class="panel-title">状态概览</span>
          </template>
          <div class="overview-list">
            <button
              v-for="item in stats"
              :key="item.key"
              class="overview-item"
              :class="{ active: isStatActive(item) }"
              @click="onStatSelect(item)"
            >
              <span class="overview-dot" :style="{ backgroundColor: item.color }" />
              <span class="overview-label">{{ item.label }}</span>
              <b class="overview-value">{{ item.value }}</b>
            </button>
          </div>
          <p class="overview-tip">“试卷总数”为全部数据，其余按当前页统计</p>
        </el-card>
      </aside>

      <section class="content-panel">
        <el-card shadow="never">
          <template #header>
            <div class="list-head">
              <span class="panel-title">试卷列表</span>
              <span class="list-count">共 {{ total }} 份</span>
            </div>
          </template>

          <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="paperNo" label="试卷编号" width="150" />
        <el-table-column prop="paperTitle" label="试卷标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="courseName" label="所属课程" min-width="140" />
        <el-table-column label="作答模式" width="130">
          <template #default="{ row }">
            <el-tag :type="dictItem(ANSWER_MODE, row.answerMode).type">
              {{ dictItem(ANSWER_MODE, row.answerMode).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="dictItem(PAPER_STATUS, row.status).type">
              {{ dictItem(PAPER_STATUS, row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalScore" label="总分" width="80" />
        <el-table-column prop="questionCount" label="题数" width="70" />
        <el-table-column label="考试时间" width="180">
          <template #default="{ row }">
            <div v-if="row.startTime">{{ formatTime(row.startTime) }}</div>
            <div v-if="row.endTime" class="sub-text">至 {{ formatTime(row.endTime) }}</div>
            <span v-if="!row.startTime">-</span>
          </template>
        </el-table-column>
        <el-table-column label="AI阅卷" width="90">
          <template #default="{ row }">
            <el-tag :type="row.aiMarking === 1 ? 'success' : 'info'" effect="plain">
              {{ row.aiMarking === 1 ? '启用' : '仅人工' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="290" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              v-permission="'paper:edit'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="nextStatusMap[row.status]"
              v-permission="'paper:status'"
              link
              type="warning"
              @click="handleNextStatus(row)"
            >
              {{ nextStatusText[row.status] }}
            </el-button>
            <el-button
              v-if="row.status === 1 || row.status === 2"
              v-permission="'assignment:assign'"
              link
              type="success"
              @click="openAssign(row)"
            >
              下发
            </el-button>
            <span v-if="row.status === 3" class="sub-text">已归档</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无试卷数据" :image-size="80" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
        </el-card>
      </section>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑试卷（草稿）' : '新增试卷'"
      width="960px"
      top="5vh"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="试卷编号" prop="paperNo">
              <el-input v-model="form.paperNo" placeholder="如 DS-2026-MID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属课程" prop="courseId">
              <el-select v-model="form.courseId" filterable placeholder="请选择课程" style="width: 100%">
                <el-option
                  v-for="course in courses"
                  :key="course.id"
                  :label="course.courseName"
                  :value="course.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="试卷标题" prop="paperTitle">
          <el-input v-model="form.paperTitle" placeholder="如 数据结构与算法 期中测验" />
        </el-form-item>

        <el-form-item label="作答模式" prop="answerMode">
          <el-radio-group v-model="form.answerMode">
            <el-radio :value="1">线上作答</el-radio>
            <el-radio :value="2">纸质线下阅卷</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="及格分">
              <el-input-number v-model="form.passScore" :min="0" :precision="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="考试时长">
              <el-input-number
                v-model="form.duration"
                :min="1"
                :max="600"
                :disabled="form.answerMode === 2"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 纸质模式不需要考试时间 -->
        <template v-if="form.answerMode === 1">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="开考时间" prop="startTime">
                <el-date-picker
                  v-model="form.startTime"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  placeholder="选择开考时间"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="收卷时间" prop="endTime">
                <el-date-picker
                  v-model="form.endTime"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  placeholder="选择收卷时间"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <el-form-item label="AI阅卷">
          <el-radio-group v-model="form.aiMarking">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">仅人工</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="试卷说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="512" show-word-limit />
        </el-form-item>

        <el-divider content-position="left">试卷素材与题目（选填）</el-divider>

        <el-alert
          v-if="parseWarnings.length"
          type="warning"
          :closable="false"
          show-icon
          class="parse-warning"
        >
          <div v-for="(warning, index) in parseWarnings" :key="index">{{ warning }}</div>
        </el-alert>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="PDF上传">
              <el-upload
                v-model:file-list="pdfFileList"
                action="#"
                :auto-upload="false"
                :limit="1"
                accept=".pdf"
                :on-exceed="handlePdfExceed"
                :on-change="handleFileChange"
              >
                <el-button>
                  <el-icon><Upload /></el-icon>
                  <span>选择 PDF</span>
                </el-button>
              </el-upload>
              <div class="sub-text">仅 PDF，最大 20MB</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="图片上传">
              <el-upload
                v-model:file-list="imageFileList"
                action="#"
                :auto-upload="false"
                multiple
                :limit="9"
                accept=".jpg,.jpeg,.png"
                list-type="picture"
                :on-exceed="handleImageExceed"
                :on-change="handleFileChange"
              >
                <el-button>
                  <el-icon><Camera /></el-icon>
                  <span>选择图片</span>
                </el-button>
              </el-upload>
              <div class="sub-text">拍照纸质试卷，支持 jpg/png，最多 9 张</div>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="parse-bar">
          <el-button
            type="primary"
            plain
            :loading="parsing"
            :disabled="!hasSelectedFiles"
            @click="handleParse"
          >
            上传并识别题目
          </el-button>
          <span class="sub-text">识别结果仅作辅助，请核对修改后再保存</span>
        </div>

        <el-collapse v-if="extractedText" class="extracted-text">
          <el-collapse-item title="查看识别原文">
            <el-input :model-value="extractedText" type="textarea" :rows="6" readonly />
          </el-collapse-item>
        </el-collapse>

        <el-divider content-position="left">
          题目清单（{{ questionRows.length }} 题，总分 {{ questionTotalScore }}）
        </el-divider>

        <div class="question-toolbar">
          <el-button size="small" @click="addQuestionRow">新增一题</el-button>
          <span class="sub-text">题号、题干、分值为必填；主观题还需参考答案与评分细则</span>
        </div>

        <el-table :data="questionRows" border size="small" max-height="340">
          <el-table-column label="题号" width="90">
            <template #default="{ row }">
              <el-input v-model="row.questionNo" size="small" @input="markQuestionsDirty" />
            </template>
          </el-table-column>
          <el-table-column label="大题" width="110">
            <template #default="{ row }">
              <el-input v-model="row.sectionName" size="small" @input="markQuestionsDirty" />
            </template>
          </el-table-column>
          <el-table-column label="题型" width="100">
            <template #default="{ row }">
              <el-select v-model="row.questionType" size="small" @change="markQuestionsDirty">
                <el-option label="简答" :value="5" />
                <el-option label="论述" :value="6" />
                <el-option label="计算" :value="7" />
                <el-option label="案例" :value="8" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="题干" min-width="240">
            <template #default="{ row }">
              <el-input
                v-model="row.questionContent"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 4 }"
                size="small"
                @input="markQuestionsDirty"
              />
            </template>
          </el-table-column>
          <el-table-column label="分值" width="110">
            <template #default="{ row }">
              <el-input-number
                v-model="row.score"
                :min="0.5"
                :max="100"
                :precision="1"
                size="small"
                controls-position="right"
                style="width: 100%"
                @change="markQuestionsDirty"
              />
            </template>
          </el-table-column>
          <el-table-column label="参考答案" min-width="200">
            <template #default="{ row }">
              <el-input
                v-model="row.standardAnswer"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 4 }"
                size="small"
                @input="markQuestionsDirty"
              />
            </template>
          </el-table-column>
          <el-table-column label="评分细则" min-width="200">
            <template #default="{ row }">
              <el-input
                v-model="row.scoringRule"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 4 }"
                size="small"
                @input="markQuestionsDirty"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" fixed="right">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeQuestionRow($index)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无题目，可上传识别或手动新增" :image-size="60" />
          </template>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 下发试卷 -->
    <el-dialog v-model="assignDialogVisible" title="下发试卷" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="试卷">
          <el-input :model-value="currentPaper?.paperTitle" disabled />
        </el-form-item>
        <el-form-item label="下发班级">
          <el-select v-model="assignForm.classId" filterable clearable placeholder="选择班级（可不选）" style="width: 100%">
            <el-option
              v-for="clazz in classOptions"
              :key="clazz.id"
              :label="`${clazz.className}（${clazz.classCode}）`"
              :value="clazz.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="指定学生">
          <el-select
            v-model="assignForm.studentIds"
            multiple
            filterable
            placeholder="可选，单独下发给不在班内的学生"
            style="width: 100%"
          >
            <el-option
              v-for="student in allStudents"
              :key="student.studentId"
              :label="`${student.realName || student.username}（${student.username}）`"
              :value="student.studentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="assignForm.deadline"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="可不填"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignSubmitting" @click="submitAssign">确定下发</el-button>
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

/* 左筛选 / 右列表 双栏 */
.paper-layout {
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.filter-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #2b2e45;
}

.filter-form {
  padding-top: 4px;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.filter-form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  font-size: 13px;
  color: #7d829c;
}

.filter-actions {
  display: flex;
  gap: 10px;
}

.filter-actions .el-button {
  flex: 1;
  margin-left: 0;
}

.overview-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.overview-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  font-size: 13px;
  color: #5b6079;
  cursor: pointer;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-radius: 9px;
  transition: background 0.18s ease, border-color 0.18s ease;
}

.overview-item:hover {
  background: #f5f7ff;
  border-color: #d5dcfa;
}

.overview-item.active {
  color: #3a3e5c;
  background: linear-gradient(90deg, #eef1ff, #f6f2ff);
  border-color: #cdd6fa;
}

.overview-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.overview-label {
  flex: 1;
  text-align: left;
}

.overview-value {
  font-size: 15px;
  color: #23264a;
}

.overview-tip {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #a2a7bd;
}

.content-panel {
  min-width: 0;
}

.list-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.list-count {
  font-size: 12px;
  font-weight: 400;
  color: #9aa0b8;
}

@media (max-width: 1080px) {
  .paper-layout {
    grid-template-columns: 1fr;
  }
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.sub-text {
  font-size: 12px;
  color: #909399;
}

.parse-warning {
  margin-bottom: 12px;
}

.parse-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 4px 0 12px;
}

.question-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.extracted-text {
  margin-bottom: 8px;
}
</style>
