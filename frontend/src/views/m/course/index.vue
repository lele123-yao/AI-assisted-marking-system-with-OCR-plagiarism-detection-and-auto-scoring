<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addCourse, archiveCourse, pageCourses, updateCourse } from '@/api/course'
import { pageUsers } from '@/api/user'
import { useMobileList } from '@/composables/useMobileList'
import { useUserStore } from '@/store/user'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileSearchBar from '@/components/m/MobileSearchBar.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileLoadMore from '@/components/m/MobileLoadMore.vue'
import MobileSheet from '@/components/m/MobileSheet.vue'

/** 课程管理：查询、新增、编辑、归档 */
const userStore = useUserStore()
const isAdmin = computed(() => userStore.hasRole('ADMIN'))

const { query, list, loading, finished, load, loadMore, search } = useMobileList(pageCourses, {
  keyword: '',
  status: ''
})
const empty = computed(() => !loading.value && !list.value.length)

const statusOptions = [
  { value: '', label: '全部' },
  { value: 1, label: '正常' },
  { value: 0, label: '停用' }
]

function pickStatus(value) {
  query.status = value
  search()
}

// 任课教师候选：仅管理员可查询用户列表；教师新增课程时默认由本人担任
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

const sheetVisible = ref(false)
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
    teacherId: isAdmin.value ? null : userStore.userInfo?.id ?? null,
    term: '',
    credit: null,
    description: '',
    status: 1
  })
  sheetVisible.value = true
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
      await updateCourse(editingId.value, { ...form })
      ElMessage.success('修改成功')
    } else {
      await addCourse({ ...form })
      ElMessage.success('新增成功')
    }
    sheetVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

const archivingId = ref(null)
async function handleArchive(row) {
  if (row.status !== 1) return
  try {
    await ElMessageBox.confirm(
      `确认归档课程「${row.courseName}」？课程下仍有未归档试卷时会被后端拒绝。`,
      '归档确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  archivingId.value = row.id
  try {
    await archiveCourse(row.id)
    ElMessage.success('归档成功')
    load()
  } finally {
    archivingId.value = null
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
      title="课程管理"
      subtitle="维护课程编码、任课教师与开课学期；仍有未归档试卷的课程不能归档。"
      icon="Notebook"
    >
      <template #actions>
        <el-button size="small" type="primary" @click="openCreate">新增</el-button>
      </template>
    </MobileHero>

    <MobileSearchBar
      v-model="query.keyword"
      placeholder="课程名称 / 编码"
      :loading="loading"
      @search="search"
    />

    <div class="m-chips">
      <button
        v-for="item in statusOptions"
        :key="item.label"
        type="button"
        class="m-chip"
        :class="{ active: query.status === item.value }"
        @click="pickStatus(item.value)"
      >
        {{ item.label }}
      </button>
    </div>

    <div v-loading="loading" class="list">
      <article v-for="row in list" :key="row.id" class="m-card">
        <div class="m-row-between">
          <span class="code">{{ row.courseCode }}</span>
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" round>
            {{ row.status === 1 ? '正常' : '已停用' }}
          </el-tag>
        </div>

        <h3 class="m-title name">{{ row.courseName }}</h3>

        <div class="meta">
          <span>
            <el-icon><User /></el-icon>
            {{ row.teacherName || row.teacherId || '未指派教师' }}
          </span>
          <span>
            <el-icon><Calendar /></el-icon>
            {{ row.term || '未设置学期' }}
          </span>
          <span>
            <el-icon><Reading /></el-icon>
            {{ row.credit ?? '-' }} 学分
          </span>
        </div>

        <p class="desc m-clamp-2">{{ row.description || '暂无课程简介' }}</p>

        <div class="actions">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            size="small"
            type="warning"
            plain
            :disabled="row.status !== 1"
            :loading="archivingId === row.id"
            @click="handleArchive(row)"
          >
            {{ row.status === 1 ? '归档' : '已归档' }}
          </el-button>
        </div>
      </article>

      <MobileEmpty
        v-if="empty"
        description="暂无课程数据"
        icon="Notebook"
        action-text="刷新"
        @action="load"
      />

      <MobileLoadMore :loading="loading" :finished="finished" :empty="empty" @load="loadMore" />
    </div>

    <MobileSheet
      v-model="sheetVisible"
      :title="editingId ? '编辑课程' : '新增课程'"
      height="86%"
      :loading="submitting"
      @confirm="submit"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
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
          <el-input-number
            v-model="form.credit"
            :min="0"
            :max="99.9"
            :precision="1"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="课程简介">
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
  </div>
</template>

<style scoped>
.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
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

.meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
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
</style>
