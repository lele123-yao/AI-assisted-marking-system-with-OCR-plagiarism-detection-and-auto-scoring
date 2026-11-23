<script setup>
import { computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { pagePaperAnswers } from '@/api/paperAnswer'
import { pagePapers } from '@/api/paper'
import { myPapers, myScores } from '@/api/student'

const userStore = useUserStore()
const router = useRouter()

// 根据时间显示问候语
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

// 教师 / 管理员展示阅卷工作台；学生展示自己的试卷与成绩
const isTeacher = computed(() => userStore.hasRole('ADMIN') || userStore.hasRole('TEACHER'))

const welcomeDesc = computed(() =>
  isTeacher.value
    ? '欢迎使用高校主观题智能异步阅卷系统，这里是你的阅卷工作台。'
    : '欢迎使用高校主观题智能异步阅卷系统，可在「我的学习」查看试卷与成绩。'
)

const stats = reactive({
  loading: false,
  pending: null, // 待阅卷（待录入 / OCR 识别中 / AI 批改中）
  marked: null, // 已阅卷（待教师确认 / 已归档）
  papers: null, // 我的试卷
  review: null // 待复核
})

const studentStats = reactive({
  loading: false,
  papers: null,
  scores: null
})

/** 单个请求失败时返回 null，避免一个接口异常让整张卡片报错 */
function safe(promise) {
  return promise.catch(() => null)
}

/** 按答卷状态统计数量：pageSize=1 只为拿 total，不传实际数据 */
async function totalAnswers(markingStatus) {
  const data = await pagePaperAnswers({ pageNum: 1, pageSize: 1, markingStatus })
  return Number(data?.total || 0)
}

/**
 * 待复核数量：后端没有按复核状态统计的接口，
 * 这里取「待教师确认」答卷的第一页统计 reviewStatus = 0 的条数。
 */
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

    // 三项相加，全部失败时才保持 null（页面显示 -）
    stats.pending = [s0, s1, s2].every((v) => v === null)
      ? null
      : (s0 || 0) + (s1 || 0) + (s2 || 0)
    stats.marked = [s3, s5].every((v) => v === null)
      ? null
      : (s3 || 0) + (s5 || 0)
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

function display(value) {
  return value === null || value === undefined ? '-' : value
}

// 卡片配置：只做数字展示，不引入图表；to 为点击后跳转的业务页面
const statCards = computed(() => {
  if (isTeacher.value) {
    return [
      { key: 'pending', label: '待阅卷答卷', value: display(stats.pending), icon: 'Tickets', color: '#e6a23c', bg: '#fdf6ec', to: '/mark/task?status=3' },
      { key: 'marked', label: '已阅卷数量', value: display(stats.marked), icon: 'CircleCheck', color: '#67c23a', bg: '#f0f9eb', to: '/mark/task?status=5' },
      { key: 'papers', label: '我的试卷', value: display(stats.papers), icon: 'Document', color: '#5b6cff', bg: '#eef1ff', to: '/paper' },
      { key: 'review', label: '待复核数量', value: display(stats.review), icon: 'Stamp', color: '#f56c6c', bg: '#fef0f0', to: '/grade/review' }
    ]
  }
  return [
    { key: 'studentPapers', label: '我的试卷', value: display(studentStats.papers), icon: 'Document', color: '#5b6cff', bg: '#eef1ff', to: '/student/papers' },
    { key: 'studentScores', label: '我的成绩', value: display(studentStats.scores), icon: 'Trophy', color: '#67c23a', bg: '#f0f9eb', to: '/student/scores' }
  ]
})

/** 点击统计卡片跳转到对应业务页面 */
function goTo(item) {
  if (item.to) router.push(item.to)
}

// 工作台快捷入口
const quickLinks = computed(() =>
  isTeacher.value
    ? [
        { label: '试卷管理', desc: '创建、发布与下发试卷', to: '/paper', icon: 'Document' },
        { label: 'AI阅卷监控', desc: '查看批改进度与任务明细', to: '/mark/task', icon: 'Cpu' },
        { label: '成绩复核', desc: '确认 AI 评分与人工调整', to: '/grade/review', icon: 'Stamp' },
        { label: '学情报表', desc: '班级对比与薄弱知识点', to: '/learning/report', icon: 'DataBoard' }
      ]
    : [
        { label: '我的试卷', desc: '查看已下发的试卷', to: '/student/papers', icon: 'Document' },
        { label: '我的成绩', desc: '查看已出的成绩与反馈', to: '/student/scores', icon: 'Trophy' },
        { label: '在线作答', desc: '进入当前开放的考试', to: '/answer/submit', icon: 'EditPen' }
      ]
)

const isLoading = computed(() => (isTeacher.value ? stats.loading : studentStats.loading))

// 顶部横幅展示当前日期
const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long'
})

const roleNames = computed(() =>
  (userStore.userInfo?.roles || []).map((role) => role.roleName || role.roleCode)
)

onMounted(() => {
  if (isTeacher.value) loadTeacherStats()
  else loadStudentStats()
})
</script>

<template>
  <div class="page-container dashboard">
    <!-- 指挥中心横幅 -->
    <section class="command-hero">
      <div class="command-text">
        <div class="command-eyebrow">
          <span class="pulse-dot" />
          AI MARKING · {{ isTeacher ? '阅卷工作台' : '学习空间' }}
        </div>
        <h1 class="command-title">
          {{ greeting }}，{{ userStore.nickname }}
          <el-tag v-for="name in roleNames" :key="name" size="small" effect="plain" class="role-tag">
            {{ name }}
          </el-tag>
        </h1>
        <p class="command-desc">{{ welcomeDesc }}</p>
        <div class="command-actions">
          <el-button
            class="hero-btn"
            @click="router.push(isTeacher ? '/mark/task' : '/answer/submit')"
          >
            {{ isTeacher ? '进入阅卷监控' : '进入在线作答' }}
            <el-icon class="btn-icon"><ArrowRight /></el-icon>
          </el-button>
          <span class="command-time">{{ today }}</span>
        </div>
      </div>

      <!-- 右侧装饰轨道 -->
      <div class="command-visual">
        <div class="orbit orbit-a"><el-icon :size="22"><Cpu /></el-icon></div>
        <div class="orbit orbit-b"><el-icon :size="20"><DataAnalysis /></el-icon></div>
        <div class="orbit orbit-c"><el-icon :size="20"><CircleCheck /></el-icon></div>
        <div class="core"><el-icon :size="34"><Reading /></el-icon></div>
      </div>
    </section>

    <!-- 指标卡 -->
    <section v-loading="isLoading" class="stat-grid">
      <article
        v-for="item in statCards"
        :key="item.key"
        class="stat-card"
        @click="goTo(item)"
      >
        <div class="stat-top">
          <div class="stat-icon" :style="{ color: item.color, backgroundColor: item.bg }">
            <el-icon :size="22"><component :is="item.icon" /></el-icon>
          </div>
          <el-icon class="stat-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="stat-value">{{ item.value }}</div>
        <div class="stat-label">{{ item.label }}</div>
        <span class="stat-glow" :style="{ background: item.color }" />
      </article>
    </section>

    <!-- 快捷入口 + 账号信息 -->
    <section class="lower-grid">
      <el-card shadow="never" class="quick-panel">
        <template #header>
          <div class="panel-head">
            <span class="panel-title">快捷入口</span>
            <span class="panel-sub">常用功能一键直达</span>
          </div>
        </template>
        <div class="quick-grid">
          <button
            v-for="link in quickLinks"
            :key="link.to"
            class="quick-tile"
            @click="router.push(link.to)"
          >
            <div class="quick-icon"><el-icon :size="20"><component :is="link.icon" /></el-icon></div>
            <div class="quick-text">
              <div class="quick-label">{{ link.label }}</div>
              <div class="quick-desc">{{ link.desc }}</div>
            </div>
            <el-icon class="quick-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
      </el-card>

      <el-card shadow="never" class="account-panel">
        <template #header>
          <div class="panel-head">
            <span class="panel-title">当前账号</span>
          </div>
        </template>
        <div class="account-top">
          <el-avatar :size="52" class="account-avatar">
            {{ userStore.nickname.charAt(0).toUpperCase() }}
          </el-avatar>
          <div>
            <div class="account-name">{{ userStore.nickname }}</div>
            <div class="account-roles">
              <el-tag
                v-for="role in userStore.userInfo?.roles || []"
                :key="role.roleCode"
                size="small"
                effect="plain"
              >
                {{ role.roleName || role.roleCode }}
              </el-tag>
              <span v-if="!userStore.userInfo?.roles?.length" class="panel-sub">未分配角色</span>
            </div>
          </div>
        </div>
        <ul class="account-list">
          <li><span>用户名</span><b>{{ userStore.userInfo?.username || '-' }}</b></li>
          <li><span>昵称</span><b>{{ userStore.userInfo?.nickname || '-' }}</b></li>
          <li><span>邮箱</span><b>{{ userStore.userInfo?.email || '-' }}</b></li>
        </ul>
      </el-card>
    </section>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* ==================== 指挥中心横幅 ==================== */
.command-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 30px 34px;
  overflow: hidden;
  color: #fff;
  border: 1px solid rgba(140, 156, 255, 0.18);
  border-radius: 16px;
  background: var(--ai-gradient);
  box-shadow: 0 18px 40px rgba(30, 30, 100, 0.22);
}

.command-hero::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: '';
  background-image:
    linear-gradient(rgba(160, 174, 255, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(160, 174, 255, 0.08) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: radial-gradient(circle at 20% 30%, #000 0%, transparent 76%);
}

.command-text {
  position: relative;
  z-index: 1;
  min-width: 0;
}

.command-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 13px;
  margin-bottom: 16px;
  font-size: 12px;
  letter-spacing: 1px;
  color: #b6c1ff;
  background: rgba(126, 146, 255, 0.14);
  border: 1px solid rgba(126, 146, 255, 0.3);
  border-radius: 999px;
}

.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #5cf0c8;
  box-shadow: 0 0 10px #5cf0c8;
}

.command-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 0 0 10px;
  font-size: 26px;
  font-weight: 700;
}

.role-tag {
  font-weight: 400;
  color: #cfd8ff;
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(180, 192, 255, 0.4);
}

.command-desc {
  max-width: 620px;
  margin: 0 0 20px;
  font-size: 14px;
  line-height: 1.8;
  color: rgba(214, 220, 255, 0.75);
}

.command-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
}

.hero-btn {
  height: 40px;
  padding: 0 22px;
  font-weight: 500;
  color: #1a1c50;
  background: linear-gradient(96deg, #cfd8ff 0%, #a9b8ff 100%);
  border: none;
  border-radius: 999px;
  box-shadow: 0 10px 24px rgba(10, 12, 40, 0.28);
}

.hero-btn:hover {
  color: #1a1c50;
  background: linear-gradient(96deg, #e2e8ff 0%, #c3ceff 100%);
}

.btn-icon {
  margin-left: 4px;
}

.command-time {
  font-size: 13px;
  color: rgba(190, 198, 240, 0.6);
}

/* 右侧轨道装饰 */
.command-visual {
  position: relative;
  flex-shrink: 0;
  width: 210px;
  height: 170px;
}

.core {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 78px;
  height: 78px;
  color: #fff;
  background: linear-gradient(135deg, #5b6cff 0%, #9a5bff 100%);
  border-radius: 24px;
  box-shadow: 0 16px 36px rgba(88, 100, 255, 0.45);
  transform: translate(-50%, -50%);
}

.orbit {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  color: #c9d3ff;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(160, 174, 255, 0.28);
  border-radius: 14px;
  backdrop-filter: blur(6px);
  animation: bob 6s ease-in-out infinite;
}

.orbit-a {
  top: 4px;
  left: 12px;
}

.orbit-b {
  top: 20px;
  right: 6px;
  animation-delay: -2s;
}

.orbit-c {
  bottom: 6px;
  left: 52px;
  animation-delay: -4s;
}

@keyframes bob {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

/* ==================== 指标卡 ==================== */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}

.stat-card {
  position: relative;
  padding: 20px 22px;
  overflow: hidden;
  cursor: pointer;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 14px;
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
}

.stat-card:hover {
  border-color: #d5dcfa;
  box-shadow: 0 14px 30px rgba(44, 52, 120, 0.12);
  transform: translateY(-4px);
}

.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
}

.stat-arrow {
  color: #d0d5ea;
  transition: color 0.2s ease, transform 0.2s ease;
}

.stat-card:hover .stat-arrow {
  color: var(--ai-primary);
  transform: translateX(4px);
}

.stat-value {
  margin-top: 16px;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
  color: #23264a;
}

.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: #8b90ab;
}

.stat-glow {
  position: absolute;
  right: -34px;
  bottom: -46px;
  width: 110px;
  height: 110px;
  opacity: 0.1;
  border-radius: 50%;
  filter: blur(6px);
  transition: opacity 0.22s ease;
}

.stat-card:hover .stat-glow {
  opacity: 0.2;
}

/* ==================== 下方两栏 ==================== */
.lower-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(280px, 0.9fr);
  gap: 16px;
  align-items: stretch;
}

.panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #2b2e45;
}

.panel-sub {
  font-size: 12px;
  font-weight: 400;
  color: #9aa0b8;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.quick-tile {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  text-align: left;
  cursor: pointer;
  background: linear-gradient(135deg, #fafbff 0%, #f4f6ff 100%);
  border: 1px solid #eceffa;
  border-radius: 12px;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.quick-tile:hover {
  border-color: #cdd6fa;
  box-shadow: 0 10px 24px rgba(64, 76, 160, 0.1);
  transform: translateY(-2px);
}

.quick-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  color: #5b6cff;
  background: #eaeefe;
  border-radius: 12px;
  flex-shrink: 0;
}

.quick-text {
  min-width: 0;
}

.quick-label {
  font-size: 14px;
  font-weight: 600;
  color: #2b2e45;
}

.quick-desc {
  margin-top: 3px;
  overflow: hidden;
  font-size: 12px;
  color: #9aa0b8;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-arrow {
  margin-left: auto;
  color: #cbd1e8;
}

.quick-tile:hover .quick-arrow {
  color: #5b6cff;
}

.account-top {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 18px;
  margin-bottom: 6px;
  border-bottom: 1px dashed #eceffa;
}

.account-avatar {
  font-size: 20px;
  color: #fff;
  background: linear-gradient(135deg, #5b6cff 0%, #9a5bff 100%);
}

.account-name {
  font-size: 17px;
  font-weight: 600;
  color: #2b2e45;
}

.account-roles {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.account-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.account-list li {
  display: flex;
  align-items: center;
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

@media (max-width: 1100px) {
  .lower-grid {
    grid-template-columns: 1fr;
  }

  .command-visual {
    display: none;
  }
}

@media (max-width: 640px) {
  .quick-grid {
    grid-template-columns: 1fr;
  }

  .command-hero {
    padding: 24px 22px;
  }

  .command-title {
    font-size: 21px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .orbit {
    animation: none;
  }
}
</style>
