<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { importRoster } from '@/api/paperAnswer'
import { pagePapers } from '@/api/paper'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileStatGrid from '@/components/m/MobileStatGrid.vue'

/** 移动端：Excel 考生名单导入，导入完成后可继续上传或去上传答卷 */
const route = useRoute()
const router = useRouter()

const papers = ref([])
const loading = ref(false)
const submitting = ref(false)
const file = ref(null)
const fileInput = ref(null)
const result = ref(null)
const form = reactive({ paperId: null, defaultClass: '' })

const fileName = computed(() => file.value?.name || '')
const canImport = computed(() => !!form.paperId && !!file.value)

async function loadPapers() {
  loading.value = true
  try {
    const data = await pagePapers({ pageNum: 1, pageSize: 50 })
    papers.value = data?.records || []
    // 支持从其他页面带 paperId 进入
    const preset = Number(route.query.paperId)
    if (preset && papers.value.some((item) => item.id === preset)) {
      form.paperId = preset
    }
  } finally {
    loading.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}

function handleFileChange(event) {
  const picked = event.target.files?.[0]
  event.target.value = ''
  if (!picked) return
  if (!/\.(xls|xlsx)$/i.test(picked.name)) {
    ElMessage.warning('仅支持 .xls / .xlsx 文件')
    return
  }
  file.value = picked
  result.value = null
}

async function submit() {
  if (submitting.value) return
  if (!form.paperId) {
    ElMessage.warning('请选择试卷')
    return
  }
  if (!file.value) {
    ElMessage.warning('请选择考生名单 Excel 文件')
    return
  }

  submitting.value = true
  try {
    result.value = await importRoster({
      paperId: form.paperId,
      defaultClass: form.defaultClass.trim(),
      file: file.value
    })
    ElMessage.success('导入完成')
    file.value = null
  } finally {
    submitting.value = false
  }
}

/** 导入结果统计，字段与桌面 roster.vue 保持一致 */
const statItems = computed(() => {
  const data = result.value
  if (!data) return []
  return [
    { key: 'total', label: '总行数', value: data.totalRows ?? 0, icon: 'Tickets', tone: 'primary' },
    { key: 'success', label: '成功', value: data.successCount ?? 0, icon: 'CircleCheck', tone: 'success' },
    { key: 'skipped', label: '跳过', value: data.skippedCount ?? 0, icon: 'RefreshRight', tone: 'warning' },
    { key: 'failed', label: '失败', value: data.failedCount ?? 0, icon: 'Warning', tone: 'danger' }
  ]
})

function continueUpload() {
  result.value = null
  file.value = null
}

function goUpload() {
  router.push({
    path: '/m/paper-answer/upload',
    query: form.paperId ? { paperId: form.paperId } : {}
  })
}

onMounted(loadPapers)
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="名单导入"
      subtitle="通过 Excel 为试卷批量建立考生答卷，学号与姓名必填"
      icon="UploadFilled"
    >
      <template #actions>
        <el-button size="small" plain :loading="loading" @click="loadPapers">刷新</el-button>
      </template>
    </MobileHero>

    <section class="m-card">
      <div class="m-col">
        <div class="field">
          <span class="m-label">选择试卷</span>
          <el-select v-model="form.paperId" filterable placeholder="请选择试卷" class="full">
            <el-option
              v-for="paper in papers"
              :key="paper.id"
              :label="`${paper.paperTitle}（${paper.paperNo}）`"
              :value="paper.id"
            />
          </el-select>
        </div>

        <div class="field">
          <span class="m-label">默认班级</span>
          <el-input
            v-model="form.defaultClass"
            placeholder="Excel 无班级列时使用，如 计算机 2301"
            clearable
          />
        </div>

        <div class="file-box" @click="pickFile">
          <el-icon :size="24"><UploadFilled /></el-icon>
          <span class="file-name" :class="{ empty: !fileName }">{{ fileName || '点击选择 Excel 文件' }}</span>
          <span class="m-sub">支持 .xls / .xlsx，单次一个文件</span>
        </div>
        <input
          ref="fileInput"
          class="file-input"
          type="file"
          accept=".xls,.xlsx"
          @change="handleFileChange"
        />

        <el-button
          type="primary"
          class="submit-btn"
          :loading="submitting"
          :disabled="!canImport"
          @click="submit"
        >
          导入名单
        </el-button>
        <p class="hint">Excel 需包含学号、姓名两列，可选班级列；同一试卷下已存在的学号会自动跳过。</p>
      </div>
    </section>

    <template v-if="result">
      <MobileStatGrid :items="statItems" />

      <section class="m-card">
        <div class="m-row-between">
          <span class="m-title">导入结果</span>
          <el-tag type="success" effect="plain">批次 {{ result.batchNo || '-' }}</el-tag>
        </div>

        <div v-if="result.failures?.length" class="failures">
          <div v-for="row in result.failures" :key="row.rowNum" class="failure">
            <div class="m-row-between">
              <b>第 {{ row.rowNum }} 行</b>
              <span class="m-sub">{{ row.studentNo || '无学号' }}</span>
            </div>
            <p>{{ row.reason }}</p>
          </div>
        </div>
        <MobileEmpty v-else description="全部行导入成功" icon="CircleCheck" />

        <div class="result-actions">
          <el-button plain @click="continueUpload">继续上传</el-button>
          <el-button type="primary" @click="goUpload">去上传答卷</el-button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.full {
  width: 100%;
}

.file-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 20px 12px;
  color: var(--ai-primary);
  text-align: center;
  cursor: pointer;
  background: #f8f9ff;
  border: 1px dashed #cfd6f5;
  border-radius: 12px;
}

.file-box:active {
  background: #eef1ff;
}

.file-name {
  font-size: 13px;
  color: #23264a;
  word-break: break-all;
}

.file-name.empty {
  color: #9aa0b8;
}

.file-input {
  display: none;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
}

.hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: #9aa0b8;
}

.failures {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.failure {
  padding: 10px 12px;
  background: #fff7f7;
  border: 1px solid #fde2e2;
  border-radius: 10px;
}

.failure b {
  font-size: 12px;
  color: #f56c6c;
}

.failure p {
  margin: 5px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #5c6180;
}

.result-actions {
  display: flex;
  gap: 10px;
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px dashed #f0f2fb;
}

.result-actions .el-button {
  flex: 1;
  margin-left: 0;
}
</style>
