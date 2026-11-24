# 移动端 H5 实施任务清单

前置：规格见 `docs/mobile-h5/spec.md`。全部任务在前端工程 `frontend/` 内完成，不修改后端接口。

## 阶段一：基础能力

- [x] T1 设备识别工具
  - 新增 `src/utils/device.js`：`isWeChat`、`isMobileDevice`、`preferDesktop`、`setPreferDesktop`。
  - 验收：桌面 Chrome 返回 false；390px 宽度或移动 UA 返回 true。

- [x] T2 全局样式与 HTML 元信息
  - 新增 `src/styles/mobile.css`（工具类 + Element Plus 移动端覆盖），在 `src/main.js` 引入。
  - 修改 `index.html` viewport、theme-color、iOS 全屏 meta。
  - 验收：移动端输入框字号 16px、弹窗宽度不超过 92%。

- [x] T3 共享组件
  - 新增 `src/components/m/MobileHero.vue`、`MobileCard.vue`、`MobileEmpty.vue`、`MobileSheet.vue`、`MobileSearchBar.vue`、`MobileLoadMore.vue`、`MobileStatGrid.vue`。
  - 验收：组件可独立渲染，props/emits 与规格一致，无控制台警告。

- [x] T4 移动端分页组合式函数
  - 新增 `src/composables/useMobileList.js`，支持 `load/refresh/loadMore/search/reset`，`pageNum/pageSize` 与后端一致。
  - 验收：切换查询条件后回到第 1 页；加载更多不重复。

- [x] T5 移动端布局
  - 新增 `src/layout/mobile/MobileLayout.vue`：顶部栏、底部 TabBar（按权限）、菜单抽屉（读 `menuTree` 并过滤）、微信下载提示、安全区。
  - 验收：4 个 Tab 可切换；无权限菜单不出现；直接访问无权限路由跳 `/m/403`。

- [x] T6 路由与守卫
  - 修改 `src/router/index.js`：新增规格第 2.1 节全部 `/m` 路由；移动端跳转规则；`/m` 重定向 `/m/home`；scrollBehavior。
  - 修改 `src/api/request.js`：401 跳转区分 `/m/login` 与 `/login`。
  - 验收：移动端访问 `/` 进入 `/m/login`；桌面访问 `/` 不变；token 失效跳转正确。

- [x] T7 版本切换入口
  - 修改 `src/layout/components/NavBar.vue` 增加「手机版」。
  - `src/views/m/profile.vue` 增加「使用电脑版」。
  - 验收：两端可互相跳转，刷新后记忆选择。

## 阶段二：账号与首页

- [x] T8 移动端登录 / 注册
  - 新增 `src/views/m/login.vue`、`src/views/m/register.vue`。
  - 验收：学生登录后进入 `/m/student/papers`；教师/管理员进入 `/m/home`；错误提示正常；注册页行为与桌面一致或给出明确指引。

- [x] T9 移动端首页
  - 新增 `src/views/m/home.vue`：问候、角色、日期、指标卡、快捷入口，数据源与桌面一致，单接口失败降级为 `-`。
  - 验收：教师 4 指标、学生 2 指标均可加载；快捷入口权限正确。

- [x] T10 我的与全部功能
  - 新增 `src/views/m/profile.vue`、`src/views/m/menu.vue`。
  - 验收：全部菜单按角色显示；点击进入对应 `/m` 页面；退出回到 `/m/login`。

## 阶段三：学生端

- [x] T11 我的试卷 / 我的成绩
  - `src/views/m/student/papers.vue`、`scores.vue`。
  - 验收：筛选芯片生效；「去作答」带 `paperId`；成绩环形与记录正确。

- [x] T12 在线作答 / 答卷列表 / 答卷详情
  - `src/views/m/answer/submit.vue`、`list.vue`、`detail.vue`。
  - 验收：逐题作答、进度、提交成功；列表分页加载；详情长文本可展开。

## 阶段四：教学资源

- [x] T13 课程 / 班级
  - `src/views/m/course/index.vue`、`class/index.vue`。
  - 验收：新增/编辑/归档/删除可用；教师不查询用户列表；班级成员可增删与导入。

- [x] T14 试卷 / 题目 / Prompt
  - `src/views/m/paper/index.vue`、`question/index.vue`、`prompt/index.vue`。
  - 验收：试卷状态变更与下发可用；上传解析可调起文件/相机；题目增删改与知识点维护可用；模板增删改可用。

## 阶段五：批改与阅卷

- [x] T15 纸质批改三页
  - `src/views/m/paper-answer/roster.vue`、`upload.vue`、`score.vue`。
  - 验收：名单导入返回统计；图片上传后显示队列；逐题录分合计正确并保存。

- [x] T16 AI 阅卷任务与详情
  - `src/views/m/mark/task.vue`、`detail.vue`。
  - 验收：状态筛选、加载更多、详情跳转；OCR 文本与 AI 分展示；重新触发 OCR 可用。

- [x] T17 知识库
  - `src/views/m/agent/knowledge.vue`。
  - 验收：文档增删改、重建索引按钮可用；未配置 embedding 有提示。

## 阶段六：成绩与学情

- [x] T18 成绩复核 / 统计 / 导出
  - `src/views/m/grade/review.vue`、`statistics.vue`、`export.vue`。
  - 验收：三种复核动作提交成功；图表在 390px 下可读；导出触发下载或微信提示。

- [x] T19 学情四页
  - `src/views/m/learning/report.vue`、`knowledge.vue`、`advice.vue`、`ppt.vue`。
  - 验收：统计图表渲染；AI 建议生成并可复制；PPT 生成后可下载或复制链接。

## 阶段七：抄袭与系统

- [x] T20 抄袭检测
  - `src/views/m/plagiarism/compare.vue`、`report.vue`。
  - 验收：生成指纹与报告可用；高风险卷对与相似度展示正确。

- [x] T21 用户 / 角色 / 菜单
  - `src/views/m/system/user.vue`、`role.vue`、`menu.vue`。
  - 验收：用户启用禁用、重置密码、分配角色可用；角色增删改可用；菜单矩阵可横向查看。

- [x] T22 大模型设置
  - `src/views/m/setting/llm.vue`。
  - 验收：脱敏展示、保存、测试、清除可用。

## 阶段八：错误页与收尾

- [x] T23 错误页
  - `src/views/m/error/403.vue`、`404.vue`。
  - 验收：可返回首页。

- [x] T24 构建与验证
  - `cd frontend && npm run build` 通过。
  - 移动视口逐角色走查：学生（试卷→作答→成绩）、教师（任务→复核→学情→批改）、管理员（用户/角色/菜单/大模型）。
  - 桌面视口回归：首页、课程、试卷、成绩复核样式与功能不变。
  - 验收：无编译错误、无控制台报错、无无权限菜单泄漏。

- [x] T25 文档补充
  - 在 `README.md` 增加「移动端 / 微信访问」：局域网调试、HTTPS 与 history fallback 要求、微信下载限制说明。
  - 验收：按文档可从手机打开 `/m/login`。

## 完成标准

- 微信内置浏览器与移动 Chrome/Safari 可完成登录与各自角色的核心业务闭环。
- 桌面端现有功能、布局、权限行为零回归。
- `npm run build` 成功，构建产物可直接部署到静态服务器。
