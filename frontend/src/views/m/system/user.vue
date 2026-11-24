<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addUser,
  assignUserRoles,
  pageUsers,
  resetUserPassword,
  updateUserStatus
} from '@/api/user'
import { listRoles } from '@/api/role'
import { useMobileList } from '@/composables/useMobileList'
import { formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileSearchBar from '@/components/m/MobileSearchBar.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileLoadMore from '@/components/m/MobileLoadMore.vue'
import MobileSheet from '@/components/m/MobileSheet.vue'

/** 移动端用户管理：分页查询、启停、重置密码、分配角色、新增用户 */
const fetchPage = (params) => {
  const payload = { ...params }
  if (!payload.username) delete payload.username
  if (payload.status === '' || payload.status === null) delete payload.status
  return pageUsers(payload)
}

const { query, list, total, loading, finished, load, loadMore, search, reset } = useMobileList(
  fetchPage,
  { username: '', status: '' }
)

const statusFilters = [
  { value: '', label: '全部' },
  { value: 1, label: '启用' },
  { value: 0, label: '禁用' }
]

function pickStatus(value) {
  query.status = value
  search()
}

// 角色字典（新增与分配角色共用）
const roles = ref([])
async function loadRoles() {
  roles.value = (await listRoles({})) || []
}

// ==================== 新增用户 ====================
const addVisible = ref(false)
const addSubmitting = ref(false)
const addFormRef = ref()
const addForm = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  roleIds: []
})
const addRules = {
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
  Object.assign(addForm, {
    username: '',
    password: '',
    nickname: '',
    email: '',
    phone: '',
    roleIds: []
  })
  addVisible.value = true
}

async function submitAdd() {
  if (addSubmitting.value) return
  const valid = await addFormRef.value?.validate().catch(() => false)
  if (!valid) return
  addSubmitting.value = true
  try {
    await addUser({ ...addForm })
    ElMessage.success('新增成功')
    addVisible.value = false
    search()
  } finally {
    addSubmitting.value = false
  }
}

// ==================== 启用 / 禁用 ====================
const statusBusyId = ref(null)

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
  statusBusyId.value = row.id
  try {
    await updateUserStatus(row.id, target)
    ElMessage.success('操作成功')
    search()
  } catch {
    // 错误信息由 request 拦截器统一提示
  } finally {
    statusBusyId.value = null
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
}

async function submitResetPwd() {
  if (pwdSubmitting.value) return
  const valid = await pwdFormRef.value?.validate().catch(() => false)
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

// ==================== 分配角色 ====================
const assignVisible = ref(false)
const assignSubmitting = ref(false)
const assignForm = reactive({ id: null, username: '', roleIds: [] })

function openAssign(row) {
  assignForm.id = row.id
  assignForm.username = row.username
  assignForm.roleIds = (row.roles || []).map((role) => role.id)
  assignVisible.value = true
}

async function submitAssign() {
  if (assignSubmitting.value) return
  assignSubmitting.value = true
  try {
    await assignUserRoles(assignForm.id, assignForm.roleIds)
    ElMessage.success('角色已更新')
    assignVisible.value = false
    search()
  } finally {
    assignSubmitting.value = false
  }
}

onMounted(() => {
  load()
  loadRoles()
})
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="用户管理"
      subtitle="维护系统账号，支持启用/禁用、重置密码与角色分配。禁用后登录会话立即失效。"
      icon="User"
    >
      <template #actions>
        <el-button size="small" type="primary" @click="openAdd">新增</el-button>
      </template>
    </MobileHero>

    <MobileSearchBar
      v-model="query.username"
      placeholder="搜索用户名"
      :loading="loading"
      show-reset
      @search="search"
      @reset="reset"
    />

    <div class="m-chips">
      <button
        v-for="item in statusFilters"
        :key="item.label"
        type="button"
        class="m-chip"
        :class="{ active: query.status === item.value }"
        @click="pickStatus(item.value)"
      >
        {{ item.label }}
      </button>
    </div>

    <div class="m-row-between">
      <span class="m-sub">共 {{ total }} 个账号</span>
      <span class="m-sub">已加载 {{ list.length }} 条</span>
    </div>

    <div v-loading="loading" class="list">
      <article v-for="row in list" :key="row.id" class="m-card user">
        <div class="m-row-between">
          <div class="user-head">
            <b>{{ row.username }}</b>
            <span v-if="row.nickname" class="m-sub">{{ row.nickname }}</span>
          </div>
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </div>

        <div class="roles">
          <el-tag v-for="role in row.roles || []" :key="role.id" size="small" effect="plain">
            {{ role.roleName }}
          </el-tag>
          <span v-if="!row.roles?.length" class="m-sub">未分配角色</span>
        </div>

        <ul class="meta">
          <li><span>邮箱</span><b>{{ row.email || '-' }}</b></li>
          <li><span>手机号</span><b>{{ row.phone || '-' }}</b></li>
          <li><span>最近登录</span><b>{{ formatTime(row.lastLoginTime) }}</b></li>
        </ul>

        <div class="actions">
          <el-button
            size="small"
            :type="row.status === 1 ? 'danger' : 'success'"
            plain
            :loading="statusBusyId === row.id"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button size="small" plain @click="openAssign(row)">分配角色</el-button>
          <el-button size="small" plain type="warning" @click="openResetPwd(row)">重置密码</el-button>
        </div>
      </article>

      <MobileEmpty
        v-if="!loading && !list.length"
        description="暂无用户数据"
        icon="User"
        action-text="刷新"
        @action="load"
      />

      <MobileLoadMore
        :loading="loading"
        :finished="finished && list.length > 0"
        :empty="!loading && !list.length"
        @load="loadMore"
      />
    </div>

    <!-- 新增用户 -->
    <MobileSheet
      v-model="addVisible"
      title="新增用户"
      height="82%"
      :loading="addSubmitting"
      @confirm="submitAdd"
    >
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="addForm.username" placeholder="4-20 位" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="addForm.password" type="password" show-password placeholder="6-20 位" />
        </el-form-item>
        <el-form-item label="姓名/昵称" prop="nickname">
          <el-input v-model="addForm.nickname" placeholder="选填" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="addForm.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="addForm.phone" placeholder="选填" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="addForm.roleIds" multiple placeholder="可暂不分配" style="width: 100%">
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </MobileSheet>

    <!-- 分配角色 -->
    <MobileSheet
      v-model="assignVisible"
      title="分配角色"
      :loading="assignSubmitting"
      @confirm="submitAssign"
    >
      <div class="sheet-tip">
        角色会整体覆盖。清空后该用户将失去所有业务权限。
      </div>
      <div class="m-row-between sheet-user">
        <span class="m-label">用户</span>
        <b>{{ assignForm.username }}</b>
      </div>
      <el-checkbox-group v-model="assignForm.roleIds" class="role-checks">
        <el-checkbox v-for="role in roles" :key="role.id" :value="role.id">
          {{ role.roleName }}
        </el-checkbox>
      </el-checkbox-group>
      <div v-if="!roles.length" class="m-sub">暂无可分配角色</div>
    </MobileSheet>

    <!-- 重置密码 -->
    <MobileSheet
      v-model="pwdVisible"
      title="重置密码"
      :loading="pwdSubmitting"
      @confirm="submitResetPwd"
    >
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-position="top">
        <el-form-item label="用户">
          <span>{{ pwdForm.username }}</span>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="6-20 位"
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

.user-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.user-head b {
  font-size: 15px;
  color: #23264a;
}

.roles {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
}

.meta {
  padding: 0;
  margin: 8px 0 0;
  list-style: none;
  border-top: 1px dashed #eef1fa;
}

.meta li {
  display: flex;
  justify-content: space-between;
  padding: 7px 0;
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
  gap: 8px;
  padding-top: 10px;
  margin-top: 6px;
  border-top: 1px dashed #eef1fa;
}

.actions .el-button {
  margin-left: 0;
}

.sheet-tip {
  padding: 9px 11px;
  margin-bottom: 12px;
  font-size: 12px;
  line-height: 1.7;
  color: #8b90ab;
  background: #f8f9ff;
  border-radius: 9px;
}

.sheet-user {
  padding-bottom: 10px;
  margin-bottom: 10px;
  border-bottom: 1px dashed #eef1fa;
}

.sheet-user b {
  font-size: 13px;
  color: #2f3247;
}

.role-checks {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
