<script setup>
import { ref } from 'vue'
import SideMenu from './components/SideMenu.vue'
import NavBar from './components/NavBar.vue'

// 侧边栏折叠状态
const collapsed = ref(false)
</script>

<template>
  <el-container class="layout">
    <!-- 侧边菜单栏 -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <el-icon class="logo-icon"><Reading /></el-icon>
        <span v-show="!collapsed" class="logo-text">智能阅卷系统</span>
      </div>
      <SideMenu :collapse="collapsed" />
    </el-aside>

    <el-container class="layout-body">
      <!-- 顶部导航栏 -->
      <el-header class="layout-header">
        <NavBar :collapse="collapsed" @toggle="collapsed = !collapsed" />
      </el-header>

      <!-- 内容区 -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.layout-aside {
  background: linear-gradient(180deg, #16183f 0%, #1d1b52 55%, #211a5c 100%);
  box-shadow: 6px 0 24px rgba(18, 20, 60, 0.16);
  transition: width 0.2s ease;
  overflow-x: hidden;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 60px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
  background: rgba(9, 10, 32, 0.45);
  border-bottom: 1px solid rgba(140, 156, 255, 0.14);
}

.logo-icon {
  font-size: 22px;
  color: #8ab4ff;
}

.layout-body {
  height: 100%;
  overflow: hidden;
}

.layout-header {
  height: 60px;
  padding: 0 20px;
  background-color: rgba(255, 255, 255, 0.86);
  border-bottom: 1px solid #e8ebf8;
  backdrop-filter: blur(10px);
}

.layout-main {
  padding: 20px;
  background:
    radial-gradient(900px 420px at 8% -10%, rgba(91, 108, 255, 0.1), transparent 62%),
    radial-gradient(760px 400px at 100% 0%, rgba(154, 91, 255, 0.08), transparent 58%),
    linear-gradient(180deg, #f2f5fe 0%, #f6f8fd 320px);
  overflow-y: auto;
}
</style>
