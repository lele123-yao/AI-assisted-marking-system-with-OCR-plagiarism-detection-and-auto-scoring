<script setup>
/** 移动端空态：图标 + 文案 + 可选操作按钮。 */
defineProps({
  description: { type: String, default: '暂无数据' },
  icon: { type: String, default: 'Document' },
  actionText: { type: String, default: '' }
})

defineEmits(['action'])
</script>

<template>
  <div class="m-empty">
    <el-icon class="m-empty-icon" :size="30"><component :is="icon" /></el-icon>
    <p class="m-empty-text">{{ description }}</p>
    <el-button v-if="actionText" size="small" plain @click="$emit('action')">
      {{ actionText }}
    </el-button>
  </div>
</template>

<style scoped>
.m-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 38px 16px;
}

.m-empty-icon {
  color: #c6cce4;
}

.m-empty-text {
  margin: 0;
  font-size: 13px;
  color: #9aa0b8;
}
</style>
