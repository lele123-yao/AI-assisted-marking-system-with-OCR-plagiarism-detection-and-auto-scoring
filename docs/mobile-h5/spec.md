# 移动端 H5（微信可访问）改造规格

## 1. 目标与范围

把现有「高校主观题智能异步阅卷系统」前端改造成在微信内置浏览器中可直接打开的 H5 应用，移动端覆盖全部菜单功能，桌面端保持现状不改。

已确认的决策：
- 形态：H5 移动端网页（同一 Vue 应用内新增移动端路由树），不做微信小程序，不做公众号授权登录。
- 范围：全部功能都搬，包括学生端、教师端、管理员端全部菜单。
- 后端：不改接口，仅前端适配；开发环境继续走 Vite 代理 `/api -> http://localhost:8080`。
- 入口：同一域名，微信/移动浏览器 UA 自动进入 `/m/**`；桌面浏览器仍进入原桌面布局。页面提供「电脑版 / 手机版」切换。

不在本次范围：
- 微信小程序（AppID、HTTPS 备案域名、Taro 重构）。
- 微信 OAuth 静默登录、模板消息、支付。
- 后端 CORS、网关、Nginx 配置的实际部署操作（只在文档中给出配置示例）。
- 原生 App 打包。

## 2. 总体架构

### 2.1 路由

在 `frontend/src/router/index.js` 中新增 `/m` 路由树，挂载新的 `MobileLayout`，与现有桌面路由并存。

移动端路由与页面文件一一对应（全部懒加载）：

| 路由 | 页面文件 | 对应桌面功能 |
| --- | --- | --- |
| `/m/login` | `views/m/login.vue` | 登录 |
| `/m/register` | `views/m/register.vue` | 注册 |
| `/m/home` | `views/m/home.vue` | 首页工作台 |
| `/m/menu` | `views/m/menu.vue` | 全部功能入口 |
| `/m/profile` | `views/m/profile.vue` | 我的（账号、切换、退出） |
| `/m/course` | `views/m/course/index.vue` | 课程管理 |
| `/m/class` | `views/m/class/index.vue` | 班级管理 |
| `/m/paper` | `views/m/paper/index.vue` | 试卷管理 |
| `/m/question` | `views/m/question/index.vue` | 主观题目 |
| `/m/prompt` | `views/m/prompt/index.vue` | Prompt 模板 |
| `/m/answer/submit` | `views/m/answer/submit.vue` | 在线作答 |
| `/m/answer/list` | `views/m/answer/list.vue` | 答卷列表 |
| `/m/answer/detail/:id` | `views/m/answer/detail.vue` | 答卷详情（隐藏路由） |
| `/m/student/papers` | `views/m/student/papers.vue` | 我的试卷 |
| `/m/student/scores` | `views/m/student/scores.vue` | 我的成绩 |
| `/m/paper-answer/roster` | `views/m/paper-answer/roster.vue` | 名单导入 |
| `/m/paper-answer/upload` | `views/m/paper-answer/upload.vue` | 图片上传/OCR |
| `/m/paper-answer/score` | `views/m/paper-answer/score.vue` | 教师录分 |
| `/m/mark/task` | `views/m/mark/task.vue` | AI 异步阅卷任务监控 |
| `/m/mark/detail/:id` | `views/m/mark/detail.vue` | 阅卷详情（隐藏路由） |
| `/m/agent/knowledge` | `views/m/agent/knowledge.vue` | 知识库管理 |
| `/m/plagiarism/compare` | `views/m/plagiarism/compare.vue` | 相似度比对 |
| `/m/plagiarism/report` | `views/m/plagiarism/report.vue` | 抄袭报告 |
| `/m/grade/review` | `views/m/grade/review.vue` | 成绩复核 |
| `/m/grade/statistics` | `views/m/grade/statistics.vue` | 成绩统计 |
| `/m/grade/export` | `views/m/grade/export.vue` | 成绩导出 |
| `/m/learning/report` | `views/m/learning/report.vue` | 学情报表 |
| `/m/learning/knowledge` | `views/m/learning/knowledge.vue` | 知识点得分率 |
| `/m/learning/advice` | `views/m/learning/advice.vue` | AI 讲评建议 |
| `/m/learning/ppt` | `views/m/learning/ppt.vue` | PPT 生成下载 |
| `/m/llm/setting` | `views/m/setting/llm.vue` | 大模型设置 |
| `/m/system/user` | `views/m/system/user.vue` | 用户管理 |
| `/m/system/role` | `views/m/system/role.vue` | 角色管理 |
| `/m/system/menu` | `views/m/system/menu.vue` | 菜单权限（只读矩阵） |
| `/m/403`、`/m/404` | `views/m/error/403.vue`、`404.vue` | 错误页 |

路由 meta 与桌面保持一致：`title`、`permission`、`hidden`。`/m` 根路径重定向到 `/m/home`。

### 2.2 设备识别与跳转

新增 `frontend/src/utils/device.js`：

```js
export function isWeChat() // UA 含 MicroMessenger
export function isMobileDevice() // UA 含 Android|iPhone|iPad|iPod|Mobile|HarmonyOS，或宽度 <= 768
export function preferDesktop() // localStorage.PREFER_DESKTOP === '1'
export function setPreferDesktop(value)
```

`router.beforeEach` 新增逻辑（在登录守卫之前）：
1. 若 `isMobileDevice() && !preferDesktop()` 且目标路径不以 `/m` 开头：重定向 `/m` + 原路径（`/` -> `/m/home`）。
2. 若 `!isMobileDevice() && 目标路径以 /m 开头`：允许访问（方便桌面调试），不强制跳回。
3. 移动端未登录访问非公开页：跳 `/m/login?redirect=...`。
4. 移动端权限不足：跳 `/m/403`。
5. 登录页在移动端若访问 `/login` 已由第 1 条转到 `/m/login`。

`api/request.js` 的 `redirectToLogin()` 改为：
- 移动端跳 `/m/login?redirect=...`，桌面端保持 `/login?redirect=...`。
- 同时清除 Pinia 用户态：`window.location.href` 跳转会刷新页面，`clearAuth()` 已足够。

「切换版本」：
- 移动端「我的」页提供「使用电脑版」：`setPreferDesktop(true)` 后 `location.href = '/'`。
- 桌面端 NavBar 下拉菜单增加「手机版」：`setPreferDesktop(false)` 后 `location.href = '/m/home'`。

### 2.3 移动端布局

新增 `frontend/src/layout/mobile/MobileLayout.vue`：
- 结构：`<header class="m-nav">` + `<main class="m-main"><router-view/></main>` + `<nav class="m-tabbar">`。
- 顶部栏：返回按钮（非一级页显示，`router.back()`）、页面标题（`route.meta.title`）、右侧「全部」图标按钮（打开菜单抽屉）。
- 底部 TabBar 4 项，按权限过滤：
  - 首页 `/m/home`（`dashboard:view`）
  - 学习 `/m/student/papers`（`student:paper`）或 任务 `/m/mark/task`（教师/管理员，`mark:view`）二选一
  - 全部 `/m/menu`
  - 我的 `/m/profile`
- 菜单抽屉：完整复用 `config/menu.js` 的 `menuTree`，按 `meta.permission` 过滤，分组折叠展示；点击跳 `/m` + 桌面 path。
- 微信环境检测：`isWeChat()` 时在内容区顶部显示一条可关闭提示「如需下载文件，请点击右上角 ··· 选择在浏览器中打开」。
- 安全区：`padding-bottom: env(safe-area-inset-bottom)`，顶部 `env(safe-area-inset-top)`。
- 路由切换不保留滚动位置（scrollBehavior 仅对 /m 返回顶部）。

### 2.4 共享组件与样式

新增 `frontend/src/components/m/`：
- `MobileHero.vue`：标题、副标题、右侧插槽、可选渐变底色。
- `MobileCard.vue`：统一白卡容器（圆角、边框、内边距）。
- `MobileEmpty.vue`：空态（图标 + 文案 + 可选按钮）。
- `MobileSheet.vue`：底部弹层表单容器，封装 `el-drawer direction="btt"`，props：`modelValue`、`title`、`height`；footer 插槽固定「取消 / 确定」。
- `MobileSearchBar.vue`：搜索输入 + 筛选插槽 + 查询/重置。
- `MobileLoadMore.vue`：加载更多按钮（`loading`、`finished`）。
- `MobileStatGrid.vue`：2 列指标卡（label/value/tone/icon）。

新增 `frontend/src/composables/useMobileList.js`：
- 基于 `pageNum/pageSize` 的分页加载，返回 `list/total/loading/hasMore/query/load/refresh/loadMore/search/reset`。
- 追加模式：`loadMore` 将新页 `records` 拼接到 `list`。
- 默认 `pageSize = 10`。

新增 `frontend/src/styles/mobile.css`，在 `main.js` 引入：
- Element Plus 移动端覆盖：输入框/选择框字号 16px（防 iOS 聚焦缩放）、`el-dialog` 宽度 92%、`el-message-box` 宽度 88%、表格横向滚动、`el-button` 最小高度 36px、`el-tag` 字号 12px。
- `.m-page`（页面内边距 16px + 安全区）、`.m-card`、`.m-row`、`.m-chip`、`.m-field`、`.m-actions`、`.m-divider`、`.m-title`、`.m-sub`、`.m-tag-*` 等工具类。
- 图表容器在窄屏下的最小高度。
- 禁用 `-webkit-tap-highlight-color`。

`index.html` 的 viewport 改为：
```html
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover" />
<meta name="theme-color" content="#171a4a" />
<meta name="apple-mobile-web-app-capable" content="yes" />
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
```

### 2.5 移动端交互约定

- 列表：卡片式，不做固定列宽表格；分页改「加载更多」。
- 表单：底部弹层 `MobileSheet`，字段单列，label 在输入框上方；必填校验沿用 el-form rules。
- 危险操作：`ElMessageBox.confirm`，文案明确对象名。
- 长文本（OCR 全文、评语）：默认折叠 6 行，点击「展开」。
- 图表：ECharts 复用现有 `components/EChart.vue`，`height` 传 220~260。
- 文件上传：`<input type="file" accept="image/*" capture="environment">` 或现有 `FileUploader`，移动端优先调起相机/相册。
- 下载：Excel / PPT 在微信内先提示「在浏览器打开」，再调用现有 `downloadBlob` / `downloadByUrl`。

## 3. 各页面实现要求

所有页面均使用 `<script setup>`，复用现有 `api/*` 与 `utils/dict.js`，不新增后端接口。以下只列关键数据源与交互，UI 统一采用「MobileHero + 卡片列表 + 底部弹层」。

### 3.1 登录 / 注册

- `views/m/login.vue`：深色渐变头部 + 白色圆角表单卡；用户名、密码、登录按钮；调用 `useUserStore().login`；按 `route.query.redirect` 或角色跳转：学生 `/m/student/papers`，教师/管理员 `/m/home`；底部「注册账号」「使用电脑版」。
- `views/m/register.vue`：复用现有注册接口（若有）；若无注册 API，则提供「请联系管理员开通」说明并链接回登录。实现前先读 `views/login/Register.vue` 确认真实接口，不臆造。

### 3.2 首页

- `views/m/home.vue`：问候语、角色标签、今日日期；教师端 4 个指标（待阅卷、已阅卷、我的试卷、待复核）来自 `pagePaperAnswers` 与 `pagePapers`，学生端 2 个指标来自 `myPapers`/`myScores`；下方快捷入口宫格（按权限显示）；单接口失败仅该项显示 `-`。

### 3.3 我的

- `views/m/profile.vue`：头像、昵称、用户名、角色、邮箱；菜单项：我的试卷、我的成绩、消息（占位不显示）、大模型设置（有权限时）、全部功能、使用电脑版、退出登录。
- 退出：清空 store 后跳 `/m/login`。

### 3.4 学生端

- `views/m/student/papers.vue`：`myPapers()`；顶部状态筛选芯片（全部/待作答/已提交/线下）；卡片显示课程、试卷名、编号、满分、截止时间；「去作答」跳 `/m/answer/submit?paperId=`。
- `views/m/student/scores.vue`：`myScores()`；顶部平均得分率环形 + 平均分/最高分/及格率；成绩记录卡片；未出分显示「等待阅卷」。
- `views/m/answer/submit.vue`：读取 `paperId`，用 `listQuestionsByPaper` 拉题目；题号横向滚动导航；单题卡片作答；底部吸底进度与提交按钮；提交调用 `submitOnlineAnswer`，`answerContent` 为 `{"1":"..."}` JSON 字符串；提交前校验未答题数量并二次确认。
- `views/m/answer/list.vue`：`pagePaperAnswers` 分页；按 `markingStatus` 筛选芯片；卡片跳 `/m/answer/detail/:id`。
- `views/m/answer/detail.vue`：`getPaperAnswerDetail`；展示考生信息、状态、各题得分与评语、OCR 文本（折叠）；提供「进入复核」入口（有 `grade:review` 权限时）。

### 3.5 教学资源

- `views/m/course/index.vue`：`pageCourses`；搜索 + 状态筛选；卡片显示编码/名称/教师/学期/学分；新增/编辑底部弹层；归档确认。管理员可选教师（`pageUsers` 过滤 TEACHER/ADMIN），教师默认本人。
- `views/m/class/index.vue`：`pageClasses`；班级卡片（名称、年级、人数）；新增/编辑/删除；班级详情抽屉内 `listClassStudents`，可 `addClassStudents`、`removeClassStudent`、`importClassStudents`（Excel）。
- `views/m/paper/index.vue`：`pagePapers`；卡片显示试卷名、编号、课程、总分、状态；操作：新增/编辑（草稿）、改状态（`changePaperStatus`）、下发（`assignPaper`）、查看题目（跳 `/m/question?paperId=`）、上传解析（`parsePaperFiles`，相册/文件多选，180s 超时提示）。
- `views/m/question/index.vue`：先选试卷（`pagePapers` 前 50 条）或读取 `paperId` 查询参数；`listQuestionsByPaper`；题目卡片（题号、题型、分值、题干摘要、知识点）；新增/编辑/删除；知识点维护调用 `assignKnowledge`。
- `views/m/prompt/index.vue`：`pageTemplates`；模板卡片（名称、题型、版本、启用状态）；新增/编辑/删除；模板内容长文本折叠。

### 3.6 纸质辅助批改与 AI 阅卷

- `views/m/paper-answer/roster.vue`：选择试卷 + 上传 Excel（`importRoster`）；展示导入结果统计；支持填写默认班级。
- `views/m/paper-answer/upload.vue`：选择试卷 + 学号/姓名 + 拍照/相册上传（`uploadPaperImage`）；上传成功后提示可触发 OCR；批量队列展示每份状态；`triggerOcr` 手动触发。
- `views/m/paper-answer/score.vue`：`pagePaperAnswers` 队列（`markingStatus=3` 优先）；右侧/下方逐题评分卡，`listQuestionsByPaper` 取题；输入每题得分，底部吸底合计；`saveTeacherScore` 保存 `items` 与 `teacherFinalScore`；校验分数不超过满分。
- `views/m/mark/task.vue`：`pageMarkingTasks`（即 `pagePaperAnswers`）；状态筛选（待录入/OCR中/AI批改中/待确认/失败/已归档）；卡片显示考生、试卷、状态、更新时间；点击跳 `/m/mark/detail/:id`；支持下拉刷新与加载更多。
- `views/m/mark/detail.vue`：`getMarkingTaskDetail`；展示状态流转、OCR 文本、各题 AI 分与评语、失败原因；提供「重新触发 OCR」「去确认分数」入口。
- `views/m/agent/knowledge.vue`：`listKnowledge`；文档卡片（标题、课程、更新时间）；新增/编辑/删除；「重建向量索引」`reindexKnowledge`；未配置 embedding 时提示仍可 BM25 检索。

### 3.7 抄袭检测

- `views/m/plagiarism/compare.vue`：选择试卷；`generateFingerprints` + `generateReport`；展示高风险份数、相似卷对列表、相似度进度条；支持单份 `getAnswerReport` 查看。
- `views/m/plagiarism/report.vue`：选择试卷读取 `getReport`；风险总览（平均相似度、高风险数、最高相似对）；疑似卷对卡片；点击展开明细（相同片段/相似度）。

### 3.8 成绩与学情

- `views/m/grade/review.vue`：`pagePaperAnswers` 中 `markingStatus=3`；筛选待复核；卡片进入复核弹层：通过 / 改分 / 驳回，`reviewGrade` 提交 `answerId/reviewAction/finalScore/reviewRemark`；改分时校验分数区间。
- `views/m/grade/statistics.vue`：选择试卷后 `getGradeStatistics`；顶部指标（平均分、最高、最低、及格率）；分数段柱状图、逐题得分率（ECharts，高 240）；薄弱题目列表。
- `views/m/grade/export.vue`：选择试卷；展示可导出信息；点击导出调用 `exportGrades` + `downloadBlob`；微信内先提示用浏览器打开。
- `views/m/learning/report.vue`：`getLearningStatistics`；整体指标、班级对比条形图、高频错题 TOP、知识点得分率。
- `views/m/learning/knowledge.vue`：同一接口的知识点维度；环形/条形图 + 得分率排行；点击知识点展开题目明细（若接口有）。
- `views/m/learning/advice.vue`：选择试卷 → `generateAnalysis` 生成建议 → 展示文档式内容（标题、概述、教学建议）；支持复制文本。
- `views/m/learning/ppt.vue`：选择已生成的讲评报告 → `generatePpt` → 展示下载地址；微信内提示浏览器打开，或复制链接。

### 3.9 系统与大模型

- `views/m/system/user.vue`：`pageUsers`；搜索；用户卡片（用户名、昵称、角色、状态）；新增用户、启用/禁用、重置密码、分配角色。
- `views/m/system/role.vue`：`listRoles`；角色卡片；新增/编辑/删除；被用户绑定时后端拒绝，前端展示错误信息。
- `views/m/system/menu.vue`：只读使用 `getMenuPermissionMatrix()`；按模块分组的「菜单 × 角色」矩阵，移动端用横向滚动表格或折叠卡展示。
- `views/m/setting/llm.vue`：`getLlmCredential` 展示脱敏 Key 与状态；保存/清除/测试 Key（`saveLlmCredential`/`removeLlmCredential`/`testLlmCredential`）；Key 输入框不自动填充。

### 3.10 错误页

- `views/m/error/403.vue`、`404.vue`：图标 + 说明 + 「返回首页」按钮。

## 4. 需要修改的现有文件

| 文件 | 改动 |
| --- | --- |
| `frontend/index.html` | viewport、theme-color、iOS 全屏 meta |
| `frontend/src/main.js` | 引入 `styles/mobile.css` |
| `frontend/src/router/index.js` | 新增 `/m` 路由树、移动端守卫、scrollBehavior |
| `frontend/src/api/request.js` | `redirectToLogin` 区分移动端与桌面端 |
| `frontend/src/layout/components/NavBar.vue` | 下拉菜单增加「手机版」入口 |
| `frontend/src/config/menu.js` | 不改结构；移动端菜单从 `menuTree` 读取并过滤 |
| `README.md`（可选） | 增加「移动端 / 微信访问」小节：部署需 HTTPS 与 history fallback |

`config/menu.js` 不新增重复的移动菜单表，避免两套配置漂移。

## 5. 部署与微信访问说明

- 开发：`npm run dev` 后手机与电脑同局域网，访问 `http://<电脑IP>:5173/m/login`。
- 生产：前端 `dist` 部署到 HTTPS 域名；Vue Router 为 history 模式，Nginx 需 `try_files $uri $uri/ /index.html;`。Spring Boot 若直接从 `static` 提供，需要增加 SPA 转发（本次不改后端，只在 README 说明）。
- 微信内访问要求：HTTPS + 已备案域名（业务域名）、不能是 localhost；分享链接即为 `/m/login` 或 `/m/home`。
- 微信内下载限制：Excel/PPT 下载统一提示「右上角 ··· 在浏览器打开」。

## 6. 验证方案

1. `cd frontend && npm run build` 退出码 0，无新增编译错误。
2. 浏览器 DevTools 切换 iPhone 12（390×844）：
   - 访问 `/` 自动进入 `/m/login`；登录学生账号后进入 `/m/student/papers`。
   - 学生：试卷 → 在线作答 → 提交；我的成绩展示环形与记录；底部 4 个 Tab 可切换。
   - 教师：首页 4 指标、任务监控列表与详情、成绩复核改分、学情报表图表、纸质批改三页可打开。
   - 管理员：用户/角色/菜单、大模型设置可打开；无权限菜单不显示，直接访问显示 `/m/403`。
3. 桌面 1440×900 访问 `/`：仍为原桌面布局，功能与样式不变；NavBar「手机版」可跳 `/m/home`。
4. 微信 UA 模拟（DevTools 自定义 UA 含 MicroMessenger）：出现下载提示条；登录、作答、上传图片入口可用。
5. 401 失效：清空 localStorage token 后请求任意接口，移动端跳 `/m/login`，桌面端跳 `/login`。

## 7. 风险与约束

- Element Plus 为桌面组件库，移动端只做样式覆盖，不追求原生手感；复杂表格类页面（菜单权限矩阵、成绩统计）以卡片/横向滚动呈现。
- 微信 iOS 下 `100vh` 不稳定，布局使用 `100dvh` 并提供 `100vh` 回退。
- 移动端功能与桌面共用同一套 API，不改变任何后端行为；若某个接口在手机上响应较慢，沿用现有 loading 与超时提示。
- 不引入 Taro、uni-app 等新框架，避免与现有 Vue 3 + Vite 工程冲突。
