<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { menuTree } from '@/config/menu'
import { useUserStore } from '@/store/user'
import MobileHero from '@/components/m/MobileHero.vue'

/** 全部功能：直接复用桌面菜单配置，按权限过滤 */
const router = useRouter()
const userStore = useUserStore()

const can = (permission) => userStore.hasPermission(permission)

/** 顶层单页合并为「常用功能」，其余分组按权限过滤 */
const groups = computed(() => {
  const leaves = menuTree.filter(
    (item) => !item.children?.length && !item.meta?.hidden && can(item.meta?.permission)
  )
  const result = menuTree
    .filter((group) => group.children?.length)
    .map((group) => ({
      key: group.path,
      title: group.meta?.title || '基础功能',
      icon: group.meta?.icon || 'Menu',
      children: group.children.filter((item) => !item.meta?.hidden && can(item.meta?.permission))
    }))
    .filter((group) => group.children.length > 0)

  return leaves.length
    ? [{ key: 'common', title: '常用功能', icon: 'Star', children: leaves }, ...result]
    : result
})

function go(path) {
  router.push(path.startsWith('/m') ? path : `/m${path}`)
}
</script>

<template>
  <div class="m-page">
    <MobileHero title="全部功能" subtitle="按当前角色权限展示，点击进入对应页面" icon="Grid" />

    <section v-for="group in groups" :key="group.key" class="m-card group">
      <div class="group-head">
        <el-icon><component :is="group.icon" /></el-icon>
        <span>{{ group.title }}</span>
        <small>{{ group.children.length }}</small>
      </div>
      <div class="group-grid">
        <button
          v-for="item in group.children"
          :key="item.path"
          type="button"
          class="group-item"
          @click="go(item.path)"
        >
          <span class="group-icon">
            <el-icon :size="18"><component :is="item.meta.icon || 'Document'" /></el-icon>
          </span>
          <span class="group-label">{{ item.meta.title }}</span>
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.group {
  padding: 14px 10px 10px;
}

.group-head {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 4px 10px;
  font-size: 14px;
  font-weight: 600;
  color: #23264a;
}

.group-head .el-icon {
  color: var(--ai-primary);
}

.group-head small {
  padding: 1px 7px;
  margin-left: auto;
  font-size: 11px;
  font-weight: 400;
  color: #9aa0b8;
  background: #f4f6fc;
  border-radius: 999px;
}

.group-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 2px;
}

.group-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 2px;
  font-size: 12px;
  color: #4c5170;
  background: transparent;
  border: none;
  border-radius: 12px;
}

.group-item:active {
  background: #f4f6ff;
}

.group-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: var(--ai-primary);
  background: linear-gradient(135deg, #eef1ff, #f6f0ff);
  border-radius: 12px;
}

.group-label {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
