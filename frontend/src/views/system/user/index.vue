<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addUser,
  assignUserRoles,
  pageUsers,
  resetUserPassword,
  updateUserStatus
} from '@/api/user'
import { listRoles } from '@/api/role'
import PageHero from '@/components/PageHero.vue'
import { useTable } from '@/composables/useTable'
import { formatTime } from '@/utils/dict'

/** 用户管理：查询、新增、启停、重置密码、分配角色 */
const { loading, list, total, query, load, search, reset, handleSizeChange, handlePageChange } =
  useTable(pageUsers, { username: '', status: '' })

// 角色字典（新增与分配角色共用）
const roles = ref([])
async function loadRoles() {
  roles.value = await listRoles({})
}

/** 状态筛选项：数量按当前页统计，避免额外请求 */
const statusOptions = computed(() => [
  { value: '', label: '全部账号', count: list.value.length },
  { value: 1, label: '已启用', count: list.value.filter((row) => row.status === 1).length },
  { value: 0, label: '已禁用', count: list.value.filter((row) => row.status !== 1).length }
])

function pickStatus(value) {
  query.status = value
  search()
}

// ==================== 新增用户 ====================
const addVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  roleIds: []
})
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度需为 4-20 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需为 6-20 位', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

function openAdd() {
  Object.assign(form, {
    username: '',
    password: '',
    nickname: '',
    email: '',
    phone: '',
    roleIds: []
  })
  addVisible.value = true
  formRef.value?.clearValidate()
}

async function submitAdd() {
  if (submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await addUser({ ...form })
    ElMessage.success('新增成功')
    addVisible.value = false
    search()
  } finally {
    submitting.value = false
  }
}

// ==================== 启用 / 禁用 ====================
async function toggleStatus(row) {
  const target = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      `确认${target === 1 ? '启用' : '禁用'}用户「${row.username}」？禁用后该用户的登录会话会立即失效。`,
      '操作确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await updateUserStatus(row.id, target)
  ElMessage.success('操作成功')
  load()
}

// ==================== 分配角色 ====================
const assignVisible = ref(false)
const assigning = ref(false)
const assignForm = reactive({ id: null, username: '', roleIds: [] })

function openAssign(row) {
  assignForm.id = row.id
  assignForm.username = row.username
  assignForm.roleIds = (row.roles || []).map((role) => role.id)
  assignVisible.value = true
}

async function submitAssign() {
  if (assigning.value) return
  assigning.value = true
  try {
    await assignUserRoles(assignForm.id, assignForm.roleIds)
    ElMessage.success('角色已更新')
    assignVisible.value = false
    load()
  } finally {
    assigning.value = false
  }
}

// ==================== 重置密码 ====================
const pwdVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdFormRef = ref()
const pwdForm = reactive({ id: null, username: '', newPassword: '' })
const pwdRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需为 6-20 位', trigger: 'blur' }
  ]
}

function openResetPwd(row) {
  pwdForm.id = row.id
  pwdForm.username = row.username
  pwdForm.newPassword = ''
  pwdVisible.value = true
  pwdFormRef.value?.clearValidate()
}

async function submitResetPwd() {
  if (pwdSubmitting.value) return
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return
  pwdSubmitting.value = true
  try {
    await resetUserPassword(pwdForm.id, pwdForm.newPassword)
    ElMessage.success('密码已重置，该用户需重新登录')
    pwdVisible.value = false
  } finally {
    pwdSubmitting.value = false
  }
}

onMounted(() => {
  load()
  loadRoles()
})
</script>

<template>
  <div class="page-container">
    <PageHero
      title="用户管理"
      subtitle="维护系统账号信息，支持启用/禁用、重置密码与角色分配，禁用后登录会话将立即失效。"
      icon="User"
    />

    <div class="user-layout">
      <!-- 左：筛选栏 -->
      <aside class="filter-panel">
        <span class="filter-eyebrow">筛选账号</span>
        <el-input
          v-model="query.username"
          placeholder="用户名（模糊匹配）"
          clearable
          @keyup.enter="search"
        />
        <div class="filter-actions">
          <el-button type="primary" :loading="loading" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>

        <div class="status-group">
          <span class="group-title">账号状态</span>
          <button
            v-for="opt in statusOptions"
            :key="opt.label"
            class="status-option"
            :class="{ active: query.status === opt.value }"
            @click="pickStatus(opt.value)"
          >
            <span>{{ opt.label }}</span>
            <b>{{ opt.count }}</b>
          </button>
          <span class="group-tip">数量按当前页统计</span>
        </div>

        <div class="role-group">
          <span class="group-title">可选角色</span>
          <div class="role-chips">
            <span v-for="role in roles" :key="role.id" class="role-chip">{{ role.roleName }}</span>
            <span v-if="!roles.length" class="group-tip">暂无角色</span>
          </div>
        </div>
      </aside>

      <!-- 右：用户表格 -->
      <section class="table-panel">
        <div class="panel-head">
          <div>
            <span class="panel-eyebrow">用户目录</span>
            <h3>{{ total }} 个账号</h3>
          </div>
          <el-button v-permission="'system:user:add'" type="primary" @click="openAdd">
            新增用户
          </el-button>
        </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="姓名/昵称" min-width="110" />
        <el-table-column prop="email" label="邮箱" min-width="160" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="role in row.roles || []" :key="role.id" class="mr-6" effect="plain">
              {{ role.roleName }}
            </el-tag>
            <span v-if="!row.roles?.length">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" width="170">
          <template #default="{ row }">{{ formatTime(row.lastLoginTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'system:user:status'"
              link
              :type="row.status === 1 ? 'danger' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button v-permission="'system:user:assign'" link type="primary" @click="openAssign(row)">
              分配角色
            </el-button>
            <el-button v-permission="'system:user:reset'" link type="warning" @click="openResetPwd(row)">
              重置密码
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户数据" :image-size="80" />
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
      </section>
    </div>

    <!-- 新增用户 -->
    <el-dialog v-model="addVisible" title="新增用户" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="4-20 位" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6-20 位" />
        </el-form-item>
        <el-form-item label="姓名/昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="选填" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="选填" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="可暂不分配" style="width: 100%">
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAdd">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色 -->
    <el-dialog v-model="assignVisible" title="分配角色" width="480px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="角色会整体覆盖。清空后该用户将失去所有业务权限。"
        class="mb-16"
      />
      <el-form label-width="80px">
        <el-form-item label="用户">
          <span>{{ assignForm.username }}</span>
        </el-form-item>
        <el-form-item label="角色">
          <el-checkbox-group v-model="assignForm.roleIds">
            <el-checkbox v-for="role in roles" :key="role.id" :value="role.id">
              {{ role.roleName }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="submitAssign">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="pwdVisible" title="重置密码" width="460px" destroy-on-close>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="用户">
          <span>{{ pwdForm.username }}</span>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="submitResetPwd">确定</el-button>
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

.user-layout {
  display: grid;
  grid-template-columns: minmax(240px, 280px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

/* 左侧筛选栏 */
.filter-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 22px 20px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.filter-eyebrow {
  font-size: 12px;
  color: #9aa0b8;
  letter-spacing: 1px;
}

.filter-actions {
  display: flex;
  gap: 8px;
}

.filter-actions :deep(.el-button) {
  flex: 1;
  margin-left: 0;
}

.status-group,
.role-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 14px;
  border-top: 1px dashed #e9ecf7;
}

.group-title {
  font-size: 12px;
  font-weight: 600;
  color: #5c6280;
}

.group-tip {
  font-size: 11px;
  color: #a9aec4;
}

.status-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 14px;
  font-size: 13px;
  color: #5c6280;
  cursor: pointer;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-radius: 10px;
  transition: all 0.2s ease;
}

.status-option b {
  font-size: 13px;
  color: #2b2e45;
}

.status-option:hover {
  border-color: #d5dcfa;
}

.status-option.active {
  color: #5b6cff;
  background: #eef1ff;
  border-color: #c3ccfb;
}

.status-option.active b {
  color: #5b6cff;
}

.role-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.role-chip {
  padding: 3px 10px;
  font-size: 12px;
  color: #5b6cff;
  background: #eef1ff;
  border-radius: 999px;
}

/* 右侧表格 */
.table-panel {
  padding: 22px 24px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.panel-eyebrow {
  font-size: 12px;
  color: #9aa0b8;
}

.panel-head h3 {
  margin: 4px 0 0;
  font-size: 18px;
  font-weight: 700;
  color: #23264a;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.mr-6 {
  margin-right: 6px;
}

.mb-16 {
  margin-bottom: 16px;
}

@media (max-width: 1000px) {
  .user-layout {
    grid-template-columns: 1fr;
  }
}
</style>
