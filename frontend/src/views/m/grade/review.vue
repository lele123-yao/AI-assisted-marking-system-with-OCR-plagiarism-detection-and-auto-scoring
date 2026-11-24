<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pagePaperAnswers } from '@/api/paperAnswer'
import { pagePapers } from '@/api/paper'
import { reviewGrade } from '@/api/grade'
import { useMobileList } from '@/composables/useMobileList'
import { MARKING_STATUS, REVIEW_STATUS, dictItem, formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileLoadMore from '@/components/m/MobileLoadMore.vue'
import MobileSheet from '@/components/m/MobileSheet.vue'

/** 成绩复核：待确认答卷列表 + 底部弹层完成通过 / 改分 / 驳回 */
const filter = ref(3)

const fetchPage = (params) => {
  const payload = { ...params }
  if (payload.markingStatus === '' || payload.markingStatus === null || payload.markingStatus === undefined) {
    delete payload.markingStatus
  }
  return pagePaperAnswers(payload)
}

const { query, list, loading, finished, load, loadMore, search } = useMobileList(
  fetchPage,
  { markingStatus: 3 },
  10
)

const filters = [
  { value: 3, label: '待复核' },
  { value: 5, label: '已归档' },
  { value: '', label: '全部' }
]

/** 试卷列表仅用于取满分，接口失败不影响复核流程 */
const papers = ref([])

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
}

function pickStatus(value) {
  filter.value = value
  query.markingStatus = value
  search()
}

/** 答卷 VO 未返回复核状态时按桌面端规则推导 */
function reviewStatusOf(row) {
  if (row.reviewStatus !== null && row.reviewStatus !== undefined) return row.reviewStatus
  if (row.markingStatus === 5) {
    const ai = row.aiReferenceScore
    const final = row.teacherFinalScore
    const changed =
      ai !== null && ai !== undefined && final !== null && final !== undefined && Number(ai) !== Number(final)
    return changed ? 2 : 1
  }
  return 0
}

const sheetVisible = ref(false)
const submitting = ref(false)
const current = ref(null)
const form = reactive({ reviewAction: 1, finalScore: null, reviewRemark: '' })

const fullScore = computed(() => {
  const row = current.value
  if (!row) return null
  const fromRow = Number(row.paperTotalScore)
  if (!Number.isNaN(fromRow) && fromRow > 0) return fromRow
  const paper = papers.value.find((item) => item.id === row.paperId)
  const fromPaper = Number(paper?.totalScore)
  return !Number.isNaN(fromPaper) && fromPaper > 0 ? fromPaper : null
})

function openReview(row) {
  current.value = row
  form.reviewAction = 1
  form.finalScore = row.teacherFinalScore ?? null
  form.reviewRemark = ''
  sheetVisible.value = true
}

async function submit() {
  if (submitting.value || !current.value) return
  const action = form.reviewAction

  if (action === 3 && !form.reviewRemark.trim()) {
    ElMessage.warning('驳回时必须填写复核意见')
    return
  }

  // 通过会沿用已有最终分，未录分时引导改用「改分」
  if (action === 1 && (current.value.teacherFinalScore === null || current.value.teacherFinalScore === undefined)) {
    ElMessage.warning('该答卷尚未录入最终分，请先选择「改分」填写最终分')
    return
  }

  const payload = {
    answerId: current.value.id,
    reviewAction: action,
    reviewRemark: form.reviewRemark.trim() || undefined
  }

  if (action === 2) {
    if (form.finalScore === null || form.finalScore === '') {
      ElMessage.warning('请输入改分后的最终分')
      return
    }
    const score = Number(form.finalScore)
    if (Number.isNaN(score) || score < 0) {
      ElMessage.warning('最终分必须为非负数字')
      return
    }
    if (fullScore.value !== null && score > fullScore.value) {
      ElMessage.warning(`最终分不能超过试卷满分 ${fullScore.value}`)
      return
    }
    payload.finalScore = score
  }

  // 改分 / 驳回会影响最终成绩，二次确认
  if (action === 2 || action === 3) {
    const tip = action === 2 ? `确认将最终分修改为 ${form.finalScore} 分？` : '确认驳回该答卷并要求重新批改？'
    try {
      await ElMessageBox.confirm(tip, '操作确认', {
        type: 'warning',
        confirmButtonText: '确认提交',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
  }

  submitting.value = true
  try {
    await reviewGrade(payload)
    ElMessage.success('复核已提交')
    sheetVisible.value = false
    search()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loadPapers()
  load()
})
</script>

<template>
  <div class="m-page">
    <MobileHero title="成绩复核" subtitle="确认 AI 参考分：可复核通过、改分或驳回重批" icon="Stamp">
      <template #actions>
        <el-button size="small" plain :loading="loading" @click="search">刷新</el-button>
      </template>
    </MobileHero>

    <div class="m-chips">
      <button
        v-for="item in filters"
        :key="item.label"
        type="button"
        class="m-chip"
        :class="{ active: filter === item.value }"
        @click="pickStatus(item.value)"
      >
        {{ item.label }}
      </button>
    </div>

    <div v-loading="loading" class="list">
      <article v-for="row in list" :key="row.id" class="m-card item" @click="openReview(row)">
        <div class="m-row-between">
          <span class="student">
            {{ row.studentName || '未命名' }}
            <em>{{ row.studentNo || '-' }}</em>
          </span>
          <el-tag
            size="small"
            effect="plain"
            :type="dictItem(REVIEW_STATUS, reviewStatusOf(row)).type"
          >
            {{ row.reviewStatusDesc || dictItem(REVIEW_STATUS, reviewStatusOf(row)).label }}
          </el-tag>
        </div>

        <h3 class="paper">{{ row.paperTitle || '未关联试卷' }}</h3>

        <div class="score-row">
          <div>
            <span>AI 参考分</span>
            <b>{{ row.aiReferenceScore ?? '-' }}</b>
          </div>
          <div>
            <span>教师最终分</span>
            <b class="final">{{ row.teacherFinalScore ?? '-' }}</b>
          </div>
          <div v-if="row.studentClass" class="class-col">
            <span>班级</span>
            <b>{{ row.studentClass }}</b>
          </div>
        </div>

        <div class="meta">
          <el-tag size="small" effect="plain" :type="dictItem(MARKING_STATUS, row.markingStatus).type">
            {{ row.markingStatusDesc || dictItem(MARKING_STATUS, row.markingStatus).label }}
          </el-tag>
          <span>{{ formatTime(row.markTime || row.createTime) }}</span>
          <span class="entry">复核<el-icon :size="12"><ArrowRight /></el-icon></span>
        </div>
      </article>

      <MobileEmpty
        v-if="!loading && !list.length"
        description="当前筛选下暂无答卷"
        icon="Stamp"
        action-text="刷新"
        @action="search"
      />

      <MobileLoadMore
        :loading="loading"
        :finished="finished && list.length > 0"
        :empty="!loading && !list.length"
        @load="loadMore"
      />
    </div>

    <MobileSheet
      v-model="sheetVisible"
      title="成绩复核"
      height="76%"
      confirm-text="提交复核"
      :loading="submitting"
      @confirm="submit"
    >
      <template v-if="current">
        <div class="sheet-scores">
          <div>
            <span>AI 参考分</span>
            <b>{{ current.aiReferenceScore ?? '-' }}</b>
          </div>
          <div>
            <span>当前最终分</span>
            <b>{{ current.teacherFinalScore ?? '-' }}</b>
          </div>
          <div>
            <span>试卷满分</span>
            <b>{{ fullScore ?? '-' }}</b>
          </div>
        </div>

        <p class="sheet-sub">
          {{ current.studentName || '未命名' }} · {{ current.studentNo || '-' }}
          <br />
          {{ current.paperTitle || '未关联试卷' }}
        </p>

        <el-form label-position="top" class="form">
          <el-form-item label="复核动作">
            <el-radio-group v-model="form.reviewAction" class="actions">
              <el-radio :value="1" border>复核通过</el-radio>
              <el-radio :value="2" border>改分</el-radio>
              <el-radio :value="3" border>驳回重批</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="form.reviewAction === 2" :label="`最终分${fullScore !== null ? `（0 ~ ${fullScore}）` : ''}`">
            <el-input-number
              v-model="form.finalScore"
              :min="0"
              :max="fullScore ?? undefined"
              :precision="1"
              :step="1"
              class="score-input"
            />
          </el-form-item>

          <el-form-item :label="form.reviewAction === 3 ? '复核意见（必填）' : '复核意见（选填）'">
            <el-input
              v-model="form.reviewRemark"
              type="textarea"
              :rows="4"
              maxlength="256"
              show-word-limit
              :placeholder="form.reviewAction === 3 ? '请说明驳回原因' : '可补充评分说明'"
            />
          </el-form-item>
        </el-form>
      </template>
    </MobileSheet>
  </div>
</template>

<style scoped>
.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.item {
  cursor: pointer;
  transition: transform 0.16s ease;
}

.item:active {
  transform: scale(0.995);
}

.student {
  font-size: 14px;
  font-weight: 600;
  color: #23264a;
}

.student em {
  margin-left: 6px;
  font-size: 11px;
  font-style: normal;
  font-weight: 400;
  color: #9aa0b8;
}

.paper {
  margin: 7px 0 9px;
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  color: #5c6180;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-row {
  display: flex;
  gap: 10px;
  padding: 9px 12px;
  background: #f7f8ff;
  border-radius: 10px;
}

.score-row > div {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.score-row span {
  font-size: 10px;
  color: #9aa0b8;
}

.score-row b {
  overflow: hidden;
  font-size: 17px;
  color: var(--ai-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-row b.final {
  color: #2fae7a;
}

.score-row .class-col b {
  font-size: 12px;
  font-weight: 500;
  line-height: 22px;
  color: #3a3e5c;
}

.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 9px;
  font-size: 11px;
  color: #9aa0b8;
}

.meta .entry {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  margin-left: auto;
  color: var(--ai-primary);
}

.sheet-scores {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 12px;
  background: #f7f8ff;
  border-radius: 12px;
}

.sheet-scores > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
  text-align: center;
}

.sheet-scores span {
  font-size: 11px;
  color: #9aa0b8;
}

.sheet-scores b {
  font-size: 18px;
  color: #23264a;
}

.sheet-sub {
  margin: 12px 2px 2px;
  font-size: 12px;
  line-height: 1.7;
  color: #8b90ab;
}

.form {
  margin-top: 6px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.actions :deep(.el-radio) {
  margin-right: 0;
}

.score-input {
  width: 100%;
}

.score-input :deep(.el-input__inner) {
  text-align: left;
}
</style>
