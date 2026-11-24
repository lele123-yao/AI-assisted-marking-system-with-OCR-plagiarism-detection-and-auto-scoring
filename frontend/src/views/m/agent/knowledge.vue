<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteKnowledge,
  listKnowledge,
  reindexKnowledge,
  saveKnowledge,
  updateKnowledge
} from '@/api/agent'
import { pageCourses } from '@/api/course'
import { QUESTION_TYPE, dictItem, formatTime } from '@/utils/dict'
import MobileHero from '@/components/m/MobileHero.vue'
import MobileSearchBar from '@/components/m/MobileSearchBar.vue'
import MobileEmpty from '@/components/m/MobileEmpty.vue'
import MobileLoadMore from '@/components/m/MobileLoadMore.vue'
import MobileSheet from '@/components/m/MobileSheet.vue'

/**
 * 移动端 Agent 知识库管理。
 * 列表接口一次返回全部文档，课程筛选走服务端 courseId，关键词与加载更多在前端完成。
 */
const loading = ref(false)
const docs = ref([])
const courses = ref([])
const query = reactive({ courseId: '', keyword: '' })

const visibleCount = ref(10)
const expandedId = ref(null)

const questionTypes = computed(() =>
  Object.keys(QUESTION_TYPE)
    .map((key) => ({ value: Number(key), label: QUESTION_TYPE[key].label }))
    .filter((item) => item.value > 0)
)

const courseMap = computed(() =>
  Object.fromEntries(courses.value.map((course) => [course.id, course.courseName]))
)

const filteredDocs = computed(() => {
  const keyword = query.keyword.trim().toLowerCase()
  if (!keyword) return docs.value
  return docs.value.filter((doc) =>
    `${doc.title || ''} ${doc.tags || ''} ${doc.content || ''}`.toLowerCase().includes(keyword)
  )
})

const visibleDocs = computed(() => filteredDocs.value.slice(0, visibleCount.value))
const finished = computed(() => visibleCount.value >= filteredDocs.value.length)

async function loadDocs() {
  loading.value = true
  try {
    // GLOBAL 是前端的“全局文档”筛选项，后端不接收该值
    const selected = query.courseId
    const courseId = selected === 'GLOBAL' ? undefined : selected || undefined
    const rows = (await listKnowledge(courseId)) || []
    docs.value = selected === 'GLOBAL' ? rows.filter((doc) => !doc.courseId) : rows
  } catch {
    docs.value = []
  } finally {
    loading.value = false
  }
}

async function loadCourses() {
  const data = await pageCourses({ pageNum: 1, pageSize: 50 })
  courses.value = data?.records || []
}

function applyFilter() {
  visibleCount.value = 10
  loadDocs()
}

function resetQuery() {
  query.courseId = ''
  query.keyword = ''
  applyFilter()
}

function loadMore() {
  visibleCount.value += 10
}

function toggleExcerpt(doc) {
  expandedId.value = expandedId.value === doc.id ? null : doc.id
}

function courseLabel(courseId) {
  if (!courseId) return '全局'
  return courseMap.value[courseId] || `课程#${courseId}`
}

function typeLabel(questionType) {
  return questionType == null ? '通用' : dictItem(QUESTION_TYPE, questionType).label
}

function typeTag(questionType) {
  return questionType == null ? 'info' : dictItem(QUESTION_TYPE, questionType).type
}

function splitTags(tags) {
  return (tags || '')
    .split(/[,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

/* ==================== 新增 / 编辑文档 ==================== */

const sheetVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({
  courseId: null,
  title: '',
  tags: '',
  questionType: null,
  content: ''
})
const rules = {
  title: [
    { required: true, message: '请输入文档标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过 100 个字符', trigger: 'blur' }
  ],
  content: [{ required: true, message: '请输入文档正文', trigger: 'blur' }]
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { courseId: null, title: '', tags: '', questionType: null, content: '' })
  sheetVisible.value = true
}

function openEdit(doc) {
  editingId.value = doc.id
  Object.assign(form, {
    courseId: doc.courseId ?? null,
    title: doc.title || '',
    tags: doc.tags || '',
    questionType: doc.questionType ?? null,
    content: doc.content || ''
  })
  sheetVisible.value = true
}

async function submit() {
  if (submitting.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      courseId: form.courseId,
      title: form.title.trim(),
      tags: form.tags.trim() || null,
      questionType: form.questionType ?? null,
      content: form.content.trim()
    }
    if (editingId.value) {
      await updateKnowledge(editingId.value, payload)
      ElMessage.success('修改成功，向量索引已同步')
    } else {
      await saveKnowledge(payload)
      ElMessage.success('保存成功，已参与后续检索')
    }
    sheetVisible.value = false
    await loadDocs()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(doc) {
  try {
    await ElMessageBox.confirm(
      `确定删除《${doc.title}》吗？删除后该文档不再参与检索，对应向量也会一起清理。`,
      '删除知识文档',
      { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' }
    )
  } catch {
    return
  }
  await deleteKnowledge(doc.id)
  ElMessage.success('删除成功')
  await loadDocs()
}

/* ==================== 重建向量索引 ==================== */

const reindexing = ref(false)

async function handleReindex() {
  try {
    await ElMessageBox.confirm(
      '将重新为全部知识文档生成向量索引。未配置 embedding 时不会报错，检索仍走纯 BM25。',
      '重建向量索引',
      { type: 'warning', confirmButtonText: '开始重建' }
    )
  } catch {
    return
  }

  reindexing.value = true
  try {
    const count = await reindexKnowledge()
    if (Number(count) > 0) {
      ElMessage.success(`重建完成，成功索引 ${count} 篇文档`)
    } else {
      ElMessage.warning('重建完成，成功 0 篇：可能未配置 embedding，当前检索走纯 BM25')
    }
  } finally {
    reindexing.value = false
  }
}

onMounted(() => {
  loadDocs()
  loadCourses()
})
</script>

<template>
  <div class="m-page">
    <MobileHero
      title="知识库管理"
      subtitle="维护评分规范、知识点讲解与讲评建议，供 Agent 混合检索"
      icon="Collection"
    >
      <template #actions>
        <el-button size="small" plain :loading="loading" @click="loadDocs">刷新</el-button>
        <el-button size="small" type="primary" @click="openCreate">新增</el-button>
      </template>
    </MobileHero>

    <MobileSearchBar
      v-model="query.keyword"
      placeholder="搜索标题 / 标签 / 正文"
      :loading="loading"
      show-reset
      @search="applyFilter"
      @reset="resetQuery"
    >
      <el-select
        v-model="query.courseId"
        placeholder="全部课程"
        clearable
        class="course-select"
        @change="applyFilter"
      >
        <el-option label="全局文档" value="GLOBAL" />
        <el-option
          v-for="course in courses"
          :key="course.id"
          :label="course.courseName"
          :value="course.id"
        />
      </el-select>
    </MobileSearchBar>

    <section class="m-card">
      <div class="m-row-between">
        <div class="m-col reindex-text">
          <span class="m-title">向量索引</span>
          <span class="m-sub">未配置 embedding 时返回 0 篇，检索仍走纯 BM25</span>
        </div>
        <el-button type="warning" plain size="small" :loading="reindexing" @click="handleReindex">
          重建
        </el-button>
      </div>
    </section>

    <div v-loading="loading" class="doc-list">
      <article v-for="doc in visibleDocs" :key="doc.id" class="m-card doc">
        <div class="doc-head">
          <span class="doc-icon"><el-icon><Document /></el-icon></span>
          <div class="doc-heading">
            <h3 class="doc-title">{{ doc.title }}</h3>
            <div class="doc-tags">
              <el-tag :type="doc.courseId ? 'primary' : 'success'" size="small" effect="plain">
                {{ courseLabel(doc.courseId) }}
              </el-tag>
              <el-tag :type="typeTag(doc.questionType)" size="small" effect="plain">
                {{ typeLabel(doc.questionType) }}
              </el-tag>
            </div>
          </div>
        </div>

        <p
          class="doc-excerpt"
          :class="{ expanded: expandedId === doc.id }"
          @click="toggleExcerpt(doc)"
        >
          {{ doc.content }}
        </p>
        <button v-if="doc.content" type="button" class="excerpt-toggle" @click="toggleExcerpt(doc)">
          {{ expandedId === doc.id ? '收起' : '展开' }}
        </button>

        <div v-if="splitTags(doc.tags).length" class="doc-tags tag-row">
          <el-tag
            v-for="tag in splitTags(doc.tags)"
            :key="tag"
            size="small"
            type="info"
            effect="plain"
          >
            {{ tag }}
          </el-tag>
        </div>

        <div class="m-row-between doc-foot">
          <span class="m-sub">{{ formatTime(doc.updateTime || doc.createTime) }}</span>
          <div class="doc-actions">
            <el-button link type="primary" @click="openEdit(doc)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(doc)">删除</el-button>
          </div>
        </div>
      </article>

      <MobileEmpty
        v-if="!loading && !filteredDocs.length"
        description="暂无知识文档，可点击新增"
        icon="Collection"
        action-text="新增文档"
        @action="openCreate"
      />

      <MobileLoadMore
        :loading="false"
        :finished="finished && filteredDocs.length > 0"
        :empty="!filteredDocs.length"
        @load="loadMore"
      />
    </div>

    <MobileSheet
      v-model="sheetVisible"
      :title="editingId ? '编辑知识文档' : '新增知识文档'"
      height="86%"
      confirm-text="保存"
      :loading="submitting"
      @confirm="submit"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="doc-form">
        <el-form-item label="所属课程">
          <el-select v-model="form.courseId" clearable filterable placeholder="全局通用">
            <el-option
              v-for="course in courses"
              :key="course.id"
              :label="course.courseName"
              :value="course.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="适用题型">
          <el-select v-model="form.questionType" clearable placeholder="通用">
            <el-option
              v-for="item in questionTypes"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="如 简答题评分规范" />
        </el-form-item>

        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="逗号分隔，如 评分规范,简答题" />
        </el-form-item>

        <el-form-item label="正文" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            maxlength="5000"
            show-word-limit
            placeholder="填写评分细则、知识点讲解或讲评建议"
          />
        </el-form-item>
      </el-form>
    </MobileSheet>
  </div>
</template>

<style scoped>
.course-select {
  width: 100%;
}

.reindex-text {
  gap: 3px;
}

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 80px;
}

.doc-head {
  display: flex;
  gap: 10px;
}

.doc-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  color: var(--ai-primary);
  background: #eef1ff;
  border-radius: 10px;
}

.doc-heading {
  min-width: 0;
}

.doc-title {
  margin: 0 0 6px;
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  color: #23264a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.doc-excerpt {
  display: -webkit-box;
  overflow: hidden;
  margin: 12px 0 0;
  font-size: 13px;
  line-height: 1.75;
  color: #5c6180;
  white-space: pre-wrap;
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.doc-excerpt.expanded {
  display: block;
  max-height: 50vh;
  overflow-y: auto;
}

.excerpt-toggle {
  padding: 0;
  margin-top: 4px;
  font-size: 12px;
  color: var(--ai-primary);
  background: transparent;
  border: none;
}

.tag-row {
  margin-top: 8px;
}

.doc-foot {
  padding-top: 10px;
  margin-top: 10px;
  border-top: 1px dashed #f0f2fb;
}

.doc-actions {
  display: flex;
}

.doc-actions .el-button {
  margin-left: 10px;
}

.doc-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.doc-form :deep(.el-select) {
  width: 100%;
}
</style>
