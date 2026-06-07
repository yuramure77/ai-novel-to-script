# 📄 YAML Schema — 剧本结构化格式

> 本文定义剧本工坊生成的 YAML 剧本的完整数据结构，并解释每个设计决策的原因。

---

## 一、Schema 总览

```yaml
script:          # 剧本元数据
  title:         # 剧本名称
  based_on:      # 原著名称
  author:        # 改编者
  version:       # 版本号
  language:       # 语言代码
  created_at:    # 创建日期

characters:      # 角色列表
  - name:        # 角色名
    role:        # 角色类型 (protagonist/antagonist/supporting/minor)
    description: # 外貌/身份描述
    traits:      # 性格特征

scenes:          # 场景列表
  - chapter:     # 所属章节
    scene_number: # 全局场景编号
    type:        # 室内/室外 (INT/EXT/INT-EXT)
    location:    # 地点
    time:        # 时辰
    description: # 场景描述
    mood:        # 氛围
    characters:  # 出场角色名列表
      - 角色名
    beats:       # 拍摄单元
      - type:    # 动作/对白/独白/旁白/转场
        character: # 说话者或执行者
        line:    # 台词
        direction: # 表演指导
        emotion: # 情绪
```

---

## 二、字段详解

### 2.1 `script` — 剧本元数据

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | string | ✅ | 剧本名称 |
| `based_on` | string | | 原著小说名称 |
| `author` | string | | 改编者 |
| `version` | string | | 语义化版本，默认 `"1.0"` |
| `language` | string | | IETF 语言标签，默认 `"zh-CN"` |
| `created_at` | string | | ISO 8601 日期 |

**设计原因**：元数据独立于内容，便于批量检索和版本管理。`based_on` 保留原著追溯链，对版权合规有用。

### 2.2 `characters` — 角色

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | ✅ | 使用原文中的角色名 |
| `role` | enum | ✅ | `protagonist` / `antagonist` / `supporting` / `minor` |
| `description` | string | | 外貌、身份、背景（AI 生图的输入源） |
| `traits` | string[] | | 性格标签（AI 写台词的输入源） |

**设计原因**：

- **顶层独立**：角色跨场景复用。放在 scnen 里会重复定义数十次。
- **四档角色类型**：影视工业标准，覆盖 95% 的角色定位。
- **描述与特征分离**：`description` 是外貌（→ AI 生图），`traits` 是性格（→ AI 写台词），分开避免干扰。
- **使用原文名称**：保持与小说的直接关联，AI 不自作主张改名。

### 2.3 `scenes` — 场景

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `chapter` | int | ✅ | 所属章节，从 1 开始 |
| `scene_number` | int | ✅ | 全局递增场景编号 |
| `type` | enum | | `INT`(室内) / `EXT`(室外) / `INT/EXT`(内外交替) |
| `location` | string | | 地点描述 |
| `time` | string | | 时辰描述（"黄昏""午夜"） |
| `description` | string | | 场景内容概要 |
| `mood` | string | | 情绪基调（"紧张""温馨"） |
| `characters` | string[] | | 出场角色名（引用 characters 的 name） |
| `beats` | Beat[] | | 拍摄单元 |

**设计原因**：

- **扁平列表而非嵌套在 chapter 下**：章节是编辑时的组织方式，场景需要全局编号。增量生成时新场景直接追加，无需修改父节点。
- **独立 scene_number**：不与章节耦合。后续调整章节划分，场景编号不受影响。Final Draft 等行业软件也使用全局编号。
- **type 使用 INT/EXT**：影视标准缩写，美术灯光部门的前期依据。
- **characters 是名字引用而非内嵌**：指向顶层角色名。修改角色只需改一处。
- **location/time 分离**：场景图生成需要独立的时空参数。
- **mood 独立存储**：情绪是生图 prompt 和表演指导的输入，不应混在 description 里。

### 2.4 `beats` — 拍摄单元

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | enum | `action` / `dialogue` / `monologue` / `narration` / `transition` |
| `character` | string | 说话者或动作执行者（可选） |
| `line` | string | 台词内容 |
| `direction` | string | 表演指导（中文） |
| `emotion` | string | 情绪状态 |

**五分类法的设计依据**：

| 类型 | 对应 | 示例 |
|------|------|------|
| `action` | 动作/场景描写 | "窗外一道闪电划破夜空" |
| `dialogue` | 对白 | "你终于来了" |
| `monologue` | 独白/内心 | 角色自言自语 |
| `narration` | 旁白 | 画外音 |
| `transition` | 转场 | 淡入淡出 |

比"对白/动作"二分法精确，又不至于过度细分。覆盖剧本里 95% 的叙事单元。

**direction 使用中文**：中文剧本习惯用"冷笑一声""眼眶微红"等表达，英文 `stage direction` 格式不适合中文创作。

---

## 三、设计理念

### 3.1 为什么选择 YAML

| 对比 | YAML | JSON | XML | Fountain |
|------|------|------|-----|----------|
| 人类可读 | 最优 | 括号密集 | 标签冗余 | 结构松散 |
| 层级表达 | 缩进天然 | ✅ | ✅ | ❌ |
| 版本控制 diff | 清晰 | 需格式化 | ⚠️ | ⚠️ |
| 行内注释 | ✅ `#` | 无标准 | ✅ | ✅ |
| 多行文本 | ✅ | 转义 | ✅ | ✅ |
| Java 工具链 | SnakeYAML | Jackson | JAXB | 专用解析器 |

核心原因：**剧本是给人读的**。编剧打开 YAML 不需要工具就能理解。缩进层级天然映射"角色→场景→对白"结构。

YAML 是中间表示（IR），可以导出为 Fountain、Markdown 等任意格式，保留最大灵活性。

### 3.2 增量友好的扁平结构

断点续传要求生成第 N 章时，新场景直接 append 到列表末尾，mergeCharacters 去重。如果 scenes 嵌套在 chapters 下，每次更新需要修改父节点，并发和合并逻辑复杂得多。

### 3.3 扩展预留

通过 `version` 字段区分版本。未来可扩展：

- `scenes[].shots` — 分镜头
- `characters[].relations` — 角色关系图
- `beats[].camera` — 摄影指导
- `script.genre` — 类型标签

---

## 四、完整示例

```yaml
script:
  title: 春雪 改编剧本
  based_on: 三岛由纪夫《春雪》
  author: yuramure
  version: "1.0"
  language: zh-CN
  created_at: "2026-06-07"

characters:
  - name: 松枝清显
    role: protagonist
    description: 松枝侯爵家继承人，十八岁，容貌俊美而忧郁
    traits:
      - 感性
      - 忧郁
      - 纤细敏感
  - name: 本多繁邦
    role: supporting
    description: 清显的好友，法官之子，理性稳重
    traits:
      - 理性
      - 稳重
      - 善于观察

scenes:
  - chapter: 1
    scene_number: 1
    type: INT
    location: 松枝家宅邸，清显的房间
    time: 冬日午后
    description: 清显独自看雪，本多前来探望
    mood: 静谧而忧郁
    characters:
      - 松枝清显
      - 本多繁邦
    beats:
      - type: action
        character: 松枝清显
        direction: 站在窗前，一手轻触玻璃，凝视飘雪
        emotion: 若有所思
      - type: dialogue
        character: 本多繁邦
        line: 又在看雪。你已经看了一个时辰了。
        emotion: 无奈
      - type: dialogue
        character: 松枝清显
        line: 雪是活的。每一片都有不同的轨迹。
        emotion: 平静
```

---

## 五、与导出格式的映射

| YAML 字段 | Markdown | Fountain | TXT |
|-----------|----------|----------|-----|
| `script.title` | `# 标题` | Title Page | 标题行 |
| `characters` | 角色表格 | Character List | 分行列出 |
| `scenes[].chapter` | `## 第X章` | `# Chapter X` | `=== 第X章 ===` |
| `scenes[].type/location/time` | 场景头 | `INT. LOCATION - TIME` | 缩进标注 |
| `beats[].type=action` | 段落 | Action 块 | 正文 |
| `beats[].type=dialogue` | `> 台词` | `角色名\n台词` | `角色：台词` |
| `beats[].direction` | 括号注释 | `(指导)` | `【指导】` |
