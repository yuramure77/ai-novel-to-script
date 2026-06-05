# AI 小说转剧本工具

> 一款基于 AI 的小说到剧本智能转换工具，帮助作者快速获得结构化、可编辑的剧本初稿。

## 功能特性

- 📖 **自动分章** — 支持中文（第一/二/三章）和英文（Chapter 1/2/3）章节标记自动识别
- 🤖 **AI 剧本生成** — 调用 DeepSeek 大模型，智能提取角色、场景、对白、动作指导
- 📝 **结构化 YAML 输出** — 输出符合标准 Schema 的 YAML 剧本，可直接编辑
- 🔄 **版本管理** — 每次生成保存为一个版本，支持历史回溯和对比
- 👤 **用户系统** — 注册/登录，JWT 鉴权，个人项目管理

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- npm 9+

### 1. 配置 API Key

编辑 `backend/src/main/resources/application.yml`，填入你的 DeepSeek API Key：

```yaml
deepseek:
  api-key: sk-your-api-key-here
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端启动后访问 http://localhost:8080

- H2 控制台：http://localhost:8080/h2-console
- 数据库文件自动保存在 `backend/data/scripttool.mv.db`

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端启动后访问 http://localhost:3000

## 技术架构

```
┌─────────────────────────┐     ┌─────────────────────────┐
│      前端 (Vue 3)        │────▶│     后端 (Spring Boot)   │
│                         │     │                         │
│  Vue 3 + Element Plus   │     │  Spring MVC             │
│  Vue Router (路由守卫)   │     │  Spring Security + JWT  │
│  Axios (HTTP Client)    │     │  Spring Data JPA + H2   │
│  Vite (构建工具)         │     │  SnakeYAML              │
└─────────────────────────┘     └───────────┬─────────────┘
                                            │
                              ┌─────────────▼─────────────┐
                              │     DeepSeek API          │
                              │  chat/completions         │
                              │  model: deepseek-chat     │
                              │  response_format: json    │
                              └───────────────────────────┘
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
 AI 分析 (ScriptGenService)
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

## API 文档

### 认证接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册（username, password, nickname） |
| POST | `/api/auth/login` | 登录，返回 JWT token |

### 项目接口（需 Bearer Token）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/projects` | 获取项目列表 |
| GET | `/api/projects/{id}` | 获取项目详情（含原文） |
| POST | `/api/projects` | 创建项目（title, originalText） |
| DELETE | `/api/projects/{id}` | 删除项目 |
| POST | `/api/projects/{id}/split` | 分章预览 |
| POST | `/api/projects/{id}/generate` | AI 生成剧本 |

### 剧本接口（需 Bearer Token）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/scripts/project/{projectId}/versions` | 获取版本列表 |
| GET | `/api/scripts/project/{projectId}/latest` | 获取最新版本 |
| GET | `/api/scripts/{versionId}/yaml` | 下载 YAML 文件 |

### 统一返回格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

## 数据库表设计

### users（用户表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| username | VARCHAR(50) UNIQUE | 用户名 |
| password | VARCHAR(255) | BCrypt 加密密码 |
| nickname | VARCHAR(100) | 昵称 |
| created_at | TIMESTAMP | 创建时间 |

### projects（项目表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| user_id | BIGINT FK→users | 所属用户 |
| title | VARCHAR(200) | 项目标题 |
| original_text | CLOB | 小说原文 |
| chapter_count | INT | 识别到的章节数 |
| status | VARCHAR(20) | DRAFT/PROCESSING/COMPLETED |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

### script_versions（剧本版本表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| project_id | BIGINT FK→projects | 所属项目 |
| version_number | INT | 版本号（自增） |
| yaml_content | CLOB | YAML 剧本内容 |
| created_at | TIMESTAMP | 生成时间 |

## 项目结构

```
ai-novel-to-script/
├── backend/                          # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/scripttool/
│       │   ├── ScriptToolApplication.java
│       │   ├── config/               # 配置 (JWT, Security, DeepSeek)
│       │   ├── controller/           # REST 控制器
│       │   ├── model/
│       │   │   ├── entity/           # JPA 实体
│       │   │   └── dto/              # 传输对象
│       │   ├── repository/           # JPA Repository
│       │   └── service/              # 业务逻辑
│       └── resources/
│           └── application.yml       # 应用配置
├── frontend/                         # Vue 3 前端
│   ├── src/
│   │   ├── api/                      # Axios API 封装
│   │   ├── router/                   # Vue Router
│   │   └── views/                    # 页面组件
│   └── vite.config.js
└── docs/
    └── yaml-schema.md                # YAML Schema 文档
```

## 使用流程

1. **注册/登录** — 新建账户或登录
2. **创建项目** — 输入项目标题，粘贴小说原文（至少 3 章）
3. **分章预览** — 点击"分章预览"查看自动识别的章节结构
4. **生成剧本** — 点击"生成剧本"，AI 将自动分析并生成结构化剧本
5. **预览编辑** — 在右侧面板查看 YAML 格式的剧本输出
6. **下载导出** — 点击"下载 YAML"导出剧本文件，可在任何文本编辑器中进一步打磨
7. **重新生成** — 可多次生成，每个版本独立保存

## 生产部署

### 快速部署（推荐）

```bash
# 1. 克隆项目
git clone git@github.com:yuramure77/ai-novel-to-script.git
cd ai-novel-to-script

# 2. 设置环境变量
export DEEPSEEK_API_KEY=sk-your-key-here
export JWT_SECRET=$(openssl rand -base64 32)

# 3. 一键部署
chmod +x deploy/deploy.sh build.sh
sudo bash deploy/deploy.sh your-domain.com
```

部署完成后：
- 应用自动注册为 systemd 服务（崩溃自动重启）
- Nginx 反向代理 + Let's Encrypt HTTPS 自动证书
- 数据持久化在 `/opt/ai-novel-to-script/data/`

### Docker 部署

```bash
DEEPSEEK_API_KEY=sk-xxx docker-compose up -d
```

### 手动部署

```bash
# 构建
./build.sh

# 上传 JAR 到服务器后启动
java -Ddeepseek.api-key=sk-xxx -Djwt.secret=xxx -jar app.jar
```

### 管理命令

```bash
sudo systemctl status novel-script    # 查看状态
sudo journalctl -u novel-script -f    # 实时日志
sudo systemctl restart novel-script   # 重启
```

### 环境变量

| 变量 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `DEEPSEEK_API_KEY` | 是 | - | DeepSeek API 密钥 |
| `JWT_SECRET` | 生产必填 | 内置值 | JWT 签名密钥 |
| `PORT` | 否 | 8080 | 服务端口 |
| `H2_PATH` | 否 | ./data/scripttool | 数据库文件路径 |

## YAML 剧本 Schema

详见 [docs/yaml-schema.md](docs/yaml-schema.md)
