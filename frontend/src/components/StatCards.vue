<script setup>
/**
 * 概览统计卡片组。
 *
 * items 每项：
 * { label, value, icon, color, bg, hint, clickable }
 * 点击卡片抛出 select 事件，页面可据此筛选列表或跳转。
 */
const props = defineProps({
  items: { type: Array, default: () => [] },
  columns: { type: Number, default: 4 },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['select'])

function handleClick(item) {
  if (item.clickable || item.to) emit('select', item)
}
</script>

<template>
  <div
    v-loading="loading"
    class="stat-cards"
    :style="{ '--cols': Math.min(props.columns, 4) }"
  >
    <div
      v-for="item in items"
      :key="item.label"
      class="stat-card"
      :class="{ clickable: item.clickable || item.to }"
      @click="handleClick(item)"
    >
      <div class="stat-icon" :style="{ color: item.color || '#409eff', backgroundColor: item.bg || '#ecf5ff' }">
        <el-icon :size="22"><component :is="item.icon || 'DataLine'" /></el-icon>
      </div>
      <div class="stat-info">
        <div class="stat-value">{{ item.value ?? '-' }}</div>
        <div class="stat-label">{{ item.label }}</div>
        <div v-if="item.hint" class="stat-hint">{{ item.hint }}</div>
      </div>
      <el-icon v-if="item.to" class="stat-arrow"><ArrowRight /></el-icon>
    </div>
  </div>
</template>

<style scoped>
.stat-cards {
  display: grid;
  grid-template-columns: repeat(var(--cols), minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.stat-card.clickable {
  cursor: pointer;
}

.stat-card.clickable:hover {
  transform: translateY(-3px);
  border-color: #c6e2ff;
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.12);
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  flex-shrink: 0;
}

.stat-info {
  min-width: 0;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
  color: #303133;
}

.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}

.stat-hint {
  margin-top: 2px;
  font-size: 12px;
  color: #c0c4cc;
}

.stat-arrow {
  margin-left: auto;
  color: #c0c4cc;
  transition: color 0.2s ease, transform 0.2s ease;
}

.stat-card:hover .stat-arrow {
  color: #409eff;
  transform: translateX(4px);
}

@media (max-width: 1200px) {
  .stat-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .stat-cards {
    grid-template-columns: 1fr;
  }
}
</style>
