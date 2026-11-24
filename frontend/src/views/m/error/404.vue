<script setup>
import { useRouter } from 'vue-router'

/** 移动端 404：页面不存在 */
const router = useRouter()
</script>

<template>
  <div class="m-page">
    <section class="m-card error-card">
      <div class="error-icon">
        <el-icon :size="34"><DocumentDelete /></el-icon>
      </div>
      <h1>404</h1>
      <p>抱歉，你访问的页面不存在或已被移除</p>
      <div class="error-actions">
        <el-button type="primary" @click="router.replace('/m/home')">返回首页</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.error-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 46px 20px;
  text-align: center;
}

.error-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 74px;
  height: 74px;
  color: #5b6cff;
  background: #eef1ff;
  border-radius: 22px;
}

h1 {
  margin: 16px 0 6px;
  font-size: 26px;
  color: #23264a;
}

p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #8b90ab;
}

.error-actions {
  margin-top: 22px;
}
</style>
