# 任务清单：阅卷完成实时通知

依赖：`spec.md` 已评审通过后执行。

## T1 依赖与 MQ 拓扑

- [x] pom.xml 增加 `spring-boot-starter-websocket`（不写版本）；
- [x] `RabbitMQConfig.messageConverter()` trustedPackages 追加 `com.marking.aimarkingsystem.notify.mq`；
- [x] 新增 `notify/config/NotifyRabbitConfig.java`：FanoutExchange `marking.notify.exchange`（durable）+ `AnonymousQueue markNotifyFanoutQueue` + Binding + `notifyListenerContainerFactory`（AcknowledgeMode.AUTO、复用现有 MessageConverter）。

验收：编译通过；启动日志已确认匿名队列 `spring.gen-xxx`（non-durable / auto-delete / exclusive）被自动声明。

## T2 站内信落库与接口

- [x] 新建 `sql/notify_message.sql`（表 + 索引，见 spec 第 5 节），并在本地库执行、核对表结构；
- [x] `notify/entity/NotifyMessage.java`、`notify/mapper/NotifyMessageMapper.java`；
- [x] `notify/service/NotifyService.java`：
  - `notifyMarkCompleted(answerId, paperId, paperTitle, studentId, courseId, score, needsReview)`：接收人 = studentId + 课程 teacherId（去重），逐条 insert，再发布事件；
  - `page`、`unreadCount`、`markRead`、`markAllRead`（全部限定当前登录用户）；
- [x] `notify/controller/NotifyController.java`：4 个接口，返回 `Result`/`PageResult`。

验收：单条已读只能改自己的记录；未读数与列表一致（SQL 条件层面已强制 user_id）。

## T3 WebSocket 服务端

- [x] `notify/websocket/WsSessionRegistry.java`（多端会话、发送加锁、失效清理）；
- [x] `notify/websocket/JwtHandshakeInterceptor.java`（query token → validateToken → Redis 单会话比对 → 查 User → attributes 写 userId/username；失败 401 拒绝握手）；
- [x] `notify/websocket/MarkNotifyWebSocketHandler.java`（注册/注销、ping→pong）；
- [x] `notify/config/WebSocketConfig.java`（`@EnableWebSocket`，`/ws/mark`，跨域允许）；
- [x] `SecurityConfig.WHITE_LIST` 增加 `/ws/**`。

验收：无效 token 握手返回 401，日志出现「WebSocket 握手被拒绝」——已用 curl 握手请求实测。

## T4 事件发布与消费

- [x] `notify/mq/MarkNotifyEvent.java`（字段见 spec 第 6.2 节，不含 taskNo）；
- [x] `notify/mq/MarkNotifyPublisher.java`（try/catch 仅日志）；
- [x] `notify/mq/MarkNotifyListener.java`（监听匿名队列，按 receiverIds 推送本地会话）。

验收：应用启动时 `@RabbitListener` 的 `#{markNotifyFanoutQueue.name}` 与专用容器工厂均解析成功（无上下文启动错误）。

## T5 接入阅卷消费者

- [x] `AiMarkConsumer` 构造器注入 `NotifyService`；
- [x] 在 `markSuccess(task)` 之后、`ack` 之前调用通知，外层 try/catch 仅 warn；
- [x] 使用已有的 `paper.getPaperTitle()`、`answer.getStudentId()`、`courseId`、`totalAiScore`、`needsReview`。

验收：编译通过；通知异常路径被 catch，不会进入 markFailed 分支。

## T6 前端

- [x] `frontend/src/api/notify.js`；
- [x] `frontend/src/composables/useMarkNotify.js`（单例连接、25s 心跳、指数退避重连、eventId 去重、未读数刷新、通知提示、`resolveNotifyTarget`）；
- [x] `frontend/src/components/NotifyBell.vue`（角标、列表、单条/全部已读、点击跳转）；
- [x] `NavBar.vue` 与 `MobileLayout.vue` 挂载铃铛；
- [x] `vite.config.js` 增加 `/ws` 代理（`ws: true`，不 rewrite）。

验收：`npm run build` 成功（16.95s，exit 0）。

## T7 验证

- [x] `.\mvnw.cmd -o -DskipTests compile` 通过；
- [x] `cd frontend; npm run build` 通过；
- [x] 执行 `sql/notify_message.sql`；
- [x] `spring-boot:run` 启动成功（20.6s），Tomcat 8080、RabbitMQ 匿名队列声明、WebSocket 握手鉴权均正常；
- [ ] 学生提交线上答卷 → AI 阅卷完成 → 双端铃铛实时 +1（需 Redis 在线与真实学生账号，本机 Redis 未启动、且会消耗大模型额度，未执行）；
- [ ] 点击消息跳转并变已读（同上，依赖登录态）。

## 风险提示

- 不执行 `sql/notify_message.sql` 时，通知接口 500、消费者通知被 catch 成 warn，阅卷主流程不受影响；
- 集群部署必须让 Nginx 透传 WebSocket 升级头。
