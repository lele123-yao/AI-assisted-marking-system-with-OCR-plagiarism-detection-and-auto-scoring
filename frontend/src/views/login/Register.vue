<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { EditPen, Lock, User } from '@element-plus/icons-vue'
import request from '@/api/request'
import { derivePermissions } from '@/config/permission'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
// 后端返回"用户名已存在"时记录该用户名，用于表单行内提示
const duplicateUsername = ref('')
// 后端返回"密码已被其他账号使用"时记录该密码，用于表单行内提示
const duplicatePassword = ref('')

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  // 自助注册只允许学生，教师账号由管理员创建
  identity: 'STUDENT'
})

// 用户名重复校验：后端是唯一权威，提交返回重复后回填到表单
function validateUsername(rule, value, callback) {
  if (duplicateUsername.value && duplicateUsername.value === value) {
    callback(new Error('用户名已存在'))
    return
  }
  callback()
}

// 密码重复校验：后端提示密码已被占用时回填到表单
function validatePassword(rule, value, callback) {
  if (duplicatePassword.value && duplicatePassword.value === value) {
    callback(new Error('该密码已被其他账号使用，请更换密码'))
    return
  }
  callback()
}

// 两次密码一致性校验
function validateConfirmPassword(rule, value, callback) {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

// 与后端 RegisterDTO 的校验规则保持一致
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度需为 4-20 位', trigger: 'blur' },
    { validator: validateUsername, trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { max: 32, message: '真实姓名最长 32 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需为 6-20 位', trigger: 'blur' },
    { validator: validatePassword, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// 修改用户名时清除"已存在"提示
function handleUsernameInput() {
  if (duplicateUsername.value) {
    duplicateUsername.value = ''
    formRef.value?.clearValidate('username')
  }
}

// 修改密码时清除"密码已被占用"提示
function handlePasswordInput() {
  if (duplicatePassword.value) {
    duplicatePassword.value = ''
    formRef.value?.clearValidate('password')
  }
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
      identity: form.identity
    })

    // 与登录页保持一致：保存 token / 用户 / 角色 / 权限，实现注册即登录
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
    router.replace('/')
  } catch (error) {
    // 用户名重复：Axios 拦截器已统一提示，这里额外定位到用户名输入框
    if (error?.message?.includes('用户名已存在')) {
      duplicateUsername.value = form.username.trim()
      formRef.value?.validateField('username', () => {})
    } else if (error?.message?.includes('密码已被')) {
      // 密码重复：定位到密码输入框，提示更换密码
      duplicatePassword.value = form.password
      formRef.value?.validateField('password', () => {})
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-page">
    <div class="register-card">
      <div class="register-header">
        <el-icon class="register-icon"><Reading /></el-icon>
        <h1 class="register-title">注册账号</h1>
        <p class="register-subtitle">高校主观题智能异步阅卷系统</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @keyup.enter="handleRegister"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名（4-20 位）"
            :prefix-icon="User"
            clearable
            @input="handleUsernameInput"
          />
        </el-form-item>

        <el-form-item prop="realName">
          <el-input
            v-model="form.realName"
            placeholder="请输入真实姓名"
            :prefix-icon="EditPen"
            clearable
          />
        </el-form-item>

        <el-form-item>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="学生自助注册"
            description="教师账号请联系管理员在「系统管理 → 用户管理」中创建"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码（6-20 位）"
            :prefix-icon="Lock"
            show-password
            @input="handlePasswordInput"
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="register-btn"
            :loading="loading"
            @click="handleRegister"
          >
            注 册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="register-footer">
        <span>已有账号？</span>
        <router-link to="/login" class="footer-link">返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #1f2d3d 0%, #2b4c7e 55%, #409eff 100%);
}

.register-card {
  width: 440px;
  padding: 36px 36px 20px;
  background-color: #fff;
  border-radius: 10px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.25);
}

.register-header {
  text-align: center;
  margin-bottom: 24px;
}

.register-icon {
  font-size: 42px;
  color: #409eff;
}

.register-title {
  margin: 12px 0 4px;
  font-size: 20px;
  color: #303133;
}

.register-subtitle {
  margin: 0;
  font-size: 13px;
  color: #909399;
  letter-spacing: 1px;
}

.identity-select {
  width: 100%;
}

.register-btn {
  width: 100%;
}

.register-footer {
  margin-top: 4px;
  text-align: center;
  font-size: 13px;
  color: #909399;
}

.footer-link {
  color: #409eff;
}

.footer-link:hover {
  text-decoration: underline;
}
</style>
