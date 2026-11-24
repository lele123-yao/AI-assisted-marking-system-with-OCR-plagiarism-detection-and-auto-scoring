<script setup>
/**
 * 移动端搜索栏：关键词输入 + 可选筛选插槽 + 查询/重置。
 * 只有一个输入框时隐藏「重置」，减少小屏占用。
 */
defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '搜索' },
  showReset: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'search', 'reset'])
</script>

<template>
  <div class="m-search">
    <div class="m-search-main">
      <el-input
        :model-value="modelValue"
        :placeholder="placeholder"
        clearable
        @update:model-value="emit('update:modelValue', $event)"
        @keyup.enter="emit('search')"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" :loading="loading" @click="emit('search')">查询</el-button>
    </div>
    <div v-if="$slots.default" class="m-search-filters">
      <slot />
    </div>
    <el-button v-if="showReset" link size="small" class="m-search-reset" @click="emit('reset')">
      重置筛选条件
    </el-button>
  </div>
</template>

<style scoped>
.m-search {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 14px;
}

.m-search-main {
  display: flex;
  gap: 8px;
}

.m-search-main .el-button {
  flex-shrink: 0;
}

.m-search-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.m-search-filters :deep(.el-select),
.m-search-filters :deep(.el-input) {
  flex: 1 1 130px;
}

.m-search-reset {
  align-self: flex-start;
}
</style>
