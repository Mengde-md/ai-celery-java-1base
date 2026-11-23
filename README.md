# AICelery 智序 AI 任务中台

基于 Java 21、Spring Boot、Spring AI、MyBatis 和 MySQL 的异步 AI 任务服务，包含原生静态任务面板。支持提交任务、轮询状态、查看结果和逻辑取消，任务租约与执行记录存储在数据库中。

## 构建与验证

需要 JDK 21 和 Maven。在项目根目录执行：

    mvn -B -ntp verify

现有单元测试使用模拟模型对象，不需要真实模型 Key 或正在运行的数据库。GitHub Actions 使用 Java 21 执行同一验证命令，也支持手动运行。

## 本地配置

将 .env.example 复制为被忽略的 .env，填写本地数据库密码、独立的 MySQL root 密码和强访问码。Docker Compose 会读取 .env 中的数据库密码：

    docker compose up -d mysql

Java / Spring Boot 不会自动加载 .env，需要在启动进程的环境变量或服务的 EnvironmentFile 中设置对应值。PowerShell 示例使用占位值，运行前替换：

    $env:DB_PASSWORD = 'replace-with-a-local-database-password'
    $env:APP_ACCESS_CODE = 'replace-with-a-long-random-access-code'
    $env:DEEPSEEK_ENABLED = 'true'
    $env:DEEPSEEK_API_KEY = 'replace-with-your-provider-key'
    mvn spring-boot:run

默认监听 127.0.0.1:8080，浏览器打开该地址访问任务面板。数据库连接使用 UTC，Flyway 在启动时执行 src/main/resources/db/migration 中的迁移。数据库可通过 DB_URL、DB_USERNAME、DB_PASSWORD 配置，模型配置见 .env.example 和 application-ai.yml。

访问码通过 APP_ACCESS_CODE 注入。空访问码会关闭接口的访问码校验，部署时应提供强访问码。

## 提交与查询任务

`POST /api/v1/tasks` 成功后，服务已保存任务。客户端应保存响应中的 `data.taskId`，使用 `GET /api/v1/tasks/{taskId}` 轮询，直到状态变为 `SUCCEEDED`、`FAILED` 或 `CANCELLED`。

每次成功提交都会生成新的任务 ID。即使沿用相同的 `X-Request-Id`，再次发送创建请求也可能触发第二次模型调用。已取得任务 ID 时，轮询超时后应使用该 ID 重试查询。

## 取消任务

调用 `POST /api/v1/tasks/{taskId}/cancel` 后，先检查响应中的业务码 `code`。成功时 `code` 为 `0`，`data.status` 表示任务的最终状态。待执行或执行中的任务取消成功后返回 `CANCELLED`。如果任务已经进入 `SUCCEEDED`、`FAILED` 或 `CANCELLED`，重复取消会返回已有状态。任务不存在时，业务码为 `20006`。

取消正在执行的任务后，上游模型请求可能继续运行。服务会丢弃该任务随后返回的模型结果。

## 模型路由

提交任务时选择的 `provider` 和 `model` 会随任务保存。执行时，工作进程按已保存的厂商名称选择已启用的适配器，模型名称也沿用提交时的值。处理队列积压时，执行实例需要继续启用这些任务所选的厂商。找不到对应适配器时，任务会以不可重试的 `INVALID_TASK_PAYLOAD` 失败。

模型调用成功后，服务按结果正文的 UTF-8 字节数检查 `app.ai.max-result-bytes`，默认上限为 1 MiB（1,048,576 字节）。正好达到上限的正文可以保存。超出上限时，任务以 `RESULT_TOO_LARGE` 失败，调度器不会自动重试。排查中文或含表情的长结果时，应按 UTF-8 编码后的大小估算。

## 调度与租约

默认配置每轮在 800 到 1200 毫秒之间随机等待。调整 `app.scheduler.min-poll-interval` 时，最小值必须至少为 1 毫秒，且不能超过 `app.scheduler.max-poll-interval`。`TASK_CONCURRENCY` 默认为 4，控制每个实例的并发槽位和单次最多领取的任务数。

`app.scheduler.lease-duration` 默认为 4 分钟，应为模型调用和结果写回留出时间。工作进程退出后，恢复任务默认每 30 秒检查一次过期租约。

## 请求追踪

调用方可以在 HTTP 请求中提供 `X-Request-Id`，用于关联 API 响应和服务端日志。ID 长度为 1 到 128 个字符，只允许 ASCII 字母、数字及 `.`、`_`、`:`、`-`。省略请求头或值不符合规则时，服务端会生成 UUID。排查问题时请记录响应头中的 `X-Request-Id`。

## 配置隔离

API Key、数据库密码与访问码通过运行环境提供。仓库仅包含空值或占位值的示例文件。.env、config/、私钥、证书、日志、上传文件、备份和构建产物由 .gitignore 排除。

仅与原服务器目录和旧服务配置绑定的运维脚本保留在本地，公开仓库包含通用的 deploy/start.sh。生产配置可由服务 EnvironmentFile 提供，部署环境需要自行配置反向代理和服务启动。

静态前端位于 src/main/resources/static，后端源码与测试位于 src/main/java、src/test/java。
