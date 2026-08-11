# 猫娘社主站后端

猫娘社 Minecraft 主站的 Kotlin/Spring Boot 单体后端。项目保留 Controller、Service、Repository、JPA Entity 的原有分层，使用 JWT 认证、Flyway 数据库迁移、PostgreSQL 持久化和 Redis 登录令牌。

配套前端仓库：[DUT-Craft/neko-mc-hub](https://github.com/DUT-Craft/neko-mc-hub)。本仓库是 Gradle 后端项目，根目录应使用 `gradlew` 或 `gradlew.bat`；`npm` 命令属于前端仓库，不要在这里执行。

## 技术要求

- JDK 25
- 项目自带 Gradle Wrapper，不要求全局安装 Gradle
- 生产环境：PostgreSQL 15 或更高版本
- 生产环境：Redis 7 或更高版本
- HTTPS 反向代理，例如 Nginx 或 Caddy

项目没有绑定任何个人电脑的 JDK 绝对路径。打开 IntelliJ IDEA 后，将 Project SDK 和 Gradle JVM 都选择为 JDK 25。

## 本地开发

默认 `local` Profile 使用 H2 内存数据库和内存登录令牌，不需要 PostgreSQL 或 Redis。

Windows：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-25"
.\gradlew.bat clean test
.\gradlew.bat bootRun
```

Linux/macOS：

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew clean test
./gradlew bootRun
```

本地健康检查：`http://127.0.0.1:8080/actuator/health`

本地默认管理员为 `admin / admin12345`，只用于本机开发。不要把该账号用于生产环境。

本地通过 Nuxt 的同源 `/api` 代理联调时，将 `PUBLIC_BASE_URL` 留空，图片地址会返回 `/api/public/media/**` 相对路径，因此浏览器只需要访问前端网址。只有浏览器直接访问后端或前后端使用不同域名时，才填写完整公开地址。

## 生产环境准备

创建 PostgreSQL 数据库和用户，例如：

```sql
CREATE USER neko WITH PASSWORD '替换为数据库强密码';
CREATE DATABASE neko_main_site OWNER neko ENCODING 'UTF8';
```

Redis 建议只监听本机或私有网络，并配置访问密码。数据库和 Redis 都不应直接暴露到公网。

将 `.env.production.example` 复制为 `.env`，修改文件权限并填写所有必需值：

```bash
cp .env.production.example .env
chmod 600 .env
```

必须配置：

- `SPRING_PROFILES_ACTIVE=prod`
- `DATABASE_URL`、`DATABASE_USERNAME`、`DATABASE_PASSWORD`
- `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`
- 至少 32 个字符的随机 `JWT_SECRET`
- 公开 HTTPS 地址 `PUBLIC_BASE_URL`
- 明确的 HTTPS 前端来源 `CORS_ALLOWED_ORIGINS`
- 首次启动使用的 `BOOTSTRAP_ADMIN_*`
- 可持久化并纳入备份的 `FILE_STORAGE_PATH`

生成 JWT 密钥示例：

```bash
openssl rand -base64 48
```

## 首次管理员

全新的生产数据库没有默认管理员。第一次启动前必须填写：

```dotenv
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=替换为至少12个字符的强密码
BOOTSTRAP_ADMIN_EMAIL=admin@example.com
BOOTSTRAP_ADMIN_DISPLAY_NAME=站点管理员
```

应用只会在数据库中不存在可用管理员时创建该账号。创建成功并确认能够登录后，可以从服务环境中移除 `BOOTSTRAP_ADMIN_*`，以后由后台的用户管理接口维护账号。

如果没有管理员且未提供有效引导账号，生产启动会主动失败，避免生成一个无法管理的空站点。

## 构建与启动

```bash
./gradlew clean test bootJar
java -jar build/libs/NekoMainSite-0.0.1-SNAPSHOT.jar
```

应用从当前工作目录读取 `.env`，因此生产服务的工作目录必须是后端项目目录。建议使用 systemd、Supervisor 或容器平台托管，并在变更前先运行测试。

生产默认监听 `127.0.0.1:8080`：

```dotenv
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
```

只有在容器网络或受保护的内网中确实需要时，才将 `SERVER_ADDRESS` 改为 `0.0.0.0`。公网访问应通过 HTTPS 反向代理进入 `/api`。

## 与 Nuxt 前端连接

推荐前后端使用同一个公开域名。例如站点是 `https://mc.example.com`：

```dotenv
CORS_ALLOWED_ORIGINS=https://mc.example.com
PUBLIC_BASE_URL=https://mc.example.com
```

Nginx 将 `/api/**` 转发到 `127.0.0.1:8080`，其余请求转发到 Nuxt。前端公开 API 地址留空，内部地址使用 `http://127.0.0.1:8080`。

图片单文件上限为 8 MB，请在 Nginx 对应站点中配置 `client_max_body_size 10m;`，与后端 `MAX_UPLOAD_FILE_SIZE=8MB`、`MAX_UPLOAD_REQUEST_SIZE=10MB` 保持一致。超过限制时接口会返回 HTTP 413 和中文提示。

如果前后端使用同一注册域名下的不同子域名，`CORS_ALLOWED_ORIGINS` 填前端完整 HTTPS 来源，`PUBLIC_BASE_URL` 填后端公开 HTTPS 来源。认证 Cookie 使用 `SameSite=Strict`，因此完全不同站点的两个域名不支持成员登录和后台操作。禁止使用 `*`。

## 数据与文件

- Flyway 在启动时自动执行 `src/main/resources/db/migration` 中的 V1-V6 迁移
- Hibernate 仅验证表结构，不自动修改生产数据库
- 上传图片保存在 `FILE_STORAGE_PATH`
- 单张图片最大 8 MB，支持 JPG、JPEG、PNG 和 WebP
- 数据库和文件目录必须一起备份，否则正文图片可能丢失
- Redis 只存登录令牌，不替代数据库备份

建议至少执行：

- PostgreSQL 每日备份
- `FILE_STORAGE_PATH` 每日增量备份
- `.env` 单独安全保管，不放入源码包或 Git
- 定期验证备份恢复流程

## 主要接口

公共接口：

- `GET /api/public/home`
- `GET /api/public/servers`、`/servers/{slug}`
- `GET /api/public/activities`、`/activities/{slug}`
- `GET /api/public/announcements`、`/announcements/{slug}`
- `GET /api/public/wiki`、`/wiki/{slug}`
- `GET /api/public/history`、`/history/{slug}`
- `GET /api/public/contacts`
- `GET /api/public/media/{filename}`

认证接口：

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`
- `GET /api/auth/blessing/start`
- `GET /api/auth/blessing/callback`

管理员接口：

- `/api/admin/content/{servers|activities|announcements|wiki|history}`
- `/api/admin/content/drafts/{draftId}`
- `/api/admin/content/drafts/{draftId}/media`
- `/api/admin/contacts`
- `/api/admin/servers/{id}/refresh`
- `/api/admin/audit-logs`
- `/api/users/**`

响应统一使用：

```json
{
  "status": 200,
  "message": "OK",
  "data": {}
}
```

## 上线验收

部署完成后依次检查：

```bash
curl -fsS https://mc.example.com/actuator/health
curl -fsS https://mc.example.com/api/public/home
```

然后在浏览器验证：

- 管理员可以登录和退出
- 首页显示所有已发布服务器，有人在线的服务器排在前面
- 当前每周活动排在长期活动之前
- 公告、Wiki 和历史详情可以打开
- 草稿可以保存、预览、发布、下线和恢复上一版
- Word 风格正文的标题、列表、链接和图片可以往返保存
- 危险协议链接和未知正文字段会被后端拒绝
- 图片上传后公开地址使用正式 HTTPS 域名
- 重启服务后数据库内容和上传文件仍然存在

只有上述检查全部通过后，才应切换正式域名流量。
