<script setup>
import { computed, onMounted, ref } from 'vue'
import { myScores } from '@/api/student'
import PageHero from '@/components/PageHero.vue'
import { MARKING_STATUS, dictItem, formatTime } from '@/utils/dict'

/** 学生端：我的成绩（教师最终分为准） */
const loading = ref(false)
const list = ref([])

async function load() {
  loading.value = true
  try {
    list.value = (await myScores()) || []
  } finally {
    loading.value = false
  }
}

/** 已出分记录 */
const scoredList = computed(() =>
  list.value.filter((row) => row.finalScore !== null && row.finalScore !== undefined)
)

/** 平均得分率：按试卷总分折算，无总分时退回平均分 */
const overview = computed(() => {
  const rows = scoredList.value
  const values = rows.map((row) => Number(row.finalScore))
  const rates = rows.map((row) => {
    const total = Number(row.paperTotalScore)
    if (!total) return null
    return Math.min(100, (Number(row.finalScore) / total) * 100)
  }).filter((item) => item !== null)
  const avg = values.length ? values.reduce((sum, v) => sum + v, 0) / values.length : 0
  const avgRate = rates.length
    ? rates.reduce((sum, v) => sum + v, 0) / rates.length
    : 0
  const passed = rates.filter((v) => v >= 60).length
  return {
    avg: values.length ? avg.toFixed(1) : '-',
    avgRate: Number(avgRate.toFixed(1)),
    max: values.length ? Math.max(...values) : '-',
    passRate: rates.length ? Math.round((passed / rates.length) * 100) : 0,
    total: list.value.length,
    scored: rows.length
  }
})

/** 单条记录的得分率与颜色 */
function scoreRate(row) {
  const total = Number(row.paperTotalScore)
  if (!total) return null
  return Math.min(100, (Number(row.finalScore) / total) * 100)
}

function rateColor(rate) {
  if (rate === null) return '#c8cde0'
  if (rate < 60) return '#f56c6c'
  if (rate < 80) return '#e6a23c'
  return '#5b6cff'
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <PageHero
      title="我的成绩"
      subtitle="最终成绩以教师确认为准，AI 参考分仅供查看，可按试卷回顾每场考试的得分情况。"
      icon="Trophy"
    >
      <template #actions>
        <el-button :loading="loading" plain @click="load">刷新成绩</el-button>
      </template>
    </PageHero>

    <div v-loading="loading" class="score-layout">
      <!-- 左：成绩总览 -->
      <aside class="overview-card">
        <span class="overview-eyebrow">成绩总览</span>

        <div class="ring-wrap">
          <div
            class="ring"
            :style="{ '--rate': `${overview.avgRate}%` }"
          >
            <div class="ring-inner">
              <b>{{ overview.avgRate }}<em>%</em></b>
              <span>平均得分率</span>
            </div>
          </div>
        </div>

        <div class="overview-metrics">
          <div class="metric">
            <b>{{ overview.scored }}<em>/{{ overview.total }}</em></b>
            <span>已出分 / 全部</span>
          </div>
          <div class="metric">
            <b>{{ overview.avg }}</b>
            <span>平均分</span>
          </div>
          <div class="metric">
            <b>{{ overview.max }}</b>
            <span>最高分</span>
          </div>
          <div class="metric">
            <b>{{ overview.passRate }}<em>%</em></b>
            <span>及格率</span>
          </div>
        </div>

        <p class="overview-note">
          及格按得分率 60% 折算；未出分的试卷不计入平均分与及格率。
        </p>
      </aside>

      <!-- 右：成绩记录 -->
      <section class="record-panel">
        <div class="record-head">
          <span class="record-title">成绩记录</span>
          <span class="record-sub">共 {{ list.length }} 场考试</span>
        </div>

        <div v-if="list.length" class="record-list">
          <article v-for="row in list" :key="row.id || row.paperId" class="record-card">
            <div class="record-score" :style="{ color: rateColor(scoreRate(row)) }">
              <b>{{ row.finalScore ?? '--' }}</b>
              <span>/ {{ row.paperTotalScore ?? '-' }}</span>
            </div>

            <div class="record-main">
              <div class="record-top">
                <h4>{{ row.paperTitle }}</h4>
                <el-tag :type="dictItem(MARKING_STATUS, row.markingStatus).type" size="small" effect="plain">
                  {{ dictItem(MARKING_STATUS, row.markingStatus).label }}
                </el-tag>
              </div>

              <p class="record-no">{{ row.paperNo }}</p>

              <div class="record-bar">
                <span
                  class="bar-fill"
                  :style="{
                    width: `${scoreRate(row) ?? 0}%`,
                    background: rateColor(scoreRate(row))
                  }"
                />
              </div>

              <div class="record-meta">
                <span>AI 参考分：{{ row.aiReferenceScore ?? '-' }}</span>
                <span>提交：{{ formatTime(row.submitTime) }}</span>
                <span>阅卷：{{ formatTime(row.markTime) }}</span>
              </div>
            </div>
          </article>
        </div>

        <el-empty v-else-if="!loading" description="暂无成绩记录" :image-size="90" />
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

.score-layout {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

/* 左侧总览卡 */
.overview-card {
  position: sticky;
  top: 16px;
  padding: 26px 24px 22px;
  color: #fff;
  background:
    radial-gradient(360px 200px at 100% 0%, rgba(154, 91, 255, 0.4), transparent 62%),
    linear-gradient(160deg, #1b1e52 0%, #2a2a72 55%, #3d358f 100%);
  border: 1px solid rgba(140, 156, 255, 0.2);
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(25, 28, 80, 0.24);
}

.overview-eyebrow {
  font-size: 12px;
  color: #a9b4f2;
  letter-spacing: 1px;
}

.ring-wrap {
  display: flex;
  justify-content: center;
  padding: 22px 0 18px;
}

.ring {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 172px;
  height: 172px;
  background: conic-gradient(#7f8cff var(--rate), rgba(255, 255, 255, 0.12) 0);
  border-radius: 50%;
  box-shadow: 0 0 0 1px rgba(160, 174, 255, 0.18) inset;
}

.ring-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 134px;
  height: 134px;
  background: #232660;
  border-radius: 50%;
}

.ring-inner b {
  font-size: 34px;
  font-weight: 700;
  color: #eef1ff;
}

.ring-inner b em {
  font-size: 15px;
  font-style: normal;
  opacity: 0.75;
}

.ring-inner span {
  font-size: 12px;
  color: rgba(190, 198, 240, 0.7);
}

.overview-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding-top: 18px;
  border-top: 1px solid rgba(160, 174, 255, 0.16);
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.metric b {
  font-size: 19px;
  font-weight: 700;
  color: #eef1ff;
}

.metric b em {
  font-size: 12px;
  font-style: normal;
  opacity: 0.6;
}

.metric span {
  font-size: 12px;
  color: rgba(190, 198, 240, 0.65);
}

.overview-note {
  padding-top: 16px;
  margin: 16px 0 0;
  font-size: 11px;
  line-height: 1.7;
  color: rgba(178, 187, 232, 0.6);
  border-top: 1px dashed rgba(160, 174, 255, 0.16);
}

/* 右侧记录 */
.record-panel {
  padding: 22px 24px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(40, 50, 120, 0.05);
}

.record-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 18px;
}

.record-title {
  font-size: 16px;
  font-weight: 600;
  color: #23264a;
}

.record-sub {
  font-size: 12px;
  color: #9aa0b8;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.record-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 18px;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-radius: 14px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.record-card:hover {
  border-color: #d5dcfa;
  box-shadow: 0 10px 24px rgba(60, 70, 160, 0.08);
}

.record-score {
  display: flex;
  align-items: baseline;
  flex-shrink: 0;
  gap: 3px;
  min-width: 96px;
}

.record-score b {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}

.record-score span {
  font-size: 12px;
  color: #9aa0b8;
}

.record-main {
  flex: 1;
  min-width: 0;
}

.record-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.record-top h4 {
  margin: 0;
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  color: #2b2e45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-no {
  margin: 3px 0 10px;
  font-size: 12px;
  color: #9aa0b8;
}

.record-bar {
  height: 6px;
  overflow: hidden;
  background: #eef1fa;
  border-radius: 999px;
}

.bar-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  transition: width 0.4s ease;
}

.record-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 8px;
  font-size: 12px;
  color: #9aa0b8;
}

@media (max-width: 960px) {
  .score-layout {
    grid-template-columns: 1fr;
  }

  .overview-card {
    position: static;
  }
}
</style>
