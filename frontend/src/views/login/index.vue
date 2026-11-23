<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

// 与后端 LoginDTO 的校验规则保持一致
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

// 左侧能力介绍
const features = [
  { title: '异步 AI 批改', desc: '主观题自动评分，人工只做确认与复核' },
  { title: '多维度学情', desc: '分数段、知识点、班级对比一屏掌握' },
  { title: '全流程留痕', desc: 'OCR、批改、复核、归档每一步可追溯' }
]

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
    // 登录前访问的页面（由路由守卫记录），没有则回首页
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' ? redirect : '/')
  } catch {
    // 具体错误信息已由 Axios 响应拦截器统一提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- 背景光斑与网格 -->
    <div class="bg-grid" />
    <div class="orb orb-1" />
    <div class="orb orb-2" />
    <div class="orb orb-3" />

    <div class="login-shell">
      <!-- 左侧品牌介绍 -->
      <section class="brand">
        <div class="brand-badge">
          <span class="dot" />
          AI MARKING SYSTEM
        </div>

        <h1 class="brand-title">
          高校主观题<br />
          <span class="gradient-text">智能异步阅卷系统</span>
        </h1>

        <p class="brand-desc">
          从答卷采集、OCR 识别到 AI 批改与成绩分析，构建一条可追溯的智能阅卷流水线，
          让教师把时间还给教学。
        </p>

        <ul class="feature-list">
          <li v-for="(item, index) in features" :key="item.title" class="feature-item">
            <span class="feature-index">0{{ index + 1 }}</span>
            <div>
              <div class="feature-title">{{ item.title }}</div>
              <div class="feature-desc">{{ item.desc }}</div>
            </div>
          </li>
        </ul>

        <div class="brand-stats">
          <div class="brand-stat">
            <strong>3</strong>
            <span>角色权限体系</span>
          </div>
          <div class="brand-stat">
            <strong>5+</strong>
            <span>阅卷处理阶段</span>
          </div>
          <div class="brand-stat">
            <strong>24h</strong>
            <span>在线服务</span>
          </div>
        </div>
      </section>

      <!-- 右侧登录卡 -->
      <section class="login-panel">
        <div class="login-card">
          <div class="login-head">
            <div class="login-logo">
              <el-icon :size="24"><Reading /></el-icon>
            </div>
            <div>
              <h2 class="login-title">账号登录</h2>
              <p class="login-subtitle">欢迎回来，请使用系统账号进入工作台</p>
            </div>
          </div>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            size="large"
            class="login-form"
            @keyup.enter="handleLogin"
          >
            <el-form-item prop="username">
              <el-input
                v-model="form.username"
                placeholder="请输入用户名"
                :prefix-icon="User"
                clearable
              />
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>

            <el-form-item class="submit-item">
              <el-button
                type="primary"
                class="login-btn"
                :loading="loading"
                @click="handleLogin"
              >
                登 录
              </el-button>
            </el-form-item>
          </el-form>

          <div class="login-foot">
            <span>没有账号？</span>
            <router-link to="/register" class="foot-link">去注册</router-link>
          </div>
          <p class="login-tip">仅限管理员 / 教师 / 学生使用</p>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 100%;
  padding: 32px;
  overflow: hidden;
  background:
    radial-gradient(1200px 700px at 12% 8%, #23235f 0%, transparent 60%),
    radial-gradient(900px 600px at 88% 92%, #3b2f9c 0%, transparent 62%),
    linear-gradient(135deg, #0b0c24 0%, #121338 45%, #1b1a4d 100%);
}

/* 网格背景 */
.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(140, 160, 255, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(140, 160, 255, 0.08) 1px, transparent 1px);
  background-size: 46px 46px;
  mask-image: radial-gradient(circle at 50% 45%, #000 0%, transparent 78%);
}

/* 漂浮光斑 */
.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.55;
  animation: float 12s ease-in-out infinite;
}

.orb-1 {
  top: -120px;
  left: -80px;
  width: 380px;
  height: 380px;
  background: radial-gradient(circle, #4d6bff 0%, transparent 68%);
}

.orb-2 {
  right: -110px;
  bottom: -140px;
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, #9a5bff 0%, transparent 68%);
  animation-delay: -4s;
}

.orb-3 {
  top: 42%;
  right: 26%;
  width: 240px;
  height: 240px;
  background: radial-gradient(circle, #29d2e4 0%, transparent 70%);
  opacity: 0.25;
  animation-delay: -7s;
}

@keyframes float {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(0, -26px, 0) scale(1.06);
  }
}

.login-shell {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 0.85fr);
  gap: 56px;
  align-items: center;
  width: 100%;
  max-width: 1120px;
}

/* ==================== 左侧品牌 ==================== */
.brand {
  color: #fff;
}

.brand-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  margin-bottom: 26px;
  font-size: 12px;
  letter-spacing: 2px;
  color: #aeb8ff;
  background: rgba(122, 140, 255, 0.12);
  border: 1px solid rgba(122, 140, 255, 0.32);
  border-radius: 999px;
}

.brand-badge .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #5cf0c8;
  box-shadow: 0 0 10px #5cf0c8;
}

.brand-title {
  margin: 0 0 20px;
  font-size: 44px;
  font-weight: 700;
  line-height: 1.28;
  letter-spacing: 1px;
}

.gradient-text {
  background: linear-gradient(96deg, #8ea2ff 0%, #b98cff 55%, #5cf0c8 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.brand-desc {
  max-width: 520px;
  margin: 0 0 32px;
  font-size: 15px;
  line-height: 1.9;
  color: rgba(214, 220, 255, 0.72);
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 0;
  margin: 0 0 40px;
  list-style: none;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.feature-index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  font-size: 12px;
  font-weight: 600;
  color: #9fb0ff;
  background: rgba(126, 146, 255, 0.14);
  border: 1px solid rgba(126, 146, 255, 0.28);
  border-radius: 10px;
  flex-shrink: 0;
}

.feature-title {
  font-size: 15px;
  font-weight: 600;
  color: #eef1ff;
}

.feature-desc {
  margin-top: 4px;
  font-size: 13px;
  color: rgba(190, 198, 240, 0.62);
}

.brand-stats {
  display: flex;
  gap: 44px;
  padding-top: 26px;
  border-top: 1px solid rgba(140, 156, 255, 0.16);
}

.brand-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.brand-stat strong {
  font-size: 26px;
  font-weight: 700;
  color: #fff;
}

.brand-stat span {
  font-size: 12px;
  color: rgba(190, 198, 240, 0.6);
}

/* ==================== 右侧登录卡 ==================== */
.login-panel {
  display: flex;
  justify-content: flex-end;
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 36px 32px 26px;
  background: rgba(20, 23, 62, 0.62);
  border: 1px solid rgba(150, 166, 255, 0.22);
  border-radius: 20px;
  box-shadow:
    0 30px 80px rgba(3, 5, 26, 0.6),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(22px);
}

.login-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;
}

.login-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  color: #fff;
  background: linear-gradient(135deg, #4d6bff 0%, #9a5bff 100%);
  border-radius: 14px;
  box-shadow: 0 10px 24px rgba(88, 100, 255, 0.35);
}

.login-title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
  color: #f2f4ff;
}

.login-subtitle {
  margin: 0;
  font-size: 12px;
  color: rgba(190, 198, 240, 0.6);
}

/* 玻璃风输入框 */
.login-form :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.06);
  box-shadow: 0 0 0 1px rgba(150, 166, 255, 0.24) inset;
  border-radius: 10px;
  transition: box-shadow 0.2s ease, background 0.2s ease;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(150, 166, 255, 0.5) inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  background: rgba(255, 255, 255, 0.1);
  box-shadow: 0 0 0 1px #6c7bff inset;
}

.login-form :deep(.el-input__inner) {
  color: #f2f4ff;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: rgba(176, 186, 232, 0.5);
}

.login-form :deep(.el-input__prefix-inner .el-icon) {
  color: rgba(176, 186, 232, 0.7);
}

.login-form :deep(.el-form-item__error) {
  color: #ff9f9f;
}

.submit-item {
  margin-top: 6px;
}

.login-btn {
  width: 100%;
  height: 46px;
  font-size: 15px;
  letter-spacing: 4px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(96deg, #4d6bff 0%, #7a5bff 55%, #9a5bff 100%);
  box-shadow: 0 12px 26px rgba(88, 100, 255, 0.38);
}

.login-btn:hover {
  background: linear-gradient(96deg, #5b78ff 0%, #8768ff 55%, #a869ff 100%);
}

.login-foot {
  margin-top: 10px;
  text-align: center;
  font-size: 13px;
  color: rgba(190, 198, 240, 0.62);
}

.foot-link {
  margin-left: 6px;
  color: #9fb0ff;
}

.foot-link:hover {
  color: #c3cbff;
  text-decoration: underline;
}

.login-tip {
  margin: 8px 0 0;
  text-align: center;
  font-size: 12px;
  color: rgba(176, 186, 232, 0.4);
}

@media (max-width: 960px) {
  .login-shell {
    grid-template-columns: 1fr;
    justify-items: center;
    gap: 32px;
  }

  .brand {
    text-align: center;
  }

  .brand-desc {
    margin-left: auto;
    margin-right: auto;
  }

  .feature-list,
  .brand-stats {
    display: none;
  }

  .brand-title {
    font-size: 32px;
  }

  .login-panel {
    justify-content: center;
  }
}

@media (prefers-reduced-motion: reduce) {
  .orb {
    animation: none;
  }
}
</style>
