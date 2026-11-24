<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createRole, deleteRole, listRoles, updateRole } from '@/api/role'
import { formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileSheet from '@/components/m/MobileSheet.vue'

/** 移动端角色管理：列表、新增、编辑、删除 */
const loading = ref(false)
const list = ref([])
const statusFilter = ref('')
const deletingId = ref(null)

const statusFilters = [
  { value: '', label: '全部' },
  { value: 1, label: '启用' },
  { value: 0, label: '禁用' }
]

const counts = computed(() => ({
  all: list.value.length,
  enabled: list.value.filter((row) => row.status === 1).length,
  disabled: list.value.filter((row) => row.status !== 1).length
}))

function filterCount(value) {
  if (value === 1) return counts.value.enabled
  if (value === 0) return counts.value.disabled
  return counts.value.all
}

async function load() {
  loading.value = true
  try {
    list.value = (await listRoles(statusFilter.value === '' ? {} : { status: statusFilter.value })) || []
  } finally {
    loading.value = false
  }
}

function pickStatus(value) {
  statusFilter.value = value
  load()
}

// ==================== 新增 / 编辑 ====================
const sheetVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({
  roleCode: '',
  roleName: '',
  description: '',
  sort: 0,
  status: 1
})
const rules = {
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_]{2,32}$/,
      message: '只能由字母、数字、下划线组成，长度 2-32 位',
      trigger: 'blur'
    }
  ],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { roleCode: '', roleName: '', description: '', sort: 0, status: 1 })
  sheetVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    roleCode: row.roleCode,
    roleName: row.roleName,
    description: row.description || '',
    sort: row.sort ?? 0,
    status: row.status ?? 1
  })
  sheetVisible.value = true
}

async function submit() {
  if (submitting.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingId.value) {
      await updateRole(editingId.value, { ...form })
      ElMessage.success('修改成功')
    } else {
      await createRole({ ...form })
      ElMessage.success('新增成功')
    }
    sheetVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

// ==================== 删除 ====================
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除角色「${row.roleName}」？仍被用户绑定的角色无法删除。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  deletingId.value = row.id
  try {
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    load()
  } catch {
    // 被用户绑定时后端会拒绝，错误信息由 request 拦截器直接展示
  } finally {
    deletingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="角色管理"
      subtitle="维护系统角色及启停状态，账号通过分配角色获得对应的业务与数据权限。"
      icon="Avatar"
    >
      <template #actions>
        <el-button size="small" type="primary" @click="openCreate">新增</el-button>
      </template>
    </MobileHero>

    <div class="m-chips">
      <button
        v-for="item in statusFilters"
        :key="item.label"
        type="button"
        class="m-chip"
        :class="{ active: statusFilter === item.value }"
        @click="pickStatus(item.value)"
      >
        {{ item.label }}
        <b>{{ filterCount(item.value) }}</b>
      </button>
    </div>

    <div v-loading="loading" class="list">
      <article v-for="row in list" :key="row.id" class="m-card role">
        <div class="m-row-between">
          <div>
            <b class="role-name">{{ row.roleName }}</b>
            <p class="role-code">{{ row.roleCode }}</p>
          </div>
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </div>

        <p class="desc">{{ row.description || '暂无描述' }}</p>

        <div class="meta">
          <span>排序 {{ row.sort ?? 0 }}</span>
          <span>创建于 {{ formatTime(row.createTime) }}</span>
        </div>

        <div class="actions">
          <el-button size="small" plain @click="openEdit(row)">编辑</el-button>
          <el-button
            size="small"
            plain
            type="danger"
            :loading="deletingId === row.id"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </div>
      </article>

      <MobileEmpty
        v-if="!loading && !list.length"
        description="暂无角色数据"
        icon="Avatar"
        action-text="刷新"
        @action="load"
      />
    </div>

    <MobileSheet
      v-model="sheetVisible"
      :title="editingId ? '编辑角色' : '新增角色'"
      height="78%"
      :loading="submitting"
      @confirm="submit"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="如 TEACHER" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="如 任课教师" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="256"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
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
  min-height: 100px;
}

.role-name {
  font-size: 15px;
  color: #23264a;
}

.role-code {
  margin: 3px 0 0;
  font-size: 12px;
  color: #8b90ab;
}

.desc {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #5c6180;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding-top: 9px;
  margin-top: 9px;
  font-size: 11px;
  color: #9aa0b8;
  border-top: 1px dashed #eef1fa;
}

.actions {
  display: flex;
  gap: 8px;
  padding-top: 10px;
}

.actions .el-button {
  flex: 1;
  margin-left: 0;
}
</style>
