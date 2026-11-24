<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { myScores } from '@/api/student'
import { MARKING_STATUS, dictItem, formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'

/** 学生端：我的成绩，教师最终分为准 */
const router = useRouter()
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

const scoredList = computed(() =>
  list.value.filter((row) => row.finalScore !== null && row.finalScore !== undefined)
)

const overview = computed(() => {
  const rows = scoredList.value
  const values = rows.map((row) => Number(row.finalScore))
  const rates = rows
    .map((row) => {
      const total = Number(row.paperTotalScore)
      return total ? Math.min(100, (Number(row.finalScore) / total) * 100) : null
    })
    .filter((item) => item !== null)
  const avg = values.length ? values.reduce((sum, v) => sum + v, 0) / values.length : 0
  const avgRate = rates.length ? rates.reduce((sum, v) => sum + v, 0) / rates.length : 0
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
  <div class="m-page">
    <MobileHero title="我的成绩" subtitle="最终成绩以教师确认为准，AI 参考分仅供查看" icon="Trophy">
      <template #actions>
        <el-button size="small" plain :loading="loading" @click="load">刷新</el-button>
      </template>
    </MobileHero>

    <section class="overview">
      <div class="ring" :style="{ '--rate': `${overview.avgRate}%` }">
        <div class="ring-inner">
          <b>{{ overview.avgRate }}<em>%</em></b>
          <span>平均得分率</span>
        </div>
      </div>
      <div class="metrics">
        <div><b>{{ overview.avg }}</b><span>平均分</span></div>
        <div><b>{{ overview.max }}</b><span>最高分</span></div>
        <div><b>{{ overview.passRate }}<em>%</em></b><span>及格率</span></div>
        <div><b>{{ overview.scored }}<em>/{{ overview.total }}</em></b><span>已出分</span></div>
      </div>
    </section>

    <div v-loading="loading" class="list">
      <article
        v-for="row in list"
        :key="row.id || row.paperId"
        class="m-card record"
        :class="{ clickable: row.answerId }"
        @click="row.answerId && router.push(`/m/student/answer/${row.answerId}`)"
      >
        <div class="record-score" :style="{ color: rateColor(scoreRate(row)) }">
          <b>{{ row.finalScore ?? '--' }}</b>
          <span>/ {{ row.paperTotalScore ?? '-' }}</span>
        </div>

        <div class="record-main">
          <div class="m-row-between">
            <h3 class="m-title">{{ row.paperTitle }}</h3>
            <el-tag :type="dictItem(MARKING_STATUS, row.markingStatus).type" size="small" effect="plain">
              {{ dictItem(MARKING_STATUS, row.markingStatus).label }}
            </el-tag>
          </div>
          <p class="paper-no">{{ row.paperNo }}</p>

          <div class="bar">
            <span
              class="bar-fill"
              :style="{ width: `${scoreRate(row) ?? 0}%`, background: rateColor(scoreRate(row)) }"
            />
          </div>

          <div class="record-meta">
            <span>AI 参考分 {{ row.aiReferenceScore ?? '-' }}</span>
            <span>阅卷 {{ formatTime(row.markTime) }}</span>
            <span v-if="row.answerId" class="detail-link">查看答卷 ›</span>
          </div>
        </div>
      </article>

      <MobileEmpty
        v-if="!loading && !list.length"
        description="暂无成绩记录"
        icon="Trophy"
        action-text="刷新"
        @action="load"
      />
    </div>
  </div>
</template>

<style scoped>
.overview {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 18px;
  background:
    radial-gradient(300px 180px at 100% 0%, rgba(154, 91, 255, 0.4), transparent 62%),
    linear-gradient(160deg, #1b1e52 0%, #2a2a72 55%, #3d358f 100%);
  border-radius: 18px;
  box-shadow: 0 16px 34px rgba(25, 28, 80, 0.24);
}

.ring {
  display: flex;
  align-self: center;
  align-items: center;
  justify-content: center;
  width: 150px;
  height: 150px;
  background: conic-gradient(#7f8cff var(--rate), rgba(255, 255, 255, 0.12) 0);
  border-radius: 50%;
}

.ring-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 118px;
  height: 118px;
  background: #232463;
  border-radius: 50%;
}

.ring-inner b {
  font-size: 27px;
  color: #fff;
}

.ring-inner em {
  font-size: 13px;
  font-style: normal;
}

.ring-inner span {
  margin-top: 3px;
  font-size: 11px;
  color: #a9b4f2;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  text-align: center;
}

.metrics b {
  display: block;
  font-size: 17px;
  color: #fff;
}

.metrics em {
  font-size: 11px;
  font-style: normal;
  color: #c4ccff;
}

.metrics span {
  font-size: 11px;
  color: #a9b4f2;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 80px;
}

.record {
  display: flex;
  gap: 14px;
}

.record-score {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 66px;
}

.record-score b {
  font-size: 24px;
  line-height: 1.1;
}

.record-score span {
  font-size: 11px;
  color: #9aa0b8;
}

.record-main {
  flex: 1;
  min-width: 0;
}

.record-main h3 {
  font-size: 14px;
}

.paper-no {
  margin: 4px 0 0;
  font-size: 11px;
  color: #9aa0b8;
}

.bar {
  height: 5px;
  margin: 9px 0 7px;
  overflow: hidden;
  background: #eef1fb;
  border-radius: 999px;
}

.bar-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  transition: width 0.3s ease;
}

.record-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 11px;
  color: #8b90ab;
}

.record.clickable {
  cursor: pointer;
}

.detail-link {
  color: var(--ai-primary);
}
</style>
