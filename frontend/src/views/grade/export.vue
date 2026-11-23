<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportGrades } from '@/api/grade'
import { pagePapers } from '@/api/paper'
import PageHero from '@/components/PageHero.vue'
import { downloadBlob } from '@/utils/download'
import { ANSWER_MODE, dictItem } from '@/utils/dict'

/** Excel 成绩导出 */
const papers = ref([])
const paperId = ref(null)
const exporting = ref(false)

const selectedPaper = computed(() => papers.value.find((item) => item.id === paperId.value) || null)

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
  if (!paperId.value && papers.value.length) paperId.value = papers.value[0].id
}

async function handleExport() {
  if (exporting.value) return
  if (!paperId.value) {
    ElMessage.warning('请先选择试卷')
    return
  }
  exporting.value = true
  try {
    const blob = await exportGrades(paperId.value)
    if (!blob || !blob.size) {
      ElMessage.error('导出失败：文件内容为空')
      return
    }
    const title = selectedPaper.value?.paperTitle || '成绩单'
    downloadBlob(blob, `${title}_成绩单.xlsx`)
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

onMounted(loadPapers)
</script>

<template>
  <div class="page-container">
    <PageHero
      title="Excel 成绩导出"
      subtitle="按试卷导出 xlsx 成绩单，包含学号、姓名、班级、AI 参考分、教师最终分与复核状态。"
      icon="Download"
      tone="light"
    >
      <template #meta>
        <div v-if="selectedPaper" class="hero-meta">
          <el-tag effect="plain">{{ selectedPaper.paperTitle }}</el-tag>
          <el-tag type="info" effect="plain">{{ selectedPaper.paperNo }}</el-tag>
          <el-tag
            :type="dictItem(ANSWER_MODE, selectedPaper.answerMode).type"
            effect="plain"
          >
            {{ dictItem(ANSWER_MODE, selectedPaper.answerMode).label }}
          </el-tag>
        </div>
      </template>
    </PageHero>

    <div class="export-layout">
      <!-- 左：试卷列表 -->
      <el-card shadow="never" class="paper-panel">
        <template #header>
          <div class="panel-head">
            <span>选择试卷</span>
            <span class="panel-sub">共 {{ papers.length }} 份</span>
          </div>
        </template>

        <div class="paper-list">
          <button
            v-for="paper in papers"
            :key="paper.id"
            class="paper-option"
            :class="{ active: paperId === paper.id }"
            @click="paperId = paper.id"
          >
            <span class="paper-radio">
              <el-icon v-if="paperId === paper.id" :size="12"><Check /></el-icon>
            </span>
            <span class="paper-info">
              <b class="paper-name">{{ paper.paperTitle }}</b>
              <span class="paper-meta">
                {{ paper.paperNo }}
                <em>·</em>
                {{ dictItem(ANSWER_MODE, paper.answerMode).label }}
              </span>
            </span>
            <span class="paper-score">{{ paper.totalScore ?? '-' }} 分</span>
          </button>

          <el-empty v-if="!papers.length" description="暂无可导出的试卷" :image-size="80" />
        </div>
      </el-card>

      <!-- 右：导出预览 -->
      <section class="export-panel">
        <div class="file-card">
          <div class="file-badge">
            <el-icon :size="30"><Document /></el-icon>
            <span class="file-ext">XLSX</span>
          </div>
          <h3 class="file-name">{{ selectedPaper?.paperTitle || '成绩单' }}</h3>
          <p class="file-desc">
            包含学号、姓名、班级、AI 参考分、教师最终分与复核状态，导出后可直接用 Excel / WPS 打开。
          </p>

          <div class="file-meta">
            <div class="meta-item">
              <b>{{ selectedPaper?.totalScore ?? '-' }}</b>
              <span>试卷总分</span>
            </div>
            <div class="meta-item">
              <b>{{ selectedPaper?.questionCount ?? '-' }}</b>
              <span>题目数</span>
            </div>
            <div class="meta-item">
              <b>{{ papers.length }}</b>
              <span>可选试卷</span>
            </div>
          </div>

          <el-button
            type="primary"
            size="large"
            class="export-btn"
            :loading="exporting"
            :disabled="!paperId"
            @click="handleExport"
          >
            <el-icon class="btn-icon"><Download /></el-icon>
            导出成绩单
          </el-button>
          <p class="file-tip">未选择试卷时按钮不可用</p>
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

/* 左试卷列表 / 右导出预览 */
.export-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 16px;
  align-items: start;
}

.panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #2b2e45;
}

.panel-sub {
  font-size: 12px;
  font-weight: 400;
  color: #9aa0b8;
}

.paper-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 520px;
  overflow-y: auto;
  padding-right: 2px;
}

.paper-option {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  text-align: left;
  cursor: pointer;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-radius: 12px;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}

.paper-option:hover {
  background: #f5f7ff;
  border-color: #d5dcfa;
}

.paper-option.active {
  background: linear-gradient(90deg, #eef1ff, #f7f3ff);
  border-color: #b9c4fb;
  box-shadow: 0 6px 16px rgba(91, 108, 255, 0.12);
}

.paper-radio {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  color: #fff;
  background: #fff;
  border: 1px solid #cdd4ea;
  border-radius: 50%;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.paper-option.active .paper-radio {
  background: var(--ai-primary);
  border-color: var(--ai-primary);
}

.paper-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.paper-name {
  overflow: hidden;
  font-size: 14px;
  color: #2b2e45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.paper-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #9aa0b8;
}

.paper-meta em {
  font-style: normal;
  color: #c9cfe4;
}

.paper-score {
  margin-left: auto;
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--ai-primary);
}

/* 右侧导出卡 */
.file-card {
  position: relative;
  padding: 32px 28px 26px;
  overflow: hidden;
  text-align: center;
  border: 1px solid var(--ai-border);
  border-radius: 16px;
  background:
    radial-gradient(420px 220px at 100% 0%, rgba(154, 91, 255, 0.1), transparent 60%),
    linear-gradient(160deg, #ffffff 0%, #f7f8ff 100%);
  box-shadow: var(--ai-shadow);
}

.file-badge {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 78px;
  height: 90px;
  margin: 0 auto 18px;
  color: #fff;
  background: linear-gradient(150deg, #5b6cff 0%, #9a5bff 100%);
  border-radius: 12px 12px 12px 4px;
  box-shadow: 0 14px 28px rgba(88, 100, 255, 0.32);
}

.file-ext {
  font-size: 10px;
  letter-spacing: 1px;
  opacity: 0.85;
}

.file-name {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: #2b2e45;
}

.file-desc {
  max-width: 340px;
  margin: 0 auto 22px;
  font-size: 13px;
  line-height: 1.8;
  color: #8b90ab;
}

.file-meta {
  display: flex;
  justify-content: space-around;
  padding: 16px 0;
  margin-bottom: 22px;
  border-top: 1px dashed #e6e9f7;
  border-bottom: 1px dashed #e6e9f7;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.meta-item b {
  font-size: 20px;
  font-weight: 700;
  color: #23264a;
}

.meta-item span {
  font-size: 12px;
  color: #9aa0b8;
}

.export-btn {
  width: 100%;
  height: 46px;
  font-size: 15px;
  background: linear-gradient(96deg, #5b6cff 0%, #7a5bff 55%, #9a5bff 100%);
  border: none;
  border-radius: 10px;
  box-shadow: 0 12px 26px rgba(88, 100, 255, 0.3);
}

.btn-icon {
  margin-right: 6px;
}

.file-tip {
  margin: 12px 0 0;
  font-size: 12px;
  color: #b0b5c9;
}

@media (max-width: 980px) {
  .export-layout {
    grid-template-columns: 1fr;
  }
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
</style>
