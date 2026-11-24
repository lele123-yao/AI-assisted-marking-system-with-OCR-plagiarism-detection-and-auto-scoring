<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import request from '@/api/request'
import { derivePermissions } from '@/config/permission'
import { useUserStore } from '@/store/user'

/** 移动端注册：与桌面注册页保持一致，仅开放学生自助注册 */
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度需为 4-20 位', trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { max: 32, message: '真实姓名最长 32 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需为 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

async function handleRegister() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const data = await request.post('/auth/register', {
      username: form.username.trim(),
      password: form.password,
      realName: form.realName.trim(),
      identity: 'STUDENT'
    })

    // 注册即登录
    const user = data?.user || null
    userStore.token = data?.token || ''
    userStore.userInfo = user
    userStore.roles = (user?.roles || []).map((role) => role.roleCode)
    const backendPermissions = user?.permissions
    userStore.permissions =
      Array.isArray(backendPermissions) && backendPermissions.length
        ? backendPermissions
        : derivePermissions(userStore.roles)
    userStore.persist()

    ElMessage.success('注册成功')
    router.replace('/m/student/papers')
  } catch {
    // 错误提示由 Axios 响应拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="m-register">
    <header class="register-head">
      <el-icon :size="26"><Reading /></el-icon>
      <h1>注册学生账号</h1>
      <p>教师账号由管理员创建</p>
    </header>

    <section class="register-card">
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleRegister">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名（4-20 位）" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名" :prefix-icon="EditPen" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码（6-20 位）"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="确认密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-button type="primary" class="register-btn" :loading="loading" @click="handleRegister">
          注册并登录
        </el-button>
      </el-form>

      <button type="button" class="back-login" @click="router.replace('/m/login')">
        已有账号，返回登录
      </button>
    </section>
  </div>
</template>

<style scoped>
.m-register {
  min-height: 100vh;
  min-height: 100dvh;
  padding: calc(40px + env(safe-area-inset-top)) 20px calc(24px + env(safe-area-inset-bottom));
  background: linear-gradient(165deg, #14163f 0%, #232463 55%, #3d3290 100%);
}

.register-head {
  margin-bottom: 20px;
  color: #fff;
}

.register-head h1 {
  margin: 10px 0 6px;
  font-size: 22px;
  font-weight: 700;
}

.register-head p {
  margin: 0;
  font-size: 12px;
  color: rgba(214, 220, 255, 0.66);
}

.register-card {
  padding: 20px 18px 16px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 18px 44px rgba(8, 10, 40, 0.32);
}

.register-btn {
  width: 100%;
  height: 44px;
  margin-top: 4px;
  font-size: 15px;
  background: linear-gradient(120deg, #5b6cff, #9a5bff);
  border: none;
  border-radius: 12px;
}

.back-login {
  display: block;
  margin: 14px auto 0;
  font-size: 13px;
  color: #6b7192;
  background: transparent;
  border: none;
}
</style>
