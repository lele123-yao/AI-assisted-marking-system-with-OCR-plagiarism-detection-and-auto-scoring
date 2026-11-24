<script setup>
import { useRouter } from 'vue-router'

/** 移动端 403：无访问权限 */
const router = useRouter()
</script>

<template>
  <div class="m-page">
    <section class="m-card error-card">
      <div class="error-icon">
        <el-icon :size="34"><Lock /></el-icon>
      </div>
      <h1>403</h1>
      <p>抱歉，你没有访问该页面的权限</p>
      <div class="error-actions">
        <el-button type="primary" @click="router.replace('/m/home')">返回首页</el-button>
        <el-button plain @click="router.back()">返回上一页</el-button>
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
  color: #e6a23c;
  background: #fdf6ec;
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
  display: flex;
  gap: 10px;
  margin-top: 22px;
}

.error-actions .el-button {
  margin-left: 0;
}
</style>
