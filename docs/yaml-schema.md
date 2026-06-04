# 剧本 YAML Schema 定义文档

## 概述

本 Schema 定义了 AI 小说转剧本工具输出的结构化剧本 YAML 格式。该格式设计目标是：

1. **人类可读可编辑** — 作者拿到初稿后可以用任何文本编辑器修改
2. **机器可解析** — 方便下游工具链（导演、制片、分镜软件）消费
3. **涵盖剧本核心要素** — 角色、场景、对白、动作、情绪、转场

## 完整 Schema

```yaml
# ==================== 顶层结构 ====================
script:           # 剧本元信息
  title: ""       # 剧本标题
  based_on: ""    # 原著小说名称
  author: ""      # 改编作者
  version: "1.0"  # 剧本版本号
  language: zh-CN # 语言代码
  created_at: ""  # 创建日期 (ISO 8601)

# ==================== 角色注册表 ====================
characters:       # 所有出场角色，按首次出现顺序排列
  - id: CHAR_001                 # 唯一标识符
    name: "角色名"                # 角色名称（原文中的名字）
    role: protagonist            # 角色类型
    description: "外貌与性格描述"  # 角色概要
    traits: ["特长", "性格"]      # 角色特征标签
    first_appearance: SCENE_001  # 首次出场场景

# ==================== 场景序列 ====================
scenes:           # 剧本主体，按时间顺序排列
  - id: SCENE_001                 # 场景唯一标识
    chapter: 1                    # 对应原著章节号
    scene_number: 1               # 该章内的场景序号
    type: INT                     # 场景类型
    location: "场景地点"          # 具体地点描述
    time: "时间描述"              # 时间环境描述
    description: "场景氛围与环境"  # 场景整体描述（舞台指导）
    characters: [CHAR_001]       # 本场景出场角色 ID 列表
    beats:                        # 场景内原子节奏单元
      - type: action              # 节奏类型
        character: null           # 发言人（action/narration/transition 时为 null）
        line: null                # 对白内容（action/narration/transition 时为 null）
        direction: "动作/表演指导" # 舞台说明
        emotion: null             # 角色情绪（action 时可为 null）

      - type: dialogue            # 对白类型
        character: CHAR_001       # 发言人 ID
        line: "对白台词"           # 实际对白内容
        direction: "说话时的动作或表情指导"  # 可选，表演指导
        emotion: "愤怒"            # 说话时的情绪状态

      - type: monologue           # 独白/内心
        character: CHAR_001       # 独白的角色
        line: "内心独白内容"
        direction: "画外音 或 面对观众"
        emotion: "悲伤"

      - type: narration           # 旁白
        character: null           # 旁白通常无具体角色
        line: "旁白内容"
        direction: "背景音画外音"
        emotion: null

      - type: transition          # 转场
        character: null
        line: null
        direction: "淡入淡出 / 硬切 / 叠化"
        emotion: null
```

## 字段详细说明

### script（剧本元信息）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | string | 是 | 剧本标题 |
| `based_on` | string | 否 | 原著小说名称，用于版权溯源 |
| `author` | string | 否 | 改编剧本的作者名 |
| `version` | string | 是 | 语义化版本号，支持多次修订追踪 |
| `language` | string | 是 | BCP 47 语言标签，默认 zh-CN |
| `created_at` | string | 是 | ISO 8601 日期格式 |

### characters（角色注册表）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 全局唯一角色标识，格式 `CHAR_XXX`，全剧本引用 |
| `name` | string | 是 | 角色在原文中的名称 |
| `role` | enum | 是 | `protagonist`(主角) / `antagonist`(反派) / `supporting`(配角) / `minor`(次要) |
| `description` | string | 否 | 角色外貌、性格、背景的综合描述 |
| `traits` | string[] | 否 | 便于检索的关键特征标签，如 ["聪明", "武力高强"] |
| `first_appearance` | string | 否 | 首次出场场景 ID，便于快速定位角色引入位置 |

### scenes（场景序列）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 全局唯一场景标识，格式 `SCENE_XXX` |
| `chapter` | integer | 是 | 对应原著章节编号，保持可溯源 |
| `scene_number` | integer | 是 | 该章内的场景序号 |
| `type` | enum | 是 | 拍摄类型：`INT`(室内)、`EXT`(室外)、`INT/EXT`(内外交替) |
| `location` | string | 是 | 具体地点描述，如"长安城外破庙" |
| `time` | string | 是 | 时间环境，如"深夜月明"、"午后" |
| `description` | string | 否 | 场景整体氛围、环境细节的舞台说明 |
| `characters` | string[] | 是 | 本场景出场角色 ID 列表，引用 characters 中的 id |
| `beats` | array | 是 | 场景内按时间顺序排列的原子节奏单元 |

### beats（节奏单元）

Beat 是剧本的最小结构单元。每个 beat 描述一个不可再分的戏剧节奏：一句对白、一个动作、一次转场。导演和演员可以按 beat 粒度进行排练和调整。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `type` | enum | 是 | `action`(动作/场景描写) / `dialogue`(对白) / `monologue`(独白/内心) / `narration`(旁白) / `transition`(转场) |
| `character` | string \| null | 条件 | `dialogue` 和 `monologue` 类型时必须填写角色 ID，其他类型为 null |
| `line` | string \| null | 条件 | `dialogue`/`monologue`/`narration` 时填写实际内容，其他为 null |
| `direction` | string | 是 | 表演指导或动作说明，导演和演员使用 |
| `emotion` | string \| null | 否 | 角色当前的情绪状态，如"狂喜"、"绝望" |

## 设计原则与理由

### 1. 为什么使用 YAML 而非 JSON 或纯文本格式？

- **比 JSON 更可读**：YAML 不用引号和花括号，作者用文本编辑器就能直接修改
- **比纯文本更结构化**：YAML 保留了层级结构，方便程序解析和处理
- **行业接地**：戏剧行业常用 YAML 作为交换格式，比 JSON 更易被非技术人员接受

### 2. 为什么将 characters 设计为独立的注册表而非内嵌在场景中？

- **避免重复**：一个角色可能出现在多个场景中，注册表模式确保角色信息定义一次、引用多次
- **便于修改**：修改角色描述只需改一处，所有场景自动更新
- **可独立管理**：导演可以先通读角色表了解人物，再深入具体场景
- **支持工具链**：角色表可以单独导出为演员分配表、角色关系图等

### 3. 为什么引入 beat 作为最小结构单元？

- **原子性**：每个 beat 是一个不可再分的戏剧动作，编剧和导演可以在 beat 级别做精确调整
- **类型枚举覆盖全**：对白 (dialogue)、独白 (monologue)、旁白 (narration)、动作 (action)、转场 (transition) 五种类型覆盖了剧本需要的所有表达形式
- **表演指导内置**：每个 beat 都有独立的 `direction` 和 `emotion` 字段，演员定位更准确

### 4. 为什么区分 dialogue、monologue 和 narration？

三种"说话"方式在剧本中有不同的呈现形式：
- **dialogue（对白）**：角色之间互相说话，镜头通常对准说话人和倾听者
- **monologue（独白）**：角色对自己说话或打破第四面墙面对观众，表演方式不同
- **narration（旁白）**：画外音，角色不在画面内，通常录制后叠加

这种区分让分镜师和导演可以精确理解每种语言的呈现方式。

### 5. 为什么要保持与原著章节的对应关系（chapter 字段）？

- **可溯源**：导演可以随时回到原著对应章节，核对待改编内容
- **差异分析**：可以逐章对比原著和剧本，评估改编忠实度
- **进度管理**：按章节管理改编进度，支持分章节审核和修改

### 6. 为什么场景类型只有 INT/EXT/INT-EXT 三种？

这是电影工业的标准分类，足够覆盖所有场景需求：
- **INT (Interior)**：室内场景
- **EXT (Exterior)**：室外场景
- **INT/EXT**：室内外交替场景

保持简单有助于下游工具（如预算软件自动估算场景成本）准确解析。

### 7. 为什么 role 枚举使用英文值？

- **与国际化工具链兼容**：protagonist/antagonist 等术语是影视工业的通用术语
- **避免编码歧义**：中文"反派"在某些语境下可能被翻译为 villain/antagonist/opponent，使用英文枚举值更精确
- **AI 生成友好**：主流 LLM 对英文 role 值的理解更一致

## 版本演进计划

| 版本 | 计划新增 |
|------|---------|
| v1.0 | 当前版本，基础剧本结构 |
| v1.1 | 支持 camera（景别/运镜）提示 |
| v1.2 | 支持 soundtrack（配乐/音效）标记 |
| v2.0 | 支持多线叙事、闪回/闪前等非线性结构 |

## 示例片段

```yaml
script:
  title: "江湖旧事"
  based_on: "侠客行"
  author: "张三"
  version: "1.0"
  language: zh-CN
  created_at: "2026-06-05"

characters:
  - id: CHAR_001
    name: "李云"
    role: protagonist
    description: "三十来岁，江湖游侠，身怀绝技却内敛沉稳"
    traits: ["剑术高超", "侠义心肠", "少言寡语"]
    first_appearance: SCENE_001

  - id: CHAR_002
    name: "赵铁山"
    role: antagonist
    description: "黑风寨寨主，身材魁梧，满脸横肉"
    traits: ["力大无穷", "心狠手辣"]
    first_appearance: SCENE_003

scenes:
  - id: SCENE_001
    chapter: 1
    scene_number: 1
    type: EXT
    location: "长安城外古道"
    time: "黄昏，残阳如血"
    description: "秋风萧瑟，黄沙漫卷，一条古道蜿蜒通向远方"
    characters: [CHAR_001]
    beats:
      - type: action
        character: null
        line: null
        direction: "镜头从天空摇下，缓缓推向古道上一个孤独的身影"
        emotion: null

      - type: action
        character: null
        line: null
        direction: "李云骑着马缓缓前行，斗笠遮住半张脸，风衣随风飘扬"
        emotion: null

      - type: monologue
        character: CHAR_001
        line: "十年了...该来的终究要来。"
        direction: "面对观众，目光深沉"
        emotion: "沧桑而坚定"

      - type: transition
        character: null
        line: null
        direction: "叠化至下一场景"
        emotion: null
```

## 下游消费示例

此 Schema 可被以下工具直接消费：

- **分镜软件**（如 Storyboarder）：按场景和 beat 自动生成分镜草稿
- **预算估算工具**：解析场景类型（INT/EXT）和角色数量，估算拍摄成本
- **演员调度系统**：按角色出场场景编排拍摄日程
- **字幕工具**：直接提取所有 dialogue 和 monologue 的 line 字段
