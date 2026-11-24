<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { menuTree } from '@/config/menu'
import { useUserStore } from '@/store/user'
import { isWeChat } from '@/utils/device'

/**
 * 移动端布局：
 * - 顶部栏：返回 / 标题 / 全部功能
 * - 内容区：独立滚动
 * - 底部 TabBar：按权限动态生成
 * - 菜单抽屉：直接复用桌面 menuTree，避免两套菜单配置漂移
 */

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const mainRef = ref(null)
const menuVisible = ref(false)
const wechatTip = ref(isWeChat() && sessionStorage.getItem('M_WECHAT_TIP') !== '1')

/** 底部 Tab 根页面不显示返回按钮 */
const ROOT_PATHS = ['/m/home', '/m/menu', '/m/profile', '/m/student/papers', '/m/mark/task']
const showBack = computed(() => !ROOT_PATHS.includes(route.path))

function can(permission) {
  return userStore.hasPermission(permission)
}

const tabs = computed(() => {
  const items = []
  if (can('dashboard:view')) {
    items.push({ label: '首页', icon: 'HomeFilled', path: '/m/home', root: '/m/home' })
  }
  // 管理员拥有通配符权限，需先判断是否具备阅卷能力，避免被当成学生
  if (can('mark:view')) {
    items.push({ label: '任务', icon: 'Cpu', path: '/m/mark/task', root: '/m/mark' })
  } else if (can('student:paper')) {
    items.push({ label: '学习', icon: 'Reading', path: '/m/student/papers', root: '/m/student' })
  }
  items.push({ label: '全部', icon: 'Grid', path: '/m/menu', root: '/m/menu' })
  items.push({ label: '我的', icon: 'User', path: '/m/profile', root: '/m/profile' })
  return items
})

const isTabActive = (tab) => route.path === tab.root || route.path.startsWith(`${tab.root}/`)

/** 抽屉菜单：顶层单页（首页、大模型设置等）合并为「常用功能」，分组菜单按权限过滤 */
const menuGroups = computed(() => {
  const leaves = menuTree.filter(
    (item) => !item.children?.length && !item.meta?.hidden && can(item.meta?.permission)
  )
  const groups = menuTree
    .filter((group) => group.children?.length)
    .map((group) => ({
      title: group.meta?.title || '基础功能',
      icon: group.meta?.icon || 'Menu',
      children: group.children.filter((item) => !item.meta?.hidden && can(item.meta?.permission))
    }))
    .filter((group) => group.children.length > 0)

  return leaves.length
    ? [{ title: '常用功能', icon: 'Star', children: leaves }, ...groups]
    : groups
})

function go(path) {
  menuVisible.value = false
  const target = path.startsWith('/m') ? path : `/m${path}`
  if (target !== route.path) router.push(target)
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/m/home')
}

function closeWechatTip() {
  wechatTip.value = false
  sessionStorage.setItem('M_WECHAT_TIP', '1')
}

watch(
  () => route.fullPath,
  () => mainRef.value?.scrollTo({ top: 0 })
)
</script>

<template>
  <div class="m-app">
    <!-- 顶部栏 -->
    <header class="m-nav">
      <button type="button" class="m-nav-btn" @click="showBack ? goBack() : null">
        <el-icon v-if="showBack" :size="20"><ArrowLeft /></el-icon>
        <el-icon v-else :size="20" class="m-brand-icon"><Reading /></el-icon>
      </button>

      <div class="m-nav-title">
        <span>{{ route.meta.title || '智能阅卷' }}</span>
        <small v-if="route.meta.subtitle">{{ route.meta.subtitle }}</small>
      </div>

      <button type="button" class="m-nav-btn" @click="menuVisible = true">
        <el-icon :size="20"><Grid /></el-icon>
      </button>
    </header>

    <!-- 微信下载提示 -->
    <div v-if="wechatTip" class="m-wechat-tip">
      <el-icon><InfoFilled /></el-icon>
      <span>如需下载 Excel / PPT，请点右上角 ··· 选择在浏览器中打开</span>
      <el-icon class="m-tip-close" @click="closeWechatTip"><Close /></el-icon>
    </div>

    <!-- 内容区 -->
    <main ref="mainRef" class="m-main">
      <router-view />
    </main>

    <!-- 底部 TabBar -->
    <nav class="m-tabbar">
      <button
        v-for="tab in tabs"
        :key="tab.path"
        type="button"
        class="m-tab"
        :class="{ active: isTabActive(tab) }"
        @click="go(tab.path)"
      >
        <el-icon :size="20"><component :is="tab.icon" /></el-icon>
        <span>{{ tab.label }}</span>
      </button>
    </nav>

    <!-- 全部功能抽屉 -->
    <el-drawer v-model="menuVisible" direction="rtl" size="82%" title="全部功能" append-to-body>
      <div class="m-menu">
        <section v-for="group in menuGroups" :key="group.title" class="m-menu-group">
          <h3 class="m-menu-title">
            <el-icon><component :is="group.icon" /></el-icon>
            {{ group.title }}
          </h3>
          <div class="m-menu-list">
            <button
              v-for="item in group.children"
              :key="item.path"
              type="button"
              class="m-menu-item"
              @click="go(item.path)"
            >
              <el-icon><component :is="item.meta.icon || 'Document'" /></el-icon>
              <span>{{ item.meta.title }}</span>
              <el-icon class="m-menu-arrow"><ArrowRight /></el-icon>
            </button>
          </div>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.m-nav {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
  padding: calc(8px + env(safe-area-inset-top)) 10px 8px;
  background: rgba(255, 255, 255, 0.94);
  border-bottom: 1px solid #eceffa;
  backdrop-filter: blur(12px);
}

.m-nav-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: #3a3e5c;
  background: transparent;
  border: none;
  border-radius: 10px;
}

.m-nav-btn:active {
  background: #f0f2fb;
}

.m-brand-icon {
  color: var(--ai-primary);
}

.m-nav-title {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  min-width: 0;
  line-height: 1.2;
}

.m-nav-title span {
  overflow: hidden;
  font-size: 16px;
  font-weight: 600;
  color: #23264a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-nav-title small {
  font-size: 11px;
  color: #9aa0b8;
}

.m-wechat-tip {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.5;
  color: #8a5d00;
  background: #fff8e6;
  border-bottom: 1px solid #f5e6c0;
}

.m-wechat-tip span {
  flex: 1;
}

.m-tip-close {
  flex-shrink: 0;
  cursor: pointer;
}

.m-main {
  flex: 1;
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  background:
    radial-gradient(600px 300px at 0% 0%, rgba(91, 108, 255, 0.08), transparent 62%),
    linear-gradient(180deg, #f4f6fc 0%, #f6f8fd 100%);
}

.m-tabbar {
  display: flex;
  flex-shrink: 0;
  align-items: stretch;
  padding-bottom: env(safe-area-inset-bottom);
  background: rgba(255, 255, 255, 0.97);
  border-top: 1px solid #eceffa;
  box-shadow: 0 -6px 18px rgba(31, 36, 94, 0.04);
}

.m-tab {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 8px 0 6px;
  font-size: 11px;
  color: #9aa0b8;
  background: transparent;
  border: none;
}

.m-tab.active {
  color: var(--ai-primary);
}

.m-tab.active .el-icon {
  transform: translateY(-1px);
}

.m-menu {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 20px;
}

.m-menu-title {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #8b90ab;
}

.m-menu-list {
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 12px;
}

.m-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 13px 14px;
  font-size: 14px;
  color: #3a3e5c;
  text-align: left;
  background: transparent;
  border: none;
  border-bottom: 1px solid #f2f4fb;
}

.m-menu-item:last-child {
  border-bottom: none;
}

.m-menu-item:active {
  background: #f6f8ff;
}

.m-menu-item .el-icon:first-child {
  color: var(--ai-primary);
}

.m-menu-arrow {
  margin-left: auto;
  color: #c6cce4;
}
</style>
