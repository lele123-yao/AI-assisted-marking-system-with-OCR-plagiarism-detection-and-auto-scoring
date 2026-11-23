<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { pagePaperAnswers } from '@/api/paperAnswer'
import { pagePapers } from '@/api/paper'
import { useTable } from '@/composables/useTable'
import PageHero from '@/components/PageHero.vue'
import { MARKING_STATUS, REVIEW_STATUS, dictItem, formatTime } from '@/utils/dict'

/** 答卷列表（面向纸质扫描答卷；线上答卷后端暂无查询接口） */
const router = useRouter()
const papers = ref([])

const { loading, list, total, query, load, search, reset, handleSizeChange, handlePageChange } =
  useTable(pagePaperAnswers, { paperId: '', keyword: '', markingStatus: '' })

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
}

function goDetail(row) {
  router.push(`/answer/detail/${row.id}`)
}

/** 概览统计：按当前页数据统计，总量用 total */
const stats = computed(() => {
  const page = list.value || []
  return [
    {
      label: '当前页答卷',
      value: page.length,
      icon: 'Tickets',
      color: '#409eff',
      bg: '#ecf5ff',
      hint: `全部共 ${total.value} 份`
    },
    {
      label: '待教师确认',
      value: page.filter((row) => row.markingStatus === 3).length,
      icon: 'Timer',
      color: '#e6a23c',
      bg: '#fdf6ec',
      hint: '当前页'
    },
    {
      label: '已确认归档',
      value: page.filter((row) => row.markingStatus === 5).length,
      icon: 'CircleCheck',
      color: '#67c23a',
      bg: '#f0f9eb',
      hint: '当前页'
    },
    {
      label: '已传扫描件',
      value: page.filter((row) => row.hasImage).length,
      icon: 'Camera',
      color: '#9254de',
      bg: '#f9f0ff',
      hint: '当前页'
    }
  ]
})

onMounted(() => {
  load()
  loadPapers()
})
</script>

<template>
  <div class="page-container">
    <PageHero
      title="答卷列表"
      subtitle="汇总纸质扫描答卷的扫描件、AI 参考分与教师确认结果，可按试卷、学号/姓名和阅卷状态筛选；线上答卷暂不提供分页查询。"
      icon="Tickets"
    >
      <template #actions>
        <el-button :loading="loading" plain @click="load">刷新</el-button>
      </template>
    </PageHero>

    <!-- 状态流水线：阅卷推进阶段的整体分布 -->
    <div v-loading="loading" class="pipeline">
      <div v-for="(item, index) in stats" :key="item.label" class="stage">
        <div class="stage-icon" :style="{ color: item.color, background: item.bg }">
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
        </div>
        <div class="stage-body">
          <div class="stage-value" :style="{ color: item.color }">{{ item.value }}</div>
          <div class="stage-label">{{ item.label }}</div>
          <div class="stage-hint">{{ item.hint }}</div>
        </div>
        <span v-if="index < stats.length - 1" class="stage-sep" aria-hidden="true" />
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="query.paperId" placeholder="选择试卷" clearable filterable style="width: 300px">
        <el-option
          v-for="paper in papers"
          :key="paper.id"
          :label="`${paper.paperTitle}（${paper.paperNo}）`"
          :value="paper.id"
        />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="学号 / 姓名"
        clearable
        style="width: 200px"
        @keyup.enter="search"
      />
      <el-select v-model="query.markingStatus" placeholder="阅卷状态" clearable style="width: 160px">
        <el-option v-for="(item, key) in MARKING_STATUS" :key="key" :label="item.label" :value="Number(key)" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <!-- 答卷卡片看板 -->
    <div v-loading="loading" class="answer-grid">
      <article v-for="row in list" :key="row.id" class="answer-card">
        <header class="card-head">
          <div class="student">
            <span class="avatar">{{ (row.studentName || row.realName || '?').slice(0, 1) }}</span>
            <div class="student-text">
              <span class="student-name">{{ row.studentName || row.realName || '未知学生' }}</span>
              <span class="student-no">{{ row.studentNo || '学号未录入' }}</span>
            </div>
          </div>
          <el-tag
            size="small"
            round
            effect="plain"
            :type="dictItem(REVIEW_STATUS, row.reviewStatus).type"
          >
            {{ row.reviewStatusDesc || dictItem(REVIEW_STATUS, row.reviewStatus).label }}
          </el-tag>
        </header>

        <div class="card-paper">
          <el-icon class="paper-icon" :size="15"><Document /></el-icon>
          <span class="paper-title" :title="row.paperTitle || row.paperId">
            {{ row.paperTitle || row.paperId || '未关联试卷' }}
          </span>
          <el-tag size="small" :type="dictItem(MARKING_STATUS, row.markingStatus).type">
            {{ row.markingStatusDesc || dictItem(MARKING_STATUS, row.markingStatus).label }}
          </el-tag>
        </div>

        <div class="score-row">
          <div class="score">
            <span class="score-label">AI 参考分</span>
            <span class="score-value ai">{{ row.aiReferenceScore ?? '-' }}</span>
          </div>
          <span class="score-divider" aria-hidden="true" />
          <div class="score">
            <span class="score-label">教师最终分</span>
            <span class="score-value final">{{ row.teacherFinalScore ?? '-' }}</span>
          </div>
          <span v-if="row.scoreModified" class="score-diff">分差 {{ row.scoreDiff }}</span>
        </div>

        <div class="card-meta">
          <span class="meta-item image-badge" :class="{ on: row.hasImage }">
            <el-icon :size="13"><Camera /></el-icon>
            {{ row.hasImage ? '已传扫描件' : '无扫描件' }}
          </span>
          <span class="meta-item">
            <el-icon :size="13"><Clock /></el-icon>
            {{ formatTime(row.createTime) }}
          </span>
          <span v-if="row.updateTime" class="meta-item">
            <el-icon :size="13"><Refresh /></el-icon>
            {{ formatTime(row.updateTime) }}
          </span>
        </div>

        <footer class="card-foot">
          <span v-if="row.studentClass" class="class-chip">{{ row.studentClass }}</span>
          <span v-else class="class-chip muted">班级未录入</span>
          <el-button type="primary" plain size="small" @click="goDetail(row)">
            查看详情
            <el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
        </footer>
      </article>

      <el-empty
        v-if="!loading && !list.length"
        class="grid-empty"
        description="暂无答卷数据"
        :image-size="80"
      />
    </div>

    <div class="pager">
      <el-pagination
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ============ 状态流水线 ============ */
.pipeline {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 12px;
  box-shadow: 0 4px 18px rgba(31, 36, 94, 0.04);
}

.stage {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 18px 22px;
}

/* 连接箭头：用旋转边框拼一个 chevron */
.stage-sep {
  position: absolute;
  top: 50%;
  right: 0;
  z-index: 1;
  width: 9px;
  height: 9px;
  background: #fff;
  border-top: 2px solid #ccd4f1;
  border-right: 2px solid #ccd4f1;
  transform: translate(50%, -50%) rotate(45deg);
}

.stage-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 11px;
}

.stage-body {
  min-width: 0;
}

.stage-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.1;
}

.stage-label {
  margin-top: 2px;
  overflow: hidden;
  font-size: 13px;
  color: #2b2e45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-hint {
  margin-top: 2px;
  overflow: hidden;
  font-size: 11px;
  color: #9096b3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ============ 筛选栏 ============ */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 12px;
}

/* ============ 答卷卡片看板 ============ */
.answer-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  min-height: 180px;
}

.grid-empty {
  grid-column: 1 / -1;
  padding: 24px 0;
}

.answer-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 18px 14px;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 12px;
  box-shadow: 0 6px 20px rgba(31, 36, 94, 0.05);
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
}

.answer-card::before {
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  content: '';
  background: linear-gradient(90deg, var(--ai-primary), var(--ai-accent));
  opacity: 0.85;
}

.answer-card:hover {
  border-color: #dfe4f6;
  box-shadow: 0 14px 32px rgba(31, 36, 94, 0.12);
  transform: translateY(-3px);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.student {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.avatar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, var(--ai-primary), var(--ai-accent));
  border-radius: 10px;
}

.student-text {
  min-width: 0;
}

.student-name {
  display: block;
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  color: #23264a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.student-no {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  font-size: 12px;
  color: #9096b3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-paper {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: var(--ai-gradient-soft);
  border-radius: 8px;
}

.paper-icon {
  flex-shrink: 0;
  color: var(--ai-primary);
}

.paper-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  color: #3a3f66;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 12px;
  border: 1px dashed var(--ai-border);
  border-radius: 10px;
}

.score {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.score-label {
  font-size: 11px;
  color: #9096b3;
}

.score-value {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.1;
}

.score-value.ai {
  color: var(--ai-primary);
}

.score-value.final {
  color: var(--ai-accent);
}

.score-divider {
  width: 1px;
  height: 26px;
  background: var(--ai-border);
}

.score-diff {
  margin-left: auto;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 600;
  color: #e6a23c;
  background: #fdf6ec;
  border-radius: 999px;
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  font-size: 12px;
  color: #9096b3;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.image-badge {
  padding: 2px 9px;
  color: #9096b3;
  background: #f4f5fa;
  border-radius: 999px;
}

.image-badge.on {
  color: #67c23a;
  background: #f0f9eb;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 12px;
  margin-top: auto;
  border-top: 1px solid #eef1fb;
}

.class-chip {
  max-width: 60%;
  overflow: hidden;
  font-size: 12px;
  color: #6b7194;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: #f4f6fd;
  padding: 3px 10px;
  border-radius: 999px;
}

.class-chip.muted {
  color: #b0b5cc;
}

/* ============ 分页 ============ */
.pager {
  display: flex;
  justify-content: flex-end;
}

/* ============ 响应式 ============ */
@media (max-width: 960px) {
  .pipeline {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stage-sep {
    display: none;
  }

  .stage:nth-child(odd) {
    border-right: 1px solid #eef1fb;
  }

  .stage:nth-child(-n + 2) {
    border-bottom: 1px solid #eef1fb;
  }
}

@media (max-width: 560px) {
  .pipeline {
    grid-template-columns: minmax(0, 1fr);
  }

  .stage {
    border-right: none;
    border-bottom: 1px solid #eef1fb;
  }

  .stage:last-child {
    border-bottom: none;
  }

  .filter-bar :deep(.el-select),
  .filter-bar :deep(.el-input) {
    width: 100% !important;
  }
}
</style>
