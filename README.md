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

访问码通过 APP_ACCESS_CODE 注入。空访问码会关闭接口的访问码校验，部署时应提供强访问码。模型调用已经发出后，逻辑取消不保证立即中断上游请求。

## 配置隔离

API Key、数据库密码与访问码通过运行环境提供。仓库仅包含空值或占位值的示例文件。.env、config/、私钥、证书、日志、上传文件、备份和构建产物由 .gitignore 排除。

仅与原服务器目录和旧服务配置绑定的运维脚本保留在本地，公开仓库包含通用的 deploy/start.sh。生产配置可由服务 EnvironmentFile 提供，部署环境需要自行配置反向代理和服务启动。

静态前端位于 src/main/resources/static，后端源码与测试位于 src/main/java、src/test/java。
