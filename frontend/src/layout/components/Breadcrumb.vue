<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

// 面包屑取当前匹配路由中带 title 的记录
const items = computed(() => {
  const matched = route.matched.filter((item) => item.meta?.title)
  const list = matched.map((item) => ({
    path: item.path,
    title: item.meta.title
  }))
  // 非首页时补一个"首页"入口
  if (route.path !== '/dashboard' && list[0]?.title !== '首页') {
    list.unshift({ path: '/dashboard', title: '首页' })
  }
  return list
})
</script>

<template>
  <el-breadcrumb separator="/" class="breadcrumb">
    <el-breadcrumb-item v-for="(item, index) in items" :key="item.path">
      <span :class="{ 'is-current': index === items.length - 1 }">{{ item.title }}</span>
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<style scoped>
.breadcrumb {
  display: inline-flex;
  align-items: center;
  font-size: 14px;
}

.is-current {
  color: #909399;
}
</style>
