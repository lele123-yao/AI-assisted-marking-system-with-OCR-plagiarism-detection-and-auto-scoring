<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { setPreferDesktop } from '@/utils/device'

/** 移动端登录：深色渐变头图 + 白色圆角表单卡 */
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度需为 4-20 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需为 6-20 位', trigger: 'blur' }
  ]
}

async function handleLogin() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')

    const redirect = route.query.redirect
    if (typeof redirect === 'string' && redirect.startsWith('/m')) {
      router.replace(redirect)
      return
    }
    router.replace(userStore.hasRole('STUDENT') ? '/m/student/papers' : '/m/home')
  } catch {
    // 错误提示由 Axios 响应拦截器统一处理
  } finally {
    loading.value = false
  }
}

function useDesktop() {
  setPreferDesktop(true)
  window.location.href = '/login'
}
</script>

<template>
  <div class="m-login">
    <div class="login-glow glow-a" />
    <div class="login-glow glow-b" />

    <header class="login-brand">
      <div class="brand-badge">
        <span class="dot" />
        AI MARKING
      </div>
      <h1>高校主观题<br /><span>智能异步阅卷系统</span></h1>
      <p>手机端随时查看试卷、成绩与批改进度</p>
    </header>

    <section class="login-card">
      <h2>账号登录</h2>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form>

      <div class="login-links">
        <button type="button" @click="router.push('/m/register')">注册账号</button>
        <span />
        <button type="button" @click="useDesktop">使用电脑版</button>
      </div>
    </section>

    <p class="login-foot">微信内可直接使用，建议添加到桌面</p>
  </div>
</template>

<style scoped>
.m-login {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-height: 100dvh;
  padding: calc(44px + env(safe-area-inset-top)) 22px calc(24px + env(safe-area-inset-bottom));
  overflow: hidden;
  background: linear-gradient(165deg, #14163f 0%, #232463 55%, #3d3290 100%);
}

.login-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(20px);
}

.glow-a {
  top: -80px;
  right: -60px;
  width: 240px;
  height: 240px;
  background: radial-gradient(circle, rgba(154, 91, 255, 0.5), transparent 70%);
}

.glow-b {
  bottom: 40px;
  left: -90px;
  width: 260px;
  height: 260px;
  background: radial-gradient(circle, rgba(41, 210, 228, 0.28), transparent 70%);
}

.login-brand {
  position: relative;
  z-index: 1;
  color: #fff;
}

.brand-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 12px;
  margin-bottom: 18px;
  font-size: 11px;
  letter-spacing: 1px;
  color: #b6c1ff;
  background: rgba(126, 146, 255, 0.14);
  border: 1px solid rgba(126, 146, 255, 0.3);
  border-radius: 999px;
}

.brand-badge .dot {
  width: 6px;
  height: 6px;
  background: #5cf0c8;
  border-radius: 50%;
  box-shadow: 0 0 8px #5cf0c8;
}

.login-brand h1 {
  margin: 0 0 10px;
  font-size: 25px;
  font-weight: 700;
  line-height: 1.4;
}

.login-brand h1 span {
  color: #b9c4ff;
}

.login-brand p {
  margin: 0;
  font-size: 13px;
  color: rgba(214, 220, 255, 0.68);
}

.login-card {
  position: relative;
  z-index: 1;
  padding: 20px 18px 16px;
  margin-top: auto;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 18px 44px rgba(8, 10, 40, 0.35);
}

.login-card h2 {
  margin: 0 0 16px;
  font-size: 17px;
  font-weight: 600;
  color: #23264a;
}

.login-btn {
  width: 100%;
  height: 44px;
  margin-top: 4px;
  font-size: 15px;
  background: linear-gradient(120deg, #5b6cff, #9a5bff);
  border: none;
  border-radius: 12px;
}

.login-links {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 14px;
}

.login-links button {
  padding: 4px;
  font-size: 13px;
  color: #6b7192;
  background: transparent;
  border: none;
}

.login-links span {
  width: 1px;
  height: 12px;
  background: #e2e6f5;
}

.login-foot {
  position: relative;
  z-index: 1;
  margin: 18px 0 0;
  font-size: 11px;
  text-align: center;
  color: rgba(190, 198, 240, 0.55);
}
</style>
