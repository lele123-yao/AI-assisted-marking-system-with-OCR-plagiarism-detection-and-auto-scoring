<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addClass,
  addClassStudents,
  importClassStudents,
  listClassStudents,
  listStudentOptions,
  pageClasses,
  removeClass,
  removeClassStudent,
  updateClass
} from '@/api/class'
import { pagePapers } from '@/api/paper'
import { assignPaper } from '@/api/assignment'
import { pageUsers } from '@/api/user'
import PageHero from '@/components/PageHero.vue'
import { useTable } from '@/composables/useTable'
import { useUserStore } from '@/store/user'
import { formatTime } from '@/utils/dict'

/** 班级管理：班级 CRUD、学生管理、下发试卷 */
const userStore = useUserStore()
const isAdmin = computed(() => userStore.hasRole('ADMIN'))

const { loading, list, total, query, load, search, reset, handlePageChange } = useTable(
  pageClasses,
  { keyword: '', status: '' }
)

/** 主从布局：左侧列表选中项；列表刷新后保持选中有效，并默认选中第一行 */
const selectedClass = ref(null)

watch(
  list,
  (rows) => {
    if (!rows?.length) {
      selectedClass.value = null
      return
    }
    const currentId = selectedClass.value?.id
    const matched = currentId != null ? rows.find((row) => row.id === currentId) : null
    selectedClass.value = matched || rows[0]
  },
  { immediate: true }
)

function selectClass(row) {
  selectedClass.value = row
}

// ==================== 班级新增 / 编辑 ====================
const teachers = ref([])
async function loadTeachers() {
  if (!isAdmin.value) return
  const data = await pageUsers({ pageNum: 1, pageSize: 100 })
  teachers.value = data?.records || []
}

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({
  classCode: '',
  className: '',
  grade: '',
  teacherId: null,
  description: '',
  status: 1
})
const rules = {
  classCode: [
    { required: true, message: '请输入班级编码', trigger: 'blur' },
    { max: 32, message: '班级编码最长 32 位', trigger: 'blur' }
  ],
  className: [{ required: true, message: '请输入班级名称', trigger: 'blur' }]
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    classCode: '',
    className: '',
    grade: '',
    teacherId: null,
    description: '',
    status: 1
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    classCode: row.classCode,
    className: row.className,
    grade: row.grade || '',
    teacherId: row.teacherId,
    description: row.description || '',
    status: row.status ?? 1
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submit() {
  if (submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingId.value) {
      await updateClass(editingId.value, { ...form })
      ElMessage.success('修改成功')
    } else {
      await addClass({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除班级「${row.className}」？班级学生关系将一并移除。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await removeClass(row.id)
  ElMessage.success('删除成功')
  load()
}

// ==================== 学生管理 ====================
const studentDialogVisible = ref(false)
const currentClass = ref(null)
const students = ref([])
const studentsLoading = ref(false)

async function openStudents(row) {
  currentClass.value = row
  studentDialogVisible.value = true
  await loadStudents()
}

async function loadStudents() {
  if (!currentClass.value) return
  studentsLoading.value = true
  try {
    students.value = (await listClassStudents(currentClass.value.id)) || []
  } finally {
    studentsLoading.value = false
  }
}

const addDialogVisible = ref(false)
const addSubmitting = ref(false)
const studentOptions = ref([])
const optionsLoading = ref(false)
const studentKeyword = ref('')
const selectedStudentIds = ref([])

async function openAddStudents() {
  studentKeyword.value = ''
  selectedStudentIds.value = []
  addDialogVisible.value = true
  await loadStudentOptions()
}

async function loadStudentOptions() {
  optionsLoading.value = true
  try {
    studentOptions.value =
      (await listStudentOptions({
        onlyAvailable: true,
        keyword: studentKeyword.value || undefined
      })) || []
  } finally {
    optionsLoading.value = false
  }
}

async function submitAddStudents() {
  if (!selectedStudentIds.value.length) {
    ElMessage.warning('请至少选择一名学生')
    return
  }
  addSubmitting.value = true
  try {
    const count = await addClassStudents(currentClass.value.id, selectedStudentIds.value)
    ElMessage.success(`已添加 ${count} 名学生`)
    addDialogVisible.value = false
    await loadStudents()
    load()
  } finally {
    addSubmitting.value = false
  }
}

async function handleRemoveStudent(row) {
  try {
    await ElMessageBox.confirm(
      `确认将「${row.realName || row.username}」移出班级？移出后整班下发的试卷对该生不再可见。`,
      '移出确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await removeClassStudent(currentClass.value.id, row.studentId)
  ElMessage.success('已移出班级')
  await loadStudents()
  load()
}

// ==================== 名单导入 ====================
const importDialogVisible = ref(false)
const importSubmitting = ref(false)
const autoCreateUser = ref(true)
const importFileList = ref([])
const importResult = ref(null)

function openImport() {
  autoCreateUser.value = true
  importFileList.value = []
  importResult.value = null
  importDialogVisible.value = true
}

function handleImportFileChange(file) {
  // 只保留最后一次选择的文件，同时清空上一次导入结果
  importFileList.value = [file]
  importResult.value = null
}

function handleImportExceed(files) {
  const file = files[0]
  importFileList.value = [{ name: file.name, raw: file, uid: Date.now() }]
  importResult.value = null
}

async function submitImport() {
  const raw = importFileList.value[0]?.raw
  if (!raw) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  importSubmitting.value = true
  try {
    importResult.value = await importClassStudents(currentClass.value.id, raw, autoCreateUser.value)
    const result = importResult.value
    ElMessage.success(`导入完成：成功 ${result.successCount}，跳过 ${result.skippedCount}，失败 ${result.failedCount}`)
    await loadStudents()
    load()
  } finally {
    importSubmitting.value = false
  }
}

/** 失败明细导出为 CSV（带 BOM，Excel 直接打开不乱码） */
function downloadFailures() {
  const failures = importResult.value?.failures || []
  if (!failures.length) return
  const escape = (value) => `"${String(value ?? '').replace(/"/g, '""')}"`
  const lines = [
    ['Excel行号', '学号', '姓名', '失败原因'],
    ...failures.map((item) => [item.rowNum, item.studentNo, item.studentName, item.reason])
  ].map((row) => row.map(escape).join(','))
  const blob = new Blob(['\ufeff' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `班级名单导入失败明细_${currentClass.value?.className || ''}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

// ==================== 下发试卷 ====================
const assignDialogVisible = ref(false)
const assignSubmitting = ref(false)
const paperOptions = ref([])
const assignStudents = ref([])
const assignForm = reactive({
  paperId: null,
  classId: null,
  studentIds: [],
  deadline: ''
})

async function openAssign(row) {
  currentClass.value = row
  Object.assign(assignForm, {
    paperId: null,
    classId: row.id,
    studentIds: [],
    deadline: ''
  })
  assignDialogVisible.value = true
  const [papers, options] = await Promise.all([
    pagePapers({ pageNum: 1, pageSize: 200 }).catch(() => null),
    listStudentOptions({ onlyAvailable: false }).catch(() => [])
  ])
  paperOptions.value = (papers?.records || []).filter((paper) => [1, 2].includes(paper.status))
  assignStudents.value = options || []
}

async function submitAssign() {
  if (!assignForm.paperId) {
    ElMessage.warning('请选择要下发的试卷')
    return
  }
  assignSubmitting.value = true
  try {
    const count = await assignPaper({
      paperId: assignForm.paperId,
      classId: assignForm.classId,
      studentIds: assignForm.studentIds,
      deadline: assignForm.deadline || null
    })
    ElMessage.success(`下发成功，新增 ${count} 条记录`)
    assignDialogVisible.value = false
    load()
  } finally {
    assignSubmitting.value = false
  }
}

onMounted(() => {
  load()
  loadTeachers()
})
</script>

<template>
  <div class="page-container">
    <PageHero
      title="班级管理"
      subtitle="按班级维护学生名单并整班下发试卷；支持 Excel 导入名单与自动创建缺失账号，转班需先在原班级移出学生。"
      icon="School"
    >
      <template #actions>
        <el-button v-permission="'class:add'" type="primary" @click="openCreate">新增班级</el-button>
      </template>
    </PageHero>

    <div class="split-layout">
      <!-- 左侧：班级列表 -->
      <el-card shadow="never" class="list-panel">
        <div class="filter-bar">
          <el-input
            v-model="query.keyword"
            placeholder="班级名称 / 编码"
            clearable
            @keyup.enter="search"
          />
          <div class="filter-row">
            <el-select v-model="query.status" placeholder="状态" clearable class="status-select">
              <el-option label="正常" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
            <el-button type="primary" :loading="loading" @click="search">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </div>
        </div>

        <div v-loading="loading" class="class-list">
          <div
            v-for="row in list"
            :key="row.id"
            class="class-item"
            :class="{ active: selectedClass?.id === row.id }"
            @click="selectClass(row)"
          >
            <div class="item-head">
              <span class="item-name">{{ row.className }}</span>
              <span
                class="status-dot"
                :class="row.status === 1 ? 'on' : 'off'"
                :title="row.status === 1 ? '正常' : '停用'"
              ></span>
            </div>
            <div class="item-code">{{ row.classCode }}</div>
            <div class="item-meta">
              <span>{{ row.grade || '未设置年级' }}</span>
              <span>{{ row.teacherName || row.teacherId || '未指派教师' }}</span>
              <span>{{ row.studentCount ?? 0 }} 人</span>
            </div>
          </div>

          <el-empty v-if="!loading && !list.length" description="暂无班级" :image-size="70" />
        </div>

        <div class="list-pager">
          <el-pagination
            size="small"
            :current-page="query.pageNum"
            :page-size="query.pageSize"
            :pager-count="5"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="handlePageChange"
          />
        </div>
      </el-card>

      <!-- 右侧：班级详情 -->
      <el-card shadow="never" class="detail-panel">
        <template v-if="selectedClass">
          <div class="detail-head">
            <div class="detail-title">
              <h2>{{ selectedClass.className }}</h2>
              <span class="detail-code">{{ selectedClass.classCode }}</span>
            </div>
            <el-tag :type="selectedClass.status === 1 ? 'success' : 'info'" round>
              {{ selectedClass.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </div>

          <div class="meta-grid">
            <div class="meta-cell">
              <span class="meta-label">年级</span>
              <span class="meta-value">{{ selectedClass.grade || '-' }}</span>
            </div>
            <div class="meta-cell">
              <span class="meta-label">任课教师</span>
              <span class="meta-value">
                {{ selectedClass.teacherName || selectedClass.teacherId || '-' }}
              </span>
            </div>
            <div class="meta-cell">
              <span class="meta-label">学生数</span>
              <span class="meta-value">{{ selectedClass.studentCount ?? 0 }} 人</span>
            </div>
            <div class="meta-cell">
              <span class="meta-label">创建时间</span>
              <span class="meta-value">{{ formatTime(selectedClass.createTime) }}</span>
            </div>
          </div>

          <div class="detail-section">
            <h3>班级简介</h3>
            <p>{{ selectedClass.description || '暂无班级简介' }}</p>
          </div>

          <div class="detail-actions">
            <el-button v-permission="'class:edit'" type="primary" @click="openEdit(selectedClass)">
              编辑
            </el-button>
            <el-button v-permission="'class:edit'" @click="openStudents(selectedClass)">
              学生管理
            </el-button>
            <el-button
              v-permission="'assignment:assign'"
              type="success"
              @click="openAssign(selectedClass)"
            >
              下发试卷
            </el-button>
            <el-button
              v-permission="'class:delete'"
              type="danger"
              plain
              @click="handleDelete(selectedClass)"
            >
              删除
            </el-button>
          </div>
        </template>
        <el-empty v-else description="请从左侧选择一个班级查看详情" :image-size="90" />
      </el-card>
    </div>

    <!-- 新增 / 编辑班级 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑班级' : '新增班级'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="班级编码" prop="classCode">
          <el-input v-model="form.classCode" placeholder="如 CS-2023-1" />
        </el-form-item>
        <el-form-item label="班级名称" prop="className">
          <el-input v-model="form.className" placeholder="如 计算机 2023 级 1 班" />
        </el-form-item>
        <el-form-item label="年级">
          <el-input v-model="form.grade" placeholder="如 2023" />
        </el-form-item>
        <el-form-item v-if="isAdmin" label="任课教师">
          <el-select v-model="form.teacherId" filterable clearable placeholder="不选则默认为自己" style="width: 100%">
            <el-option
              v-for="teacher in teachers"
              :key="teacher.id"
              :label="`${teacher.nickname || teacher.username}（${teacher.username}）`"
              :value="teacher.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班级简介">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="512" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 学生管理 -->
    <el-dialog
      v-model="studentDialogVisible"
      :title="`学生管理 - ${currentClass?.className || ''}`"
      width="720px"
      destroy-on-close
    >
      <div class="toolbar">
        <el-button v-permission="'class:edit'" type="primary" @click="openAddStudents">
          添加学生
        </el-button>
        <el-button v-permission="'class:edit'" @click="openImport">名单导入</el-button>
        <span class="sub-text">共 {{ students.length }} 名学生</span>
      </div>
      <el-table v-loading="studentsLoading" :data="students" border stripe max-height="420">
        <el-table-column prop="realName" label="姓名" width="130" />
        <el-table-column prop="username" label="账号（学号）" min-width="140" />
        <el-table-column label="加入时间" width="170">
          <template #default="{ row }">{{ formatTime(row.joinTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleRemoveStudent(row)">移出</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="班级暂无学生" :image-size="70" />
        </template>
      </el-table>
    </el-dialog>

    <!-- 添加学生 -->
    <el-dialog v-model="addDialogVisible" title="添加学生" width="560px" destroy-on-close>
      <div class="toolbar">
        <el-input
          v-model="studentKeyword"
          placeholder="搜索姓名 / 账号"
          clearable
          style="width: 240px"
          @keyup.enter="loadStudentOptions"
        />
        <el-button :loading="optionsLoading" @click="loadStudentOptions">搜索</el-button>
      </div>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="只显示尚未加入任何班级的学生；如需转班，请先在原班级移出"
        class="mb-12"
      />
      <el-select
        v-model="selectedStudentIds"
        multiple
        filterable
        placeholder="请选择学生"
        style="width: 100%"
        :loading="optionsLoading"
      >
        <el-option
          v-for="student in studentOptions"
          :key="student.studentId"
          :label="`${student.realName || student.username}（${student.username}）`"
          :value="student.studentId"
        />
      </el-select>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addSubmitting" @click="submitAddStudents">确定</el-button>
      </template>
    </el-dialog>

    <!-- 名单导入 -->
    <el-dialog
      v-model="importDialogVisible"
      :title="`名单导入 - ${currentClass?.className || ''}`"
      width="720px"
      destroy-on-close
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="Excel 第一行表头必须为「学号」「姓名」，一行一名学生；单行失败不会中断整批导入"
        class="mb-12"
      />
      <el-checkbox v-model="autoCreateUser" class="mb-8">自动创建缺失学生账号</el-checkbox>
      <div class="sub-text mb-12">
        勾选后，名单内不存在的学号会自动生成学生账号，初始密码为学号后 6 位；取消勾选则这些学号会被标记为失败。
      </div>
      <el-upload
        v-model:file-list="importFileList"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="handleImportFileChange"
        :on-exceed="handleImportExceed"
        drag
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将 Excel 拖到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx / .xls，文件不超过 10MB</div>
        </template>
      </el-upload>

      <div v-if="importResult" class="import-result">
        <el-descriptions :column="5" border size="small">
          <el-descriptions-item label="数据行">{{ importResult.totalRows }}</el-descriptions-item>
          <el-descriptions-item label="成功">{{ importResult.successCount }}</el-descriptions-item>
          <el-descriptions-item label="跳过">{{ importResult.skippedCount }}</el-descriptions-item>
          <el-descriptions-item label="失败">{{ importResult.failedCount }}</el-descriptions-item>
          <el-descriptions-item label="新建账号">{{ importResult.createdCount }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="importResult.failures.length" class="failure-block">
          <div class="failure-head">
            <span>失败明细（{{ importResult.failures.length }} 条）</span>
            <el-button link type="primary" @click="downloadFailures">下载失败清单</el-button>
          </div>
          <el-table :data="importResult.failures" border size="small" max-height="240">
            <el-table-column prop="rowNum" label="行号" width="70" />
            <el-table-column prop="studentNo" label="学号" width="130" />
            <el-table-column prop="studentName" label="姓名" width="110" />
            <el-table-column prop="reason" label="失败原因" min-width="220" />
          </el-table>
        </div>
      </div>

      <template #footer>
        <el-button @click="importDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importSubmitting" @click="submitImport">开始导入</el-button>
      </template>
    </el-dialog>

    <!-- 下发试卷 -->
    <el-dialog v-model="assignDialogVisible" title="下发试卷" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="下发班级">
          <el-input :model-value="currentClass?.className" disabled />
        </el-form-item>
        <el-form-item label="选择试卷" required>
          <el-select v-model="assignForm.paperId" filterable placeholder="仅显示待考试 / 阅卷中的试卷" style="width: 100%">
            <el-option
              v-for="paper in paperOptions"
              :key="paper.id"
              :label="`${paper.paperTitle}（${paper.paperNo}）`"
              :value="paper.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="额外指定学生">
          <el-select
            v-model="assignForm.studentIds"
            multiple
            filterable
            placeholder="可选，不在本班的学生可单独指定"
            style="width: 100%"
          >
            <el-option
              v-for="student in assignStudents"
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

/* 主从两栏布局 */
.split-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.list-panel :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-bar {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-select {
  flex: 1;
  min-width: 0;
}

.class-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 520px;
  min-height: 120px;
  padding-right: 4px;
  overflow-y: auto;
}

.class-item {
  padding: 12px 14px;
  cursor: pointer;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 10px;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}

.class-item:hover {
  background: #fafbff;
  border-color: #c9d1f8;
}

.class-item.active {
  background: var(--ai-gradient-soft);
  border-color: var(--ai-primary);
  box-shadow: 0 6px 16px rgba(91, 108, 255, 0.14);
}

.item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.item-name {
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: #2b2e45;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.status-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.on {
  background: #67c23a;
  box-shadow: 0 0 0 3px rgba(103, 194, 58, 0.14);
}

.status-dot.off {
  background: #c0c4cc;
  box-shadow: 0 0 0 3px rgba(192, 196, 204, 0.16);
}

.item-code {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ai-primary);
}

.item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 10px;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.list-pager {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}

/* 右侧详情 */
.detail-panel {
  min-height: 320px;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eef1fb;
}

.detail-title h2 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
  color: #2b2e45;
}

.detail-code {
  display: inline-flex;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ai-primary);
  background: #eef1ff;
  border-radius: 999px;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin: 18px 0;
}

.meta-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  background: #f8f9ff;
  border: 1px solid var(--ai-border);
  border-radius: 10px;
}

.meta-label {
  font-size: 12px;
  color: #909399;
}

.meta-value {
  font-size: 15px;
  font-weight: 600;
  color: #2b2e45;
}

.detail-section h3 {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #2b2e45;
}

.detail-section p {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: #606266;
  white-space: pre-wrap;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px dashed #eef1fb;
}

.detail-actions .el-button + .el-button {
  margin-left: 0;
}

@media (max-width: 1080px) {
  .split-layout {
    grid-template-columns: 1fr;
  }

  .class-list {
    max-height: 320px;
  }
}

/* 弹窗内的工具栏（学生管理 / 添加学生） */
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.sub-text {
  font-size: 12px;
  color: #909399;
}

.mb-12 {
  margin-bottom: 12px;
}

.mb-8 {
  margin-bottom: 8px;
}

.import-result {
  margin-top: 16px;
}

.failure-block {
  margin-top: 12px;
}

.failure-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
}
</style>
