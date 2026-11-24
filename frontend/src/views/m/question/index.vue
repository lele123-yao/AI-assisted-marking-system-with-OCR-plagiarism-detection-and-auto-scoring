<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
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
import { QUESTION_TYPE, dictItem } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileSheet from '@/components/m/MobileSheet.vue'

/** 题目管理：先选试卷，再维护题目与知识点 */
const route = useRoute()

const papers = ref([])
const paperId = ref(null)
const list = ref([])
const loading = ref(false)
const templates = ref([])

async function loadPapers() {
  const data = await pagePapers({ pageNum: 1, pageSize: 50 })
  papers.value = data?.records || []
  const preset = Number(route.query.paperId)
  if (preset && papers.value.some((paper) => paper.id === preset)) {
    paperId.value = preset
  } else if (papers.value.length) {
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

const emptyDescription = computed(() => {
  if (!papers.value.length) return '暂无试卷，请先创建试卷'
  if (!paperId.value) return '请先选择试卷'
  return '该试卷暂无题目，点击右上角新增'
})

// ==================== 新增 / 编辑题目 ====================
const sheetVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const formRef = ref()
const knowledgeText = ref('')
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
  sheetVisible.value = true
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
  sheetVisible.value = true
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
      if (newId && knowledgeIds.length) await assignKnowledge(newId, knowledgeIds)
      ElMessage.success('新增成功')
    }
    sheetVisible.value = false
    loadQuestions()
  } finally {
    submitting.value = false
  }
}

const deletingId = ref(null)
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除题目「${row.questionNo}」？试卷总分会自动回写。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  deletingId.value = row.id
  try {
    await deleteQuestion(row.id)
    ElMessage.success('删除成功')
    loadQuestions()
  } finally {
    deletingId.value = null
  }
}

onMounted(async () => {
  await loadPapers()
  loadTemplates()
  loadQuestions()
})
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="题目管理"
      subtitle="先选择试卷，再逐题维护题干、参考答案与评分细则。"
      icon="EditPen"
    >
      <template #actions>
        <el-button size="small" type="primary" @click="openCreate">新增</el-button>
      </template>
    </MobileHero>

    <section class="m-card paper-box">
      <span class="m-label">当前试卷</span>
      <el-select
        v-model="paperId"
        filterable
        placeholder="请选择试卷"
        class="paper-select"
        @change="loadQuestions"
      >
        <el-option
          v-for="paper in papers"
          :key="paper.id"
          :label="`${paper.paperTitle}（${paper.paperNo}）`"
          :value="paper.id"
        />
      </el-select>
      <div class="summary">
        <div><b>{{ list.length }}</b><span>题目数</span></div>
        <div>
          <b>{{ list.reduce((sum, row) => sum + (Number(row.score) || 0), 0) }}</b>
          <span>合计分值</span>
        </div>
      </div>
    </section>

    <div v-loading="loading" class="list">
      <article v-for="row in list" :key="row.id" class="m-card question">
        <div class="q-head">
          <span class="q-no">{{ row.questionNo }}</span>
          <el-tag :type="dictItem(QUESTION_TYPE, row.questionType).type" size="small" effect="plain">
            {{ dictItem(QUESTION_TYPE, row.questionType).label }}
          </el-tag>
          <b class="q-score">{{ row.score }} 分</b>
        </div>

        <p class="q-content">{{ row.questionContent }}</p>

        <div class="q-meta">
          <span v-if="row.sectionName">大题：{{ row.sectionName }}</span>
          <span>难度：{{ row.difficulty ?? '-' }}</span>
          <span v-if="row.promptTemplateName">模板：{{ row.promptTemplateName }}</span>
          <span v-if="row.knowledgeNames || (row.knowledgeIds || []).length">
            知识点：{{ row.knowledgeNames || (row.knowledgeIds || []).join(', ') }}
          </span>
        </div>

        <div class="actions">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            size="small"
            type="danger"
            plain
            :loading="deletingId === row.id"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </div>
      </article>

      <MobileEmpty
        v-if="!loading && !list.length"
        :description="emptyDescription"
        icon="EditPen"
      />
    </div>

    <MobileSheet
      v-model="sheetVisible"
      :title="editingId ? '编辑题目' : '新增题目'"
      height="90%"
      :loading="submitting"
      @confirm="submit"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="题号" prop="questionNo">
          <el-input v-model="form.questionNo" placeholder="如 1 / 2-1" />
        </el-form-item>
        <el-form-item label="所属大题">
          <el-input v-model="form.sectionName" placeholder="如 简答题" />
        </el-form-item>
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
        <el-form-item label="题干" prop="questionContent">
          <el-input
            v-model="form.questionContent"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="参考答案" prop="standardAnswer">
          <el-input
            v-model="form.standardAnswer"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="评分细则" prop="scoringRule">
          <el-input
            v-model="form.scoringRule"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="分值" prop="score">
          <el-input-number v-model="form.score" :min="0.5" :max="100" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="难度（1-5）">
          <el-slider v-model="form.difficulty" :min="1" :max="5" show-stops />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="1" :max="999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="提示词模板">
          <el-select
            v-model="form.promptTemplateId"
            clearable
            filterable
            placeholder="可选"
            style="width: 100%"
          >
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
          <p class="tip">后端未提供知识点字典接口，此处按知识点 ID 绑定。</p>
        </el-form-item>
      </el-form>
    </MobileSheet>
  </div>
</template>

<style scoped>
.paper-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.paper-select {
  width: 100%;
}

.summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding-top: 10px;
  text-align: center;
  border-top: 1px dashed #f0f2fb;
}

.summary b {
  display: block;
  font-size: 18px;
  color: #23264a;
}

.summary span {
  font-size: 11px;
  color: #9aa0b8;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.q-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 26px;
  height: 26px;
  padding: 0 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--ai-primary);
  background: #eef1ff;
  border-radius: 8px;
}

.q-score {
  margin-left: auto;
  font-size: 12px;
  color: #8b90ab;
}

.q-content {
  margin: 10px 0 8px;
  font-size: 14px;
  line-height: 1.75;
  color: #3a3e5c;
  white-space: pre-wrap;
  word-break: break-word;
}

.q-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #9aa0b8;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 10px;
  margin-top: 10px;
  border-top: 1px dashed #f0f2fb;
}

.tip {
  margin: 4px 0 0;
  font-size: 12px;
  color: #9aa0b8;
}
</style>
