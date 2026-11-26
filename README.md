# 🎓 高校主观题智能异步阅卷系统

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F?logo=springboot&logoColor=white)](https://spring.io)
[![Java](https://img.shields.io/badge/Java-21-red?logo=openjdk&logoColor=white)](https://openjdk.org)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883?logo=vuedotjs&logoColor=white)](https://vuejs.org)
[![Element Plus](https://img.shields.io/badge/Element%20Plus-2.11-409EFF?logo=element&logoColor=white)](https://element-plus.org)
[![Vite](https://img.shields.io/badge/Vite-7-646CFF?logo=vite&logoColor=white)](https://vitejs.dev)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](./LICENSE)

> 面向高校主观题场景的 AI 异步阅卷全链路解决方案：**试卷题库 → 学生作答 → OCR 识别 → SimHash 查重 → 大模型 AI 阅卷 → 教师复核录分 → 学情分析与讲评 PPT**，后端 Spring Boot + RabbitMQ 异步解耦，前端 Vue 3 玻璃拟态科技风。

---

## ✨ 项目亮点

- 🤖 **AI 全链路**：DeepSeek 大模型驱动主观题自动阅卷、学情讲评正文、讲评 PPT 自动编排
- 🔄 **异步解耦**：RabbitMQ 消费 AI 阅卷任务，手动 Ack + prefetch 限流 + 死信队列 + 定时补偿重投
- 🛡️ **熔断降级**：Sentinel 对下游大模型做 QPS 限流 + 异常比例熔断，熔断时返回兜底文案，阅卷任务延后重试、绝不写 0 分
- 📡 **实时通知**：RabbitMQ Fanout + WebSocket，多实例部署下阅卷完成通知实时推送，站内信持久化离线兜底
- 🧯 **OCR 独立服务**：Flask + RapidOCR 引擎池 + gunicorn，并发打满返回 429 快速失败，由消息队列自动重试
- 🔍 **答卷查重**：SimHash 指纹两两比对，高风险 / 可疑 / 正常三档报告，可下钻逐份明细
- 📄 **OCR 可选**：PDFBox 提取 PDF 文本层、图片走可插拔 OCR HTTP 接口；未配置时提示手动录入
- 🎯 **一页一结构**：前端页面按业务定制视觉骨架（卡片画廊 / 主从双栏 / 卡片墙 / 向导 / 折叠分组），告别同质化
- 🔐 **RBAC 权限**：admin / teacher / student 三种角色，菜单按权限过滤，按钮级 `v-permission` 指令
- 🛡️ **工程化支撑**：JWT 登录、Redis 滑动窗口限流、操作日志切面、分布式锁、逻辑删除、统一异常
- 🧑‍🏫 **ReAct Agent**：评分一致性 / 历史案例 / 知识库检索多 Agent + 工具注册表 + 运行轨迹

---

## 🧩 技术栈

### 后端

| 组件 | 版本 / 说明 |
| --- | --- |
| Spring Boot | 4.1.2 |
| Java | 21 |
| MyBatis-Plus | 自动扫描、下划线驼峰映射、逻辑删除 |
| Spring Security + JWT | HS256 签名、30min 过期、Redis 会话 TTL |
| Spring Data Redis | 滑动窗口限流、分布式锁、会话存储 |
| RabbitMQ | 手动 Ack、prefetch=5、死信队列、publisher-confirm |
| MySQL | 8.0，库 `ai_marking` |
| MinIO | 9000，答卷图片预签名直链、私有桶防盗链 |
| EasyExcel | 考生名单 Excel 批量导入 |
| PDFBox | PDF 试卷文本层提取 |
| DeepSeek LLM | Chat Completions 标准协议，可扩展 / 通义 / 智谱 |
| Sentinel | 1.8.8，下游大模型 QPS 限流 + 异常比例熔断 + 兜底降级 |
| WebSocket | 原生 TextWebSocketHandler + JWT 握手鉴权 + 多实例广播 |
| Flask + gunicorn | 独立 OCR 服务，EnginePool 引擎池控制并发 |
| Lombok | 简化 DTO / Entity |

### 前端

| 组件 | 版本 |
| --- | --- |
| Vue | 3.5 |
| Vite | 7 |
| Element Plus | 2.11 |
| Pinia | 3 |
| Vue Router | 4 |
| ECharts | 6 |
| Axios | 1.12 |
| @element-plus/icons-vue | 2.3 |

### 基础设施

| 服务 | 默认端口 | 说明 |
| --- | --- | --- |
| MySQL | 3306 | 数据库，库 `ai_marking` |
| Redis | 6379 | 会话 / 限流 / 分布式锁 |
| RabbitMQ | 5672 | guest / guest |
| MinIO | 9000 | minioadmin / minioadmin |
| OCR（可选） | 9100 | Python Flask + RapidOCR，gunicorn 部署 |
| Nginx（可选） | 80 | 多副本网关，WS 透传，配置见 `deploy/nginx.conf` |
| 后端 | 8080 | Spring Boot |
| 前端 | 5173 | Vite dev server |

---

## 📐 功能地图

### 🔐 认证与用户

- 账号登录 / 注册（admin / teacher / student 三种角色）
- JWT + Redis 会话，登出即时失效
- 密码哈希（BCrypt），重置密码
- 用户增删改、启用 / 禁用、角色分配

### 📚 课程与题库

- 课程管理：卡片画廊，按课程筛选试卷
- 试卷管理：题库 / 线上 / 线下三种模式，试卷上传自动解析 PDF 或手动录入题目
- 题目管理：按大题分组折叠（`el-collapse`），支持主观题参考答案 + 评分细则 + 知识点绑定
- 提示词模板：左列表右预览，按角色过滤

### 👥 班级与下发

- 班级管理：左列表右详情，支持批量加入学生、Excel 导入名单
- 试卷下发：按班级下发或指定本人，设置考试时间窗口
- 考生名单导入：三步向导（试卷 + Excel + 结果）

### ✍️ 答卷与阅卷

- 答卷图片上传 / OCR：左侧上传工作区 + 右侧答卷流水线，支持手工触发 OCR
- 学生在线作答：试卷卡片网格（按状态分组）、题号导航、逐题提交
- AI 异步阅卷：RabbitMQ 消费，手动 Ack，失败不自动重入，走死信
- 教师复核：左队列右详情，可采纳 AI 参考分或逐题录分
- 教师录分：答卷队列 + 逐题评分卡 + 吸底合计条，支持教师评语覆盖 AI 评语
- 成绩导出：向导式左列表 + 右 XLSX 文件预览卡

### 🔍 查重与风控

- 答卷相似度比对：生成 SimHash 指纹 → 两两比对 → 生成风险报告
- 抄袭检测报告：风险总览横幅 + 疑似答卷卡片墙 + 相似卷对表格
- 报告下钻：单份答卷相似答卷明细抽屉

### 📊 学情与讲评

- 成绩统计：仪表盘 + 环形 / 折线 / 柱状多图联动
- 学情报表：深色概览横幅 + 班级图表 + 错题分布图
- 知识点掌握：饼图 / 柱状图切换 + 掌握度排行（红橙蓝三色）
- AI 讲评建议：左控制台 + 右讲评正文（ReAct Agent + 大模型生成）
- 讲评 PPT：两步向导（报告 → PPT）+ 16:9 课件预览
- 知识检索：DeepSeek Embedding + 混合检索 + 向量化知识库

### 🧑‍🏫 Agent 模块

- 阅卷复核 Agent：工具注册表 + ReAct 执行器 + 运行轨迹
- 可用工具：知识库检索 / 历史案例 / 题目详情 / 作答概览 / 得分一致性检查

### 🛠️ 系统支撑

- 操作日志切面 / 接口限流（Redis 滑动窗口）/ Sentinel 预留
- 权限拦截器 + RBAC 权限表 + 动态菜单
- MinIO 预签名 URL、后端生成带签名的答卷直链
- 全局异常处理 + 统一响应体 `R<T>`

---

## 🗂️ 项目结构

```
ai-marking-system/
├── pom.xml                           # Maven 父 POM，Java 21 + Spring Boot 4.1.2
├── README.md
├── .gitignore
├── sql/                              # 建表与 mock 数据脚本
│   ├── init_schema.sql
│   ├── mock_data.sql
│   ├── class_module.sql
│   ├── agent_module.sql
│   └── ...
├── src/main/java/com/marking/aimarkingsystem/
│   ├── agent/                        # 🧑‍🏫 ReAct Agent：知识库、工具、执行器
│   ├── answer/                       # ✍️ 答卷 / 题目明细 / 录分
│   ├── auth/                         # 🔐 登录 / 注册 / JWT
│   ├── clazz/                        # 👥 班级 / 名单导入 / 试卷下发
│   ├── common/                       # 🛠️ 切面 / 安全 / 工具（Redis / JWT / DeepSeek / PDF / OCR）
│   ├── learning/                     # 📊 学情 / 讲评报告 / PPT
│   ├── llm/                          # 🔑 大模型凭据服务（内存存储，重启清空）
│   ├── mark/                         # 🤖 AI 阅卷消费者 / 任务生产者
│   ├── paper/                        # 📚 试卷 / 题目 / 题库 / 试卷解析
│   ├── user/                         # 👤 用户管理（Mapper + Service）
│   └── Application.java
├── src/main/resources/
│   ├── application.yml               # ⚙️ 统一配置（见下方「关键配置」）
│   └── mapper/                       # MyBatis XML 映射
└── frontend/                         # 🎨 Vue 3 + Vite 前端（子工程）
    ├── package.json
    ├── vite.config.js
    ├── index.html
    ├── .env.development
    └── src/
        ├── api/                      # axios 封装 + 各业务接口
        ├── components/               # PageHero / StatCards / EChart / FileUploader
        ├── composables/              # useTable 通用分页
        ├── config/                   # 菜单 & 权限 derive
        ├── directives/               # v-permission 指令
        ├── layout/                   # SideMenu / NavBar / Breadcrumb
        ├── router/                   # 路由 + beforeEach 守卫
        ├── store/                    # Pinia：user / 全局状态
        ├── styles/                   # 主题变量（深蓝紫渐变）
        ├── utils/                    # auth / dict / download / request
        └── views/                    # 20+ 业务页面（一页一结构）
```

---

## 🚀 快速启动

### 前置依赖

| 依赖 | 最低版本 | 备注 |
| --- | --- | --- |
| JDK | 21 | pom.xml `<java.version>21</java.version>` |
| Maven | 3.8+ | 或直接用项目自带 `mvnw` |
| Node.js | 20.19+ | Vite 7 要求；推荐 22 LTS |
| npm | 9+ | 或 pnpm / yarn |
| MySQL | 8.0 | 数据库名 `ai_marking` |
| Redis | 6.0+ | 默认 0 号 DB |
| RabbitMQ | 3.11+ | guest / guest |
| MinIO | latest | minioadmin / minioadmin，桶 `ai-marking` |

### 1. 准备数据库与中间件

```bash
# 1) MySQL：建库建表 + 各模块增量脚本 + mock 数据（按顺序执行）
mysql -uroot -p < sql/init_schema.sql
mysql -uroot -p ai_marking < sql/class_module.sql
mysql -uroot -p ai_marking < sql/paper_parse.sql
mysql -uroot -p ai_marking < sql/agent_module.sql
mysql -uroot -p ai_marking < sql/agent_module_v2.sql
mysql -uroot -p ai_marking < sql/notify_message.sql
mysql -uroot -p ai_marking < sql/mock_data.sql

# 2) 启动 Redis、RabbitMQ、MinIO（以 Docker 为例）
docker run -d --name redis -p 6379:6379 redis:7
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.11-management
docker run -d --name minio -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data --console-address ":9001"
```

### 2. 后端配置与启动

**复制一份本地配置**（推荐，避免把密码写死在仓库）：

```bash
# PowerShell 示例（请根据你的用户名 / 密码调整）
Copy-Item src/main/resources/application.yml src/main/resources/application-local.yml
```

`application-local.yml` 中覆盖：

```yaml
spring:
  datasource:
    username: root
    password: 你的密码
  data:
    redis:
      password:
  rabbitmq:
    username: guest
    password: guest

minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin

llm:
  api-key: sk-xxxxxx  # DeepSeek API Key；留空也能跑，只是 AI 阅卷会跳过
  model: deepseek-chat

jwt:
  secret: 至少 32 字节，生产环境务必换随机串
```

启动后端（激活 `local` profile 让 Spring 读取 `application-local.yml`）：

```bash
# 方式 A：mvnw（无需本机安装 Maven）
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"

# 方式 B：本机 Maven
mvn spring-boot:run "-Dspring-boot.run.profiles=local"

# 方式 C：打包后 java -jar
mvn clean package -DskipTests
java -jar target/ai-marking-system-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

后端启动后：`http://localhost:8080`

### 3. 前端配置与启动

```bash
cd frontend
npm install            # 或 pnpm install / yarn
npm run dev            # Vite dev server，默认 http://localhost:5173
```

默认 `.env.development`：

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

构建生产包：

```bash
npm run build          # 产物 frontend/dist/，直接部署到任意静态服务器
npm run preview        # 本地预览 dist
```

### 4. 可选：启动 OCR 服务（图片试卷识别）

```bash
cd tools/ocr-server
pip install -r requirements.txt

# 开发（Windows / macOS 均可）
python server.py

# 生产（Linux，多线程 + 引擎池）
gunicorn -c gunicorn.conf.py server:app
```

启动后把后端环境变量 `PAPER_OCR_URL` 指向 `http://localhost:9100/ocr` 即可；服务端并发上限由 `OCR_MAX_CONCURRENCY`（默认 2）控制，超出返回 429，后端会自动转 MQ 重试。

### 5. 可选：Nginx 多副本部署

`deploy/nginx.conf` 已包含后端 8080/8081、OCR 9100/9101 双副本 upstream、WebSocket Upgrade 透传、失败自动切副本与前端 history 回退。起第二个后端实例只需：

```bash
java -jar target/ai-marking-system-0.0.1-SNAPSHOT.jar --server.port=8081
```

然后 `nginx -c /绝对路径/deploy/nginx.conf`（注意把配置里的 `root` 改成本机 `frontend/dist` 路径）。

---

## ⚙️ 关键配置速查（`src/main/resources/application.yml`）

```yaml
server:
  port: 8080

spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 100MB

# OCR 接口可插拔；留空时图片仍可上传，但解析会提示手动录入
paper-parse:
  ocr-url: ${PAPER_OCR_URL:}
  ocr-api-key: ${PAPER_OCR_API_KEY:}

# LLM 统一入口，provider 仅作日志区分
llm:
  provider: deepseek
  api-url: https://api.deepseek.com/chat/completions
  model: deepseek-chat
  api-key:

jwt:
  secret: ai-marking-system-jwt-secret-key-2026-please-change-in-production
  expiration: 1800000   # 30min（ms）

# Redis 滑动窗口，登录默认 20 次 / 60s
rate-limit:
  enabled: true
  key-prefix: "rate_limit:"
  default-limit: 20
  default-window-seconds: 60

# RabbitMQ 消费策略：手动 Ack + 预取 5 + 失败入死信
rabbitmq:
  listener:
    simple:
      acknowledge-mode: manual
      prefetch: 5
      concurrency: 2
      max-concurrency: 5
      default-requeue-rejected: false

logging:
  level:
    root: info
    com.marking.aimarkingsystem: debug
  file:
    name: logs/ai-marking-system.log
```

> ⚠️ **生产环境清单**：全部密钥（MySQL 密码 / Redis 密码 / MinIO 密钥 / JWT secret / LLM API Key）改为环境变量覆盖（`DB_PASSWORD`、`REDIS_PASSWORD`、`MINIO_SECRET_KEY`、`JWT_SECRET` 等，均可直接用同名环境变量替换 yml 默认值）；把 `mybatis-plus.configuration.log-impl` 从 `StdOutImpl` 改为 `NoLoggingImpl`；OCR 用 gunicorn 启动并前置 Nginx 做多副本；Sentinel 规则已在 `SentinelConfig` 内加载，可按需接入 Dashboard 做可视化。

---

## 🧭 Redis 高可用（主从 + 哨兵）

默认配置是单机 Redis（`localhost:6379`），满足开发与演示。生产环境建议使用 **主从 + 哨兵**：

- **1 主 1 从 + 3 哨兵**，哨兵必须为奇数且至少 3 个（`quorum=2`），避免脑裂；
- 主节点故障时由哨兵自动选举新主，Lettuce 通过哨兵重新发现主节点并重连，消除 Redis 单点风险；
- 哨兵节点无状态，建议部署在不同机器。

### 1. 启动哨兵集群

编排文件：[deploy/redis-ha/docker-compose.yml](deploy/redis-ha/docker-compose.yml)，1 主 1 从 3 哨兵，密码默认 `redispass`（可用 `REDIS_PASSWORD` 覆盖）：

```bash
cd deploy/redis-ha
docker compose up -d
docker compose ps          # 5 个容器均为 running
```

### 2. 后端接入哨兵

使用 [application-redis-ha.yml](src/main/resources/application-redis-ha.yml) 激活 `redis-ha` profile：

```bash
# 本地（哨兵映射在 127.0.0.1:26379-26381）
./mvnw spring-boot:run "-Dspring-boot.run.profiles=redis-ha"

# 生产（哨兵部署在独立服务器时用环境变量覆盖）
REDIS_SENTINEL_NODES=10.0.0.1:26379,10.0.0.2:26379,10.0.0.3:26379 \
REDIS_PASSWORD=yourpass \
java -jar target/ai-marking-system-0.0.1-SNAPSHOT.jar --spring.profiles.active=redis-ha
```

哨兵模式下 `host`/`port` 不再生效，Lettuce 从哨兵获取当前主节点地址，故障转移后自动重连。

### 3. 验证故障转移

```bash
# ① 查看当前主节点地址（应指向 redis-master 容器）
docker exec redis-ha-sentinel-1 redis-cli -p 26379 sentinel get-master-addr-by-name mymaster

# ② 模拟主节点宕机
docker stop redis-ha-master

# ③ 等待 5~15 秒（down-after-milliseconds=5000），再次查询，地址应变为副本
docker exec redis-ha-sentinel-1 redis-cli -p 26379 sentinel get-master-addr-by-name mymaster

# ④ 新主可写；旧主重启后应降级为从节点
docker exec redis-ha-replica redis-cli -a redispass SET probe ok
docker start redis-ha-master && sleep 10
docker exec redis-ha-master redis-cli -a redispass ROLE      # 输出 slave 表示已降级

# 验证完成后清理
cd deploy/redis-ha && docker compose down -v
```

### 4. 环境限制（重要）

| 部署形态 | 能否直接完成端到端验证 |
| --- | --- |
| Redis 直接装在宿主机（生产常见） | 可以，`REDIS_SENTINEL_NODES` 指向真实地址即可 |
| 后端与 Redis 同在 Docker 网络 | 可以，节点地址用容器服务名 |
| Windows Docker Desktop + 宿主跑后端 | 只能验证"哨兵选主"；落主返回的是容器 IP，WSL2 NAT 下宿主不可达，端到端自动重连请在 Linux / WSL2 内验证 |

### 故障边界与 fail-open 取舍

| 场景 | 表现 |
| --- | --- |
| 主节点宕机 | 哨兵自动选主，Lettuce 重连；切换的数秒内读取失败会短暂降级，恢复后自动回到严格校验 |
| 单个哨兵宕机 | 其余哨兵继续工作（quorum=2），无影响 |
| 哨兵集群整体不可用 | 进入 fail-open：仅校验 JWT 签名与有效期放行，**登出 / 顶号踢下线能力失效** |

哨兵只解决"单点"问题。哨兵全挂时 `RedisUtil` 的所有操作都会 fail-open（`get` 返回 null、`set` 忽略异常），JWT 鉴权退化为纯无状态模式。这是**可用性优先于强一致性**的取舍：降级期间已登出的旧 token 在自身过期前（默认 30 分钟）仍可能通过鉴权，因此生产环境不要把 JWT 过期时间设得过长，并对改密码、删数据等高危操作增加二次校验。相关取舍已在 [JwtAuthenticationFilter.java](src/main/java/com/marking/aimarkingsystem/common/security/JwtAuthenticationFilter.java) 与 `application.yml` 注释中写明。

> 若业务要求"登出必须绝对即时生效"，则需要把 Redis 改为强依赖（fail-close），代价是 Redis 故障时全站请求 401。
>
> 另外 Redis 还承载登录态、接口限流、分布式锁，Redis 整体不可用时这些能力会一并降级（限流放行、锁放行），不影响主流程可用性。

---

## 🔑 默认账号

| 用户名 | 密码 | 角色 | 侧栏菜单 |
| --- | --- | --- | --- |
| admin | 123456 | ADMIN | 系统管理 + 全部教学功能 |
| （自行注册） | —— | TEACHER | 大模型设置 / 教学资源 / AI 异步阅卷 |
| demo_student | 123456 | STUDENT | 答卷管理（在线作答） / 我的学习 |

> 教师账号通过 `/auth/register` 或管理员在系统管理页新建后赋值 `TEACHER` 角色。

---

## 🛤️ 核心业务流程

```
┌────────────────────────────────────────────────────────────────────────┐
│  试卷上传（PDF / 手动录入） → 题库 / 线上 / 线下 三种模式             │
└────────────────────┬───────────────────────────────────────────────────┘
                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│  班级 → 导入名单 Excel → 试卷下发（指定时间窗口 / 线下模式）           │
└────────────────────┬───────────────────────────────────────────────────┘
                     ▼
         ┌──────────┴──────────┐
         ▼                     ▼
┌───────────────┐      ┌──────────────────┐
│ 学生在线作答    │      │ 答卷图片上传 + OCR │
└───────┬───────┘      └────────┬───────────┘
        ▼                       ▼
┌────────────────────────────────────────────────────────────────────────┐
│  生成 SimHash 指纹 → 两两比对 → 抄袭风险报告（高风险 / 可疑 / 正常）  │
└────────────────────┬───────────────────────────────────────────────────┘
                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│  RabbitMQ 异步消费 → ReAct Agent 调大模型逐题评分 + 生成 AI 评语      │
│  （知识库检索 / 历史案例 / 得分一致性检查 多工具辅助）                │
└────────────────────┬───────────────────────────────────────────────────┘
                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│  教师复核：采纳 AI 参考分 / 逐题录分覆盖 → 保存教师最终分 + 教师评语  │
└────────────────────┬───────────────────────────────────────────────────┘
                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│  学情统计 / 知识点掌握排行 → AI 讲评建议 → 讲评 PPT 自动编排 → 导出  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 📱 移动端 / 微信访问

项目内置移动端 H5，与桌面端共用同一套接口与权限：

- 手机浏览器或微信内置浏览器打开时，自动进入 `/m/**` 移动端路由；桌面浏览器保持原布局。
- 移动端覆盖全部菜单：学生作答与成绩、教师阅卷与学情、管理员用户/角色/菜单、大模型设置等。
- 桌面端右上角「手机版」、移动端「我的 → 使用电脑版」可手动切换，选择会记录在浏览器本地。
- 微信内下载 Excel / PPT 会提示「点右上角 ··· 在浏览器中打开」。

本地用手机调试：

```bash
cd frontend
npm install
npm run dev -- --host
```

手机与电脑连接同一局域网，访问 `http://<电脑局域网IP>:5173/m/login`（例如 `http://192.168.1.10:5173/m/login`）。

生产部署要求：

- 必须使用 HTTPS 且域名已备案，微信内才能稳定访问与调用部分能力；`localhost` / 内网 IP 仅适合本地调试。
- 前端为 history 路由，Nginx 需要回退到 `index.html`：

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

- 若由 Spring Boot 直接托管 `dist`，同样需要把非静态资源请求转发到 `index.html`。
- 分享给学生的链接可直接使用 `https://你的域名/m/login`。

---

## 📦 后续优化方向

- 🧪 **单元 / 集成测试**：补齐 Mapper Service Controller 三层覆盖，CI 自动跑
- 📊 **Sentinel 可视化**：规则已在代码内加载（`SentinelConfig`），后续可接入 Dashboard 做慢调用比例熔断与实时监控
- 🔎 **Embedding / RAG**：DeepSeek Embedding + Milvus / PGVector 向量化，真正意义上的知识库检索
- 📈 **Kafka 替换 RabbitMQ**：高并发下切换，RabbitMQ 适合轻量、Kafka 适合高吞吐
- 🏗️ **微服务拆分**：auth / learning / marking / agent 独立服务 + Nacos / Sentinel 注册中心
- 🛡️ **CORS + XSS + CSRF**：前后端分离场景下的完整安全加固
- 📱 **微信小程序原生版**：在现有 H5 基础上补充小程序端，支持订阅消息与更顺滑的原生交互
- 🧠 **RAG 评测集**：建立主观题阅卷黄金答案，离线评测大模型效果
- 🪵 **审计日志 + 链路追踪**：OpenTelemetry 接入 AI 阅卷全链路追踪

---

## 🤝 提交 Issue / PR

欢迎提 Issue 讨论问题，PR 建议先开 draft 分支；SQL 脚本与后端接口协议任何变更都在 README 同步更新。

**规范**：分支命名 `feat/*` / `fix/*` / `refactor/*` / `docs/*`，Commit message 建议 Conventional Commits。

---

## 📄 License

MIT © 项目作者，详见 [`./LICENSE`](./LICENSE)。
