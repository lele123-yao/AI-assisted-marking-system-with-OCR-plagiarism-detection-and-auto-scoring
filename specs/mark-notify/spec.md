# 阅卷完成实时通知（RabbitMQ Fanout + WebSocket + 站内信）技术规格

状态：待评审
方案：B（实时推送 + 站内信持久化，离线可查）

## 1. 目标

在一次 AI 阅卷任务成功后：

1. 向该答卷的学生、该课程任课教师推送一条「阅卷完成」站内信并持久化，离线/刷新后仍可查询；
2. 通过 RabbitMQ Fanout 广播交换机，让集群中每个后端实例都能收到事件，并向各自本地持有的 WebSocket 连接实时推送；
3. 前端桌面端与移动端均提供消息铃铛、未读数角标、消息列表弹层，点击可跳转答卷/成绩页面；
4. 通知失败绝不回滚阅卷结果，也不影响 MQ ack。

## 2. 范围

### 包含

- 站内信表 `notify_message` 与后端 CRUD（分页、未读数、单条已读、全部已读）；
- WebSocket 端点 `/ws/mark`，握手期 JWT 校验；
- RabbitMQ Fanout 交换机 `marking.notify.exchange` + 每实例匿名队列 + 监听器；
- AI 阅卷消费者在写库成功后触发通知；
- 前端铃铛组件、WS 连接复用、心跳与指数退避重连、eventId 去重；
- Vite 开发代理与 Security 白名单放行 `/ws/**`。

### 不包含

- 站内信的删除/批量删除、按类型筛选、消息详情独立页面；
- 管理员广播、系统公告、其他业务事件（后续可复用本通道）；
- 移动端原生推送 / 微信模板消息；
- 用 WebSocket 替代现有 HTTP 轮询（保留轮询兜底，仅新增实时通道）。

## 3. 现状核实

| 事实 | 位置 |
| --- | --- |
| Boot 4.1.2，web 使用 `spring-boot-starter-webmvc` | [pom.xml](file:///d:/ai-marking-system/pom.xml) |
| JWT 过滤器先验签，再与 Redis `login:token:{username}` 比对 | [JwtAuthenticationFilter.java](file:///d:/ai-marking-system/src/main/java/com/marking/aimarkingsystem/common/security/JwtAuthenticationFilter.java#L64-L103) |
| 白名单为静态数组，需新增 `/ws/**` | [SecurityConfig.java](file:///d:/ai-marking-system/src/main/java/com/marking/aimarkingsystem/common/config/SecurityConfig.java#L45-L60) |
| `Jackson2JsonMessageConverter` trustedPackages 仅 `java.util`、`java.lang`、`mark.dto` | [RabbitMQConfig.java](file:///d:/ai-marking-system/src/main/java/com/marking/aimarkingsystem/common/config/RabbitMQConfig.java#L180-L186) |
| 全局限定 `acknowledge-mode: manual`、prefetch 5、concurrency 2 | application.yml L64-L71 |
| AI 阅卷成功点：`examAnswerMapper.updateById` → `paperAnswerItemService.saveAiItems` → `markSuccess` → `ack` | [AiMarkConsumer.java](file:///d:/ai-marking-system/src/main/java/com/marking/aimarkingsystem/mark/consumer/AiMarkConsumer.java#L213-L242) |
| 答卷含 `studentId/paperId`，试卷含 `courseId/paperTitle`，课程含 `teacherId` | ExamAnswer / ExamPaper / Course 实体 |
| 前端 axios baseURL `/api`，dev 代理 `/api`→8080 并 rewrite | [request.js](file:///d:/ai-marking-system/frontend/src/api/request.js#L12-L16)、vite.config.js |
| 桌面导航栏挂载点 | [NavBar.vue](file:///d:/ai-marking-system/frontend/src/layout/components/NavBar.vue#L61-L82) |
| 移动顶栏挂载点 | [MobileLayout.vue](file:///d:/ai-marking-system/frontend/src/layout/mobile/MobileLayout.vue#L94-L108) |
| 已有路由：桌面 `/mark/detail/:id`、`/student/scores`；移动 `/m/mark/detail/:id`、`/m/student/answer/:id` | router/index.js、config/menu.js |

## 4. 总体设计

```
AiMarkConsumer（DB 写成功）
  └─ NotifyService.notifyMarkCompleted(...)
       ├─ 解析接收人：答卷 studentId + 课程 teacherId
       ├─ 批量写 notify_message（离线可查）
       └─ MarkNotifyPublisher.publish(event)
            └─ FanoutExchange marking.notify.exchange（durable）
                 ├─ 实例A 匿名队列 → MarkNotifyListener → 本地 WsSessionRegistry → /ws/mark 连接
                 └─ 实例B 匿名队列 → 同上
```

要点：

- **每实例一个匿名队列**（`AnonymousQueue`：非持久、自动删除），Fanout 保证所有实例都收到同一条事件；
- **持久化先于广播**，MQ 未启动时只丢实时推送，站内信仍可查；
- 通知代码在消费者内用 try/catch 包裹，异常只记日志，不影响 `markSuccess` 与 `ack`；
- 事件体不带 `taskNo` 字段，避免被 `RabbitTemplate` 的 ReturnCallback 误判为阅卷任务失败。

## 5. 数据模型

新增表 `notify_message`（脚本：`sql/notify_message.sql`）：

| 列 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AUTO | 主键 |
| user_id | BIGINT NOT NULL | 接收人 sys_user.id |
| type | VARCHAR(32) NOT NULL | 消息类型，首期 `MARK_COMPLETED` |
| title | VARCHAR(128) NOT NULL | 标题 |
| content | VARCHAR(512) | 正文 |
| biz_type | VARCHAR(32) | 业务类型 `EXAM_ANSWER` |
| biz_id | BIGINT | 业务主键（answerId） |
| is_read | TINYINT NOT NULL DEFAULT 0 | 0 未读 / 1 已读 |
| create_time | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| read_time | DATETIME | 读取时间 |

索引：`idx_user_read_time (user_id, is_read, create_time)`。

## 6. 后端改动

### 6.1 新增依赖

pom.xml 增加（版本由 parent 管理）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

> 注：本机 `.m2` 原先缺失 `spring-websocket:7.0.10`、`spring-boot-websocket:4.1.2`、starter `4.1.2`，已手工放入本地仓库，离线编译可解析。

### 6.2 新增文件（包 `com.marking.aimarkingsystem.notify`）

| 文件 | 职责 |
| --- | --- |
| `entity/NotifyMessage.java` | `@TableName("notify_message")`，字段对应上表；`createTime` 使用 `@TableField(fill = INSERT)` |
| `mapper/NotifyMessageMapper.java` | `@Mapper extends BaseMapper<NotifyMessage>` |
| `mq/MarkNotifyEvent.java` | 事件 POJO：`eventId`、`type`、`title`、`content`、`bizType`、`bizId`、`paperId`、`score`、`needsReview`、`receiverIds`、`occurredAt`（String，ISO） |
| `mq/MarkNotifyPublisher.java` | `publish(event)` → `rabbitTemplate.convertAndSend(EXCHANGE_NOTIFY, "", event)`，异常仅 warn |
| `mq/MarkNotifyListener.java` | `@RabbitListener(queues = "#{markNotifyFanoutQueue.name}", containerFactory = "notifyListenerContainerFactory")`，遍历 `receiverIds` 调 `WsSessionRegistry.sendToUser` |
| `service/NotifyService.java` | `notifyMarkCompleted(answerId, paperId, paperTitle, studentId, courseId, score, needsReview)`；`page(current,size)`；`unreadCount()`；`markRead(id)`；`markAllRead()`。接收人 = studentId + `courseMapper.selectById(courseId).teacherId`，用 `LinkedHashSet` 去重 |
| `controller/NotifyController.java` | `GET /notify/page`、`GET /notify/unread-count`、`POST /notify/read/{id}`、`POST /notify/read-all`，全部只操作当前登录用户 |
| `websocket/JwtHandshakeInterceptor.java` | 从 query `token`（兜底 Header `Authorization: Bearer`）取 token；`validateToken` + Redis 当前 token 比对 + 查 `sys_user`；失败写 401 并返回 false；成功写 attributes `userId`、`username` |
| `websocket/WsSessionRegistry.java` | `ConcurrentHashMap<Long, Set<WebSocketSession>>`；`add/remove/sendToUser`；发送时对 session 加锁，异常关闭并移除 |
| `websocket/MarkNotifyWebSocketHandler.java` | `TextWebSocketHandler`：建立时注册、关闭/异常时注销、收到 `ping` 回 `pong` |
| `config/WebSocketConfig.java` | `@EnableWebSocket`，注册 `/ws/mark`，挂载握手拦截器，`setAllowedOriginPatterns("*")` |
| `config/NotifyRabbitConfig.java` | `FanoutExchange marking.notify.exchange`（durable）、`AnonymousQueue markNotifyFanoutQueue`、`Binding`；`notifyListenerContainerFactory`（AUTO ack、独立于全局 manual） |

### 6.3 修改文件

| 文件 | 改动 |
| --- | --- |
| `RabbitMQConfig.java` | `messageConverter()` 的 trustedPackages 增加 `com.marking.aimarkingsystem.notify.mq` |
| `AiMarkConsumer.java` | 构造器注入 `NotifyService`；`markSuccess(task)` 之后、`ack` 之前调用通知，try/catch 仅 warn |
| `SecurityConfig.java` | `WHITE_LIST` 增加 `"/ws/**"`（握手是 HTTP GET，且浏览器无法带 Authorization 头） |
| `sql/notify_message.sql` | 新建表脚本（手动执行，项目无迁移工具） |

### 6.4 接口契约

- `GET /notify/page?current=1&size=10` → `Result<PageResult<NotifyMessage>>`，按 `create_time desc, id desc`；
- `GET /notify/unread-count` → `Result<Long>`；
- `POST /notify/read/{id}` → `Result<Void>`，仅当 `id + user_id` 命中且未读时更新；
- `POST /notify/read-all` → `Result<Void>`，把当前用户全部未读置已读。

WebSocket 下行消息：

```json
{
  "eventId": "uuid",
  "type": "MARK_COMPLETED",
  "title": "阅卷完成",
  "content": "试卷《期中测试》的答卷已完成 AI 阅卷，参考分 85.50 分",
  "bizType": "EXAM_ANSWER",
  "bizId": 123,
  "paperId": 45,
  "score": 85.50,
  "needsReview": false,
  "receiverIds": [7, 9],
  "occurredAt": "2026-11-25T10:30:00"
}
```

## 7. 前端改动

| 文件 | 改动 |
| --- | --- |
| `frontend/src/api/notify.js`（新增） | 封装 4 个接口 |
| `frontend/src/composables/useMarkNotify.js`（新增） | 模块级单例：token 建连 `ws(s)://host/ws/mark?token=`、25s 心跳、指数退避重连（2s→30s）、eventId 去重、收到事件刷新未读数并 `ElNotification` 提示、导出 `resolveNotifyTarget(event)` |
| `frontend/src/components/NotifyBell.vue`（新增） | `el-badge` + 铃铛 + `el-popover` 列表；打开时拉取分页；单条已读、全部已读；点击跳转 |
| `frontend/src/layout/components/NavBar.vue` | 右侧用户信息前插入 `<NotifyBell />` |
| `frontend/src/layout/mobile/MobileLayout.vue` | 顶栏「全部功能」按钮前插入 `<NotifyBell compact />` |
| `frontend/vite.config.js` | proxy 增加 `'/ws': { target, changeOrigin: true, ws: true }`（不 rewrite） |

跳转规则（事件中的 `bizId` 是答卷ID）：有 `answer:view` 权限 → `/answer/detail/{bizId}` 或 `/m/answer/detail/{bizId}`；否则 → `/student/scores` 或 `/m/student/answer/{bizId}`。

## 8. 关键决策

1. **独立 AUTO ack 容器工厂**：全局 `acknowledge-mode: manual`，若通知监听器不带 Channel 会导致消息一直 unacked 堆积；实时推送是"尽力而为"，因此单独声明 AUTO 工厂，失败即丢弃（站内信已落库）。
2. **匿名队列而非固定队列**：固定队列在集群下是竞争消费，只有一台实例能收到；匿名队列实现真正广播。
3. **token 走 query**：浏览器 WebSocket API 不能设置请求头；握手期仍做完整 JWT + Redis 单会话校验，避免只验签名导致被挤下线的 token 仍可连。
4. **事件不带 taskNo**：`RabbitTemplate.setMandatory(true)` + ReturnCallback 会从消息体解析 `taskNo` 并回写 `mark_task` 失败；事件体保持无该字段杜绝误伤。
5. **保留 REST 轮询**：WS 断线期间仍有兜底，符合"不破坏现有功能"。

## 9. 风险与限制

- 项目无 DB 迁移框架，`notify_message` 需手动执行 SQL，否则启动后接口报错、消费者通知仅 warn；
- 多实例部署需 Nginx 透传 `Upgrade`/`Connection` 头，否则 WS 握手返回 400；
- 匿名队列随实例生命周期存在，实例离线期间的事件不会补推（站内信可补查）；
- Redis 不可用时握手降级为仅校验 JWT 签名（与现有 HTTP 鉴权策略一致）。

## 10. 验收/验证

1. `.\mvnw.cmd -o -DskipTests compile` 通过；
2. `cd frontend; npm run build` 通过；
3. 手动执行 `sql/notify_message.sql` 后启动后端与前端；
4. 学生提交线上答卷 → AI 阅卷完成 → 学生端与教师端铃铛实时 +1，并弹出提示；
5. 刷新页面后未读数与消息列表仍在（持久化生效）；
6. 点击消息跳转到对应答卷详情/成绩页，消息变为已读；
7. 关闭浏览器再登录，未读数正确；
8. 关闭 RabbitMQ 后触发阅卷（或直接调用通知）：阅卷结果正常落库，通知失败仅日志。
