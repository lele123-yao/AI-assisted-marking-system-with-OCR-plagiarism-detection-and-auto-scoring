<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { importRoster } from '@/api/paperAnswer'
import { pagePapers } from '@/api/paper'
import PageHero from '@/components/PageHero.vue'

/** Excel 考生名单导入 */
const papers = ref([])
const submitting = ref(false)
const formRef = ref()
const form = reactive({ paperId: null, defaultClass: '' })
const fileList = ref([])
const result = ref(null)

const rules = {
  paperId: [{ required: true, message: '请选择试卷', trigger: 'change' }]
}

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
}

/** 只允许选择一个 Excel 文件 */
function handleChange(file, files) {
  fileList.value = files.slice(-1)
}

function handleRemove() {
  fileList.value = []
}

async function submit() {
  if (submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const file = fileList.value[0]?.raw
  if (!file) {
    ElMessage.warning('请选择考生名单 Excel 文件')
    return
  }
  if (!/\.(xlsx|xls)$/i.test(file.name)) {
    ElMessage.warning('仅支持 .xlsx / .xls 文件')
    return
  }

  submitting.value = true
  try {
    result.value = await importRoster({
      paperId: form.paperId,
      defaultClass: form.defaultClass,
      file
    })
    ElMessage.success('导入完成')
    fileList.value = []
  } finally {
    submitting.value = false
  }
}

/** 三步向导状态 */
const steps = computed(() => [
  { index: 1, title: '选择试卷', desc: form.paperId ? '已选择' : '待选择', done: !!form.paperId },
  { index: 2, title: '上传名单', desc: fileList.value.length ? '已选择文件' : '待上传', done: !!fileList.value.length },
  { index: 3, title: '查看结果', desc: result.value ? '导入完成' : '待导入', done: !!result.value }
])

/** 导入结果汇总 */
const resultTiles = computed(() => {
  const data = result.value
  if (!data) return []
  return [
    { label: '总行数', value: data.totalRows ?? 0, tone: 'blue' },
    { label: '成功', value: data.successCount ?? 0, tone: 'green' },
    { label: '跳过', value: data.skippedCount ?? 0, tone: 'orange' },
    { label: '失败', value: data.failedCount ?? 0, tone: 'red' }
  ]
})

onMounted(loadPapers)
</script>

<template>
  <div class="page-container">
    <PageHero
      title="考生名单导入"
      subtitle="通过 Excel 为指定试卷批量建立考生答卷记录：需包含学号、姓名两列，可选班级列。"
      icon="UploadFilled"
      tone="light"
    >
      <template #meta>
        <div class="hero-tags">
          <el-tag type="info" effect="plain">支持 .xlsx / .xls</el-tag>
          <el-tag type="warning" effect="plain">学号、姓名必填</el-tag>
          <el-tag effect="plain">无班级列时使用默认班级</el-tag>
        </div>
      </template>
    </PageHero>

    <!-- 三步向导 -->
    <div class="step-rail">
      <div v-for="(step, i) in steps" :key="step.index" class="step-item" :class="{ done: step.done }">
        <span class="step-dot">
          <el-icon v-if="step.done" :size="14"><Check /></el-icon>
          <template v-else>{{ step.index }}</template>
        </span>
        <div class="step-text">
          <b>{{ step.title }}</b>
          <span>{{ step.desc }}</span>
        </div>
        <span v-if="i < steps.length - 1" class="step-line" />
      </div>
    </div>

    <div class="roster-layout">
      <!-- 左：导入表单 -->
      <el-card shadow="never" class="form-card">
        <template #header>
          <div class="card-head">
            <span>名单文件</span>
            <span class="card-sub">学号重复的行会被跳过</span>
          </div>
        </template>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="选择试卷" prop="paperId">
            <el-select v-model="form.paperId" filterable placeholder="请选择试卷" class="full">
              <el-option
                v-for="paper in papers"
                :key="paper.id"
                :label="`${paper.paperTitle}（${paper.paperNo}）`"
                :value="paper.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="默认班级">
            <el-input
              v-model="form.defaultClass"
              placeholder="Excel 无班级列时使用，如 计算机 2301"
            />
          </el-form-item>

          <el-form-item label="Excel 文件">
            <el-upload
              :auto-upload="false"
              :limit="1"
              accept=".xlsx,.xls"
              :file-list="fileList"
              :on-change="handleChange"
              :on-remove="handleRemove"
              drag
              class="uploader"
            >
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="upload-text">点击或拖拽 Excel 文件到此处</div>
              <div class="upload-tip">支持 .xlsx / .xls，单次一个文件</div>
            </el-upload>
          </el-form-item>

          <el-button
            type="primary"
            class="submit-btn"
            :loading="submitting"
            :disabled="!form.paperId || !fileList.length"
            @click="submit"
          >
            开始导入
          </el-button>
        </el-form>
      </el-card>

      <!-- 右：结果面板 -->
      <section class="result-panel">
        <template v-if="result">
          <div class="result-head">
            <div>
              <span class="result-eyebrow">导入结果</span>
              <h3>批次 {{ result.batchNo || '-' }}</h3>
            </div>
            <el-tag type="success" effect="plain">已完成</el-tag>
          </div>

          <div class="result-tiles">
            <div v-for="tile in resultTiles" :key="tile.label" class="tile" :class="tile.tone">
              <b>{{ tile.value }}</b>
              <span>{{ tile.label }}</span>
            </div>
          </div>

          <template v-if="result.failures?.length">
            <h4 class="fail-title">失败明细（{{ result.failures.length }} 条）</h4>
            <el-table :data="result.failures" border size="small" class="fail-table">
              <el-table-column prop="rowNum" label="Excel 行号" width="100" />
              <el-table-column prop="studentNo" label="学号" width="140" />
              <el-table-column prop="reason" label="失败原因" min-width="220" />
            </el-table>
          </template>
          <el-empty v-else description="全部行导入成功" :image-size="70" />
        </template>

        <div v-else class="result-placeholder">
          <div class="placeholder-icon">
            <el-icon :size="30"><UploadFilled /></el-icon>
          </div>
          <h3>等待导入名单</h3>
          <p>导入完成后，这里会显示成功、跳过与失败的数量，并列出失败行原因。</p>

          <div class="column-hint">
            <span class="hint-title">Excel 列建议</span>
            <div class="hint-row">
              <span class="hint-col required">学号</span>
              <span class="hint-col required">姓名</span>
              <span class="hint-col">班级</span>
            </div>
            <p class="hint-note">前两列必填，最后一列可省略（省略时使用默认班级）。</p>
          </div>
        </div>
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

/* 步骤条 */
.step-rail {
  display: flex;
  gap: 12px;
  padding: 18px 24px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 16px;
  box-shadow: 0 6px 18px rgba(40, 50, 120, 0.04);
}

.step-item {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 12px;
}

.step-dot {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  font-size: 13px;
  font-weight: 700;
  color: #8b90ab;
  background: #f2f4fb;
  border: 1px solid #e2e7f6;
  border-radius: 10px;
}

.step-item.done .step-dot {
  color: #fff;
  background: linear-gradient(140deg, #2fae7a, #6ad3a5);
  border-color: transparent;
  box-shadow: 0 8px 18px rgba(47, 174, 122, 0.26);
}

.step-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.step-text b {
  font-size: 14px;
  color: #2b2e45;
}

.step-text span {
  font-size: 12px;
  color: #9aa0b8;
}

.step-line {
  flex: 1;
  height: 1px;
  margin: 0 6px;
  background: repeating-linear-gradient(90deg, #dfe4f7 0 6px, transparent 6px 12px);
}

/* 双栏 */
.roster-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.95fr) minmax(0, 1.05fr);
  gap: 16px;
  align-items: start;
}

.card-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #23264a;
}

.card-sub {
  font-size: 12px;
  font-weight: 400;
  color: #9aa0b8;
}

.full {
  width: 100%;
}

.uploader {
  width: 100%;
}

.upload-icon {
  font-size: 42px;
  color: #c0c4cc;
}

.upload-text {
  margin-top: 8px;
  color: #606266;
}

.upload-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.submit-btn {
  width: 100%;
  height: 42px;
  margin-top: 6px;
  background: linear-gradient(96deg, #5b6cff, #8a5bff);
  border: none;
  border-radius: 10px;
  box-shadow: 0 12px 26px rgba(91, 108, 255, 0.26);
}

/* 结果面板 */
.result-panel {
  min-height: 480px;
  padding: 24px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.result-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.result-eyebrow {
  font-size: 12px;
  color: #9aa0b8;
  letter-spacing: 1px;
}

.result-head h3 {
  margin: 6px 0 0;
  font-size: 18px;
  font-weight: 700;
  color: #23264a;
}

.result-tiles {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 18px 0 6px;
}

.tile {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 12px 14px;
  background: #f7f8ff;
  border: 1px solid #eef1fa;
  border-radius: 12px;
}

.tile b {
  font-size: 20px;
  font-weight: 700;
  color: #23264a;
}

.tile span {
  font-size: 12px;
  color: #9aa0b8;
}

.tile.blue {
  border-left: 3px solid #5b6cff;
}

.tile.green {
  border-left: 3px solid #2fae7a;
}

.tile.orange {
  border-left: 3px solid #e6a23c;
}

.tile.red {
  border-left: 3px solid #f56c6c;
}

.fail-title {
  margin: 20px 0 12px;
  font-size: 14px;
  color: #303133;
}

.fail-table {
  margin-bottom: 8px;
}

/* 空态 */
.result-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 430px;
  text-align: center;
}

.placeholder-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 70px;
  height: 70px;
  margin-bottom: 16px;
  color: #fff;
  background: linear-gradient(150deg, #5b6cff, #9a5bff);
  border-radius: 20px;
  box-shadow: 0 16px 30px rgba(91, 108, 255, 0.28);
}

.result-placeholder h3 {
  margin: 0 0 8px;
  font-size: 16px;
  color: #23264a;
}

.result-placeholder p {
  max-width: 340px;
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: #8b90ab;
}

.column-hint {
  width: 100%;
  max-width: 340px;
  padding: 16px;
  margin-top: 22px;
  text-align: left;
  background: #f7f8ff;
  border: 1px dashed #dfe4f7;
  border-radius: 12px;
}

.hint-title {
  font-size: 12px;
  font-weight: 600;
  color: #5c6280;
}

.hint-row {
  display: flex;
  gap: 8px;
  margin: 10px 0 8px;
}

.hint-col {
  padding: 4px 12px;
  font-size: 12px;
  color: #5c6280;
  background: #fff;
  border: 1px solid #e2e7f6;
  border-radius: 8px;
}

.hint-col.required {
  color: #5b6cff;
  background: #eef1ff;
  border-color: #d9defb;
}

.hint-note {
  font-size: 11px;
  line-height: 1.6;
  color: #9aa0b8;
}

@media (max-width: 1000px) {
  .roster-layout {
    grid-template-columns: 1fr;
  }

  .step-rail {
    flex-direction: column;
  }

  .step-line {
    display: none;
  }

  .result-tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
