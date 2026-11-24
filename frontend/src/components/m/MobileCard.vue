<script setup>
/** 移动端统一卡片容器，避免各页面重复写圆角、边框与阴影。 */
defineProps({
  title: { type: String, default: '' },
  padding: { type: Boolean, default: true }
})
</script>

<template>
  <section class="m-box" :class="{ flush: !padding }">
    <div v-if="title || $slots.extra" class="m-box-head">
      <span class="m-box-title">{{ title }}</span>
      <slot name="extra" />
    </div>
    <slot />
  </section>
</template>

<style scoped>
.m-box {
  padding: 14px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 14px;
  box-shadow: 0 6px 18px rgba(31, 36, 94, 0.05);
}

.m-box.flush {
  padding: 0;
  overflow: hidden;
}

.m-box-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.m-box-title {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 15px;
  font-weight: 600;
  color: #23264a;
}

.m-box-title::before {
  width: 3px;
  height: 14px;
  content: '';
  background: linear-gradient(180deg, var(--ai-primary), var(--ai-accent));
  border-radius: 2px;
}
</style>
