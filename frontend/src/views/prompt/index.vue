<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createTemplate, deleteTemplate, pageTemplates, updateTemplate } from '@/api/prompt'
import { pageCourses } from '@/api/course'
import PageHero from '@/components/PageHero.vue'
import { useTable } from '@/composables/useTable'
import { QUESTION_TYPE, dictItem } from '@/utils/dict'

/** AI 评分 Prompt 模板管理 */
const { loading, list, total, query, load, search, reset, handleSizeChange, handlePageChange } =
  useTable(pageTemplates, { questionType: '', courseId: '', status: '' })

/** 看板统计：模板总数取后端 total，其余指标按当前页数据计算 */
const stats = computed(() => [
  { label: '模板总数', value: total.value, icon: 'MagicStick', color: '#409eff', bg: '#ecf5ff' },
  {
    label: '启用模板',
    value: list.value.filter((row) => row.status === 1).length,
    icon: 'CircleCheck',
    color: '#67c23a',
    bg: '#f0f9eb',
    hint: '当前页 · 点击筛选',
    clickable: true,
    key: 'enabled'
  },
  {
    label: '停用模板',
    value: list.value.filter((row) => row.status !== 1).length,
    icon: 'CircleClose',
    color: '#909399',
    bg: '#f4f4f5',
    hint: '当前页 · 点击筛选',
    clickable: true,
    key: 'disabled'
  },
  {
    label: '覆盖题型',
    value: new Set(
      list.value.map((row) => row.questionType).filter((type) => type !== null && type !== undefined)
    ).size,
    icon: 'Collection',
    color: '#9254de',
    bg: '#f9f0ff',
    hint: '当前页去重'
  }
])

/** 点击「启用 / 停用」卡片时同步状态筛选并重新查询 */
function onStatSelect(item) {
  if (item.key === 'enabled') query.status = 1
  if (item.key === 'disabled') query.status = 0
  search()
}

/** 当前选中的模板（用于右侧详情预览），列表刷新后尽量保留选中项 */
const selectedTemplate = ref(null)
watch(
  list,
  (rows) => {
    const stillExists = rows.find((row) => row.id === selectedTemplate.value?.id)
    selectedTemplate.value = stillExists || rows[0] || null
  },
  { immediate: true }
)

const courses = ref([])
async function loadCourses() {
  const data = await pageCourses({ pageNum: 1, pageSize: 100 })
  courses.value = data?.records || []
}

const dialogVisible = ref(false)
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
  dialogVisible.value = true
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
  dialogVisible.value = true
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
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

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
  await deleteTemplate(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => {
  load()
  loadCourses()
})
</script>

<template>
  <div class="page-container">
    <PageHero
      title="提示词模板"
      subtitle="配置 AI 阅卷的系统与用户提示词，用占位符注入题干、参考答案和学生作答；修改模板会自动递增版本号，被题目引用的模板不能删除。"
      icon="MagicStick"
    >
      <template #actions>
        <el-button v-permission="'prompt:add'" type="primary" @click="openCreate">新增模板</el-button>
      </template>
    </PageHero>

    <el-card v-loading="loading" shadow="never" class="workspace">
      <!-- 顶部紧凑统计条 -->
      <div class="summary-strip">
        <div
          v-for="item in stats"
          :key="item.label"
          class="summary-item"
          :class="{
            clickable: item.clickable,
            active:
              (item.key === 'enabled' && query.status === 1) ||
              (item.key === 'disabled' && query.status === 0)
          }"
          @click="item.clickable && onStatSelect(item)"
        >
          <span class="summary-icon" :style="{ color: item.color, backgroundColor: item.bg }">
            <el-icon :size="15"><component :is="item.icon" /></el-icon>
          </span>
          <span class="summary-value">{{ item.value ?? '-' }}</span>
          <span class="summary-label">{{ item.label }}</span>
        </div>
      </div>

      <div class="prompt-tip">
        <el-icon><InfoFilled /></el-icon>
        <span v-pre>可用占位符：{{question}} {{standardAnswer}} {{scoringRule}} {{studentAnswer}} {{score}}</span>
      </div>

      <div class="split">
        <!-- 左栏：筛选 + 模板列表 -->
        <aside class="side">
          <div class="filters">
            <el-select v-model="query.questionType" placeholder="适用题型" clearable>
              <el-option
                v-for="(item, key) in QUESTION_TYPE"
                :key="key"
                :label="item.label"
                :value="Number(key)"
              />
            </el-select>
            <el-select v-model="query.courseId" placeholder="所属课程" clearable filterable>
              <el-option
                v-for="course in courses"
                :key="course.id"
                :label="course.courseName"
                :value="course.id"
              />
            </el-select>
            <el-select v-model="query.status" placeholder="状态" clearable>
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
            <div class="filter-actions">
              <el-button type="primary" :loading="loading" @click="search">查询</el-button>
              <el-button @click="reset">重置</el-button>
            </div>
          </div>

          <div v-if="!list.length" class="side-empty">
            <el-empty description="暂无提示词模板" :image-size="70" />
          </div>
          <div v-else class="tpl-list">
            <div
              v-for="row in list"
              :key="row.id"
              class="tpl-item"
              :class="{ active: selectedTemplate?.id === row.id }"
              @click="selectedTemplate = row"
            >
              <div class="tpl-item-head">
                <span class="tpl-name">{{ row.templateName }}</span>
                <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </div>
              <div class="tpl-code">{{ row.templateCode }}</div>
              <div class="tpl-foot">
                <el-tag size="small" effect="plain" :type="dictItem(QUESTION_TYPE, row.questionType).type">
                  {{ dictItem(QUESTION_TYPE, row.questionType).label }}
                </el-tag>
                <span class="tpl-version">v{{ row.version }}</span>
              </div>
            </div>
          </div>

          <div class="pager">
            <el-pagination
              :current-page="query.pageNum"
              :page-size="query.pageSize"
              :total="total"
              layout="total, prev, pager, next"
              size="small"
              @size-change="handleSizeChange"
              @current-change="handlePageChange"
            />
          </div>
        </aside>

        <!-- 右栏：模板详情 / 提示词预览 -->
        <section class="detail">
          <template v-if="selectedTemplate">
            <div class="detail-head">
              <div class="detail-title-wrap">
                <h3>{{ selectedTemplate.templateName }}</h3>
                <div class="detail-tags">
                  <el-tag :type="selectedTemplate.status === 1 ? 'success' : 'info'">
                    {{ selectedTemplate.status === 1 ? '启用' : '停用' }}
                  </el-tag>
                  <el-tag type="primary" effect="plain">v{{ selectedTemplate.version }}</el-tag>
                  <span class="detail-code">{{ selectedTemplate.templateCode }}</span>
                </div>
              </div>
              <div class="detail-actions">
                <el-button
                  v-permission="'prompt:edit'"
                  type="primary"
                  plain
                  @click="openEdit(selectedTemplate)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="'prompt:delete'"
                  type="danger"
                  plain
                  @click="handleDelete(selectedTemplate)"
                >
                  删除
                </el-button>
              </div>
            </div>

            <el-descriptions :column="2" size="small" border class="meta">
              <el-descriptions-item label="适用题型">
                <el-tag
                  size="small"
                  effect="plain"
                  :type="dictItem(QUESTION_TYPE, selectedTemplate.questionType).type"
                >
                  {{ dictItem(QUESTION_TYPE, selectedTemplate.questionType).label }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="所属课程">
                {{ selectedTemplate.courseName || '全局通用' }}
              </el-descriptions-item>
              <el-descriptions-item label="模型">
                {{ selectedTemplate.modelName || '后端默认' }}
              </el-descriptions-item>
              <el-descriptions-item label="温度">
                {{ selectedTemplate.temperature ?? '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="备注" :span="2">
                {{ selectedTemplate.remark || '-' }}
              </el-descriptions-item>
            </el-descriptions>

            <div class="code-section">
              <div class="code-label">系统提示词</div>
              <pre class="code-block">{{ selectedTemplate.systemPrompt || '（未配置系统提示词）' }}</pre>
            </div>
            <div class="code-section">
              <div class="code-label">用户提示词</div>
              <pre class="code-block">{{ selectedTemplate.userPrompt || '（未配置用户提示词）' }}</pre>
            </div>
          </template>
          <el-empty v-else description="请选择左侧模板查看提示词详情" :image-size="90" />
        </section>
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑模板' : '新增模板'"
      width="760px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="模板编码" prop="templateCode">
              <el-input v-model="form.templateCode" placeholder="如 ESSAY_DEFAULT" :disabled="!!editingId" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模板名称" prop="templateName">
              <el-input v-model="form.templateName" placeholder="如 简答题通用评分" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
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
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属课程">
              <el-select v-model="form.courseId" clearable filterable placeholder="全局通用" style="width: 100%">
                <el-option
                  v-for="course in courses"
                  :key="course.id"
                  :label="course.courseName"
                  :value="course.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

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

        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="模型">
              <el-input v-model="form.modelName" placeholder="留空使用后端默认值" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="温度">
              <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="256" show-word-limit />
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

.workspace :deep(.el-card__body) {
  padding: 0;
}

/* 顶部紧凑统计条 */
.summary-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  background: var(--ai-gradient-soft);
  border-bottom: 1px solid var(--ai-border);
}

.summary-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 14px 5px 6px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 999px;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.summary-item.clickable {
  cursor: pointer;
}

.summary-item.clickable:hover {
  border-color: #c9d0ff;
  box-shadow: 0 4px 12px rgba(91, 108, 255, 0.12);
}

.summary-item.active {
  background: #eef1ff;
  border-color: var(--ai-primary);
  box-shadow: 0 0 0 2px rgba(91, 108, 255, 0.14);
}

.summary-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  flex-shrink: 0;
}

.summary-value {
  font-size: 16px;
  font-weight: 600;
  color: #2b2e45;
}

.summary-label {
  font-size: 12px;
  color: #8a90ad;
}

.prompt-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 20px;
  font-size: 12px;
  color: #6b72a0;
  background: #f9faff;
  border-bottom: 1px solid var(--ai-border);
}

.prompt-tip .el-icon {
  color: var(--ai-primary);
}

/* 左右分栏 */
.split {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 20px;
  padding: 16px 20px 20px;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  padding-right: 20px;
  border-right: 1px solid var(--ai-border);
}

.filters {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.filters :deep(.el-select) {
  width: 100%;
}

.filter-actions {
  display: flex;
  gap: 10px;
}

.filter-actions .el-button {
  flex: 1;
}

.side-empty {
  padding: 12px 0;
}

.tpl-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 520px;
  min-height: 160px;
  overflow-y: auto;
  padding-right: 4px;
}

.tpl-item {
  padding: 12px;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 10px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.tpl-item:hover {
  transform: translateY(-1px);
  border-color: #c9d0ff;
  box-shadow: 0 6px 16px rgba(91, 108, 255, 0.1);
}

.tpl-item.active {
  background: #f6f7ff;
  border-color: var(--ai-primary);
  box-shadow: 0 0 0 2px rgba(91, 108, 255, 0.14);
}

.tpl-item-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.tpl-name {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  color: #2b2e45;
  word-break: break-all;
}

.tpl-code {
  margin-top: 2px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #8a90ad;
  word-break: break-all;
}

.tpl-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.tpl-version {
  padding: 1px 6px;
  font-size: 12px;
  color: #6b72a0;
  background: #f2f3fb;
  border-radius: 6px;
}

.pager {
  display: flex;
  justify-content: center;
}

.pager :deep(.el-pagination) {
  flex-wrap: wrap;
  justify-content: center;
}

/* 右栏详情 */
.detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.detail-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.detail-title-wrap h3 {
  margin: 0 0 6px;
  font-size: 18px;
  color: #2b2e45;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.detail-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: #8a90ad;
}

.detail-actions {
  display: flex;
  gap: 10px;
}

.meta :deep(.el-descriptions__label) {
  width: 88px;
}

.code-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.code-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #2b2e45;
}

.code-label::before {
  width: 4px;
  height: 14px;
  content: '';
  border-radius: 2px;
  background: linear-gradient(180deg, var(--ai-primary), var(--ai-accent));
}

.code-block {
  max-height: 200px;
  margin: 0;
  padding: 14px 16px;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.7;
  color: #343a5c;
  white-space: pre-wrap;
  word-break: break-word;
  background: linear-gradient(135deg, #f8f9ff 0%, #f2f0ff 100%);
  border: 1px solid var(--ai-border);
  border-radius: 10px;
}

@media (max-width: 1100px) {
  .split {
    grid-template-columns: 1fr;
  }

  .side {
    padding-right: 0;
    padding-bottom: 16px;
    border-right: none;
    border-bottom: 1px solid var(--ai-border);
  }

  .tpl-list {
    max-height: 320px;
  }
}
</style>
