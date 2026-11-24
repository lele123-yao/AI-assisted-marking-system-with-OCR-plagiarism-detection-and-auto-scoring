<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createTemplate, deleteTemplate, pageTemplates, updateTemplate } from '@/api/prompt'
import { pageCourses } from '@/api/course'
import { useMobileList } from '@/composables/useMobileList'
import { QUESTION_TYPE, dictItem } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileSearchBar from '@/components/m/MobileSearchBar.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileLoadMore from '@/components/m/MobileLoadMore.vue'
import MobileSheet from '@/components/m/MobileSheet.vue'

/** AI 评分提示词模板管理 */
const { query, list, loading, finished, load, loadMore, search, reset } = useMobileList(
  pageTemplates,
  { questionType: '' }
)

const keyword = ref('')
const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((row) =>
    `${row.templateName || ''} ${row.templateCode || ''}`.toLowerCase().includes(kw)
  )
})
const empty = computed(() => !loading.value && !filteredList.value.length)

function handleReset() {
  keyword.value = ''
  reset()
}

const expanded = ref({})
function toggle(row) {
  expanded.value[row.id] = !expanded.value[row.id]
}

function templateContent(row) {
  const parts = []
  if (row.systemPrompt) parts.push(`【系统】${row.systemPrompt}`)
  if (row.userPrompt) parts.push(`【用户】${row.userPrompt}`)
  return parts.join('\n') || '暂无提示词内容'
}

// 课程下拉
const courses = ref([])
async function loadCourses() {
  const data = await pageCourses({ pageNum: 1, pageSize: 100 })
  courses.value = data?.records || []
}

// ==================== 新增 / 编辑模板 ====================
const sheetVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({
  templateCode: '',
  templateName: '',
  questionType: 0,
  courseId: null,
  systemPrompt: '',
  userPrompt: '',
  modelName: '',
  temperature: 0.3,
  status: 1,
  remark: ''
})
const rules = {
  templateCode: [
    { required: true, message: '请输入模板编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]{2,64}$/,
      message: '只能由字母、数字、下划线、中划线组成',
      trigger: 'blur'
    }
  ],
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  questionType: [{ required: true, message: '请选择题型', trigger: 'change' }],
  userPrompt: [{ required: true, message: '请输入用户提示词模板', trigger: 'blur' }]
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    templateCode: '',
    templateName: '',
    questionType: 0,
    courseId: null,
    systemPrompt: '',
    userPrompt: '',
    modelName: '',
    temperature: 0.3,
    status: 1,
    remark: ''
  })
  sheetVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    templateCode: row.templateCode,
    templateName: row.templateName,
    questionType: row.questionType ?? 0,
    courseId: row.courseId ?? null,
    systemPrompt: row.systemPrompt || '',
    userPrompt: row.userPrompt || '',
    modelName: row.modelName || '',
    temperature: row.temperature ?? 0.3,
    status: row.status ?? 1,
    remark: row.remark || ''
  })
  sheetVisible.value = true
  formRef.value?.clearValidate()
}

async function submit() {
  if (submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingId.value) {
      await updateTemplate(editingId.value, { ...form })
      ElMessage.success('修改成功，版本号已递增')
    } else {
      await createTemplate({ ...form })
      ElMessage.success('新增成功')
    }
    sheetVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

const deletingId = ref(null)
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除模板「${row.templateName}」？已被题目引用的模板无法删除。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  deletingId.value = row.id
  try {
    await deleteTemplate(row.id)
    ElMessage.success('删除成功')
    load()
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  load()
  loadCourses()
})
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="提示词模板"
      subtitle="配置 AI 阅卷的提示词；修改模板会自动递增版本号。"
      icon="MagicStick"
    >
      <template #actions>
        <el-button size="small" type="primary" @click="openCreate">新增</el-button>
      </template>
    </MobileHero>

    <MobileSearchBar
      v-model="keyword"
      placeholder="模板名称 / 编码"
      show-reset
      :loading="loading"
      @search="load"
      @reset="handleReset"
    >
      <el-select v-model="query.questionType" placeholder="全部题型" clearable @change="search">
        <el-option
          v-for="(item, key) in QUESTION_TYPE"
          :key="key"
          :label="item.label"
          :value="Number(key)"
        />
      </el-select>
    </MobileSearchBar>

    <div v-loading="loading" class="list">
      <article v-for="row in filteredList" :key="row.id" class="m-card">
        <div class="m-row-between">
          <span class="m-title name">{{ row.templateName }}</span>
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" round>
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </div>

        <div class="tags">
          <el-tag
            size="small"
            effect="plain"
            :type="dictItem(QUESTION_TYPE, row.questionType).type"
          >
            {{ dictItem(QUESTION_TYPE, row.questionType).label }}
          </el-tag>
          <span class="version">v{{ row.version }}</span>
          <span class="code">{{ row.templateCode }}</span>
        </div>

        <p class="prompt-preview" :class="{ expanded: expanded[row.id] }">
          {{ templateContent(row) }}
        </p>
        <el-button link size="small" type="primary" @click="toggle(row)">
          {{ expanded[row.id] ? '收起' : '展开全文' }}
        </el-button>

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
        v-if="empty"
        :description="keyword ? '当前已加载模板中无匹配项' : '暂无提示词模板'"
        icon="MagicStick"
        action-text="刷新"
        @action="load"
      />

      <MobileLoadMore
        :loading="loading"
        :finished="finished"
        :empty="!loading && !list.length"
        @load="loadMore"
      />
    </div>

    <MobileSheet
      v-model="sheetVisible"
      :title="editingId ? '编辑模板' : '新增模板'"
      height="90%"
      :loading="submitting"
      @confirm="submit"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="模板编码" prop="templateCode">
          <el-input
            v-model="form.templateCode"
            placeholder="如 ESSAY_DEFAULT"
            :disabled="!!editingId"
          />
        </el-form-item>
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="form.templateName" placeholder="如 简答题通用评分" />
        </el-form-item>
        <el-form-item label="适用题型" prop="questionType">
          <el-select v-model="form.questionType" style="width: 100%">
            <el-option
              v-for="(item, key) in QUESTION_TYPE"
              :key="key"
              :label="item.label"
              :value="Number(key)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属课程">
          <el-select
            v-model="form.courseId"
            clearable
            filterable
            placeholder="全局通用"
            style="width: 100%"
          >
            <el-option
              v-for="course in courses"
              :key="course.id"
              :label="course.courseName"
              :value="course.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
            placeholder="设定评分角色与总体要求"
          />
        </el-form-item>
        <el-form-item label="用户提示词" prop="userPrompt">
          <el-input
            v-model="form.userPrompt"
            type="textarea"
            :rows="6"
            maxlength="4000"
            show-word-limit
            placeholder="使用占位符拼接题干、答案与评分细则"
          />
        </el-form-item>
        <el-form-item label="模型">
          <el-input v-model="form.modelName" placeholder="留空使用后端默认值" />
        </el-form-item>
        <el-form-item label="温度">
          <el-input-number
            v-model="form.temperature"
            :min="0"
            :max="2"
            :step="0.1"
            :precision="1"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="256" show-word-limit />
        </el-form-item>
      </el-form>
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

.name {
  overflow: hidden;
  font-size: 15px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.version {
  padding: 1px 6px;
  font-size: 12px;
  color: #6b72a0;
  background: #f2f3fb;
  border-radius: 6px;
}

.code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #9aa0b8;
  word-break: break-all;
}

.prompt-preview {
  display: -webkit-box;
  margin: 10px 0 4px;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.7;
  color: #5c6180;
  word-break: break-word;
  white-space: pre-wrap;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.prompt-preview.expanded {
  display: block;
  overflow: visible;
  -webkit-line-clamp: unset;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 10px;
  margin-top: 10px;
  border-top: 1px dashed #f0f2fb;
}
</style>
