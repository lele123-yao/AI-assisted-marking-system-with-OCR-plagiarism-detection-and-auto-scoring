<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { generateAnalysis, generatePpt } from '@/api/learning'
import { pagePapers } from '@/api/paper'
import PageHero from '@/components/PageHero.vue'
import { downloadByUrl } from '@/utils/download'

/** PPT 生成与下载：两步式向导 */
const route = useRoute()
const papers = ref([])
const paperId = ref(null)
const reportId = ref(null)
const generatingReport = ref(false)
const generatingPpt = ref(false)
const ppt = ref(null)

const form = reactive({ reportTitle: '' })

const selectedPaper = computed(() => papers.value.find((item) => item.id === paperId.value) || null)

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
  paperId.value = route.query.paperId ? Number(route.query.paperId) : papers.value[0]?.id || null
  reportId.value = route.query.reportId ? Number(route.query.reportId) : null
  form.reportTitle = selectedPaper.value ? `${selectedPaper.value.paperTitle} 学情讲评` : ''
}

/** 先生成讲评报告，拿到 reportId 才能制作 PPT */
async function handleGenerateReport() {
  if (generatingReport.value) return
  if (!paperId.value) {
    ElMessage.warning('请先选择试卷')
    return
  }
  generatingReport.value = true
  try {
    const report = await generateAnalysis({
      paperId: paperId.value,
      reportTitle: form.reportTitle || undefined
    })
    reportId.value = report?.id || null
    if (!reportId.value) {
      ElMessage.error('报告生成成功但未返回报告ID，请稍后重试')
      return
    }
    ElMessage.success('讲评报告已生成，可继续生成PPT')
  } finally {
    generatingReport.value = false
  }
}

async function handleGeneratePpt() {
  if (generatingPpt.value) return
  if (!reportId.value) {
    ElMessage.warning('请先生成讲评报告')
    return
  }
  generatingPpt.value = true
  try {
    ppt.value = await generatePpt(reportId.value)
    ElMessage.success('PPT 已生成')
  } finally {
    generatingPpt.value = false
  }
}

function handleDownload() {
  const url = ppt.value?.downloadUrl || ppt.value?.pptFileUrl
  if (!url) {
    ElMessage.warning('暂无可下载地址')
    return
  }
  downloadByUrl(url, ppt.value?.fileName || '学情分析报告.pptx')
}

/** 步骤状态 */
const steps = computed(() => [
  {
    index: 1,
    title: '生成讲评报告',
    desc: reportId.value ? `报告ID ${reportId.value}` : '先产出学情讲评正文',
    done: !!reportId.value
  },
  {
    index: 2,
    title: '生成并下载 PPT',
    desc: ppt.value ? '课件已就绪' : '基于报告制作课件',
    done: !!ppt.value
  }
])

onMounted(loadPapers)
</script>

<template>
  <div class="page-container">
    <PageHero
      title="讲评 PPT"
      subtitle="基于已生成的学情讲评报告制作讲评课件，生成后可下载带临时签名的 PPT 文件。"
      icon="Present"
      tone="light"
    >
      <template #meta>
        <div class="hero-tags">
          <el-tag v-if="selectedPaper" type="info" effect="plain">
            {{ selectedPaper.paperTitle }}
          </el-tag>
          <el-tag v-else type="info" effect="plain">未选择试卷</el-tag>
          <el-tag :type="reportId ? 'success' : 'info'" effect="plain">
            报告ID：{{ reportId || '未生成' }}
          </el-tag>
        </div>
      </template>
    </PageHero>

    <!-- 步骤条 -->
    <div class="step-rail">
      <div
        v-for="(step, i) in steps"
        :key="step.index"
        class="step-item"
        :class="{ done: step.done }"
      >
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

    <div class="ppt-layout">
      <!-- 左：操作 -->
      <div class="action-column">
        <el-card shadow="never" class="action-card">
          <template #header>
            <div class="card-head">
              <span>步骤一 · 生成讲评报告</span>
              <el-tag :type="reportId ? 'success' : 'info'" size="small" effect="plain">
                {{ reportId ? '已完成' : '待执行' }}
              </el-tag>
            </div>
          </template>

          <el-form label-position="top" class="action-form">
            <el-form-item label="选择试卷">
              <el-select v-model="paperId" filterable class="full">
                <el-option
                  v-for="paper in papers"
                  :key="paper.id"
                  :label="`${paper.paperTitle}（${paper.paperNo}）`"
                  :value="paper.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="报告标题（可选）">
              <el-input v-model="form.reportTitle" placeholder="默认使用「试卷名 学情讲评」" />
            </el-form-item>
            <el-button
              v-permission="'learning:generate'"
              class="full"
              :loading="generatingReport"
              @click="handleGenerateReport"
            >
              {{ reportId ? '重新生成讲评报告' : '生成讲评报告' }}
            </el-button>
          </el-form>
        </el-card>

        <el-card shadow="never" class="action-card">
          <template #header>
            <div class="card-head">
              <span>步骤二 · 生成讲评 PPT</span>
              <el-tag :type="ppt ? 'success' : 'info'" size="small" effect="plain">
                {{ ppt ? '已生成' : '待执行' }}
              </el-tag>
            </div>
          </template>

          <el-button
            v-permission="'learning:ppt'"
            type="primary"
            class="full generate-ppt"
            :loading="generatingPpt"
            :disabled="!reportId"
            @click="handleGeneratePpt"
          >
            {{ ppt ? '重新生成 PPT' : '生成 PPT' }}
          </el-button>
          <p class="action-tip">
            {{ reportId ? '报告已就绪，可开始制作课件' : '请先完成步骤一，生成报告后此按钮才可用' }}
          </p>

          <div v-if="ppt" class="ppt-result">
            <div class="ppt-file">
              <span class="file-icon">PPTX</span>
              <div class="file-info">
                <b>{{ ppt.fileName || '学情分析报告.pptx' }}</b>
                <span>{{ ppt.reportTitle || form.reportTitle }}</span>
              </div>
            </div>
            <el-button type="success" class="full" @click="handleDownload">下载 PPT</el-button>
            <el-button
              v-if="ppt.downloadUrl"
              link
              type="primary"
              class="link-btn"
              @click="downloadByUrl(ppt.downloadUrl, ppt.fileName)"
            >
              在新窗口打开
            </el-button>
            <p class="action-tip">下载地址为临时签名地址，请尽快下载。</p>
          </div>
        </el-card>
      </div>

      <!-- 右：课件预览 -->
      <section class="preview-panel">
        <span class="preview-eyebrow">课件预览</span>
        <div class="slide" :class="{ ready: !!ppt }">
          <div class="slide-grid" />
          <div class="slide-content">
            <span class="slide-tag">学情讲评</span>
            <h3>{{ ppt?.reportTitle || form.reportTitle || selectedPaper?.paperTitle || '学情讲评课件' }}</h3>
            <p>知识点掌握 · 共性问题 · 讲评思路</p>
            <div class="slide-meta">
              <span>{{ selectedPaper?.paperNo || '未选择试卷' }}</span>
              <span>{{ ppt ? '课件已生成' : '预览占位' }}</span>
            </div>
          </div>
        </div>

        <ul class="preview-notes">
          <li>课件内容基于讲评报告自动编排，涵盖得分概况与典型错题。</li>
          <li>报告与 PPT 均保存在服务端，可稍后重新下载。</li>
          <li>接口返回的下载链接带临时签名，过期后重新生成即可。</li>
        </ul>
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
  position: relative;
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
  box-shadow: 0 8px 18px rgba(47, 174, 122, 0.28);
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

/* 主体 */
.ppt-layout {
  display: grid;
  grid-template-columns: minmax(320px, 400px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.action-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: #23264a;
}

.full {
  width: 100%;
}

.action-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.generate-ppt {
  height: 42px;
  background: linear-gradient(96deg, #5b6cff, #8a5bff);
  border: none;
  border-radius: 10px;
  box-shadow: 0 12px 26px rgba(91, 108, 255, 0.26);
}

.action-tip {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #9aa0b8;
}

.ppt-result {
  padding-top: 18px;
  margin-top: 18px;
  border-top: 1px dashed #e9ecf7;
}

.ppt-file {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  margin-bottom: 14px;
  background: #f7f8ff;
  border-radius: 12px;
}

.file-icon {
  padding: 8px 10px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(150deg, #f0803c, #e8563c);
  border-radius: 8px;
}

.file-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.file-info b {
  overflow: hidden;
  font-size: 13px;
  color: #23264a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-info span {
  font-size: 12px;
  color: #9aa0b8;
}

.link-btn {
  display: block;
  margin: 8px auto 0;
}

/* 课件预览 */
.preview-panel {
  padding: 22px 24px 24px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.preview-eyebrow {
  font-size: 12px;
  color: #9aa0b8;
  letter-spacing: 1px;
}

.slide {
  position: relative;
  display: flex;
  align-items: center;
  aspect-ratio: 16 / 9;
  margin: 14px 0 18px;
  padding: 0 46px;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(150deg, #1b1e52 0%, #2a2a72 55%, #4a3fa8 100%);
  border-radius: 14px;
  box-shadow: 0 20px 40px rgba(25, 28, 80, 0.24);
}

.slide.ready {
  box-shadow: 0 20px 44px rgba(25, 28, 80, 0.34);
}

.slide-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(160, 174, 255, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(160, 174, 255, 0.08) 1px, transparent 1px);
  background-size: 38px 38px;
  mask-image: radial-gradient(circle at 20% 25%, #000 0%, transparent 78%);
}

.slide-content {
  position: relative;
  z-index: 1;
}

.slide-tag {
  display: inline-flex;
  padding: 4px 12px;
  margin-bottom: 14px;
  font-size: 11px;
  color: #b6c1ff;
  background: rgba(126, 146, 255, 0.16);
  border: 1px solid rgba(126, 146, 255, 0.3);
  border-radius: 999px;
}

.slide-content h3 {
  max-width: 90%;
  margin: 0 0 10px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.35;
  color: #f0f2ff;
}

.slide-content p {
  margin: 0 0 18px;
  font-size: 13px;
  color: rgba(190, 198, 240, 0.72);
}

.slide-meta {
  display: flex;
  gap: 18px;
  font-size: 12px;
  color: rgba(178, 187, 232, 0.6);
}

.preview-notes {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 0 0 18px;
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: #8b90ab;
}

@media (max-width: 1000px) {
  .ppt-layout {
    grid-template-columns: 1fr;
  }

  .step-rail {
    flex-direction: column;
  }

  .step-line {
    display: none;
  }
}
</style>
