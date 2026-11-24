<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Breadcrumb from './Breadcrumb.vue'
import { useUserStore } from '@/store/user'
import { setPreferDesktop } from '@/utils/device'

defineProps({
  collapse: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['toggle'])

const router = useRouter()
const userStore = useUserStore()

// 角色展示文案
const roleText = computed(() => userStore.roleNames.join(' / ') || '未分配角色')

// 退出登录
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
  router.replace('/login')
}

/** 下拉菜单命令：退出登录 / 切换手机版 */
function handleCommand(command) {
  if (command === 'logout') {
    handleLogout()
  } else if (command === 'mobile') {
    setPreferDesktop(false)
    window.location.href = '/m/home'
  }
}
</script>

<template>
  <div class="navbar">
    <div class="navbar-left">
      <el-icon class="collapse-btn" @click="emit('toggle')">
        <Expand v-if="collapse" />
        <Fold v-else />
      </el-icon>
      <Breadcrumb />
    </div>

    <div class="navbar-right">
      <el-tag type="info" effect="plain" class="role-tag">{{ roleText }}</el-tag>
      <el-dropdown trigger="click" @command="handleCommand">
        <span class="user-info">
          <el-avatar :size="30" :src="userStore.userInfo?.avatar || undefined">
            {{ userStore.nickname.charAt(0).toUpperCase() }}
          </el-avatar>
          <span class="nickname">{{ userStore.nickname }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="mobile">
              <el-icon><Iphone /></el-icon>
              手机版
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style scoped>
.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #606266;
}

.collapse-btn:hover {
  color: #409eff;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.role-tag {
  font-size: 12px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.nickname {
  font-size: 14px;
  color: #303133;
}
</style>
