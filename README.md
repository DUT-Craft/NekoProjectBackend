# NekoProjectBackend

猫娘社 Minecraft 夏令营项目站的后端服务。项目方可以通过项目控制密码维护自己的项目；总管理和项目管理账号通过 JWT 管理项目、想法、评论、动态与申请。

## 技术栈

- Kotlin 2.3.21
- Spring Boot 4.1.0
- Spring WebMVC + Jetty
- Spring Data JPA / PostgreSQL
- Spring Data Redis
- Spring Security + JWT
- 本地磁盘文件存储（可替换为对象存储实现）

## 环境要求

- JDK 25
- PostgreSQL
- Redis

开发配置从项目根目录的 `.env` 读取。请先复制示例文件并替换数据库、Redis、JWT 和邮件配置：

```powershell
Copy-Item .env.example .env
```

## 本地运行

本地默认使用 `ddl-auto=create`，只适合没有需要保留的数据的开发数据库：

```powershell
.\gradlew.bat bootRun
```

默认 API 地址：`http://localhost:8080`。

常用验证命令：

```powershell
.\gradlew.bat clean test bootJar --no-daemon
```

测试使用 H2，不需要本地 PostgreSQL 或 Redis；应用本身运行时仍需要这两个服务。

## 生产部署

生产环境必须设置 `SPRING_PROFILES_ACTIVE=prod`。生产 profile 会：

- 将 Hibernate DDL 策略固定为 `validate`；
- 关闭演示数据 Seeder 和默认管理员自动创建；
- 隐藏健康检查详情；
- 强制 refresh Cookie 使用 HTTPS 和 HttpOnly；
- 要求使用明确的 CORS 来源，不允许 `*`。
- 启动时拒绝缺失、过短或示例占位的 `JWT_SECRET`，并校验令牌有效期。

至少配置以下变量：

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://host:5432/database
DB_USERNAME=...
DB_PASSWORD=...
REDIS_HOST=...
REDIS_PORT=6379
REDIS_PASSWORD=...
JWT_SECRET=至少 32 字节的随机值
CORS_ALLOWED_ORIGINS=https://你的前端域名
FILE_BASE_URL=https://你的后端域名
FILE_STORAGE_PATH=/绝对路径/storage
ADMIN_SEED_ENABLED=false
SEED_ENABLED=false
```

现有数据库在切换生产 profile 前，先执行 [database/migrations/20260719_project_hub.sql](database/migrations/20260719_project_hub.sql)。脚本是幂等的，补充项目封面、项目进度、申请拒绝原因和匿名追踪码字段，并创建相应索引。项目当前没有自动执行迁移工具，因此需要由部署方手动执行 SQL，例如：

```powershell
psql -h <host> -U <username> -d <database> -f database/migrations/20260719_project_hub.sql
```

文件存储目录和审计日志目录必须由运行服务的用户创建并授予写权限。生产环境不要把 `.env`、`storage/`、`logs/` 或真实数据库数据放入 Git。

## 主要接口

- `/api/auth/**`：注册、登录、刷新令牌、注销和密码管理
- `/api/project/object-items/**`：公开项目、评论和加入申请
- `/api/project/minds/**`：公开想法和匿名投稿状态查询
- `/api/admin/object-items/**`、`/api/admin/minds/**`：JWT 管理接口
- `/api/admin/project/object-items/**`：项目方控制密码管理接口
- `/api/files/**`：文件上传、公开图片读取和私有文件下载

匿名投稿成功后返回一次性追踪码。查询状态时使用请求头 `X-Submission-Tracking-Token`；项目方管理接口使用 `X-Project-Control-Password` 请求头的读取、删除和图片上传接口，避免敏感值进入 URL。

## 安全约定

- 新项目控制密码使用 BCrypt 保存，历史明文密码仅用于兼容校验。
- 公开接口只返回已公开项目和 `APPROVED` 想法、评论、动态。
- 生产环境 PostgreSQL 使用手工迁移脚本和 `ddl-auto=validate`，禁止用 `create` 或 `update`。
- 新上传拒绝 SVG；历史 SVG 强制下载，并设置响应安全头。
