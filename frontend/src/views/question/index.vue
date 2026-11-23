<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addQuestion,
  assignKnowledge,
  deleteQuestion,
  listQuestionsByPaper,
  updateQuestion
} from '@/api/question'
import { pagePapers } from '@/api/paper'
import { pageTemplates } from '@/api/prompt'
import PageHero from '@/components/PageHero.vue'
import { QUESTION_TYPE, dictItem } from '@/utils/dict'

/** 主观题目管理：先选试卷，再维护题目与知识点 */
const papers = ref([])
const paperId = ref(null)
const loading = ref(false)
const list = ref([])
const templates = ref([])

/** 上方统计条：全部基于当前试卷已加载的题目列表计算，无额外请求 */
const summary = computed(() => [
  { label: '本卷题目', value: list.value.length, icon: 'Tickets', color: '#5b6cff', bg: '#eef1ff' },
  {
    label: '合计分值',
    value: list.value.reduce((sum, row) => sum + (Number(row.score) || 0), 0),
    icon: 'Odometer',
    color: '#2fae7a',
    bg: '#e9f8f1'
  },
  {
    label: '主观题',
    value: list.value.filter((row) => Number(row.questionType) >= 5).length,
    icon: 'EditPen',
    color: '#e6a23c',
    bg: '#fdf6ec'
  },
  {
    label: '已绑定模板',
    value: list.value.filter((row) => row.promptTemplateId).length,
    icon: 'MagicStick',
    color: '#9a5bff',
    bg: '#f3efff'
  }
])

/** 按大题分组的题目清单（无大题归入「未分组题目」） */
const groupedQuestions = computed(() => {
  const map = new Map()
  list.value.forEach((row) => {
    const name = row.sectionName?.trim() || '未分组题目'
    if (!map.has(name)) map.set(name, [])
    map.get(name).push(row)
  })
  return Array.from(map, ([name, items]) => ({
    name,
    items,
    totalScore: items.reduce((sum, row) => sum + (Number(row.score) || 0), 0)
  }))
})

const activeSections = ref([])
watch(
  groupedQuestions,
  (groups) => {
    activeSections.value = groups.map((group) => group.name)
  },
  { immediate: true }
)

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 100 })
  papers.value = data?.records || []
  if (!paperId.value && papers.value.length) {
    paperId.value = papers.value[0].id
  }
}

async function loadTemplates() {
  const data = await pageTemplates({ pageNum: 1, pageSize: 100, status: 1 })
  templates.value = data?.records || []
}

async function loadQuestions() {
  if (!paperId.value) {
    list.value = []
    return
  }
  loading.value = true
  try {
    list.value = (await listQuestionsByPaper(paperId.value)) || []
  } finally {
    loading.value = false
  }
}

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({
  paperId: null,
  questionNo: '',
  sectionName: '',
  questionType: 5,
  questionContent: '',
  standardAnswer: '',
  scoringRule: '',
  score: 10,
  difficulty: 3,
  promptTemplateId: null,
  sortOrder: 1,
  knowledgeIds: []
})
// 知识点无后端字典接口，这里输入逗号分隔的数字 ID
const knowledgeText = ref('')

const rules = {
  questionNo: [{ required: true, message: '请输入题号', trigger: 'blur' }],
  questionType: [{ required: true, message: '请选择题型', trigger: 'change' }],
  questionContent: [{ required: true, message: '请输入题干', trigger: 'blur' }],
  score: [{ required: true, message: '请输入分值', trigger: 'blur' }],
  standardAnswer: [
    {
      validator: (rule, value, callback) => {
        if (form.questionType >= 5 && !value) return callback(new Error('主观题必须填写参考答案'))
        callback()
      },
      trigger: 'blur'
    }
  ],
  scoringRule: [
    {
      validator: (rule, value, callback) => {
        if (form.questionType >= 5 && !value) return callback(new Error('主观题必须填写评分细则'))
        callback()
      },
      trigger: 'blur'
    }
  ]
}

function openCreate() {
  if (!paperId.value) {
    ElMessage.warning('请先选择试卷')
    return
  }
  editingId.value = null
  Object.assign(form, {
    paperId: paperId.value,
    questionNo: '',
    sectionName: '',
    questionType: 5,
    questionContent: '',
    standardAnswer: '',
    scoringRule: '',
    score: 10,
    difficulty: 3,
    promptTemplateId: null,
    sortOrder: list.value.length + 1,
    knowledgeIds: []
  })
  knowledgeText.value = ''
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    paperId: row.paperId,
    questionNo: row.questionNo,
    sectionName: row.sectionName || '',
    questionType: row.questionType,
    questionContent: row.questionContent,
    standardAnswer: row.standardAnswer || '',
    scoringRule: row.scoringRule || '',
    score: row.score,
    difficulty: row.difficulty ?? 3,
    promptTemplateId: row.promptTemplateId ?? null,
    sortOrder: row.sortOrder ?? 1,
    knowledgeIds: row.knowledgeIds || []
  })
  knowledgeText.value = (row.knowledgeIds || []).join(',')
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submit() {
  if (submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const knowledgeIds = knowledgeText.value
    .split(/[,，\s]+/)
    .map((item) => Number(item))
    .filter((item) => Number.isInteger(item) && item > 0)

  submitting.value = true
  try {
    const payload = { ...form, paperId: paperId.value, knowledgeIds: undefined }
    if (editingId.value) {
      await updateQuestion(editingId.value, payload)
      if (knowledgeIds.length) await assignKnowledge(editingId.value, knowledgeIds)
      ElMessage.success('修改成功')
    } else {
      const newId = await addQuestion(payload)
      // 新增接口返回题目 ID 时再补充知识点
      if (newId && knowledgeIds.length) await assignKnowledge(newId, knowledgeIds)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadQuestions()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除题目「${row.questionNo}」？试卷总分会自动回写。`, '删除确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  await deleteQuestion(row.id)
  ElMessage.success('删除成功')
  loadQuestions()
}

onMounted(async () => {
  await loadPapers()
  loadTemplates()
  loadQuestions()
})
</script>

<template>
  <div class="page-container">
    <PageHero
      title="题目管理"
      subtitle="先选择试卷，再逐题维护题干、参考答案与评分细则；主观题必须填写参考答案和细则，AI 才能按提示词模板给出评分。"
      icon="EditPen"
    >
      <template #actions>
        <el-button v-permission="'question:add'" type="primary" @click="openCreate">
          新增题目
        </el-button>
      </template>
    </PageHero>

    <!-- 试卷选择 + 统计条 -->
    <el-card shadow="never" class="bank-control">
      <div class="control-row">
        <div class="control-left">
          <span class="control-label">
            <el-icon><Document /></el-icon>
            当前试卷
          </span>
          <el-select
            v-model="paperId"
            filterable
            placeholder="请选择试卷"
            style="width: 340px"
            @change="loadQuestions"
          >
            <el-option
              v-for="paper in papers"
              :key="paper.id"
              :label="`${paper.paperTitle}（${paper.paperNo}）`"
              :value="paper.id"
            />
          </el-select>
          <el-button v-permission="'question:view'" :loading="loading" plain @click="loadQuestions">
            刷新
          </el-button>
        </div>

        <div class="control-stats">
          <div v-for="item in summary" :key="item.label" class="chip">
            <span class="chip-icon" :style="{ color: item.color, backgroundColor: item.bg }">
              <el-icon :size="16"><component :is="item.icon" /></el-icon>
            </span>
            <div class="chip-text">
              <b>{{ item.value }}</b>
              <span>{{ item.label }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 按大题折叠的题目清单 -->
    <el-card shadow="never" class="bank-card">
      <template #header>
        <div class="bank-head">
          <div class="bank-title">
            <span>题目清单</span>
            <span class="bank-sub">按大题分组 · 共 {{ list.length }} 题</span>
          </div>
          <span class="bank-tip">展开大题查看题干、参考答案与评分细则</span>
        </div>
      </template>

      <el-collapse v-if="groupedQuestions.length" v-model="activeSections" class="bank-collapse">
        <el-collapse-item
          v-for="group in groupedQuestions"
          :key="group.name"
          :name="group.name"
        >
          <template #title>
            <div class="group-title">
              <span class="group-name">{{ group.name }}</span>
              <span class="group-meta">{{ group.items.length }} 题 · 合计 {{ group.totalScore }} 分</span>
            </div>
          </template>

          <div class="q-list">
            <article v-for="row in group.items" :key="row.id" class="q-item">
              <div class="q-no">{{ row.questionNo }}</div>

              <div class="q-main">
                <div class="q-tags">
                  <el-tag :type="dictItem(QUESTION_TYPE, row.questionType).type" effect="plain" size="small">
                    {{ dictItem(QUESTION_TYPE, row.questionType).label }}
                  </el-tag>
                  <el-tag effect="plain" size="small" type="info">{{ row.score }} 分</el-tag>
                  <el-tag effect="plain" size="small" type="warning">难度 {{ row.difficulty ?? '-' }}</el-tag>
                  <el-tag v-if="row.promptTemplateName" effect="plain" size="small" type="success">
                    <el-icon><MagicStick /></el-icon>{{ row.promptTemplateName }}
                  </el-tag>
                </div>

                <p class="q-content">{{ row.questionContent }}</p>

                <div class="q-extra">
                  <div v-if="row.standardAnswer" class="q-line">
                    <span>参考答案</span>
                    <p>{{ row.standardAnswer }}</p>
                  </div>
                  <div v-if="row.scoringRule" class="q-line">
                    <span>评分细则</span>
                    <p>{{ row.scoringRule }}</p>
                  </div>
                  <div
                    v-if="row.knowledgeNames || (row.knowledgeIds || []).length"
                    class="q-line"
                  >
                    <span>知识点</span>
                    <p>{{ row.knowledgeNames || (row.knowledgeIds || []).join(', ') }}</p>
                  </div>
                </div>
              </div>

              <div class="q-actions">
                <el-button v-permission="'question:edit'" link type="primary" @click.stop="openEdit(row)">
                  编辑
                </el-button>
                <el-button v-permission="'question:delete'" link type="danger" @click.stop="handleDelete(row)">
                  删除
                </el-button>
              </div>
            </article>
          </div>
        </el-collapse-item>
      </el-collapse>

      <div v-else v-loading="loading" class="empty-wrap">
        <el-empty description="该试卷暂无题目" :image-size="90" />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑题目' : '新增题目'"
      width="760px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="题号" prop="questionNo">
              <el-input v-model="form.questionNo" placeholder="如 1 / 2-1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="所属大题">
              <el-input v-model="form.sectionName" placeholder="如 简答题" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="题型" prop="questionType">
              <el-select v-model="form.questionType" style="width: 100%">
                <el-option
                  v-for="(item, key) in QUESTION_TYPE"
                  :key="key"
                  :label="item.label"
                  :value="Number(key)"
                  :disabled="Number(key) === 0"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="题干" prop="questionContent">
          <el-input v-model="form.questionContent" type="textarea" :rows="4" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="参考答案" prop="standardAnswer">
          <el-input v-model="form.standardAnswer" type="textarea" :rows="4" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="评分细则" prop="scoringRule">
          <el-input v-model="form.scoringRule" type="textarea" :rows="4" maxlength="2000" show-word-limit />
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="分值" prop="score">
              <el-input-number v-model="form.score" :min="0.5" :max="100" :precision="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="难度">
              <el-slider v-model="form.difficulty" :min="1" :max="5" show-stops />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="1" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="提示词模板">
          <el-select v-model="form.promptTemplateId" clearable filterable placeholder="可选" style="width: 100%">
            <el-option
              v-for="template in templates"
              :key="template.id"
              :label="`${template.templateName}（v${template.version}）`"
              :value="template.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="知识点ID">
          <el-input v-model="knowledgeText" placeholder="多个知识点用逗号分隔，如 1,2,3" />
          <div class="form-tip">后端未提供知识点字典接口，此处按知识点 ID 绑定。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 试卷选择 + 统计条 */
.bank-control {
  background: linear-gradient(120deg, #ffffff 0%, #f7f8ff 100%);
}

.control-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.control-left {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.control-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #3a3e5c;
}

.control-label .el-icon {
  color: var(--ai-primary);
}

.control-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 22px;
}

.chip {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chip-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
}

.chip-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.chip-text b {
  font-size: 16px;
  color: #23264a;
}

.chip-text span {
  font-size: 11px;
  color: #9aa0b8;
}

/* 题目分组折叠 */
.bank-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.bank-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #2b2e45;
}

.bank-sub,
.bank-tip {
  font-size: 12px;
  font-weight: 400;
  color: #9aa0b8;
}

.bank-collapse {
  border-top: none;
}

.bank-collapse :deep(.el-collapse-item__header) {
  height: 52px;
  font-size: 14px;
}

.group-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.group-name {
  font-weight: 600;
  color: #2b2e45;
}

.group-meta {
  font-size: 12px;
  color: #9aa0b8;
}

.q-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 0 8px;
}

.q-item {
  display: flex;
  gap: 14px;
  padding: 16px;
  background: #fafbff;
  border: 1px solid #eef1fa;
  border-radius: 12px;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.q-item:hover {
  background: #f6f8ff;
  border-color: #d5dcfa;
}

.q-no {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  font-size: 14px;
  font-weight: 700;
  color: var(--ai-primary);
  background: #eef1ff;
  border-radius: 10px;
}

.q-main {
  flex: 1;
  min-width: 0;
}

.q-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.q-content {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.75;
  color: #3a3e5c;
  white-space: pre-wrap;
}

.q-extra {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.q-line {
  display: flex;
  gap: 10px;
  font-size: 12px;
  line-height: 1.7;
}

.q-line > span {
  flex-shrink: 0;
  padding: 1px 8px;
  color: #7d829c;
  background: #eef1fa;
  border-radius: 6px;
}

.q-line p {
  margin: 0;
  color: #6b7089;
  white-space: pre-wrap;
}

.q-actions {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  gap: 2px;
}

.empty-wrap {
  padding: 20px 0;
}

@media (max-width: 900px) {
  .control-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .q-item {
    flex-wrap: wrap;
  }

  .q-actions {
    flex-direction: row;
    width: 100%;
    justify-content: flex-end;
  }
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
</style>
