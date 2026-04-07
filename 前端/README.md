# nba-analytics-frontend

本仓库为 [**nba-analytics-pack**](https://github.com/Speas-y/nba-analytics-pack) 单体仓库中 **`前端/`** 目录的镜像：同一套 **Vue 3 + Vite** 代码，便于单独部署到 **Vercel / Netlify / 静态托管**，与 **Spring Boot API**（`/api`）分离发布。

## 技术栈

- Vue 3、Vue Router、Pinia  
- Vite 5  
- Chart.js（图表）

## 快速开始

```bash
npm install
cp .env.example .env
npm run dev
```

按 `.env` 中 **`VITE_API_BASE`** 指向你的后端根地址（须包含 **`/api`**，与 Spring Boot 的 `server.servlet.context-path` 一致），例如：

- 本地：`http://localhost:3000/api`
- 线上：`https://<你的-Render-服务名>.onrender.com/api`（示例见主仓库 `render.yaml`）

可选环境变量见 `.env.example`（如 `VITE_STATS_SCOPE`）。

## 脚本

| 命令 | 说明 |
|------|------|
| `npm run dev` | 开发服务器（热更新） |
| `npm run build` | 生产构建，输出到 `dist/` |
| `npm run preview` | 本地预览构建结果 |

## 与主仓库同步

开发通常在 monorepo 的 `前端/` 进行；发布本独立库时，请将 **`前端/`** 目录内容同步到本仓库（例如通过 CI、或主仓库文档中的 rsync/复制流程），保持两边行为一致。

## 相关链接

- 全栈与爬虫仓库：[nba-analytics-pack](https://github.com/Speas-y/nba-analytics-pack)  
- 后端本地/部署说明：见主仓库 `README.md` 与 `spring-backend/.env.example`
