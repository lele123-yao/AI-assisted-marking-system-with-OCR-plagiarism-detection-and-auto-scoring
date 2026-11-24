<script setup>
import { computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { pagePaperAnswers } from '@/api/paperAnswer'
import { pagePapers } from '@/api/paper'
import { myPapers, myScores } from '@/api/student'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileStatGrid from '@/components/m/MobileStatGrid.vue'

/** 移动端首页：指标 + 快捷入口，数据口径与桌面工作台一致 */
const userStore = useUserStore()
const router = useRouter()

const isTeacher = computed(() => userStore.hasRole('ADMIN') || userStore.hasRole('TEACHER'))

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const today = new Date().toLocaleDateString('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long'
})

const roleNames = computed(() =>
  (userStore.userInfo?.roles || []).map((role) => role.roleName || role.roleCode)
)

const stats = reactive({
  loading: false,
  pending: null,
  marked: null,
  papers: null,
  review: null
})

const studentStats = reactive({ loading: false, papers: null, scores: null })

const safe = (promise) => promise.catch(() => null)

async function totalAnswers(markingStatus) {
  const data = await pagePaperAnswers({ pageNum: 1, pageSize: 1, markingStatus })
  return Number(data?.total || 0)
}

async function countPendingReview() {
  const data = await pagePaperAnswers({ pageNum: 1, pageSize: 100, markingStatus: 3 })
  return (data?.records || []).filter((item) => item.reviewStatus === 0).length
}

async function loadTeacherStats() {
  stats.loading = true
  try {
    const [s0, s1, s2, s3, s5, papers] = await Promise.all([
      safe(totalAnswers(0)),
      safe(totalAnswers(1)),
      safe(totalAnswers(2)),
      safe(totalAnswers(3)),
      safe(totalAnswers(5)),
      safe(pagePapers({ pageNum: 1, pageSize: 1 }))
    ])
    stats.pending = [s0, s1, s2].every((v) => v === null) ? null : (s0 || 0) + (s1 || 0) + (s2 || 0)
    stats.marked = [s3, s5].every((v) => v === null) ? null : (s3 || 0) + (s5 || 0)
    stats.papers = papers ? Number(papers.total || 0) : null
    stats.review = await safe(countPendingReview())
  } finally {
    stats.loading = false
  }
}

async function loadStudentStats() {
  studentStats.loading = true
  try {
    const [papers, scores] = await Promise.all([safe(myPapers()), safe(myScores())])
    studentStats.papers = Array.isArray(papers) ? papers.length : null
    studentStats.scores = Array.isArray(scores) ? scores.length : null
  } finally {
    studentStats.loading = false
  }
}

const display = (value) => (value === null || value === undefined ? '-' : value)

const statItems = computed(() => {
  if (isTeacher.value) {
    return [
      { key: 'pending', label: '待阅卷答卷', value: display(stats.pending), icon: 'Tickets', tone: 'warning', path: '/m/mark/task?status=3' },
      { key: 'marked', label: '已阅卷数量', value: display(stats.marked), icon: 'CircleCheck', tone: 'success', path: '/m/mark/task?status=5' },
      { key: 'papers', label: '试卷总数', value: display(stats.papers), icon: 'Document', tone: 'primary', path: '/m/paper' },
      { key: 'review', label: '待复核数量', value: display(stats.review), icon: 'Stamp', tone: 'danger', path: '/m/grade/review' }
    ]
  }
  return [
    { key: 'studentPapers', label: '我的试卷', value: display(studentStats.papers), icon: 'Document', tone: 'primary', path: '/m/student/papers' },
    { key: 'studentScores', label: '我的成绩', value: display(studentStats.scores), icon: 'Trophy', tone: 'success', path: '/m/student/scores' }
  ]
})

const quickLinks = computed(() => {
  if (isTeacher.value) {
    return [
      { label: '试卷管理', icon: 'Document', path: '/m/paper', permission: 'paper:view' },
      { label: '阅卷监控', icon: 'Cpu', path: '/m/mark/task', permission: 'mark:view' },
      { label: '成绩复核', icon: 'Stamp', path: '/m/grade/review', permission: 'grade:review' },
      { label: '学情报表', icon: 'DataBoard', path: '/m/learning/report', permission: 'learning:view' },
      { label: '纸质录分', icon: 'Edit', path: '/m/paper-answer/score', permission: 'paper-answer:score' },
      { label: '抄袭检测', icon: 'Warning', path: '/m/plagiarism/report', permission: 'plagiarism:view' },
      { label: '知识库', icon: 'Collection', path: '/m/agent/knowledge', permission: 'mark:view' },
      { label: '全部功能', icon: 'Grid', path: '/m/menu' }
    ]
  }
  return [
    { label: '我的试卷', icon: 'Document', path: '/m/student/papers', permission: 'student:paper' },
    { label: '我的成绩', icon: 'Trophy', path: '/m/student/scores', permission: 'student:score' },
    { label: '在线作答', icon: 'EditPen', path: '/m/answer/submit', permission: 'answer:submit' },
    { label: '全部功能', icon: 'Grid', path: '/m/menu' }
  ].filter((item) => userStore.hasPermission(item.permission))
})

const loading = computed(() => (isTeacher.value ? stats.loading : studentStats.loading))

onMounted(() => {
  if (isTeacher.value) loadTeacherStats()
  else loadStudentStats()
})
</script>

<template>
  <div class="m-page">
    <MobileHero
      :title="`${greeting}，${userStore.nickname}`"
      :subtitle="`${today} · ${roleNames.join(' / ') || '未分配角色'}`"
      icon="Reading"
      dark
    >
      <template #actions>
        <el-button size="small" class="hero-btn" @click="router.push(isTeacher ? '/m/mark/task' : '/m/student/papers')">
          {{ isTeacher ? '阅卷' : '试卷' }}
        </el-button>
      </template>
    </MobileHero>

    <div v-loading="loading">
      <MobileStatGrid :items="statItems" @select="router.push($event.path)" />
    </div>

    <section class="m-card">
      <div class="m-row-between">
        <span class="m-title">快捷入口</span>
        <span class="m-sub">常用功能一键直达</span>
      </div>
      <div class="quick-grid">
        <button
          v-for="link in quickLinks"
          :key="link.path"
          type="button"
          class="quick-item"
          @click="router.push(link.path)"
        >
          <span class="quick-icon">
            <el-icon :size="19"><component :is="link.icon" /></el-icon>
          </span>
          <span>{{ link.label }}</span>
        </button>
      </div>
    </section>

    <section class="m-card">
      <div class="m-row-between">
        <span class="m-title">当前账号</span>
        <el-tag v-for="role in roleNames" :key="role" size="small" effect="plain">{{ role }}</el-tag>
      </div>
      <ul class="account-list">
        <li><span>用户名</span><b>{{ userStore.userInfo?.username || '-' }}</b></li>
        <li><span>昵称</span><b>{{ userStore.userInfo?.nickname || '-' }}</b></li>
        <li><span>邮箱</span><b>{{ userStore.userInfo?.email || '-' }}</b></li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.hero-btn {
  color: #1a1c50;
  background: linear-gradient(96deg, #cfd8ff, #a9b8ff);
  border: none;
  border-radius: 999px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
  margin-top: 10px;
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  padding: 10px 2px;
  font-size: 12px;
  color: #4c5170;
  background: transparent;
  border: none;
  border-radius: 12px;
}

.quick-item:active {
  background: #f4f6ff;
}

.quick-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  color: var(--ai-primary);
  background: linear-gradient(135deg, #eef1ff, #f6f0ff);
  border-radius: 13px;
}

.account-list {
  padding: 0;
  margin: 8px 0 0;
  list-style: none;
}

.account-list li {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  font-size: 13px;
  color: #8b90ab;
  border-bottom: 1px dashed #f0f2fb;
}

.account-list li:last-child {
  border-bottom: none;
}

.account-list b {
  font-weight: 500;
  color: #3a3e5c;
}
</style>
