<script setup>
/**
 * 移动端页头：标题 + 说明 + 右侧操作。
 * dark 为深色渐变样式，用于首页/登录等需要强调的页面。
 */
defineProps({
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  icon: { type: String, default: '' },
  dark: { type: Boolean, default: false }
})
</script>

<template>
  <header class="m-hero" :class="{ dark }">
    <div class="m-hero-main">
      <div v-if="icon" class="m-hero-icon">
        <el-icon :size="18"><component :is="icon" /></el-icon>
      </div>
      <div class="m-hero-text">
        <h1 class="m-hero-title">{{ title }}</h1>
        <p v-if="subtitle" class="m-hero-sub">{{ subtitle }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="m-hero-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.m-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 16px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 14px;
}

.m-hero.dark {
  position: relative;
  overflow: hidden;
  color: #fff;
  background: var(--ai-gradient);
  border-color: rgba(140, 156, 255, 0.2);
  box-shadow: 0 12px 28px rgba(30, 30, 100, 0.2);
}

.m-hero.dark::after {
  position: absolute;
  top: -40px;
  right: -30px;
  width: 130px;
  height: 130px;
  content: '';
  background: radial-gradient(circle, rgba(154, 91, 255, 0.45), transparent 68%);
}

.m-hero-main {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.m-hero-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--ai-primary);
  background: #eef1ff;
  border-radius: 10px;
}

.m-hero.dark .m-hero-icon {
  color: #cfd8ff;
  background: rgba(255, 255, 255, 0.12);
}

.m-hero-text {
  min-width: 0;
}

.m-hero-title {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  line-height: 1.35;
  color: #23264a;
}

.m-hero.dark .m-hero-title {
  color: #fff;
}

.m-hero-sub {
  margin: 5px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #8b90ab;
}

.m-hero.dark .m-hero-sub {
  color: rgba(214, 220, 255, 0.72);
}

.m-hero-actions {
  position: relative;
  z-index: 1;
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}
</style>
