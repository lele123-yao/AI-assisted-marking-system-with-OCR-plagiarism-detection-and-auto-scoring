<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  changePaperStatus,
  createPaper,
  deletePaper,
  pagePapers,
  updatePaper
} from '@/api/paper'
import { pageCourses } from '@/api/course'
import { listClassOptions, listStudentOptions } from '@/api/class'
import { assignPaper } from '@/api/assignment'
import { useMobileList } from '@/composables/useMobileList'
import { ANSWER_MODE, PAPER_STATUS, dictItem, formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileSearchBar from '@/components/m/MobileSearchBar.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileLoadMore from '@/components/m/MobileLoadMore.vue'
import MobileSheet from '@/components/m/MobileSheet.vue'
import PaperParsePanel from '@/components/m/PaperParsePanel.vue'
import { batchSaveQuestions } from '@/api/question'

/** 试卷管理：草稿维护、状态单向推进、下发与素材解析 */
const router = useRouter()

const { query, list, loading, finished, load, loadMore, search, reset } = useMobileList(
  pagePapers,
  { keyword: '', status: '', answerMode: '' }
)
const empty = computed(() => !loading.value && !list.value.length)

// 课程下拉
const courses = ref([])
async function loadCourses() {
  const data = await pageCourses({ pageNum: 1, pageSize: 100 })
  courses.value = data?.records || []
}

// ==================== 新增 / 编辑草稿 ====================
const sheetVisible = ref(false)
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

// 素材解析：上传 → 预览 → 逐题校对，面板只维护内存数据，保存试卷时才批量入库
const parsePanelRef = ref()

function resetParseState() {
  parsePanelRef.value?.reset()
}

function onParsed(data) {
  if (data?.pdfUrl) form.pdfUrl = data.pdfUrl
  if (data?.imageUrls?.length) form.imageUrl = JSON.stringify(data.imageUrls)
}

function resetForm() {
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
  resetParseState()
}

function openCreate() {
  editingId.value = null
  resetForm()
  sheetVisible.value = true
  // 面板在弹层内，首次打开时还没挂载，等渲染后再清空上一次的解析结果
  nextTick(resetParseState)
  formRef.value?.clearValidate()
}

function openEdit(row) {
  editingId.value = row.id
  resetParseState()
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
  sheetVisible.value = true
  nextTick(resetParseState)
  formRef.value?.clearValidate()
}

async function submit() {
  if (submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const panel = parsePanelRef.value
  if (panel?.files.length && !panel.parsed) {
    ElMessage.warning('已选择文件但尚未解析，请先点击「上传并解析」')
    return
  }
  // 校对后的题目随试卷一起保存：先建/改草稿拿到 paperId，再整体批量入库
  const questions = panel?.buildQuestions() || []
  submitting.value = true
  try {
    let paperId = editingId.value
    if (paperId) {
      await updatePaper(paperId, { ...form })
    } else {
      paperId = await createPaper({ ...form })
    }
    if (questions.length) {
      await batchSaveQuestions({ paperId, questions })
    }
    ElMessage.success(
      questions.length ? `保存成功，已入库 ${questions.length} 道题` : '创建成功，当前为草稿'
    )
    sheetVisible.value = false
    resetParseState()
    load()
  } finally {
    submitting.value = false
  }
}

// ==================== 状态单向推进 ====================
const nextStatusMap = { 0: 1, 1: 2, 2: 3 }
const nextStatusText = { 0: '发布（待考试）', 1: '进入阅卷', 2: '归档' }
const statusLoadingId = ref(null)

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
  statusLoadingId.value = row.id
  try {
    await changePaperStatus(row.id, target)
    ElMessage.success('状态已更新')
    load()
  } finally {
    statusLoadingId.value = null
  }
}

function goQuestions(row) {
  router.push({ path: '/m/question', query: { paperId: row.id } })
}

// 仅草稿可删：已发布试卷与学生作答、成绩统计绑定，只能归档不能删除
const deletingId = ref(null)
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除试卷「${row.paperTitle}」？其下 ${row.questionCount ?? 0} 道题将一并删除，不可恢复。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  deletingId.value = row.id
  try {
    await deletePaper(row.id)
    ElMessage.success('删除成功')
    load()
  } finally {
    deletingId.value = null
  }
}

// ==================== 下发试卷 ====================
const assignVisible = ref(false)
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
  assignVisible.value = true
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
    assignVisible.value = false
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
  <div class="m-page">
    <MobileHero
      title="试卷管理"
      subtitle="草稿 → 待考试 → 阅卷中 → 已归档单向推进；线上作答需设置开考与收卷时间。"
      icon="Document"
    >
      <template #actions>
        <el-button size="small" type="primary" @click="openCreate">新增</el-button>
      </template>
    </MobileHero>

    <MobileSearchBar
      v-model="query.keyword"
      placeholder="试卷标题 / 编号"
      show-reset
      :loading="loading"
      @search="search"
      @reset="reset"
    >
      <el-select v-model="query.status" placeholder="全部状态" clearable @change="search">
        <el-option
          v-for="(item, key) in PAPER_STATUS"
          :key="key"
          :label="item.label"
          :value="Number(key)"
        />
      </el-select>
      <el-select v-model="query.answerMode" placeholder="全部模式" clearable @change="search">
        <el-option
          v-for="(item, key) in ANSWER_MODE"
          :key="key"
          :label="item.label"
          :value="Number(key)"
        />
      </el-select>
    </MobileSearchBar>

    <div v-loading="loading" class="list">
      <article v-for="row in list" :key="row.id" class="m-card">
        <div class="m-row-between">
          <span class="no">{{ row.paperNo }}</span>
          <el-tag :type="dictItem(PAPER_STATUS, row.status).type" size="small" round>
            {{ dictItem(PAPER_STATUS, row.status).label }}
          </el-tag>
        </div>

        <h3 class="m-title name">{{ row.paperTitle }}</h3>
        <p class="course">{{ row.courseName || '未归属课程' }}</p>

        <div class="tags">
          <el-tag
            :type="dictItem(ANSWER_MODE, row.answerMode).type"
            size="small"
            effect="plain"
          >
            {{ dictItem(ANSWER_MODE, row.answerMode).label }}
          </el-tag>
          <el-tag size="small" type="info" effect="plain">总分 {{ row.totalScore ?? '-' }}</el-tag>
          <el-tag size="small" type="info" effect="plain">{{ row.questionCount ?? 0 }} 题</el-tag>
          <el-tag
            size="small"
            effect="plain"
            :type="row.aiMarking === 1 ? 'success' : 'info'"
          >
            AI {{ row.aiMarking === 1 ? '启用' : '仅人工' }}
          </el-tag>
        </div>

        <div class="meta">
          <div><span>开考</span><b>{{ row.startTime ? formatTime(row.startTime) : '-' }}</b></div>
          <div><span>截止</span><b>{{ row.endTime ? formatTime(row.endTime) : '-' }}</b></div>
        </div>

        <div class="actions">
          <el-button v-if="row.status === 0" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="nextStatusMap[row.status]"
            size="small"
            type="warning"
            plain
            :loading="statusLoadingId === row.id"
            @click="handleNextStatus(row)"
          >
            {{ nextStatusText[row.status] }}
          </el-button>
          <el-button
            v-if="row.status === 1 || row.status === 2"
            size="small"
            type="success"
            plain
            @click="openAssign(row)"
          >
            下发
          </el-button>
          <el-button size="small" plain @click="goQuestions(row)">查看题目</el-button>
          <el-button
            v-if="row.status === 0"
            size="small"
            type="danger"
            plain
            :loading="deletingId === row.id"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </div>
      </article>

      <MobileEmpty
        v-if="empty"
        description="暂无试卷数据"
        icon="Document"
        action-text="刷新"
        @action="load"
      />

      <MobileLoadMore :loading="loading" :finished="finished" :empty="empty" @load="loadMore" />
    </div>

    <!-- 新增 / 编辑试卷 -->
    <MobileSheet
      v-model="sheetVisible"
      :title="editingId ? '编辑试卷（草稿）' : '新增试卷'"
      height="90%"
      :loading="submitting"
      @confirm="submit"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="试卷编号" prop="paperNo">
          <el-input v-model="form.paperNo" placeholder="如 DS-2026-MID" />
        </el-form-item>
        <el-form-item label="试卷标题" prop="paperTitle">
          <el-input v-model="form.paperTitle" placeholder="如 数据结构与算法 期中测验" />
        </el-form-item>
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
        <el-form-item label="作答模式" prop="answerMode">
          <el-radio-group v-model="form.answerMode">
            <el-radio :value="1">线上作答</el-radio>
            <el-radio :value="2">纸质线下阅卷</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="及格分">
          <el-input-number v-model="form.passScore" :min="0" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="考试时长（分钟）">
          <el-input-number
            v-model="form.duration"
            :min="1"
            :max="600"
            :disabled="form.answerMode === 2"
            style="width: 100%"
          />
        </el-form-item>
        <template v-if="form.answerMode === 1">
          <el-form-item label="开考时间" prop="startTime">
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="选择开考时间"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="收卷时间" prop="endTime">
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="选择收卷时间"
              style="width: 100%"
            />
          </el-form-item>
        </template>
        <el-form-item label="AI阅卷">
          <el-radio-group v-model="form.aiMarking">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">仅人工</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="试卷说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="512"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <div class="parse-section">
        <div class="parse-title">试卷素材与题目（选填）</div>
        <PaperParsePanel ref="parsePanelRef" @parsed="onParsed" />
        <p v-if="form.pdfUrl || form.imageUrl" class="hint">已保存解析素材。</p>
      </div>
    </MobileSheet>

    <!-- 下发试卷 -->
    <MobileSheet
      v-model="assignVisible"
      title="下发试卷"
      height="72%"
      confirm-text="确定下发"
      :loading="assignSubmitting"
      @confirm="submitAssign"
    >
      <p class="assign-paper">{{ currentPaper?.paperTitle }}</p>
      <el-form label-position="top">
        <el-form-item label="下发班级">
          <el-select
            v-model="assignForm.classId"
            filterable
            clearable
            placeholder="选择班级（可不选）"
            style="width: 100%"
          >
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
            placeholder="可选，单独下发"
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
    </MobileSheet>
  </div>
</template>

<style scoped>
.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.no {
  font-size: 12px;
  font-weight: 600;
  color: var(--ai-primary);
}

.name {
  margin: 8px 0 4px;
  font-size: 16px;
  line-height: 1.4;
}

.course {
  margin: 0;
  font-size: 12px;
  color: #8b90ab;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.meta {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 9px 0;
  margin-top: 8px;
  border-top: 1px dashed #f0f2fb;
}

.meta div {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #8b90ab;
}

.meta b {
  font-weight: 500;
  color: #3a3e5c;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 10px;
  margin-top: 4px;
  border-top: 1px dashed #f0f2fb;
}

.parse-section {
  padding-top: 12px;
  margin-top: 4px;
  border-top: 1px dashed #f0f2fb;
}

.parse-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #23264a;
}

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

.upload-tip {
  margin: 8px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: #9aa0bb;
}

.file-name {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--ai-primary);
  word-break: break-all;
}

.parse-btn {
  width: 100%;
  margin-top: 10px;
}

.warning {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #e6a23c;
}

.hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #9aa0b8;
}

.assign-paper {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #23264a;
}
</style>
