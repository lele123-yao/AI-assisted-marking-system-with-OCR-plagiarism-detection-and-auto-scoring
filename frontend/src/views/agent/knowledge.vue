<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteKnowledge, listKnowledge, reindexKnowledge, saveKnowledge, updateKnowledge } from '@/api/agent'
import { pageCourses } from '@/api/course'
import PageHero from '@/components/PageHero.vue'
import { QUESTION_TYPE, dictItem, formatTime } from '@/utils/dict'

/**
 * Agent 知识库管理。
 *
 * 数据流：文档入库 -> 可选 embedding 建向量 -> Agent 调 search_knowledge 混合检索。
 * 列表接口一次返回全部文档（不分页），因此筛选与分页在前端完成。
 */

const loading = ref(false)
const docs = ref([])
const courses = ref([])

const query = reactive({ courseId: '', questionType: '', keyword: '' })
const pageNum = ref(1)
const pageSize = ref(10)

const courseMap = computed(() =>
  Object.fromEntries(courses.value.map((course) => [course.id, course.courseName]))
)

/** 课程筛选：GLOBAL 表示全局文档，数字表示具体课程 */
const filteredDocs = computed(() => {
  const keyword = query.keyword.trim().toLowerCase()
  return docs.value.filter((doc) => {
    if (query.courseId === 'GLOBAL' && doc.courseId) return false
    if (typeof query.courseId === 'number' && doc.courseId !== query.courseId) return false
    if (query.questionType === 'GENERAL' && doc.questionType != null) return false
    if (typeof query.questionType === 'number' && doc.questionType !== query.questionType) return false
    if (keyword) {
      const text = `${doc.title || ''} ${doc.tags || ''} ${doc.content || ''}`.toLowerCase()
      if (!text.includes(keyword)) return false
    }
    return true
  })
})

const pagedDocs = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return filteredDocs.value.slice(start, start + pageSize.value)
})

const globalCount = computed(() => docs.value.filter((doc) => !doc.courseId).length)
const courseCount = computed(() => docs.value.filter((doc) => doc.courseId).length)
const typeCount = computed(
  () => new Set(docs.value.map((doc) => doc.questionType).filter((type) => type != null)).size
)

async function loadDocs() {
  loading.value = true
  try {
    docs.value = (await listKnowledge()) || []
  } catch {
    // 错误提示由 request.js 统一处理，这里只保证表格回到空态
    docs.value = []
  } finally {
    loading.value = false
  }
}

async function loadCourses() {
  const data = await pageCourses({ pageNum: 1, pageSize: 100 })
  courses.value = data?.records || []
}

watch(
  [() => query.courseId, () => query.questionType, () => query.keyword],
  () => {
    pageNum.value = 1
  }
)

function resetQuery() {
  query.courseId = ''
  query.questionType = ''
  query.keyword = ''
  pageNum.value = 1
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

const dialogVisible = ref(false)
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
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    courseId: row.courseId ?? null,
    title: row.title || '',
    tags: row.tags || '',
    questionType: row.questionType ?? null,
    content: row.content || ''
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
    dialogVisible.value = false
    await loadDocs()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除《${row.title}》吗？删除后该文档不再参与检索，对应向量也会一起清理。`,
      '删除知识文档',
      { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' }
    )
  } catch {
    return
  }
  await deleteKnowledge(row.id)
  ElMessage.success('删除成功')
  if (pagedDocs.value.length === 1 && pageNum.value > 1) {
    pageNum.value -= 1
  }
  await loadDocs()
}

/* ==================== 查看详情 ==================== */

const detailVisible = ref(false)
const currentDoc = ref(null)

function openDetail(row) {
  currentDoc.value = row
  detailVisible.value = true
}

/* ==================== 重建向量索引 ==================== */

const reindexing = ref(false)

async function handleReindex() {
  try {
    await ElMessageBox.confirm(
      '将重新为全部知识文档生成向量索引。文档较多时耗时较长；未配置 embedding 时不会报错，检索仍走纯 BM25。',
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
  <div class="page-container">
    <PageHero
      title="知识库管理"
      subtitle="维护 Agent 的课程知识（评分规范、知识点讲解、讲评建议）。文档会先按课程与题型过滤，再经 BM25 + 向量混合检索注入阅卷复核与教师助手；未配置 embedding 时自动降级为纯 BM25。"
      icon="Collection"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadDocs">刷新</el-button>
        <el-button type="warning" plain :loading="reindexing" @click="handleReindex">
          重建向量索引
        </el-button>
      </template>
    </PageHero>

    <div class="kb-layout">
      <!-- 左侧：概览 + 筛选 -->
      <aside class="kb-side">
        <div class="kb-overview">
          <div class="kb-total">
            <span class="kb-total-label">知识文档总数</span>
            <div class="kb-total-line">
              <strong>{{ docs.length }}</strong>
              <span>篇</span>
            </div>
          </div>
          <div class="kb-mini">
            <div class="mini-item">
              <b>{{ globalCount }}</b>
              <span>全局文档</span>
            </div>
            <div class="mini-item">
              <b>{{ courseCount }}</b>
              <span>课程专属</span>
            </div>
            <div class="mini-item">
              <b>{{ typeCount }}</b>
              <span>覆盖题型</span>
            </div>
          </div>
        </div>

        <el-card shadow="never" class="filter-card">
          <template #header>
            <span class="panel-title">筛选文档</span>
          </template>
          <el-form label-position="top" class="filter-form" @submit.prevent>
            <el-form-item label="关键词">
              <el-input
                v-model="query.keyword"
                placeholder="标题 / 标签 / 正文"
                clearable
              />
            </el-form-item>
            <el-form-item label="所属课程">
              <el-select v-model="query.courseId" placeholder="全部课程" clearable style="width: 100%">
                <el-option label="全局文档" value="GLOBAL" />
                <el-option
                  v-for="course in courses"
                  :key="course.id"
                  :label="course.courseName"
                  :value="course.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="适用题型">
              <el-select v-model="query.questionType" placeholder="全部题型" clearable style="width: 100%">
                <el-option label="通用" value="GENERAL" />
                <el-option
                  v-for="(item, key) in QUESTION_TYPE"
                  :key="key"
                  v-show="Number(key) > 0"
                  :label="item.label"
                  :value="Number(key)"
                />
              </el-select>
            </el-form-item>
            <div class="filter-actions">
              <el-button @click="resetQuery">重置</el-button>
              <el-button type="primary" @click="openCreate">新增文档</el-button>
            </div>
          </el-form>
        </el-card>

        <div class="kb-tip">
          <el-icon><InfoFilled /></el-icon>
          <p>检索规则：本课程 + 全局、本题型 + 通用；两路召回各取前 10，RRF 融合后取 Top3 注入 Agent。</p>
        </div>
      </aside>

      <!-- 右侧：文档卡片墙 -->
      <section class="kb-main">
        <div class="kb-head">
          <div class="kb-head-text">
            <span class="kb-title">知识文档</span>
            <span class="kb-sub">共 {{ filteredDocs.length }} 篇 · 第 {{ pageNum }} 页</span>
          </div>
          <el-button :loading="loading" plain @click="loadDocs">刷新</el-button>
        </div>

        <div v-loading="loading" class="doc-grid">
          <article
            v-for="doc in pagedDocs"
            :key="doc.id"
            class="doc-card"
            @click="openDetail(doc)"
          >
            <div class="doc-top">
              <div class="doc-icon"><el-icon :size="20"><Document /></el-icon></div>
              <div class="doc-heading">
                <h4 class="doc-title">{{ doc.title }}</h4>
                <div class="doc-badges">
                  <el-tag :type="doc.courseId ? 'primary' : 'success'" effect="plain" size="small">
                    {{ courseLabel(doc.courseId) }}
                  </el-tag>
                  <el-tag :type="typeTag(doc.questionType)" effect="plain" size="small">
                    {{ typeLabel(doc.questionType) }}
                  </el-tag>
                </div>
              </div>
            </div>

            <p class="doc-excerpt">{{ doc.content }}</p>

            <div v-if="splitTags(doc.tags).length" class="doc-tags">
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

            <div class="doc-foot">
              <span class="doc-time">{{ formatTime(doc.updateTime || doc.createTime) }}</span>
              <div class="doc-actions">
                <el-button link type="primary" @click.stop="openEdit(doc)">编辑</el-button>
                <el-button link type="danger" @click.stop="handleDelete(doc)">删除</el-button>
              </div>
            </div>
          </article>

          <el-empty
            v-if="!loading && !pagedDocs.length"
            class="doc-empty"
            description="暂无知识文档，可在左侧新增"
            :image-size="90"
          />
        </div>

        <div class="pager">
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="filteredDocs.length"
            layout="total, sizes, prev, pager, next, jumper"
          />
        </div>
      </section>
    </div>

    <!-- 新增 / 编辑文档 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑知识文档' : '新增知识文档'"
      width="720px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
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
          <el-col :span="12">
            <el-form-item label="适用题型">
              <el-select v-model="form.questionType" clearable placeholder="通用" style="width: 100%">
                <el-option
                  v-for="(item, key) in QUESTION_TYPE"
                  :key="key"
                  v-show="Number(key) > 0"
                  :label="item.label"
                  :value="Number(key)"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

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
            :rows="10"
            maxlength="5000"
            show-word-limit
            placeholder="填写评分细则、知识点讲解或讲评建议；这段正文会作为整篇文档生成向量"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 查看详情 -->
    <el-drawer v-model="detailVisible" :title="currentDoc?.title || '知识文档'" size="620px">
      <template v-if="currentDoc">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="所属课程">
            {{ courseLabel(currentDoc.courseId) }}
          </el-descriptions-item>
          <el-descriptions-item label="适用题型">
            {{ typeLabel(currentDoc.questionType) }}
          </el-descriptions-item>
          <el-descriptions-item label="标签">
            <template v-if="splitTags(currentDoc.tags).length">
              <el-tag
                v-for="tag in splitTags(currentDoc.tags)"
                :key="tag"
                size="small"
                type="info"
                effect="plain"
                class="tag-gap"
              >
                {{ tag }}
              </el-tag>
            </template>
            <span v-else class="text-muted">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ formatTime(currentDoc.updateTime || currentDoc.createTime) }}
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="detail-title">正文</h4>
        <pre class="doc-content">{{ currentDoc.content }}</pre>

        <el-alert type="info" :closable="false" show-icon class="mt-16">
          <template #title>
            该文档作为一个整体参与检索：索引文本 = 标题 + 标签 + 正文，不切分 chunk。
          </template>
        </el-alert>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 左侧概览 + 筛选 / 右侧卡片墙 */
.kb-layout {
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.kb-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.kb-overview {
  padding: 20px;
  color: #fff;
  border: 1px solid rgba(140, 156, 255, 0.18);
  border-radius: 14px;
  background: var(--ai-gradient);
  box-shadow: 0 12px 28px rgba(30, 30, 100, 0.18);
}

.kb-total-label {
  font-size: 12px;
  letter-spacing: 1px;
  color: rgba(200, 208, 245, 0.7);
}

.kb-total-line {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 8px;
}

.kb-total-line strong {
  font-size: 34px;
  font-weight: 700;
  line-height: 1;
}

.kb-total-line span {
  font-size: 13px;
  color: rgba(200, 208, 245, 0.7);
}

.kb-mini {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding-top: 14px;
  margin-top: 18px;
  border-top: 1px solid rgba(140, 156, 255, 0.16);
}

.mini-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mini-item b {
  font-size: 16px;
}

.mini-item span {
  font-size: 11px;
  color: rgba(200, 208, 245, 0.6);
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #2b2e45;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.filter-form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  font-size: 13px;
  color: #7d829c;
}

.filter-actions {
  display: flex;
  gap: 10px;
}

.filter-actions .el-button {
  flex: 1;
  margin-left: 0;
}

.kb-tip {
  display: flex;
  gap: 10px;
  padding: 14px 16px;
  font-size: 12px;
  line-height: 1.7;
  color: #7d829c;
  background: #f5f7ff;
  border: 1px dashed #d8def5;
  border-radius: 12px;
}

.kb-tip .el-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--ai-primary);
}

.kb-tip p {
  margin: 0;
}

.kb-main {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.kb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.kb-head-text {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.kb-title {
  font-size: 16px;
  font-weight: 600;
  color: #2b2e45;
}

.kb-sub {
  font-size: 12px;
  color: #9aa0b8;
}

.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
  min-height: 220px;
}

.doc-card {
  display: flex;
  flex-direction: column;
  padding: 18px 18px 14px;
  cursor: pointer;
  background: #fff;
  border: 1px solid var(--ai-border);
  border-radius: 14px;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.doc-card:hover {
  border-color: #d5dcfa;
  box-shadow: 0 14px 30px rgba(44, 52, 120, 0.1);
  transform: translateY(-3px);
}

.doc-top {
  display: flex;
  gap: 12px;
}

.doc-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  color: var(--ai-primary);
  background: #eef1ff;
  border-radius: 11px;
}

.doc-heading {
  min-width: 0;
}

.doc-title {
  margin: 0 0 6px;
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  color: #2b2e45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.doc-excerpt {
  display: -webkit-box;
  overflow: hidden;
  margin: 14px 0 10px;
  font-size: 13px;
  line-height: 1.75;
  color: #6b7089;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.doc-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.doc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  margin-top: auto;
  border-top: 1px dashed #eef1fa;
}

.doc-time {
  font-size: 12px;
  color: #a2a7bd;
}

.doc-actions {
  display: flex;
}

.doc-actions .el-button {
  margin-left: 10px;
}

.doc-empty {
  grid-column: 1 / -1;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

@media (max-width: 1080px) {
  .kb-layout {
    grid-template-columns: 1fr;
  }
}

.mt-16 {
  margin-top: 16px;
}

.tag-gap {
  margin-right: 6px;
  margin-bottom: 4px;
}

.text-muted {
  color: #909399;
}

.detail-title {
  margin: 20px 0 10px;
  font-size: 14px;
  color: #303133;
}

.doc-content {
  margin: 0;
  padding: 14px 16px;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.75;
  color: #303133;
  background: #f7f9fc;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}
</style>
