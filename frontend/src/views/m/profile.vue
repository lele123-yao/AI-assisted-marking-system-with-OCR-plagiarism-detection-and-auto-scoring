<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { setPreferDesktop } from '@/utils/device'
import MobileHero from '@/components/m/MobileHero.vue'

/** 我的：账号信息、常用入口、版本切换、退出登录 */
const router = useRouter()
const userStore = useUserStore()

const roleNames = computed(() =>
  (userStore.userInfo?.roles || []).map((role) => role.roleName || role.roleCode)
)

const entries = computed(() =>
  [
    { label: '我的试卷', icon: 'Document', path: '/m/student/papers', permission: 'student:paper' },
    { label: '我的成绩', icon: 'Trophy', path: '/m/student/scores', permission: 'student:score' },
    { label: '大模型设置', icon: 'Key', path: '/m/llm/setting', permission: 'llm:setting' },
    { label: '全部功能', icon: 'Grid', path: '/m/menu' }
  ].filter((item) => userStore.hasPermission(item.permission))
)

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  userStore.logout()
  ElMessage.success('已退出登录')
  router.replace('/m/login')
}

function useDesktop() {
  setPreferDesktop(true)
  window.location.href = '/'
}
</script>

<template>
  <div class="m-page">
    <MobileHero title="我的" subtitle="账号信息与版本设置" icon="User" />

    <section class="m-card profile-card">
      <div class="avatar">{{ userStore.nickname.charAt(0).toUpperCase() }}</div>
      <div class="profile-main">
        <h2>{{ userStore.nickname }}</h2>
        <div class="roles">
          <el-tag v-for="role in roleNames" :key="role" size="small" effect="plain">{{ role }}</el-tag>
          <span v-if="!roleNames.length" class="m-sub">未分配角色</span>
        </div>
      </div>
    </section>

    <section class="m-card info-card">
      <ul>
        <li><span>用户名</span><b>{{ userStore.userInfo?.username || '-' }}</b></li>
        <li><span>昵称</span><b>{{ userStore.userInfo?.nickname || '-' }}</b></li>
        <li><span>邮箱</span><b>{{ userStore.userInfo?.email || '-' }}</b></li>
      </ul>
    </section>

    <section class="m-card flush">
      <button
        v-for="item in entries"
        :key="item.path"
        type="button"
        class="entry"
        @click="router.push(item.path)"
      >
        <span class="entry-icon"><el-icon :size="17"><component :is="item.icon" /></el-icon></span>
        <span>{{ item.label }}</span>
        <el-icon class="entry-arrow"><ArrowRight /></el-icon>
      </button>
    </section>

    <section class="m-card flush">
      <button type="button" class="entry" @click="useDesktop">
        <span class="entry-icon"><el-icon :size="17"><Monitor /></el-icon></span>
        <span>使用电脑版</span>
        <el-icon class="entry-arrow"><ArrowRight /></el-icon>
      </button>
      <button type="button" class="entry danger" @click="handleLogout">
        <span class="entry-icon"><el-icon :size="17"><SwitchButton /></el-icon></span>
        <span>退出登录</span>
        <el-icon class="entry-arrow"><ArrowRight /></el-icon>
      </button>
    </section>
  </div>
</template>

<style scoped>
.profile-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: linear-gradient(135deg, #1c1f52, #35307f);
  border-color: rgba(140, 156, 255, 0.24);
}

.avatar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 58px;
  height: 58px;
  font-size: 22px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #5b6cff, #9a5bff);
  border-radius: 18px;
}

.profile-main h2 {
  margin: 0 0 7px;
  font-size: 18px;
  color: #fff;
}

.roles {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.info-card ul {
  padding: 0;
  margin: 0;
  list-style: none;
}

.info-card li {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  font-size: 13px;
  color: #8b90ab;
  border-bottom: 1px dashed #f0f2fb;
}

.info-card li:last-child {
  border-bottom: none;
}

.info-card b {
  font-weight: 500;
  color: #3a3e5c;
}

.m-card.flush {
  padding: 0;
  overflow: hidden;
}

.entry {
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  padding: 14px;
  font-size: 14px;
  color: #3a3e5c;
  text-align: left;
  background: transparent;
  border: none;
  border-bottom: 1px solid #f2f4fb;
}

.entry:last-child {
  border-bottom: none;
}

.entry:active {
  background: #f6f8ff;
}

.entry-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--ai-primary);
  background: #eef1ff;
  border-radius: 10px;
}

.entry.danger {
  color: #f56c6c;
}

.entry.danger .entry-icon {
  color: #f56c6c;
  background: #fef0f0;
}

.entry-arrow {
  margin-left: auto;
  color: #c6cce4;
}
</style>
