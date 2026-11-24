<script setup>
/**
 * 移动端指标卡网格。
 * items: [{ key, label, value, icon, tone }]，tone 支持 primary / success / warning / danger / info。
 */
defineProps({
  items: { type: Array, default: () => [] },
  columns: { type: Number, default: 2 }
})

defineEmits(['select'])
</script>

<template>
  <div class="m-stat-grid" :style="{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }">
    <button
      v-for="item in items"
      :key="item.key || item.label"
      type="button"
      class="m-stat"
      :class="item.tone || 'primary'"
      @click="$emit('select', item)"
    >
      <span class="m-stat-top">
        <span class="m-stat-icon">
          <el-icon :size="16"><component :is="item.icon || 'DataLine'" /></el-icon>
        </span>
        <el-icon class="m-stat-arrow" :size="12"><ArrowRight /></el-icon>
      </span>
      <b class="m-stat-value">{{ item.value }}</b>
      <span class="m-stat-label">{{ item.label }}</span>
    </button>
  </div>
</template>

<style scoped>
.m-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 13px;
  text-align: left;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 14px;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.m-stat:active {
  transform: scale(0.98);
}

.m-stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.m-stat-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--ai-primary);
  background: #eef1ff;
  border-radius: 9px;
}

.m-stat.success .m-stat-icon {
  color: #4caf50;
  background: #f0f9eb;
}

.m-stat.warning .m-stat-icon {
  color: #e6a23c;
  background: #fdf6ec;
}

.m-stat.danger .m-stat-icon {
  color: #f56c6c;
  background: #fef0f0;
}

.m-stat-arrow {
  color: #d0d5ea;
}

.m-stat-value {
  font-size: 23px;
  font-weight: 700;
  line-height: 1.2;
  color: #23264a;
}

.m-stat-label {
  font-size: 12px;
  color: #8b90ab;
}
</style>
