# 🎬 AI 小说转剧本工具

> 基于 DeepSeek 大模型的智能小说→剧本转换工具。AI 自动分章、提取角色、生成结构化 YAML 剧本，支持多人协作、AI 生图、多格式导出。

📺 **[Demo 演示视频 — Bilibili](https://www.bilibili.com/video/BV1fvEt6oEZJ/)**

[![CI](https://github.com/yuramure77/ai-novel-to-script/actions/workflows/ci.yml/badge.svg)](https://github.com/yuramure77/ai-novel-to-script/actions/workflows/ci.yml)

---

## 📄 YAML 剧本格式

> 🎯 **核心输出格式** — 结构化剧本 Schema，字段详解、设计原因、完整示例和导出映射表，一应俱全。

### **[👉 点击查看完整 YAML Schema 文档 →](docs/yaml-schema.md)**

剧本输出采用三层结构：
- **`script`** — 剧本元数据（标题、原著、作者、版本）
- **`characters`** — 角色列表（姓名、类型、外貌描述、性格特征）
- **`scenes`** — 场景列表（章节、编号、室内外、地点、时辰、氛围、出场角色）
  - **`beats`** — 拍摄单元（动作/对白/独白/旁白/转场，含台词和表演指导）

---

## ✨ 功能特性

### 📖 智能剧本生成
- **7 级分章策略** — 中文第X章 → 英文Chapter → 第X部/篇/卷 → 罗马数字 → 日语数字 → 编号分隔 → 段落智能分块，覆盖中日英小说格式
- **无章节回退** — 如《雪国》等无章节小说，自动按段落密度切分，标题为「第N部分」
- **AI 角色提取** — DeepSeek 自动识别角色名、身份、性格特征、人物关系
- **任务制断点续传** — 分章后创建 `GenerationPlan` 持久化到数据库，每章独立任务（PENDING→IN_PROGRESS→DONE），中断后从第一个未完成章节继续
- **SSE 流式输出** — 实时显示生成进度，支持断点续写和即时预览
- **onChunk 实时推送** — 每 1000 字块完成后立即推送中间 YAML，用户不必等整章完成就能看到内容
- **章节级进度条** — 前端显示 `第X章/共N章` + 章节状态标记（✅已完成 / 🔄生成中 / ⬜待处理）
- **100万字上限** — 支持超长篇小说全文处理
- **剧本版本管理** — 每次生成独立保存版本号，历史回溯对比，可编辑保存

### 🎨 AI 图像生成
- **DeepSeek 造型师** — 先生成个性化外貌/场景描写（年龄/性别智能识别 + 性格→视觉元素转化），再送入生图模型
- **混元 hy-image-lite** — 腾讯混元大模型图生，TokenHub API 调用
- **反同质化** — 每个角色有差异化外貌特征，避免「剑眉星目」「面如冠玉」等模板化描述
- **COS 永久存储** — 生成图片通过 COS SDK 自动上传腾讯云对象存储，返回永久公开 URL
- **图片版本历史** — 每次生图独立存档至 `image_versions` 表，前端画廊支持回滚到任意历史版本
- **场景图渐进式生成** — 后台 4 线程池异步执行，完成即通过 SSE `scene_image` 事件实时推送
- **熔断保护** — 连续失败 5 次自动暂停 60 秒，避免浪费 DeepSeek 提示词配额
- **并发限制重试** — TokenHub 付费版单任务上限 `JobNumExceed`，自动等待 3 秒重试
- **图片持久化** — YAML 重新解析时从数据库恢复已有图片 URL，不会闪烁丢失
- **图片下载** — 预览弹窗一键下载原图

### 👥 多人协作
- **项目分享** — 通过用户名添加协作者，支持邀请链接一键加入
- **权限分级** — 管理员（编辑/生成/管理协作者）vs 只读（仅查看剧本和原文）
- **权限标签** — 项目列表清晰显示「🔧 协管」「👁 只读」标识
- **安全保护** — 只读用户无法修改剧本、生成新版本或管理协作者，API 层双重校验

### 📄 多格式导出
- **YAML** — 结构化剧本原始格式
- **Markdown** — 适合阅读和分享的排版
- **Fountain** — 标准剧本格式，可直接导入 Final Draft 等专业软件
- **TXT** — 纯文本格式，通用性最强

### 🎭 前端 UI/UX
- **暗色/亮色切换** — 深金主色调暗色主题 + 明亮模式一键切换
- **毛玻璃材质** — 弹窗、侧栏标题均采用 backdrop-filter 毛玻璃效果 + 金色描边
- **可拖拽面板** — 原文/剧本/AI助手三栏宽度可通过金色手柄自由拖拽调整
- **全局快捷键** — Ctrl+F 搜索原文、Ctrl+Enter 生成剧本、Ctrl+S 保存 YAML、Esc 关闭搜索
- **金色视觉系统** — 所有操作按钮统一为金色，章节 Pills 渐变金色 + hover 发光
- **响应式适配** — 手机端完整适配，弹窗宽度自适、表单移动端布局优化

### 🔧 DevOps
- **GitHub Actions CI** — push 自动编译后端（Maven）+ 前端（Vite）
- **Webhook 热部署** — GitHub push → webhook → git pull → build → restart
- **systemd 服务** — 崩溃自动重启，journalctl 日志
- **Nginx + HTTPS** — Let's Encrypt 自动证书
- **健康检查** — Spring Boot Actuator `/actuator/health`

---

## ⌨️ 快捷键

在剧本编辑器中，以下快捷键**全局生效**（不依赖焦点）：

| 快捷键 | 功能 | 说明 |
|--------|------|------|
| `Ctrl + F` | 搜索原文 | 切换搜索栏，输入关键字高亮匹配 |
| `Ctrl + Enter` | 生成剧本 | 一键触发 AI 剧本生成（非只读+未生成中） |
| `Ctrl + S` | 保存 YAML | 仅在编辑模式下，保存当前修改 |
| `Esc` | 关闭搜索 | 收起搜索栏 |

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

### 剧本生成流程（任务制断点续传）

```
点击生成剧本
    │
    ▼
① 分章 (ChapterSplitService) — 7级策略
    │
    ▼
② 创建 GenerationPlan — 持久化到 DB
   每章一个任务: {num, title, status: PENDING}
   推送 plan SSE 事件 → 前端显示章节进度条
    │
    ▼
③ 逐章执行 (逐章调用 ScriptGenService)
   标记 IN_PROGRESS → DeepSeek 生成 → 标记 DONE
   onChunk 回调: 每1000字块推送中间YAML
   累积角色/场景到 GenerationPlan JSON
    │
    ▼
④ YAML 生成 (YamlGeneratorService)
   SnakeYAML 组装，保存 ScriptVersion
    │
    ▼
⑤ 断点续传: 中断后重连 → 查 DB GenerationPlan
   → 找到 DONE 章节 → 重建 YAML → 续写 PENDING
```

### 生图流程

```
角色/场景数据
    │
    ▼
① DeepSeek 造型师 → 个性化外貌/场景描写
   （年龄识别 + 性别检测 + 性格→视觉转化）
    │
    ▼
② 混元 hy-image-lite → TokenHub API
   线程池(1线程) → 熔断保护(5次失败暂停60s)
   JobNumExceed → 自动等待3秒重试
    │
    ▼
③ COS 上传 → 永久 URL → image_versions 表
    │
    ▼
④ SSE scene_image 事件 → 前端实时更新画廊
   YAML重新解析时自动保留已有图片
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
| GET | `/api/projects/{id}/generate/stream` | SSE 流式生成（事件: plan/chapter_start/chapter_done/scene_image/done） |

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

### generation_plan（生成计划 — 任务制断点续传）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| project_id | BIGINT FK UNIQUE | 项目 |
| total_chapters | INT | 总章数 |
| completed_chapters | INT | 已完成章数 |
| version_number | INT | 对应版本号 |
| chapters_json | CLOB | 章节任务列表 JSON（含 PENDING/IN_PROGRESS/DONE 状态） |
| partial_yaml | CLOB | 累积的部分 YAML |
| characters_json | CLOB | 累积的角色 JSON |
| scenes_json | CLOB | 累积的场景 JSON |
| updated_at | TIMESTAMP | 更新时间 |

### image_versions
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| project_id | BIGINT FK | 项目 |
| image_type | VARCHAR(20) | CHARACTER/SCENE |
| target_index | INT | 角色/场景序号 |
| url | VARCHAR(1024) | 图片 URL (COS 永久链接) |
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

### 日志过滤

```bash
# 查看生图链路日志
journalctl -u novel-script -f --no-pager 2>&1 | grep --line-buffered '生图'

# 查看生成进度
journalctl -u novel-script -f --no-pager 2>&1 | grep --line-buffered 'chapter\|plan\|done'
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
│       ├── model/entity/              # User, Project, ScriptVersion, Folder, Collaboration, GenerationPlan, ImageVersion
│       ├── model/dto/                 # ApiResponse, ProjectResponse 等
│       ├── repository/                # JPA Repository 接口
│       └── service/                   # ChapterSplit, ScriptGen, ScriptService, Image, COS, Collaboration 等
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

---

## 📝 开发日志

> 2026年6月5日 – 6月7日，3天密集开发，158次提交，7,700+行代码。

### 6月5日 — 基础架构搭建
- Spring Boot 3 + Vue 3 项目初始化，JWT认证，H2数据库
- 7级智能分章策略（中文/英文/罗马/日语/段落回退）
- DeepSeek API 集成，逐章剧本生成，SSE流式推送
- YAML结构化输出，SnakeYAML序列化
- 前端三栏布局（原文/剧本/AI助手），Element Plus UI
- GitHub Actions CI + Webhook热部署 + systemd服务

### 6月6日 — AI生图 + 断点续传
- 混元 hy-image-lite 集成（TokenHub API）
- DeepSeek 造型师：年龄检测/性别识别/反同质化提示词增强
- 腾讯云 COS 图片永久存储 + 版本历史回滚
- **GenerationPlan 任务制断点续传**：替代位图，按章节持久化DB
- onChunk 实时推送：每千字块立即展示中间YAML
- 场景图 SSE 渐进式生成：4线程池后台异步，完成即推前端
- TokenHub 熔断保护 + 并发限制自动重试
- 图片持久化：YAML重建时保留已有图片避免闪烁
- 场景解析正则修复：匹配AI输出的chapter字段
- 金色主题全站统一 + 毛玻璃材质 + 可拖拽面板

### 6月7日 — 多人协作 + 完善打磨
- **邀请链接协作**：像腾讯文档生成链接，他人点击加入
- 在线用户感知：右上角头像显示当前活跃用户
- **三层权限防护**：前端UI禁用→函数拦截→后端canEdit/canView
- 协作管理弹窗：毛玻璃背景+暗色亮色适配+链接可滚动复制
- AI助手权限漏洞修复：ChatController加权限校验
- ScriptController权限修复：协作者查看/编辑剧本不再403
- 共享项目剧本数据互通（同一H2数据库）
- 权限标签修正（协管→管理员）+卡片菜单位置优化
- 全链路SLF4J日志 + 排查命令
- README 全面更新：架构图/API表/数据库表/部署指南
- 手机端响应式适配 + 快捷键全局生效

### 核心技术指标

| 指标 | 数据 |
|------|------|
| 提交总数 | 158 commits |
| 代码行数 | 5,322 行 Java + 2,407 行 Vue/JS/CSS |
| 数据库表 | 8 张（users, projects, script_versions, folders, collaborations, generation_plan, image_versions, conversations） |
| API 端点 | 35+（含 SSE 流式、邀请链接、在线检测） |
| AI 集成 | DeepSeek(文本) + 混元hy-image-lite(图片) + COS(存储) |
| 提交分布 | 修复47 + 新增21 + 优化24 + 重构6 + 文档3 + 诊断2 |
| 测试覆盖 | 9 类 36 用例，JUnit 5 + Mockito |
