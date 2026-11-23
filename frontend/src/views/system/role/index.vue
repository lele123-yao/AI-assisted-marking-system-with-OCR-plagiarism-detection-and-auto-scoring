<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createRole, deleteRole, listRoles, updateRole } from '@/api/role'
import PageHero from '@/components/PageHero.vue'
import StatCards from '@/components/StatCards.vue'
import { formatTime } from '@/utils/dict'

/** 角色管理：列表、新增、编辑、删除 */
const loading = ref(false)
const list = ref([])
const queryStatus = ref('')

const dialogVisible = ref(false)
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

async function load() {
  loading.value = true
  try {
    list.value = await listRoles(queryStatus.value === '' ? {} : { status: queryStatus.value })
  } finally {
    loading.value = false
  }
}

/** 概览统计：基于当前筛选出的角色列表计算 */
const statItems = computed(() => [
  {
    label: '角色总数',
    value: list.value.length,
    icon: 'Avatar',
    color: '#409eff',
    bg: '#ecf5ff'
  },
  {
    label: '已启用',
    value: list.value.filter((row) => row.status === 1).length,
    icon: 'CircleCheck',
    color: '#67c23a',
    bg: '#f0f9eb'
  },
  {
    label: '已禁用',
    value: list.value.filter((row) => row.status !== 1).length,
    icon: 'CircleClose',
    color: '#909399',
    bg: '#f4f4f5'
  }
])

function openCreate() {
  editingId.value = null
  Object.assign(form, { roleCode: '', roleName: '', description: '', sort: 0, status: 1 })
  dialogVisible.value = true
  formRef.value?.clearValidate()
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
      await updateRole(editingId.value, { ...form })
      ElMessage.success('修改成功')
    } else {
      await createRole({ ...form })
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
      `确认删除角色「${row.roleName}」？仍被用户绑定的角色无法删除。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await deleteRole(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <PageHero
      title="角色管理"
      subtitle="维护系统角色及其启停状态，账号通过分配角色获得对应的业务与数据权限。"
      icon="Avatar"
    >
      <template #actions>
        <el-button v-permission="'system:role:add'" type="primary" @click="openCreate">
          新增角色
        </el-button>
      </template>
    </PageHero>

    <StatCards :items="statItems" :columns="3" :loading="loading" />

    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="queryStatus" placeholder="角色状态" clearable style="width: 140px" @change="load">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="roleCode" label="角色编码" min-width="140" />
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:role:edit'" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-permission="'system:role:delete'" link type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无角色数据" :image-size="80" />
        </template>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑角色' : '新增角色'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="如 TEACHER" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="如 任课教师" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="256" show-word-limit />
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

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
