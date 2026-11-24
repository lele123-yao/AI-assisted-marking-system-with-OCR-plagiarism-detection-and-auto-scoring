<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
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
import { pageUsers } from '@/api/user'
import { useMobileList } from '@/composables/useMobileList'
import { useUserStore } from '@/store/user'
import { formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileSearchBar from '@/components/m/MobileSearchBar.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileLoadMore from '@/components/m/MobileLoadMore.vue'
import MobileSheet from '@/components/m/MobileSheet.vue'

/** 班级管理：班级 CRUD、学生名单、Excel 导入 */
const userStore = useUserStore()
const isAdmin = computed(() => userStore.hasRole('ADMIN'))

const { query, list, loading, finished, load, loadMore, search } = useMobileList(pageClasses, {
  keyword: ''
})
const empty = computed(() => !loading.value && !list.value.length)

// ==================== 班级新增 / 编辑 ====================
const teachers = ref([])
async function loadTeachers() {
  if (!isAdmin.value) return
  const data = await pageUsers({ pageNum: 1, pageSize: 100 })
  teachers.value = data?.records || []
}

const sheetVisible = ref(false)
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
  sheetVisible.value = true
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
  sheetVisible.value = true
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
    sheetVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

const deletingId = ref(null)
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
  deletingId.value = row.id
  try {
    await removeClass(row.id)
    ElMessage.success('删除成功')
    load()
  } finally {
    deletingId.value = null
  }
}

// ==================== 学生管理 ====================
const studentVisible = ref(false)
const currentClass = ref(null)
const students = ref([])
const studentsLoading = ref(false)
const removingId = ref(null)

async function openStudents(row) {
  currentClass.value = row
  studentVisible.value = true
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
  removingId.value = row.studentId
  try {
    await removeClassStudent(currentClass.value.id, row.studentId)
    ElMessage.success('已移出班级')
    await loadStudents()
    load()
  } finally {
    removingId.value = null
  }
}

// 添加学生
const addVisible = ref(false)
const addSubmitting = ref(false)
const optionsLoading = ref(false)
const studentOptions = ref([])
const studentKeyword = ref('')
const selectedStudentIds = ref([])

async function openAddStudents() {
  studentKeyword.value = ''
  selectedStudentIds.value = []
  addVisible.value = true
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
    addVisible.value = false
    await loadStudents()
    load()
  } finally {
    addSubmitting.value = false
  }
}

// ==================== 名单导入 ====================
const importVisible = ref(false)
const importSubmitting = ref(false)
const autoCreateUser = ref(true)
const importFile = ref(null)
const importResult = ref(null)
const importInputRef = ref()

function openImport() {
  autoCreateUser.value = true
  importFile.value = null
  importResult.value = null
  importVisible.value = true
  if (importInputRef.value) importInputRef.value.value = ''
}

function onImportFileChange(event) {
  importFile.value = event.target.files?.[0] || null
  importResult.value = null
}

async function submitImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  importSubmitting.value = true
  try {
    const result = await importClassStudents(
      currentClass.value.id,
      importFile.value,
      autoCreateUser.value
    )
    importResult.value = result
    ElMessage.success(
      `导入完成：成功 ${result.successCount}，跳过 ${result.skippedCount}，失败 ${result.failedCount}`
    )
    await loadStudents()
    load()
  } finally {
    importSubmitting.value = false
  }
}

onMounted(() => {
  load()
  loadTeachers()
})
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="班级管理"
      subtitle="按班级维护学生名单并支持 Excel 导入；点击卡片查看班级学生。"
      icon="School"
    >
      <template #actions>
        <el-button size="small" type="primary" @click="openCreate">新增</el-button>
      </template>
    </MobileHero>

    <MobileSearchBar
      v-model="query.keyword"
      placeholder="班级名称 / 编码"
      :loading="loading"
      @search="search"
    />

    <div v-loading="loading" class="list">
      <article
        v-for="row in list"
        :key="row.id"
        class="m-card class-card"
        @click="openStudents(row)"
      >
        <div class="m-row-between">
          <span class="code">{{ row.classCode }}</span>
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" round>
            {{ row.status === 1 ? '正常' : '停用' }}
          </el-tag>
        </div>

        <h3 class="m-title name">{{ row.className }}</h3>

        <div class="meta">
          <span>{{ row.grade || '未设置年级' }}</span>
          <span>{{ row.studentCount ?? 0 }} 人</span>
          <span>{{ row.teacherName || row.teacherId || '未指派教师' }}</span>
        </div>

        <p class="desc m-clamp-2">{{ row.description || '暂无班级简介' }}</p>

        <div class="actions" @click.stop>
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
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
        description="暂无班级数据"
        icon="School"
        action-text="刷新"
        @action="load"
      />

      <MobileLoadMore :loading="loading" :finished="finished" :empty="empty" @load="loadMore" />
    </div>

    <!-- 新增 / 编辑班级 -->
    <MobileSheet
      v-model="sheetVisible"
      :title="editingId ? '编辑班级' : '新增班级'"
      height="86%"
      :loading="submitting"
      @confirm="submit"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
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
          <el-select
            v-model="form.teacherId"
            filterable
            clearable
            placeholder="不选则默认为自己"
            style="width: 100%"
          >
            <el-option
              v-for="teacher in teachers"
              :key="teacher.id"
              :label="`${teacher.nickname || teacher.username}（${teacher.username}）`"
              :value="teacher.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班级简介">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="512"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </MobileSheet>

    <!-- 班级学生抽屉 -->
    <el-drawer
      v-model="studentVisible"
      direction="btt"
      size="82%"
      class="m-sheet"
      :title="`班级学生 - ${currentClass?.className || ''}`"
      append-to-body
    >
      <div class="drawer-tools">
        <el-button size="small" type="primary" @click="openAddStudents">添加学生</el-button>
        <el-button size="small" @click="openImport">名单导入</el-button>
        <span class="hint">共 {{ students.length }} 人</span>
      </div>

      <div v-loading="studentsLoading" class="student-list">
        <div v-for="student in students" :key="student.studentId" class="m-item">
          <div class="stu-main">
            <div class="stu-name">{{ student.realName || student.username }}</div>
            <div class="stu-meta">
              {{ student.username }} · 加入 {{ formatTime(student.joinTime) }}
            </div>
          </div>
          <el-button
            link
            type="danger"
            :loading="removingId === student.studentId"
            @click="handleRemoveStudent(student)"
          >
            移出
          </el-button>
        </div>

        <MobileEmpty
          v-if="!studentsLoading && !students.length"
          description="班级暂无学生"
          icon="User"
        />
      </div>
    </el-drawer>

    <!-- 添加学生 -->
    <MobileSheet
      v-model="addVisible"
      title="添加学生"
      height="72%"
      :loading="addSubmitting"
      @confirm="submitAddStudents"
    >
      <div class="add-tools">
        <el-input
          v-model="studentKeyword"
          placeholder="搜索姓名 / 账号"
          clearable
          @keyup.enter="loadStudentOptions"
        />
        <el-button :loading="optionsLoading" @click="loadStudentOptions">搜索</el-button>
      </div>
      <p class="hint">
        只显示尚未加入任何班级的学生；如需转班，请先在原班级移出。
      </p>
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
    </MobileSheet>

    <!-- 名单导入 -->
    <MobileSheet
      v-model="importVisible"
      title="名单导入"
      height="80%"
      confirm-text="开始导入"
      :loading="importSubmitting"
      @confirm="submitImport"
    >
      <p class="hint">
        Excel 第一行表头必须为「学号」「姓名」，一行一名学生；单行失败不会中断整批导入。
      </p>
      <el-switch v-model="autoCreateUser" active-text="自动创建缺失学生账号" />
      <p class="hint">
        勾选后，名单内不存在的学号会自动生成学生账号，初始密码为学号后 6 位。
      </p>

      <input
        ref="importInputRef"
        type="file"
        accept=".xls,.xlsx"
        class="file-input"
        @change="onImportFileChange"
      />
      <p v-if="importFile" class="file-name">已选择：{{ importFile.name }}</p>

      <div v-if="importResult" class="import-result">
        <div class="result-grid">
          <span>数据行 <b>{{ importResult.totalRows }}</b></span>
          <span>成功 <b>{{ importResult.successCount }}</b></span>
          <span>跳过 <b>{{ importResult.skippedCount }}</b></span>
          <span>失败 <b>{{ importResult.failedCount }}</b></span>
          <span>新建账号 <b>{{ importResult.createdCount }}</b></span>
        </div>
        <div v-if="importResult.failures?.length" class="failures">
          <div class="failure-head">失败明细（{{ importResult.failures.length }} 条）</div>
          <div v-for="item in importResult.failures" :key="item.rowNum" class="failure-item">
            第 {{ item.rowNum }} 行 · {{ item.studentNo }} {{ item.studentName }}：{{ item.reason }}
          </div>
        </div>
      </div>
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

.class-card {
  cursor: pointer;
}

.class-card:active {
  transform: scale(0.995);
}

.code {
  display: inline-flex;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ai-primary);
  background: #eef1ff;
  border-radius: 999px;
}

.name {
  margin: 9px 0 8px;
  font-size: 16px;
  line-height: 1.4;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  font-size: 12px;
  color: #7a7f9a;
}

.desc {
  margin: 9px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #9aa0b8;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 10px;
  margin-top: 10px;
  border-top: 1px dashed #f0f2fb;
}

.drawer-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: #8b90ab;
}

.drawer-tools .hint {
  margin-left: auto;
}

.student-list {
  min-height: 120px;
}

.stu-main {
  min-width: 0;
}

.stu-name {
  font-size: 14px;
  font-weight: 600;
  color: #23264a;
}

.stu-meta {
  margin-top: 3px;
  font-size: 12px;
  color: #9aa0b8;
}

.add-tools {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.add-tools .el-input {
  flex: 1;
}

.add-tools .el-button {
  flex-shrink: 0;
}

.file-input {
  width: 100%;
  padding: 12px;
  margin: 12px 0 6px;
  font-size: 13px;
  background: #f7f8ff;
  border: 1px dashed var(--ai-border);
  border-radius: 10px;
}

.file-name {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--ai-primary);
  word-break: break-all;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.result-grid span {
  padding: 8px;
  font-size: 12px;
  color: #8b90ab;
  text-align: center;
  background: #f7f8ff;
  border-radius: 8px;
}

.result-grid b {
  display: block;
  margin-top: 3px;
  font-size: 16px;
  color: #23264a;
}

.failures {
  margin-top: 12px;
}

.failure-head {
  margin-bottom: 6px;
  font-size: 12px;
  color: #f56c6c;
}

.failure-item {
  font-size: 12px;
  line-height: 1.7;
  color: #7a7f9a;
  word-break: break-all;
}
</style>
