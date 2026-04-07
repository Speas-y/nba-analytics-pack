# nba-analytics-pack

NBA 数据分析 **单体仓库**：**Vue 3 + Vite** 前端、**Spring Boot 3** API、**Python** 爬虫与静态 JSON 数据、**Docker** 部署配置。线上后端可参考 [Render](https://render.com/)（见 `render.yaml`），前端可托管在 Vercel 等静态平台。

> 更短的目录说明见根目录 [`README-使用说明.txt`](README-使用说明.txt)。

## 仓库结构

| 目录 | 说明 |
|------|------|
| [`spring-backend/`](spring-backend/) | Spring Boot API（`server.servlet.context-path=/api`，默认端口见 `application.yml`） |
| [`前端/`](前端/) | Vue 3 单页应用；与 GitHub 独立库 [**nba-analytics-frontend**](https://github.com/Speas-y/nba-analytics-frontend) 内容对应，发布前端时需同步该库 |
| [`爬虫/`](爬虫/) | Basketball-Reference / NBA Stats 等数据源，`output/` 为生成的 JSON/CSV |
| [`nba-pc-analytics/`](nba-pc-analytics/) | 球员名中英映射等静态资源，供 API 与构建流程使用 |
| [`scripts/`](scripts/) | 运维脚本（如 `crawler-update-and-push.sh`） |
| [`Dockerfile`](Dockerfile) | 构建可运行 Spring Boot + 爬虫运行环境的镜像（用于 Render 等） |
| [`render.yaml`](render.yaml) | Render 部署示例与环境变量说明 |
| [`更新`](更新) | 仓库根目录一键：跑爬虫 → 提交数据 → `git push` 到本仓库 |

## 环境要求

- **JDK 17**、**Maven 3.9+**
- **Node.js 18+**（前端）
- **Python 3.10+**（爬虫虚拟环境）
- 本地开发 API 时：**MySQL**（或使用 `SPRING_PROFILES_ACTIVE=h2` 等配置，见 `spring-backend` 内说明）

## 本地运行

### 1. 后端 API

```bash
cd spring-backend
cp .env.example .env   # 按本机修改数据库与 JWT 等
mvn spring-boot:run
```

- 服务地址默认为 `http://localhost:3000`，API 前缀为 **`/api`**（例如健康/公开接口：`http://localhost:3000/api/public/nba/seasons`）。
- 环境变量含义见 `spring-backend/.env.example`（含 Render、H2、Postgres、爬虫路径等说明）。

### 2. 前端

```bash
cd 前端
npm install
cp .env.example .env   # 设置 VITE_API_BASE，与后端 /api 一致
npm run dev
```

- 开发服务器一般为 Vite 默认端口（如 `5173`），需与 `FRONTEND_ORIGIN`、`VITE_API_BASE` 跨域配置一致。

### 3. 爬虫与数据

```bash
cd 爬虫
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python nba_player_crawler.py --out-dir ./output
```

- 详细参数见爬虫内脚本说明；遇 BR 403 等可在 shell 中配置 `HTTPS_PROXY`（见 `spring-backend/.env.example` 注释）。

### 4. 运营侧：更新数据并推送到本仓库

在**仓库根目录**执行（会将新数据提交并推送到 **origin**，即本仓库）：

```bash
./更新
# 或: bash scripts/crawler-update-and-push.sh
# 可选参数会传给爬虫，例如: ./更新 --season 2025-26
```

## Docker / Render

- 使用根目录 [`Dockerfile`](Dockerfile) 构建镜像；容器内为 **JRE 17** + **Python 3** + 爬虫目录与 `nba-pc-analytics` 资源。
- [`render.yaml`](render.yaml) 中注释说明了 **无信用卡** 时推荐 `SPRING_PROFILES_ACTIVE=h2`、以及 `FRONTEND_ORIGIN`、`JWT_ACCESS_SECRET` 等；健康检查路径示例：`/api/public/nba/seasons`。

## 相关仓库

| 仓库 | 说明 |
|------|------|
| [nba-analytics-pack](https://github.com/Speas-y/nba-analytics-pack) | 本仓库：全栈 + 爬虫 + 数据 |
| [nba-analytics-frontend](https://github.com/Speas-y/nba-analytics-frontend) | 仅前端，与 `前端/` 目录同步 |

## 许可与贡献

内容以项目内已有许可证与约定为准；提交前请确保不提交本机密钥与大型无关二进制文件（参见各目录 `.gitignore`）。
