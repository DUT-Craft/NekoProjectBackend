# Minecraft Club Summer Project Hub

这是猫娘社 / Minecraft 社团暑假项目站。访客可以浏览公开项目、查看想法墙、投稿项目、提交想法、给项目留言和申请加入项目。当前版本是 Nuxt/Vue 前端，包含从 `nekoFrontend` 接入的登录页面和登录校验中间件。

## 技术栈

- Nuxt 4
- Vue 3
- TypeScript
- Tailwind CSS
- Naive UI
- 本地开发默认使用 `server/api` 读写 `data/` 里的 JSON 文件

## 本地运行

```bash
npm install
npm run dev
```

默认访问：

```text
http://localhost:3000/projects
```

## 常用页面

- `/projects`：网站首页
- `/projects/groud`：全部公开项目
- `/submit`：投稿项目 / 提交想法
- `/ideas`：想法墙
- `/projects/:id`：项目详情、评论、加入申请
- `/login`：nekoFrontend 登录页
- `/register`：nekoFrontend 注册页
- `/user-center`：登录后的用户中心
- `/pve-users`：PVE 用户管理页
- `/virtual-machines`：虚拟机管理页

公开项目站页面默认不强制登录，方便外部同学直接访问。

## 环境变量

复制 `.env.example` 为 `.env`：

```env
NUXT_PUBLIC_API_BASE=/api
NUXT_PUBLIC_AUTH_CHECK_ENABLED=false
LOCAL_DATA_DIR=./data
```

说明：

- `NUXT_PUBLIC_API_BASE`：前端 API 根路径，本项目默认使用 Nuxt 自带的 `/api`。
- `NUXT_PUBLIC_AUTH_CHECK_ENABLED`：是否启用登录拦截。设为 `false` 时公开项目站可直接访问。
- `LOCAL_DATA_DIR`：本地 JSON 数据目录，默认是 `./data`。

## 数据与安全

本地数据保存在 `data/`，包括项目、想法、申请、评论、动态和操作记录。这个目录已写入 `.gitignore`，不会上传到 GitHub。

不要提交这些内容：

- `.env`
- `.env.local`
- `data/`
- `node_modules/`
- `.nuxt/`
- `.output/`
- `.next/`
- `_local-only/`

## 部署到 Vercel

1. 把代码推送到 GitHub。
2. 在 Vercel 导入仓库。
3. Framework Preset 选择 Nuxt，通常 Vercel 会自动识别。
4. 按需添加环境变量。
5. 部署后访问 `/projects`。

如果你希望公开项目站不需要登录，生产环境也要设置：

```env
NUXT_PUBLIC_AUTH_CHECK_ENABLED=false
```

## 本地归档

旧版 Next.js 代码和暂时未使用的素材已放到 `_local-only/unused-before-github-20260704/`。该目录不会上传到 GitHub，以后需要时可以从本机找回。
