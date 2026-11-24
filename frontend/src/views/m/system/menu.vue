<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMenuPermissionMatrix } from '@/api/menu'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'

/**
 * 移动端菜单权限只读视图。
 * 数据来自前端菜单表与角色权限映射，不发起授权请求，实际授权走用户管理。
 */
const router = useRouter()
const loading = ref(false)
const rows = ref([])
const activeNames = ref([])

async function load() {
  loading.value = true
  try {
    rows.value = (await getMenuPermissionMatrix()) || []
    activeNames.value = [...new Set(rows.value.map((row) => row.module))]
  } finally {
    loading.value = false
  }
}

const roles = computed(() => rows.value[0]?.roles || [])

const groups = computed(() => {
  const map = new Map()
  rows.value.forEach((row) => {
    if (!map.has(row.module)) map.set(row.module, [])
    map.get(row.module).push(row)
  })
  return Array.from(map, ([module, items]) => ({ module, items }))
})

/** 角色图例颜色与角色编码对应 */
function roleTone(roleCode) {
  if (roleCode === 'ADMIN') return 'danger'
  if (roleCode === 'TEACHER') return 'warning'
  if (roleCode === 'STUDENT') return 'success'
  return 'info'
}

onMounted(load)
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="菜单权限"
      subtitle="菜单与角色可见范围的只读对照表"
      icon="Menu"
    >
      <template #actions>
        <el-button size="small" plain :loading="loading" @click="load">刷新</el-button>
      </template>
    </MobileHero>

    <section class="m-card notice">
      <p>菜单权限由角色映射只读展示，实际授权请到用户管理分配角色。</p>
      <el-button size="small" type="primary" plain @click="router.push('/m/system/user')">
        去用户管理
      </el-button>
    </section>

    <section class="m-card legend">
      <span class="m-label">角色图例</span>
      <div class="legend-tags">
        <el-tag
          v-for="role in roles"
          :key="role.roleCode"
          :type="roleTone(role.roleCode)"
          size="small"
          effect="plain"
        >
          {{ role.roleName || role.roleCode }}
        </el-tag>
        <span v-if="!roles.length" class="m-sub">暂无角色数据</span>
      </div>
    </section>

    <div v-loading="loading" class="groups">
      <el-collapse v-model="activeNames">
        <el-collapse-item v-for="group in groups" :key="group.module" :name="group.module">
          <template #title>
            <span class="group-title">{{ group.module }}</span>
            <span class="group-count">{{ group.items.length }} 项</span>
          </template>

          <div v-for="row in group.items" :key="row.path" class="menu-row">
            <div class="menu-head">
              <b>{{ row.title }}</b>
              <el-tag v-if="row.permission && row.permission !== '-'" size="small" type="info" effect="plain">
                {{ row.permission }}
              </el-tag>
            </div>
            <p class="menu-path">{{ row.path }}</p>

            <div class="role-cells">
              <span v-for="role in row.roles" :key="role.roleCode" class="role-cell">
                <i :class="role.allowed ? 'allow' : 'deny'">{{ role.allowed ? '✓' : '✗' }}</i>
                <span>{{ role.roleName || role.roleCode }}</span>
              </span>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>

      <MobileEmpty
        v-if="!loading && !groups.length"
        description="暂无菜单权限数据"
        icon="Menu"
        action-text="刷新"
        @action="load"
      />
    </div>
  </div>
</template>

<style scoped>
.notice {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #f7f9ff;
}

.notice p {
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: #5c6180;
}

.notice .el-button {
  align-self: flex-start;
}

.legend-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 9px;
}

.groups {
  min-height: 80px;
}

.groups :deep(.el-collapse) {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: none;
}

.groups :deep(.el-collapse-item) {
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 14px;
}

.groups :deep(.el-collapse-item__header) {
  padding: 0 14px;
  border-bottom: none;
}

.groups :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}

.groups :deep(.el-collapse-item__content) {
  padding: 0 14px 12px;
}

.group-title {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: #23264a;
}

.group-count {
  margin-right: 8px;
  font-size: 11px;
  color: #9aa0b8;
}

.menu-row {
  padding: 10px 0;
  border-top: 1px dashed #eef1fa;
}

.menu-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.menu-head b {
  font-size: 13px;
  color: #2f3247;
}

.menu-path {
  margin: 4px 0 8px;
  font-size: 11px;
  color: #9aa0b8;
  word-break: break-all;
}

.role-cells {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.role-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  font-size: 11px;
  color: #5c6180;
  background: #f8f9ff;
  border-radius: 999px;
}

.role-cell i {
  font-style: normal;
  font-weight: 700;
}

.role-cell i.allow {
  color: #67c23a;
}

.role-cell i.deny {
  color: #c6cce4;
}
</style>
