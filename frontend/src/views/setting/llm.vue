<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getLlmCredential,
  saveLlmCredential,
  removeLlmCredential,
  testLlmCredential
} from '@/api/llm'
import PageHero from '@/components/PageHero.vue'

// 当前后端返回的配置状态（只含脱敏信息）
const status = reactive({
  configured: false,
  maskedKey: '',
  apiUrl: '',
  model: '',
  expireAt: null
})

// 表单：保存后清空 apiKey，避免页面残留明文
const form = reactive({ apiKey: '', apiUrl: '', model: '' })
const saving = ref(false)
const testing = ref(false)

const expireText = computed(() =>
  status.expireAt ? new Date(status.expireAt).toLocaleString() : '-'
)

function applyStatus(data) {
  if (!data) return
  status.configured = !!data.configured
  status.maskedKey = data.maskedKey || ''
  status.apiUrl = data.apiUrl || ''
  status.model = data.model || ''
  status.expireAt = data.expireAt || null
}

async function load() {
  try {
    applyStatus(await getLlmCredential())
  } catch (e) {
    // 错误提示已由 axios 拦截器统一处理，这里静默即可
  }
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
    ElMessage.success('保存成功，后续 AI 调用将使用你的 Key')
  } finally {
    saving.value = false
  }
}

async function handleTest() {
  testing.value = true
  try {
    const msg = await testLlmCredential()
    ElMessage.success(msg || '连接成功')
  } finally {
    testing.value = false
  }
}

async function handleRemove() {
  try {
    await ElMessageBox.confirm('确定清除已保存的 API Key 吗？', '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  await removeLlmCredential()
  applyStatus({ configured: false })
  ElMessage.success('已清除')
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <PageHero
      title="大模型设置"
      subtitle="配置 AI 阅卷所使用的大模型接口参数，保存后即可用于主观题自动阅卷与讲评内容生成。"
      icon="Key"
      tone="light"
    >
      <template #meta>
        <div class="hero-tags">
          <el-tag :type="status.configured ? 'success' : 'info'" effect="plain">
            {{ status.configured ? '已配置' : '未配置' }}
          </el-tag>
          <el-tag type="info" effect="plain">Key 仅保存在当前服务会话</el-tag>
        </div>
      </template>
    </PageHero>

    <div class="llm-layout">
      <!-- 左：当前状态 -->
      <aside class="status-card">
        <span class="status-eyebrow">当前配置</span>

        <div class="status-badge" :class="{ online: status.configured }">
          <span class="pulse" />
          {{ status.configured ? '接口已就绪' : '尚未配置' }}
        </div>

        <div class="status-rows">
          <div class="status-row">
            <span>当前 Key</span>
            <b>{{ status.maskedKey || '-' }}</b>
          </div>
          <div class="status-row">
            <span>有效期至</span>
            <b>{{ expireText }}</b>
          </div>
          <div class="status-row">
            <span>接口地址</span>
            <b>{{ status.apiUrl || '默认（DeepSeek 官方）' }}</b>
          </div>
          <div class="status-row">
            <span>模型</span>
            <b>{{ status.model || 'deepseek-chat' }}</b>
          </div>
        </div>

        <div class="capability-list">
          <span class="capability-title">该配置将用于</span>
          <div class="capability">主观题 AI 自动阅卷</div>
          <div class="capability">学情讲评建议生成</div>
          <div class="capability">讲评 PPT 自动编排</div>
        </div>

        <p class="status-note">API Key 不会持久化保存到数据库，服务重启后需重新配置。</p>
      </aside>

      <!-- 右：配置表单 -->
      <section class="config-card">
        <div class="config-head">
          <span class="config-title">接口参数</span>
          <span class="config-sub">默认接入 DeepSeek，亦支持任意 OpenAI 兼容服务</span>
        </div>

        <el-form :model="form" label-position="top" class="config-form" @submit.prevent>
          <el-form-item label="API Key" required>
            <el-input
              v-model="form.apiKey"
              type="password"
              show-password
              clearable
              autocomplete="new-password"
              placeholder="粘贴你的 API Key，例如 sk-xxxxxxxxxxxxxxxx"
            />
          </el-form-item>
          <el-form-item label="接口地址">
            <el-input
              v-model="form.apiUrl"
              clearable
              placeholder="留空则使用 DeepSeek 官方：https://api.deepseek.com/chat/completions"
            />
          </el-form-item>
          <el-form-item label="模型名称">
            <el-input v-model="form.model" clearable placeholder="留空则使用 deepseek-chat" />
          </el-form-item>

          <div class="config-actions">
            <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
            <el-button :loading="testing" :disabled="!status.configured" @click="handleTest">
              测试连接
            </el-button>
            <el-button type="danger" plain :disabled="!status.configured" @click="handleRemove">
              清除配置
            </el-button>
          </div>
        </el-form>

        <el-alert type="info" :closable="false" show-icon class="config-alert">
          <template #title>配置说明</template>
          <p>配置后系统将调用大模型完成主观题自动阅卷、生成讲评 PPT。</p>
          <p>如使用其他 OpenAI 兼容服务，请填写对应的接口地址与模型名称。</p>
        </el-alert>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.llm-layout {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

/* 状态卡 */
.status-card {
  position: sticky;
  top: 16px;
  padding: 26px 24px;
  color: #fff;
  background:
    radial-gradient(340px 200px at 100% 0%, rgba(154, 91, 255, 0.4), transparent 62%),
    linear-gradient(160deg, #1b1e52 0%, #2a2a72 55%, #3d358f 100%);
  border: 1px solid rgba(140, 156, 255, 0.2);
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(25, 28, 80, 0.24);
}

.status-eyebrow {
  font-size: 12px;
  color: #a9b4f2;
  letter-spacing: 1px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  margin: 16px 0 20px;
  font-size: 13px;
  color: rgba(190, 198, 240, 0.8);
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(160, 174, 255, 0.2);
  border-radius: 999px;
}

.status-badge.online {
  color: #7ce0b4;
  border-color: rgba(124, 224, 180, 0.35);
}

.pulse {
  width: 8px;
  height: 8px;
  background: currentColor;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgba(124, 224, 180, 0.16);
}

.status-rows {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 20px;
  border-bottom: 1px solid rgba(160, 174, 255, 0.16);
}

.status-row {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.status-row span {
  font-size: 11px;
  color: rgba(190, 198, 240, 0.6);
}

.status-row b {
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  color: #eef1ff;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.capability-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 20px;
}

.capability-title {
  font-size: 11px;
  color: rgba(190, 198, 240, 0.55);
}

.capability {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: rgba(220, 226, 255, 0.85);
}

.capability::before {
  width: 5px;
  height: 5px;
  content: '';
  background: #8b9bff;
  border-radius: 50%;
}

.status-note {
  padding-top: 18px;
  margin: 18px 0 0;
  font-size: 11px;
  line-height: 1.7;
  color: rgba(178, 187, 232, 0.6);
  border-top: 1px dashed rgba(160, 174, 255, 0.16);
}

/* 配置卡 */
.config-card {
  padding: 26px 28px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.config-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding-bottom: 18px;
  margin-bottom: 20px;
  border-bottom: 1px solid #eef1fa;
}

.config-title {
  font-size: 16px;
  font-weight: 600;
  color: #23264a;
}

.config-sub {
  font-size: 12px;
  color: #9aa0b8;
}

.config-form {
  max-width: 640px;
}

.config-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.config-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

.config-alert {
  max-width: 640px;
  margin-top: 22px;
  border-radius: 12px;
}

.config-alert p {
  margin: 4px 0;
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 960px) {
  .llm-layout {
    grid-template-columns: 1fr;
  }

  .status-card {
    position: static;
  }
}
</style>
