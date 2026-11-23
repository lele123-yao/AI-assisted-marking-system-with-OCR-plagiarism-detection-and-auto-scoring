<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addCourse, archiveCourse, pageCourses, updateCourse } from '@/api/course'
import { pageUsers } from '@/api/user'
import PageHero from '@/components/PageHero.vue'
import { useTable } from '@/composables/useTable'
import { useUserStore } from '@/store/user'
import { formatTime } from '@/utils/dict'

/** 课程管理：查询、新增、编辑、归档 */
const userStore = useUserStore()
const isAdmin = computed(() => userStore.hasRole('ADMIN'))

const { loading, list, total, query, load, search, reset, handleSizeChange, handlePageChange } =
  useTable(pageCourses, { keyword: '', status: '' })

/** 看板统计：总数取后端 total，其余指标按当前页数据计算 */
const stats = computed(() => [
  { label: '课程总数', value: total.value, icon: 'Notebook', color: '#409eff', bg: '#ecf5ff' },
  {
    label: '正常课程',
    value: list.value.filter((row) => row.status === 1).length,
    icon: 'CircleCheck',
    color: '#67c23a',
    bg: '#f0f9eb',
    hint: '当前页 · 点击筛选',
    clickable: true,
    key: 'active'
  },
  {
    label: '停用课程',
    value: list.value.filter((row) => row.status !== 1).length,
    icon: 'CircleClose',
    color: '#909399',
    bg: '#f4f4f5',
    hint: '当前页 · 点击筛选',
    clickable: true,
    key: 'disabled'
  },
  {
    label: '任课教师',
    value: new Set(
      list.value.map((row) => row.teacherId || row.teacherName).filter(Boolean)
    ).size,
    icon: 'User',
    color: '#9254de',
    bg: '#f9f0ff',
    hint: '当前页去重'
  }
])

/** 点击「正常 / 停用」卡片时同步状态筛选并重新查询 */
function onStatSelect(item) {
  if (item.key === 'active') query.status = 1
  if (item.key === 'disabled') query.status = 0
  search()
}

// 任课教师候选：仅管理员可查询用户列表；教师创建课程时默认使用本人
const teachers = ref([])
async function loadTeachers() {
  if (!isAdmin.value) return
  const data = await pageUsers({ pageNum: 1, pageSize: 100 })
  const all = data?.records || []
  const filtered = all.filter((user) =>
    (user.roles || []).some((role) => ['TEACHER', 'ADMIN'].includes(role.roleCode))
  )
  teachers.value = filtered.length ? filtered : all
}

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({
  courseCode: '',
  courseName: '',
  teacherId: null,
  term: '',
  credit: null,
  description: '',
  status: 1
})
const rules = {
  courseCode: [
    { required: true, message: '请输入课程编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]{2,32}$/,
      message: '只能由字母、数字、下划线、中划线组成，长度 2-32 位',
      trigger: 'blur'
    }
  ],
  courseName: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  teacherId: [{ required: true, message: '请选择任课教师', trigger: 'change' }]
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    courseCode: '',
    courseName: '',
    // 教师无用户查询权限，新增课程默认由本人担任
    teacherId: isAdmin.value ? null : userStore.userInfo?.id ?? null,
    term: '',
    credit: null,
    description: '',
    status: 1
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    courseCode: row.courseCode,
    courseName: row.courseName,
    teacherId: row.teacherId,
    term: row.term || '',
    credit: row.credit,
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
      await updateCourse(editingId.value, { ...form })
      ElMessage.success('修改成功')
    } else {
      await addCourse({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function handleArchive(row) {
  try {
    await ElMessageBox.confirm(
      `确认归档课程「${row.courseName}」？课程下仍有未归档试卷时会被后端拒绝。`,
      '归档确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await archiveCourse(row.id)
  ElMessage.success('归档成功')
  load()
}

onMounted(() => {
  load()
  loadTeachers()
})
</script>

<template>
  <div class="page-container">
    <PageHero
      title="课程管理"
      subtitle="维护课程编码、任课教师与开课学期；仍有未归档试卷的课程不能归档，归档后不再参与新的试卷关联。"
      icon="Notebook"
    >
      <template #actions>
        <el-button v-permission="'course:add'" type="primary" @click="openCreate">新增课程</el-button>
      </template>
    </PageHero>

    <!-- 统计横幅：总数 / 正常 / 停用 / 任课教师 -->
    <div class="stat-ribbon">
      <div
        v-for="item in stats"
        :key="item.label"
        class="stat-chip"
        :class="{ clickable: item.clickable }"
        :title="item.hint || item.label"
        @click="item.clickable && onStatSelect(item)"
      >
        <span class="chip-icon" :style="{ color: item.color, backgroundColor: item.bg }">
          <el-icon :size="16"><component :is="item.icon" /></el-icon>
        </span>
        <span class="chip-text">
          <span class="chip-value">{{ item.value }}</span>
          <span class="chip-label">{{ item.label }}</span>
        </span>
      </div>
    </div>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="课程名称 / 编码"
          clearable
          style="width: 220px"
          @keyup.enter="search"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
          <el-option label="正常" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </el-card>

    <!-- 课程卡片画廊 -->
    <section v-loading="loading" class="course-section">
      <div v-if="list.length" class="course-grid">
        <article v-for="row in list" :key="row.id" class="course-card">
          <div class="card-top">
            <span class="course-code">{{ row.courseCode }}</span>
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" round>
              {{ row.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </div>

          <h3 class="course-name" :title="row.courseName">{{ row.courseName }}</h3>

          <div class="course-meta">
            <span class="meta-item">
              <el-icon><User /></el-icon>
              {{ row.teacherName || row.teacherId || '未指派教师' }}
            </span>
            <span class="meta-item">
              <el-icon><Calendar /></el-icon>
              {{ row.term || '未设置学期' }}
            </span>
            <span class="meta-item">
              <el-icon><Reading /></el-icon>
              {{ row.credit ?? '-' }} 学分
            </span>
          </div>

          <p class="course-desc">{{ row.description || '暂无课程简介' }}</p>

          <div class="card-footer">
            <span class="create-time">{{ formatTime(row.createTime) }}</span>
            <div class="card-actions">
              <el-button v-permission="'course:edit'" link type="primary" @click="openEdit(row)">
                编辑
              </el-button>
              <el-button v-permission="'course:archive'" link type="warning" @click="handleArchive(row)">
                归档
              </el-button>
            </div>
          </div>
        </article>
      </div>

      <el-empty v-else description="暂无课程数据" :image-size="90" />

      <div class="gallery-pager">
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
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑课程' : '新增课程'"
      width="600px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="课程编码" prop="courseCode">
          <el-input v-model="form.courseCode" placeholder="如 CS101" />
        </el-form-item>
        <el-form-item label="课程名称" prop="courseName">
          <el-input v-model="form.courseName" placeholder="如 数据结构与算法" />
        </el-form-item>
        <el-form-item v-if="isAdmin" label="任课教师" prop="teacherId">
          <el-select v-model="form.teacherId" filterable placeholder="请选择教师" style="width: 100%">
            <el-option
              v-for="teacher in teachers"
              :key="teacher.id"
              :label="`${teacher.nickname || teacher.username}（${teacher.username}）`"
              :value="teacher.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开课学期">
          <el-input v-model="form.term" placeholder="如 2026-2027-1" />
        </el-form-item>
        <el-form-item label="学分">
          <el-input-number v-model="form.credit" :min="0" :max="99.9" :precision="1" />
        </el-form-item>
        <el-form-item label="课程简介">
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
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 统计横幅 */
.stat-ribbon {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.stat-chip {
  display: flex;
  flex: 1 1 180px;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 12px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.stat-chip.clickable {
  cursor: pointer;
}

.stat-chip.clickable:hover {
  border-color: var(--ai-primary);
  box-shadow: 0 8px 20px rgba(91, 108, 255, 0.14);
  transform: translateY(-1px);
}

.chip-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  flex-shrink: 0;
}

.chip-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.chip-value {
  font-size: 19px;
  font-weight: 600;
  color: #2b2e45;
}

.chip-label {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

/* 卡片画廊 */
.course-section {
  min-height: 200px;
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.course-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 12px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.course-card:hover {
  border-color: #dfe4f6;
  box-shadow: var(--ai-shadow);
  transform: translateY(-2px);
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.course-code {
  display: inline-flex;
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
  color: var(--ai-primary);
  background: #eef1ff;
  border-radius: 999px;
}

.course-name {
  margin: 0;
  overflow: hidden;
  font-size: 16px;
  font-weight: 600;
  color: #2b2e45;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.course-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  font-size: 12px;
  color: #7a7f9a;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.course-desc {
  display: -webkit-box;
  min-height: 38px;
  margin: 0;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.6;
  color: #909399;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px dashed #eef1fb;
}

.create-time {
  font-size: 12px;
  color: #b0b4c8;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-actions .el-button + .el-button {
  margin-left: 0;
}

.gallery-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
