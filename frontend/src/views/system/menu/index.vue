<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMenuPermissionMatrix } from '@/api/menu'

/**
 * 菜单权限管理。
 *
 * 后端当前没有菜单与角色-权限关联接口，本页只展示前端菜单与角色权限的对照关系，
 * 实际授权入口为「用户管理 → 分配角色」与「角色管理」。
 */
const router = useRouter()
const loading = ref(false)
const rows = ref([])
const keyword = ref('')

const filteredRows = computed(() => {
  const key = keyword.value.trim()
  if (!key) return rows.value
  return rows.value.filter(
    (row) => row.title?.includes(key) || row.path?.includes(key) || row.permission?.includes(key)
  )
})

// 概览数据：菜单与模块规模、各角色可见菜单数量
const moduleCount = computed(() => new Set(rows.value.map((row) => row.module)).size)

function visibleCount(roleCode) {
  return rows.value.filter(
    (row) => row.roles.find((item) => item.roleCode === roleCode)?.allowed
  ).length
}

const overview = computed(() => [
  { label: '菜单总数', value: rows.value.length, icon: 'Menu', color: '#409eff', bg: '#ecf5ff' },
  { label: '功能模块', value: moduleCount.value, icon: 'Grid', color: '#9254de', bg: '#f9f0ff' },
  { label: '教师可见', value: visibleCount('TEACHER'), icon: 'User', color: '#e6a23c', bg: '#fdf6ec' },
  { label: '学生可见', value: visibleCount('STUDENT'), icon: 'Avatar', color: '#67c23a', bg: '#f0f9eb' }
])

async function load() {
  loading.value = true
  try {
    rows.value = await getMenuPermissionMatrix()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-alert
      type="info"
      show-icon
      :closable="false"
      title="菜单与角色权限对照"
      description="菜单可见范围由角色权限统一生成。需要调整某个用户的权限时，请前往「用户管理 → 分配角色」或「角色管理」操作。"
    />

    <!-- 概览 -->
    <div class="overview-grid">
      <div v-for="item in overview" :key="item.label" class="overview-card">
        <div class="overview-icon" :style="{ color: item.color, backgroundColor: item.bg }">
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
        </div>
        <div>
          <div class="overview-value">{{ item.value }}</div>
          <div class="overview-label">{{ item.label }}</div>
        </div>
      </div>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索菜单名称 / 路径 / 权限标识"
          clearable
          style="width: 300px"
        />
        <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
        <div class="toolbar-right">
          <el-button @click="router.push('/system/user')">去分配角色</el-button>
          <el-button @click="router.push('/system/role')">去角色管理</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="filteredRows" border stripe>
        <el-table-column prop="module" label="所属模块" width="130" />
        <el-table-column prop="title" label="菜单名称" width="150" />
        <el-table-column prop="path" label="路由地址" min-width="180" />
        <el-table-column prop="permission" label="权限标识" min-width="190" />
        <el-table-column
          v-for="role in filteredRows[0]?.roles || []"
          :key="role.roleCode"
          :label="role.roleName"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.roles.find((item) => item.roleCode === role.roleCode)?.allowed ? 'success' : 'info'"
              effect="plain"
            >
              {{ row.roles.find((item) => item.roleCode === role.roleCode)?.allowed ? '可见' : '不可见' }}
            </el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有匹配的菜单" :image-size="80" />
        </template>
      </el-table>
    </el-card>
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

.toolbar-right {
  margin-left: auto;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.overview-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
}

.overview-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
}

.overview-value {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}

.overview-label {
  font-size: 12px;
  color: #909399;
}

@media (max-width: 992px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
