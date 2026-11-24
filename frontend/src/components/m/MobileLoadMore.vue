<script setup>
/** 加载更多：未加载完显示按钮，加载完显示提示。 */
defineProps({
  loading: { type: Boolean, default: false },
  finished: { type: Boolean, default: false },
  empty: { type: Boolean, default: false }
})

defineEmits(['load'])
</script>

<template>
  <div v-if="!empty" class="m-load-more">
    <el-button v-if="!finished" text :loading="loading" @click="$emit('load')">
      {{ loading ? '加载中…' : '加载更多' }}
    </el-button>
    <span v-else class="m-load-end">没有更多了</span>
  </div>
</template>

<style scoped>
.m-load-more {
  display: flex;
  justify-content: center;
  padding: 8px 0 4px;
}

.m-load-end {
  font-size: 12px;
  color: #b0b4c8;
}
</style>
