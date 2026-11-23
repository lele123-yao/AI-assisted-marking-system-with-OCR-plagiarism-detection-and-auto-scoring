<script setup>
/**
 * 页面顶部概览区（看板风格）。
 *
 * 用法：
 * <PageHero title="试卷管理" subtitle="..." icon="Document">
 *   <template #actions><el-button>新增</el-button></template>
 * </PageHero>
 */
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  icon: { type: String, default: 'Document' },
  // dark：深色渐变；light：浅色卡片，适合表单/详情类页面
  tone: { type: String, default: 'dark' }
})
</script>

<template>
  <div class="page-hero" :class="tone">
    <div class="hero-main">
      <div class="hero-icon">
        <el-icon :size="26"><component :is="icon" /></el-icon>
      </div>
      <div class="hero-text">
        <h2 class="hero-title">{{ title }}</h2>
        <p v-if="subtitle" class="hero-desc">{{ subtitle }}</p>
        <slot name="meta" />
      </div>
    </div>
    <div v-if="$slots.actions" class="hero-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-hero {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 26px;
  overflow: hidden;
  border-radius: 12px;
}

/* 深色渐变：AI 科技风（深蓝紫 + 光斑 + 网格） */
.page-hero.dark {
  color: #fff;
  background: var(--ai-gradient);
  border: 1px solid rgba(140, 156, 255, 0.18);
}

.page-hero.dark::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: '';
  background-image:
    linear-gradient(rgba(160, 174, 255, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(160, 174, 255, 0.08) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: linear-gradient(90deg, #000 0%, transparent 72%);
}

.page-hero.dark::after {
  position: absolute;
  top: -90px;
  right: -60px;
  width: 300px;
  height: 300px;
  pointer-events: none;
  content: '';
  background:
    radial-gradient(circle at 35% 40%, rgba(124, 108, 255, 0.55), transparent 62%),
    radial-gradient(circle at 68% 62%, rgba(41, 210, 228, 0.32), transparent 60%);
}

/* 浅色：适合以表单为主的页面，避免大面积深色 */
.page-hero.light {
  color: #2b2e45;
  background: linear-gradient(120deg, #ffffff 0%, #f3f5ff 60%, #f7f2ff 100%);
  border: 1px solid var(--ai-border);
}

.hero-main {
  display: flex;
  align-items: center;
  gap: 16px;
  z-index: 1;
  min-width: 0;
}

.hero-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 14px;
  flex-shrink: 0;
}

.dark .hero-icon {
  background: rgba(255, 255, 255, 0.14);
}

.light .hero-icon {
  color: var(--ai-primary);
  background: #eef1ff;
}

.hero-text {
  min-width: 0;
}

.hero-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.hero-desc {
  margin: 0;
  max-width: 720px;
  font-size: 13px;
  line-height: 1.7;
}

.dark .hero-desc {
  color: rgba(255, 255, 255, 0.78);
}

.light .hero-desc {
  color: #909399;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  z-index: 1;
}

/* 深色背景上的开关文字跟随主色 */
.dark .hero-actions :deep(.el-switch__label) {
  color: rgba(255, 255, 255, 0.85);
}
</style>
