<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { menuTree } from '@/config/menu'
import { useUserStore } from '@/store/user'

const props = defineProps({
  collapse: {
    type: Boolean,
    default: false
  }
})

const route = useRoute()
const userStore = useUserStore()

// 根据权限标识过滤菜单：分组与子菜单都做校验，实现"动态菜单"
const menus = computed(() =>
  menuTree
    .filter((item) => !item.meta?.hidden && userStore.hasPermission(item.meta?.permission))
    .map((item) => {
      if (!item.children?.length) return item
      const children = item.children.filter(
        (child) => !child.meta?.hidden && userStore.hasPermission(child.meta?.permission)
      )
      return { ...item, children }
    })
    // 分组下所有子菜单都无权限时，整个分组不显示
    .filter((item) => !item.children || item.children.length > 0)
)
</script>

<template>
  <el-menu
    :default-active="route.path"
    :collapse="props.collapse"
    :collapse-transition="false"
    :unique-opened="true"
    router
    background-color="transparent"
    text-color="#c3c7e6"
    active-text-color="#a9b8ff"
    class="side-menu"
  >
    <template v-for="item in menus" :key="item.path">
      <!-- 有子菜单 -->
      <el-sub-menu v-if="item.children?.length" :index="item.path">
        <template #title>
          <el-icon><component :is="item.meta.icon" /></el-icon>
          <span>{{ item.meta.title }}</span>
        </template>
        <el-menu-item
          v-for="child in item.children"
          :key="child.path"
          :index="child.path"
        >
          <el-icon v-if="child.meta?.icon">
            <component :is="child.meta.icon" />
          </el-icon>
          <template #title>{{ child.meta.title }}</template>
        </el-menu-item>
      </el-sub-menu>

      <!-- 无子菜单 -->
      <el-menu-item v-else :index="item.path">
        <el-icon><component :is="item.meta.icon" /></el-icon>
        <template #title>{{ item.meta.title }}</template>
      </el-menu-item>
    </template>
  </el-menu>
</template>

<style scoped>
.side-menu {
  border-right: none;
  height: calc(100% - 60px);
}

.side-menu:not(.el-menu--collapse) {
  width: 220px;
}

/* 菜单项：圆角块 + 悬浮/选中高亮，贴合深蓝紫侧边栏 */
.side-menu:not(.el-menu--collapse) :deep(.el-menu-item),
.side-menu:not(.el-menu--collapse) :deep(.el-sub-menu__title) {
  height: 46px;
  margin: 4px 10px;
  border-radius: 9px;
  line-height: 46px;
}

.side-menu :deep(.el-menu-item:hover),
.side-menu :deep(.el-sub-menu__title:hover) {
  background-color: rgba(138, 160, 255, 0.14) !important;
}

.side-menu :deep(.el-menu-item.is-active) {
  color: #cfd8ff !important;
  background: linear-gradient(90deg, rgba(91, 108, 255, 0.36), rgba(154, 91, 255, 0.16)) !important;
  box-shadow: inset 3px 0 0 #8ab4ff;
}

.side-menu :deep(.el-sub-menu .el-menu-item) {
  min-width: 0;
}
</style>
