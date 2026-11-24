<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getLlmCredential,
  removeLlmCredential,
  saveLlmCredential,
  testLlmCredential
} from '@/api/llm'
import { formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileSheet from '@/components/m/MobileSheet.vue'

/** 移动端大模型设置：查看脱敏状态，保存 / 测试 / 清除个人 API Key */
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const clearing = ref(false)
const formVisible = ref(false)
const testResult = ref('')

// 后端只返回脱敏信息
const status = reactive({
  configured: false,
  maskedKey: '',
  apiUrl: '',
  model: '',
  expireAt: null
})

// 保存后清空 apiKey，避免页面残留明文
const form = reactive({ apiKey: '', apiUrl: '', model: '' })

const expireText = computed(() => formatTime(status.expireAt))

function applyStatus(data) {
  if (!data) return
  status.configured = !!data.configured
  status.maskedKey = data.maskedKey || ''
  status.apiUrl = data.apiUrl || ''
  status.model = data.model || ''
  status.expireAt = data.expireAt || null
}

async function load() {
  loading.value = true
  try {
    applyStatus(await getLlmCredential())
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

function openForm() {
  form.apiKey = ''
  form.apiUrl = status.apiUrl || ''
  form.model = status.model || ''
  formVisible.value = true
}

async function handleSave() {
  if (!form.apiKey.trim()) {
    ElMessage.warning('请先填写 API Key')
    return
  }
  saving.value = true
  try {
    applyStatus(
      await saveLlmCredential({
        apiKey: form.apiKey.trim(),
        apiUrl: form.apiUrl.trim(),
        model: form.model.trim()
      })
    )
    form.apiKey = ''
    formVisible.value = false
    testResult.value = ''
    ElMessage.success('保存成功，后续 AI 调用将使用你的 Key')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}

async function handleTest() {
  testing.value = true
  testResult.value = ''
  try {
    const msg = await testLlmCredential()
    testResult.value = msg || '连接成功'
    ElMessage.success(testResult.value)
  } catch {
    // 真实调用失败的提示由 request 拦截器统一处理
  } finally {
    testing.value = false
  }
}

async function handleRemove() {
  try {
    await ElMessageBox.confirm('确定清除已保存的 API Key 吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  clearing.value = true
  try {
    await removeLlmCredential()
    applyStatus({ configured: false })
    testResult.value = ''
    ElMessage.success('已清除')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    clearing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="大模型设置"
      subtitle="配置 AI 阅卷所使用的大模型接口参数，Key 仅保存在当前服务会话中。"
      icon="Key"
    >
      <template #actions>
        <el-tag :type="status.configured ? 'success' : 'info'" effect="plain">
          {{ status.configured ? '已配置' : '未配置' }}
        </el-tag>
      </template>
    </MobileHero>

    <section v-loading="loading" class="m-card status-card">
      <div class="m-row-between">
        <span class="m-title">当前配置</span>
        <span class="badge" :class="{ online: status.configured }">
          <i />
          {{ status.configured ? '接口已就绪' : '尚未配置' }}
        </span>
      </div>

      <ul class="status-list">
        <li><span>当前 Key</span><b>{{ status.maskedKey || '-' }}</b></li>
        <li><span>接口地址</span><b>{{ status.apiUrl || '默认（DeepSeek 官方）' }}</b></li>
        <li><span>模型</span><b>{{ status.model || 'deepseek-chat' }}</b></li>
        <li><span>有效期至</span><b>{{ expireText }}</b></li>
      </ul>

      <div v-if="!status.configured" class="guide">
        <el-icon :size="18"><Warning /></el-icon>
        <p>尚未配置 API Key，配置后即可用于主观题 AI 自动阅卷与讲评内容生成。</p>
      </div>

      <div class="actions">
        <el-button type="primary" @click="openForm">
          {{ status.configured ? '修改 Key' : '配置 Key' }}
        </el-button>
        <el-button
          :loading="testing"
          :disabled="!status.configured"
          @click="handleTest"
        >
          测试连接
        </el-button>
        <el-button
          type="danger"
          plain
          :loading="clearing"
          :disabled="!status.configured"
          @click="handleRemove"
        >
          清除
        </el-button>
      </div>

      <el-alert
        v-if="testResult"
        class="test-result"
        type="success"
        :closable="false"
        show-icon
        :title="testResult"
      />
    </section>

    <section class="m-card tips">
      <span class="m-title">配置说明</span>
      <p>测试连接会真实发起一次极短的大模型调用，可能耗时数秒并消耗少量 token。</p>
      <p>如使用其他 OpenAI 兼容服务，请填写对应的接口地址与模型名称；留空则使用 DeepSeek 默认配置。</p>
      <p>API Key 仅保存在当前服务会话，服务重启后需重新配置。</p>
    </section>

    <MobileSheet
      v-model="formVisible"
      title="配置 API Key"
      :loading="saving"
      @confirm="handleSave"
    >
      <el-form label-position="top">
        <el-form-item label="API Key" required>
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            clearable
            autocomplete="new-password"
            placeholder="粘贴你的 API Key，例如 sk-xxxxxxxxxxxx"
          />
        </el-form-item>
        <el-form-item label="接口地址（可选）">
          <el-input
            v-model="form.apiUrl"
            clearable
            placeholder="留空使用 DeepSeek 官方地址"
          />
        </el-form-item>
        <el-form-item label="模型名称（可选）">
          <el-input v-model="form.model" clearable placeholder="留空使用 deepseek-chat" />
        </el-form-item>
      </el-form>
    </MobileSheet>
  </div>
</template>

<style scoped>
.status-card {
  min-height: 160px;
}

.badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  font-size: 11px;
  color: #8b90ab;
  background: #f4f6fc;
  border-radius: 999px;
}

.badge i {
  width: 6px;
  height: 6px;
  background: currentColor;
  border-radius: 50%;
}

.badge.online {
  color: #4caf50;
  background: #f0f9eb;
}

.status-list {
  padding: 0;
  margin: 12px 0 0;
  list-style: none;
}

.status-list li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  font-size: 12px;
  color: #8b90ab;
  border-bottom: 1px dashed #eef1fa;
}

.status-list b {
  max-width: 62%;
  overflow: hidden;
  font-weight: 500;
  color: #3a3e5c;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.guide {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 12px;
  margin-top: 12px;
  color: #e6a23c;
  background: #fdf6ec;
  border-radius: 10px;
}

.guide p {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: #8b7a5c;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px dashed #eef1fa;
}

.actions .el-button {
  flex: 1;
  min-width: 90px;
  margin-left: 0;
}

.test-result {
  margin-top: 12px;
  border-radius: 10px;
}

.tips {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tips p {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: #8b90ab;
}
</style>
