# 🎬 AI 小说转剧本工具

> 基于 DeepSeek 大模型的智能小说→剧本转换工具。AI 自动分章、提取角色、生成结构化 YAML 剧本，支持多人协作、AI 生图、多格式导出。

[![CI](https://github.com/yuramure77/ai-novel-to-script/actions/workflows/ci.yml/badge.svg)](https://github.com/yuramure77/ai-novel-to-script/actions/workflows/ci.yml)

---

## ✨ 功能特性

### 📖 智能剧本生成
- **自动分章** — 支持中文（第一/二/三章）和英文（Chapter 1/2/3）章节标记
- **AI 角色提取** — DeepSeek 自动识别角色、性格特征、人物关系
- **逐章场景生成** — 每个章节独立生成场景 beats（对白、动作、氛围）
- **SSE 流式输出** — 实时显示生成进度，支持断点续传（resume）
- **100万字上限** — 支持超长篇小说全文处理

### 🎨 AI 图像生成
- **混元 hy-image-lite** — 腾讯混元大模型图生，TokenHub API
- **DeepSeek 丰富描写** — 先生成个性化外貌/场景描写，再送入混元生图
- **年龄智能识别** — 自动检测角色年龄阶段，祖母不会生成年轻形象
- **反同质化** — 性格→视觉元素转化（如「孤傲」→冷峻神情），角色不撞脸
- **COS 永久存储** — 生成图片自动上传腾讯云 COS，获得永久 URL
- **图片版本历史** — 每次生图自动存档，支持回滚切换

### 👥 多人协作
- **项目分享** — 通过用户名添加协作者
- **权限分级** — 管理员（可编辑/生成/管理协作者）vs 只读（仅查看）
- **权限标签** — 项目列表显示「🔧 协管」「👁 只读」标识
- **只读模式保护** — 只读用户隐藏编辑/生成按钮，禁用快捷键

### 📄 多格式导出
- **YAML** — 结构化剧本原始格式
- **Markdown** — 适合阅读和分享的排版
- **Fountain** — 标准剧本格式，可直接导入 Final Draft 等专业软件

### 🎭 前端特效
- **暗色/亮色切换** — 主题自适应
- **毛玻璃弹窗** — 现代 UI 设计
- **聚光灯动画** — 交互反馈
- **响应式适配** — 手机端完整适配
- **胶片/打字机/视差** — 电影感视觉体验

### 🔧 DevOps
- **GitHub Actions CI** — push 自动编译后端（Maven）+ 前端（Vite）
- **Webhook 热部署** — GitHub push → webhook → git pull → build → restart
- **systemd 服务** — 崩溃自动重启，journalctl 日志
- **Nginx + HTTPS** — Let's Encrypt 自动证书
- **健康检查** — Spring Boot Actuator `/actuator/health`

---

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- npm 9+

### 1. 配置 API Key

```bash
export DEEPSEEK_API_KEY=sk-your-deepseek-key
export TOKENHUB_API_KEY=sk-your-tokenhub-key      # AI 生图（可选）
export TENCENT_SECRET_ID=your-secret-id            # COS 存储（可选）
export TENCENT_SECRET_KEY=your-secret-key          # COS 存储（可选）
export COS_BUCKET=your-bucket                      # COS 存储桶名
export COS_REGION=ap-guangzhou                     # COS 地域
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端启动后访问 http://localhost:8080

- H2 控制台：http://localhost:8080/h2-console
- 健康检查：http://localhost:8080/actuator/health
- 数据库文件：`backend/data/scripttool.mv.db`

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端启动后访问 http://localhost:3000

---

## 🏗 技术架构

```
┌─────────────────────────┐     ┌─────────────────────────┐
│      前端 (Vue 3)        │────▶│     后端 (Spring Boot)   │
│                         │     │                         │
│  Vue 3 + Element Plus   │     │  Spring MVC             │
│  Vue Router (路由守卫)   │     │  Spring Security + JWT  │
│  Axios (HTTP Client)    │     │  Spring Data JPA + H2   │
│  Vite (构建工具)         │     │  Spring Actuator        │
└─────────────────────────┘     └───────────┬─────────────┘
                                            │
                          ┌─────────────────┼─────────────────┐
                          ▼                 ▼                 ▼
                    DeepSeek API      混元 hy-image-lite   腾讯云 COS
                   (文本生成)         (AI 图像生成)       (图片持久化)
```

### 核心流程

```
小说文本 (粘贴/上传)
    │
    ▼
 分章服务 (ChapterSplitService)
  正则匹配"第X章" / "Chapter X"
    │
    ▼
 DeepSeek AI 分析 (ScriptGenService)
  ├─ 第一步: 全文提取角色信息
  └─ 第二步: 逐章生成场景 beats
    │
    ▼
 YAML 生成 (YamlGeneratorService)
  SnakeYAML 组装输出
    │
    ▼
 版本存储 (H2 数据库)
  script_versions 表
```

### 生图流程

```
角色/场景数据
    │
    ▼
 DeepSeek 造型师 → 个性化外貌描写（含年龄/性格→视觉）
    │
    ▼
 混元 hy-image-lite → AI 生成图片
    │
    ▼
 COS 上传 → 永久 URL → image_versions 表
```

---

## 📡 API 文档

### 认证接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录，返回 JWT |

### 项目管理（需 Bearer Token）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/projects` | 项目列表（含分享项目） |
| GET | `/api/projects/{id}` | 项目详情 + 原文 |
| POST | `/api/projects` | 创建项目 |
| PUT | `/api/projects/{id}/rename` | 重命名 |
| PUT | `/api/projects/{id}/move` | 移动到文件夹 |
| DELETE | `/api/projects/{id}` | 删除（需 ADMIN） |
| POST | `/api/projects/{id}/split` | 分章预览 |
| POST | `/api/projects/{id}/generate` | AI 生成剧本 |
| GET | `/api/projects/{id}/generate/stream` | SSE 流式生成 |

### 协作接口（需 Bearer Token）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/projects/{id}/collaborators` | 协作者列表 |
| POST | `/api/projects/{id}/collaborators` | 添加协作者 |
| PUT | `/api/projects/{id}/collaborators/{cid}` | 修改权限 |
| DELETE | `/api/projects/{id}/collaborators/{cid}` | 移除协作者 |
| GET | `/api/projects/shared` | 分享给我的项目 |

### AI 生图接口（需 Bearer Token）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/image/character` | 生成角色形象图 |
| POST | `/api/ai/image/scene` | 生成场景图 |
| GET | `/api/ai/image/versions` | 图片版本历史 |
| GET | `/api/ai/image/restore` | 恢复项目所有图片 |

### 剧本接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/scripts/project/{id}/versions` | 版本列表 |
| GET | `/api/scripts/project/{id}/latest` | 最新版本 |
| GET | `/api/scripts/{versionId}/yaml` | 下载 YAML |

### 系统接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/actuator/health` | 健康检查（无需认证） |
| GET | `/actuator/info` | 应用信息（无需认证） |

### 统一返回格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

---

## 🗄 数据库表设计

### users
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| username | VARCHAR(50) UNIQUE | 用户名 |
| password | VARCHAR(255) | BCrypt 密码 |
| nickname | VARCHAR(100) | 昵称 |
| created_at | TIMESTAMP | 创建时间 |

### projects
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| user_id | BIGINT FK | 所有者 |
| folder_id | BIGINT | 文件夹 |
| title | VARCHAR(200) | 标题 |
| original_text | CLOB | 原文 |
| chapter_count | INT | 章节数 |
| status | VARCHAR(20) | DRAFT/PROCESSING/COMPLETED |

### script_versions
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| project_id | BIGINT FK | 项目 |
| version_number | INT | 版本号 |
| yaml_content | CLOB | YAML 内容 |
| created_at | TIMESTAMP | 时间 |

### collaborations
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| project_id | BIGINT FK | 项目 |
| user_id | BIGINT FK | 用户 |
| permission | VARCHAR(20) | ADMIN/READ |

### image_versions
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| project_id | BIGINT FK | 项目 |
| image_type | VARCHAR(20) | CHARACTER/SCENE |
| target_index | INT | 角色/场景序号 |
| url | VARCHAR(1024) | 图片 URL |
| prompt | VARCHAR(1024) | 生成提示词 |
| created_at | TIMESTAMP | 时间 |

---

## 🚢 生产部署

### 一键部署

```bash
git clone git@github.com:yuramure77/ai-novel-to-script.git
cd ai-novel-to-script
chmod +x deploy/deploy.sh build.sh
sudo bash deploy/deploy.sh your-domain.com
```

### 手动部署

```bash
# 构建（前后端一起）
bash build.sh

# 复制 JAR 到服务器
cp backend/target/ai-novel-to-script-1.0.0.jar /opt/ai-novel-to-script/app.jar

# systemd 服务
sudo systemctl restart novel-script
```

### 环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `DEEPSEEK_API_KEY` | ✅ | DeepSeek API 密钥 |
| `TOKENHUB_API_KEY` | 可选 | 混元生图密钥（不配则降级） |
| `JWT_SECRET` | 生产必填 | JWT 签名密钥 |
| `TENCENT_SECRET_ID` | 可选 | COS 密钥 ID |
| `TENCENT_SECRET_KEY` | 可选 | COS 密钥 |
| `COS_BUCKET` | 可选 | COS 存储桶名 |
| `COS_REGION` | 可选 | COS 地域（默认 ap-guangzhou） |
| `PORT` | 否 | 服务端口（默认 8080） |

### 管理命令

```bash
sudo systemctl status novel-script    # 查看状态
sudo journalctl -u novel-script -f    # 实时日志
sudo systemctl restart novel-script   # 重启
curl http://localhost:8080/actuator/health  # 健康检查
```

---

## 📁 项目结构

```
ai-novel-to-script/
├── .github/workflows/ci.yml           # GitHub Actions CI
├── backend/                           # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/scripttool/
│       ├── config/                    # JWT, Security, DeepSeek, COS
│       ├── controller/                # Auth, Project, Script, AI, Export, Chat, File, Folder, Deploy, Collaboration
│       ├── model/entity/              # User, Project, ScriptVersion, Folder, Collaboration, ImageVersion
│       ├── model/dto/                 # ApiResponse, ProjectResponse 等
│       ├── repository/                # JPA Repository 接口
│       └── service/                   # ChapterSplit, ScriptGen, Image, COS, Collaboration 等
├── frontend/                          # Vue 3 前端
│   └── src/
│       ├── api/                       # Axios API 封装
│       ├── components/                # ShareDialog, YamlViewer
│       ├── router/                    # Vue Router + 路由守卫
│       └── views/                     # Login, Home, ProjectList, ScriptEditor
├── deploy/                            # systemd, Nginx, webhook 脚本
├── build.sh                           # 一键构建脚本
└── README.md
```

---

## 📄 YAML Schema

详见 [docs/yaml-schema.md](docs/yaml-schema.md)

---

## 🏆 七牛云 × XEngineer 暑期实训营

本项目为 2026 年七牛云 XEngineer 暑期实训营 **题目三：AI 小说转剧本** 参赛作品。
