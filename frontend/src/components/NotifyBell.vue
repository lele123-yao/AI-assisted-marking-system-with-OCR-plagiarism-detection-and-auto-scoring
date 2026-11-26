<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { pageNotify, readNotify, readAllNotify } from '@/api/notify'
import { useMarkNotify, resolveNotifyTarget } from '@/composables/useMarkNotify'

/**
 * 消息通知铃铛。
 *
 * 桌面端挂在顶部导航栏，移动端挂在顶栏（compact）。
 * 组件只负责"展示 + 已读 + 跳转"，WebSocket 连接由 useMarkNotify 单例维护：
 * 无论哪个布局挂载，全站始终只有一条连接。
 */
const props = defineProps({
  /** 移动端紧凑模式：更小的图标和弹层宽度 */
  compact: { type: Boolean, default: false }
})

const router = useRouter()
const { unreadCount, initMarkNotify, closeMarkNotify, refreshUnreadCount } = useMarkNotify()

const visible = ref(false)
const loading = ref(false)
const list = ref([])

onMounted(() => {
  // 建立（或复用）WebSocket 连接，并拉取一次未读数
  initMarkNotify()
})

onUnmounted(() => {
  // 布局卸载说明已离开登录态（如退出登录），关闭连接避免残留
  closeMarkNotify()
})

// 每次打开弹层都重新拉取，保证其他设备/离线期间的消息也能看到
watch(visible, (open) => {
  if (open) {
    loadList()
    refreshUnreadCount()
  }
})

async function loadList() {
  loading.value = true
  try {
    const page = await pageNotify({ current: 1, size: 20 })
    list.value = page?.records || []
  } catch {
    // 错误提示已由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function handleReadAll() {
  try {
    await readAllNotify()
    unreadCount.value = 0
    list.value = list.value.map((item) => ({ ...item, isRead: 1 }))
    ElMessage.success('已全部标为已读')
  } catch {
    // 拦截器已提示
  }
}

async function openItem(item) {
  if (item.isRead === 0) {
    try {
      await readNotify(item.id)
      item.isRead = 1
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {
      // 标记失败不阻断跳转
    }
  }
  if (!item.bizId) {
    return
  }
  visible.value = false
  router.push(resolveNotifyTarget({ bizId: item.bizId }))
}

/** 后端返回 ISO 字符串（2026-11-25T10:30:00），展示时替换为空格并截到秒 */
function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <el-popover
    v-model:visible="visible"
    trigger="click"
    placement="bottom-end"
    :width="props.compact ? 300 : 360"
    popper-class="notify-popper"
  >
    <template #reference>
      <span class="notify-trigger" :class="{ 'notify-trigger--compact': props.compact }">
        <el-badge :value="unreadCount" :max="99" :hidden="unreadCount === 0">
          <el-icon :size="props.compact ? 20 : 18"><Bell /></el-icon>
        </el-badge>
      </span>
    </template>

    <div class="notify-panel">
      <div class="notify-head">
        <span class="notify-head-title">消息通知</span>
        <el-button v-if="unreadCount > 0" link type="primary" size="small" @click="handleReadAll">
          全部已读
        </el-button>
      </div>

      <div v-loading="loading" class="notify-list">
        <el-empty
          v-if="!loading && list.length === 0"
          description="暂无消息"
          :image-size="56"
        />
        <div
          v-for="item in list"
          :key="item.id"
          class="notify-item"
          :class="{ 'is-unread': item.isRead === 0 }"
          @click="openItem(item)"
        >
          <div class="notify-item-title">
            <span v-if="item.isRead === 0" class="notify-dot" />
            {{ item.title }}
          </div>
          <div class="notify-item-content">{{ item.content }}</div>
          <div class="notify-item-time">{{ formatTime(item.createTime) }}</div>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<style scoped>
.notify-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: #606266;
  cursor: pointer;
  border-radius: 10px;
}

.notify-trigger:hover {
  color: #409eff;
  background: #f0f2fb;
}

.notify-trigger--compact {
  width: 36px;
  height: 36px;
  color: #3a3e5c;
}

.notify-trigger--compact:hover {
  background: #f0f2fb;
}

/* 弹层内部样式：弹层挂在 body 上，scoped 样式通过 notify-panel 的类名仍可命中 */
.notify-panel {
  display: flex;
  flex-direction: column;
  max-height: 420px;
}

.notify-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f2f7;
}

.notify-head-title {
  font-size: 14px;
  font-weight: 600;
  color: #23264a;
}

.notify-list {
  min-height: 80px;
  overflow-y: auto;
}

.notify-item {
  padding: 10px 4px;
  border-bottom: 1px solid #f5f7fb;
  cursor: pointer;
}

.notify-item:last-child {
  border-bottom: none;
}

.notify-item:hover {
  background: #f8faff;
}

.notify-item-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #3a3e5c;
}

.notify-dot {
  width: 6px;
  height: 6px;
  background: #f56c6c;
  border-radius: 50%;
}

.notify-item-content {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #606266;
  word-break: break-all;
}

.notify-item-time {
  margin-top: 4px;
  font-size: 11px;
  color: #9aa0b8;
}

.notify-item.is-unread .notify-item-title {
  color: #23264a;
}
</style>
