<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageUsers, assignUserRoles } from '@/api/user'
import { listRoles } from '@/api/role'
import { menuTree } from '@/config/menu'
import { ROLE_PERMISSIONS } from '@/config/permission'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

// 仅管理员可以进入权限分配页面
const isAdmin = computed(() => userStore.hasRole('ADMIN'))

// ==================== 用户列表 ====================
const userLoading = ref(false)
const users = ref([])
const keyword = ref('')
const currentUser = ref(null)

async function loadUsers() {
  userLoading.value = true
  try {
    const data = await pageUsers({ pageNum: 1, pageSize: 100, username: keyword.value || undefined })
    users.value = data?.records || []
  } catch {
    // 错误提示由请求拦截器统一处理
    users.value = []
  } finally {
    userLoading.value = false
  }
}

// ==================== 角色 ====================
const roles = ref([])

async function loadRoles() {
  try {
    roles.value = await listRoles({})
  } catch {
    roles.value = []
  }
}

const form = reactive({ roleIds: [] })

function permissionsOfRoles(roleIds) {
  const codes = roles.value.filter((role) => roleIds.includes(role.id)).map((role) => role.roleCode)
  const set = new Set()
  codes.forEach((code) => {
    ;(ROLE_PERMISSIONS[code] || []).forEach((permission) => set.add(permission))
  })
  return set
}

// ==================== 菜单权限 ====================
const menuTreeRef = ref()

// 侧边栏菜单转成权限树：分组作为父节点，页面作为叶子节点
const menuTreeData = menuTree.map((group) => ({
  key: group.path,
  label: group.meta?.title,
  permission: group.meta?.permission,
  children: group.children?.length
    ? group.children.map((child) => ({
        key: child.path,
        label: child.meta?.title,
        permission: child.meta?.permission
      }))
    : undefined
}))

// 只有叶子节点参与勾选，父节点由 el-tree 自动联动
const menuLeaves = menuTree.flatMap((group) =>
  group.children?.length ? group.children : [group]
).map((item) => ({
  key: item.path,
  label: item.meta?.title,
  permission: item.meta?.permission
}))

// ==================== 按钮权限 ====================
// 按钮权限按业务模块分组，页面只展示中文名称，不展示权限标识串
const BUTTON_GROUPS = [
  {
    label: '教学资源',
    items: [
      { label: '新增课程', value: 'course:add' },
      { label: '编辑课程', value: 'course:edit' },
      { label: '归档课程', value: 'course:archive' },
      { label: '新增班级', value: 'class:add' },
      { label: '编辑班级', value: 'class:edit' },
      { label: '删除班级', value: 'class:delete' },
      { label: '下发试卷', value: 'assignment:assign' }
    ]
  },
  {
    label: '试卷与题目',
    items: [
      { label: '新增试卷', value: 'paper:add' },
      { label: '编辑试卷', value: 'paper:edit' },
      { label: '修改试卷状态', value: 'paper:status' },
      { label: '新增题目', value: 'question:add' },
      { label: '编辑题目', value: 'question:edit' },
      { label: '删除题目', value: 'question:delete' }
    ]
  },
  {
    label: '阅卷与成绩',
    items: [
      { label: '导入考生名单', value: 'paper-answer:import' },
      { label: '上传答卷图片', value: 'paper-answer:image' },
      { label: '触发 OCR 识别', value: 'paper-answer:ocr' },
      { label: '录入分数', value: 'paper-answer:score' },
      { label: '成绩复核', value: 'grade:review' },
      { label: '导出成绩', value: 'grade:export' }
    ]
  },
  {
    label: '学情分析',
    items: [
      { label: '生成学情报告', value: 'learning:generate' },
      { label: '生成讲评 PPT', value: 'learning:ppt' }
    ]
  },
  {
    label: '系统管理',
    items: [
      { label: '新增用户', value: 'system:user:add' },
      { label: '分配角色', value: 'system:user:assign' },
      { label: '重置密码', value: 'system:user:reset' },
      { label: '新增角色', value: 'system:role:add' },
      { label: '编辑角色', value: 'system:role:edit' },
      { label: '删除角色', value: 'system:role:delete' }
    ]
  }
]

const buttonChecked = ref([])
const allButtons = BUTTON_GROUPS.flatMap((group) => group.items)

function hasButton(value) {
  return buttonChecked.value.includes(value)
}

// ==================== 权限回显与保存 ====================
const STORAGE_KEY = 'role-permission-assignments'

function readAllSaved() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
  } catch {
    return {}
  }
}

function readSaved(userId) {
  return readAllSaved()[userId] || null
}

function writeSaved(userId, data) {
  const all = readAllSaved()
  all[userId] = { ...data, savedAt: Date.now() }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(all))
}

function removeSaved(userId) {
  const all = readAllSaved()
  delete all[userId]
  localStorage.setItem(STORAGE_KEY, JSON.stringify(all))
}

/** 按权限集合回填菜单树与按钮勾选状态 */
async function applyPermissions(permissionSet) {
  const all = permissionSet.has('*')
  const keys = menuLeaves
    .filter((node) => all || !node.permission || permissionSet.has(node.permission))
    .map((node) => node.key)

  await nextTick()
  menuTreeRef.value?.setCheckedKeys(keys)
  buttonChecked.value = allButtons
    .filter((item) => all || permissionSet.has(item.value))
    .map((item) => item.value)
}

/** 切换用户：优先回显上次保存的配置，否则按当前角色生成默认权限 */
async function selectUser(row) {
  currentUser.value = row
  form.roleIds = (row.roles || []).map((role) => role.id)
  // 角色列表还没加载完时先补一次，避免默认权限为空
  if (!roles.value.length) await loadRoles()
  await nextTick()

  const saved = readSaved(row.id)
  if (saved) {
    menuTreeRef.value?.setCheckedKeys(saved.menuKeys || [])
    buttonChecked.value = [...(saved.buttons || [])]
  } else {
    await applyPermissions(permissionsOfRoles(form.roleIds))
  }
}

/** 调整角色后，权限勾选自动切换为该角色的默认权限 */
async function handleRoleChange() {
  await applyPermissions(permissionsOfRoles(form.roleIds))
}

/** 重置为当前角色的默认权限 */
async function handleReset() {
  if (!currentUser.value) return
  removeSaved(currentUser.value.id)
  await applyPermissions(permissionsOfRoles(form.roleIds))
  ElMessage.info('已恢复为角色默认权限')
}

const saving = ref(false)

async function handleSave() {
  if (!currentUser.value) {
    ElMessage.warning('请先在左侧选择用户')
    return
  }
  if (!form.roleIds.length) {
    ElMessage.warning('请至少分配一个角色')
    return
  }
  saving.value = true
  try {
    // 角色变更真正写入后端
    await assignUserRoles(currentUser.value.id, form.roleIds)

    // 菜单与按钮勾选结果按用户记录，便于下次进入本页继续查看和调整
    const menuKeys = menuTreeRef.value?.getCheckedKeys(true) || []
    writeSaved(currentUser.value.id, { menuKeys, buttons: [...buttonChecked.value] })

    // 同步左侧列表中的角色标签
    currentUser.value.roles = roles.value.filter((role) => form.roleIds.includes(role.id))
    ElMessage.success('角色与权限已保存')
  } finally {
    saving.value = false
  }
}

// 业务按钮效果预览：勾选后对应按钮才会出现
function previewAction(name) {
  ElMessage.success(`当前配置已包含「${name}」操作权限`)
}

onMounted(() => {
  if (!isAdmin.value) return
  loadUsers()
  loadRoles()
})
</script>

<template>
  <div class="page-container">
    <!-- 非管理员：无权限提示 -->
    <el-result
      v-if="!isAdmin"
      icon="warning"
      title="无权限访问"
      sub-title="角色权限分配仅限管理员使用，请联系系统管理员。"
    />

    <el-row v-else :gutter="16">
      <!-- 左侧：选择用户 -->
      <el-col :xs="24" :md="9">
        <el-card shadow="never" header="用户列表">
          <div class="toolbar">
            <el-input
              v-model="keyword"
              placeholder="搜索用户名"
              clearable
              @keyup.enter="loadUsers"
            />
            <el-button type="primary" :loading="userLoading" @click="loadUsers">查询</el-button>
          </div>

          <el-table
            v-loading="userLoading"
            :data="users"
            height="460"
            border
            row-key="id"
            highlight-current-row
            @row-click="selectUser"
          >
            <el-table-column prop="username" label="用户名" min-width="110" />
            <el-table-column prop="nickname" label="昵称" min-width="90" />
            <el-table-column label="角色" min-width="140">
              <template #default="{ row }">
                <el-tag v-for="role in row.roles || []" :key="role.id" size="small" class="role-tag">
                  {{ role.roleName }}
                </el-tag>
                <span v-if="!row.roles?.length">未分配</span>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无用户" :image-size="70" />
            </template>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧：权限配置 -->
      <el-col :xs="24" :md="15">
        <el-card shadow="never" class="config-card">
          <template #header>
            <div class="card-header">
              <span class="title">角色权限分配</span>
              <el-tag v-if="currentUser" type="info" effect="plain">
                {{ currentUser.username }}
              </el-tag>
            </div>
          </template>

          <el-empty v-if="!currentUser" description="请先在左侧选择一个用户" :image-size="90" />

          <template v-else>
            <el-alert
              type="info"
              :closable="false"
              show-icon
              class="tip"
              title="保存时会同步用户角色；菜单与按钮勾选按本次配置记录，用于该用户的权限查看与调整。"
            />

            <!-- 分配角色 -->
            <div class="section">
              <div class="section-title">分配角色</div>
              <el-checkbox-group v-model="form.roleIds" @change="handleRoleChange">
                <el-checkbox v-for="role in roles" :key="role.id" :value="role.id">
                  {{ role.roleName }}
                </el-checkbox>
              </el-checkbox-group>
              <span v-if="!roles.length" class="empty-text">暂无可分配角色</span>
            </div>

            <!-- 菜单权限 -->
            <div class="section">
              <div class="section-title">菜单权限</div>
              <div class="tree-box">
                <el-tree
                  ref="menuTreeRef"
                  :data="menuTreeData"
                  node-key="key"
                  show-checkbox
                  default-expand-all
                  :props="{ label: 'label', children: 'children' }"
                />
              </div>
            </div>

            <!-- 按钮权限 -->
            <div class="section">
              <div class="section-title">按钮权限</div>
              <div v-for="group in BUTTON_GROUPS" :key="group.label" class="button-group">
                <div class="group-title">{{ group.label }}</div>
                <el-checkbox-group v-model="buttonChecked">
                  <el-checkbox v-for="item in group.items" :key="item.value" :value="item.value">
                    {{ item.label }}
                  </el-checkbox>
                </el-checkbox-group>
              </div>
            </div>

            <!-- 按钮效果预览：使用真实业务按钮 -->
            <div class="section">
              <div class="section-title">按钮效果预览</div>
              <el-space wrap>
                <el-button v-if="hasButton('paper:add')" type="primary" @click="previewAction('新增试卷')">
                  新增试卷
                </el-button>
                <el-button
                  v-if="hasButton('paper-answer:import')"
                  @click="previewAction('导入考生名单')"
                >
                  导入考生名单
                </el-button>
                <el-button v-if="hasButton('grade:review')" type="warning" @click="previewAction('成绩复核')">
                  成绩复核
                </el-button>
                <el-button v-if="hasButton('grade:export')" type="success" @click="previewAction('导出成绩')">
                  导出成绩
                </el-button>
                <el-button v-if="hasButton('learning:ppt')" type="primary" plain @click="previewAction('生成讲评PPT')">
                  生成讲评PPT
                </el-button>
              </el-space>
              <div v-if="!buttonChecked.length" class="empty-text">当前未勾选任何按钮权限</div>
            </div>

            <div class="actions">
              <el-button type="primary" :loading="saving" @click="handleSave">保存权限</el-button>
              <el-button @click="handleReset">恢复默认</el-button>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.toolbar :deep(.el-input) {
  flex: 1;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-header .title {
  font-size: 16px;
  font-weight: 600;
}

.config-card {
  min-height: 100%;
}

.tip {
  margin-bottom: 16px;
}

.section {
  margin-bottom: 20px;
}

.section-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.tree-box {
  max-height: 300px;
  overflow: auto;
  padding: 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.button-group {
  margin-bottom: 10px;
}

.group-title {
  margin-bottom: 4px;
  font-size: 13px;
  color: #909399;
}

.empty-text {
  font-size: 13px;
  color: #c0c4cc;
}

.actions {
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.role-tag {
  margin-right: 4px;
}

@media (max-width: 992px) {
  .config-card {
    margin-top: 16px;
  }
}
</style>
