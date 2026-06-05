<template>
  <div class="editor-page">
    <!-- Top bar -->
    <header class="topbar">
      <div class="topbar-left">
        <el-button text @click="$router.push('/')">← 返回</el-button>
        <h2>{{ projectTitle }}</h2>
        <el-tag :type="statusTag" size="small">{{ statusText }}</el-tag>
      </div>
      <div class="topbar-right">
        <el-button v-if="latestVersion" @click="showHistory = true">历史</el-button>
        <el-button v-if="latestVersion" type="success" @click="downloadYaml">下载</el-button>
      </div>
    </header>

    <!-- Action bar -->
    <div class="action-bar">
      <div class="action-left">
        <el-button size="small" @click="handleSplit" :loading="splitting">分章</el-button>
        <span v-if="chapters.length" class="hint">共 {{ chapters.length }} 章</span>
      </div>
      <el-button type="primary" @click="handleGenerate" :loading="generating" :disabled="generating">
        {{ generating ? '生成中...' : '生成剧本' }}
      </el-button>
    </div>

    <!-- Progress bar (SSE stream) -->
    <div v-if="generating" class="progress-bar">
      <el-progress :percentage="100" :indeterminate="true" :stroke-width="2" :show-text="false" />
      <p class="progress-msg">{{ progressMsg }}</p>
    </div>

    <!-- Chapter tabs -->
    <div v-if="chapters.length" class="chapter-bar">
      <button
        v-for="(ch, i) in chapters"
        :key="i"
        :class="['chip', { active: currentChapter === i }]"
        @click="currentChapter = i"
      >{{ ch.title }}</button>
    </div>

    <!-- Main content: 3 columns -->
    <div class="main">
      <!-- Left: Source text -->
      <div class="col col-left">
        <div class="col-body">
          <pre v-if="chapters.length">{{ chapters[currentChapter]?.content || originalText }}</pre>
          <pre v-else>{{ originalText }}</pre>
        </div>
      </div>

      <!-- Center: YAML + edit -->
      <div class="col col-center" :class="{ 'col-wide': !showRightPanel }">
        <div class="col-header">
          <span class="col-title">剧本 YAML</span>
          <div class="col-header-actions">
            <el-button size="small" text @click="editing = !editing">
              {{ editing ? '预览' : '编辑' }}
            </el-button>
            <el-button size="small" text @click="copyYaml" v-if="yamlContent">复制</el-button>
          </div>
        </div>
        <div class="col-body">
          <div v-if="!yamlContent && !generating" class="empty-state">
            <el-empty description="点击「生成剧本」开始" />
          </div>
          <div v-else-if="editing" class="editor-area">
            <textarea v-model="editableYaml" class="yaml-editor" spellcheck="false"></textarea>
            <div class="editor-footer">
              <el-button size="small" @click="editing = false; editableYaml = yamlContent">取消</el-button>
              <el-button size="small" type="primary" :loading="saving" @click="saveYaml">保存为新版本</el-button>
            </div>
          </div>
          <div v-else class="yaml-display">
            <pre><code v-html="highlightedYaml"></code></pre>
          </div>
        </div>
      </div>

      <!-- Right: AI Chat + Characters -->
      <div class="col col-right" v-if="showRightPanel">
        <el-tabs v-model="rightTab" class="right-tabs">
          <!-- AI Chat -->
          <el-tab-pane label="AI 助手" name="chat">
            <div class="chat-panel">
              <div class="chat-messages" ref="chatRef">
                <div v-for="(m, i) in chatMessages" :key="i" :class="['msg', m.role]">
                  <div class="msg-content">{{ m.content }}</div>
                </div>
                <div v-if="chatLoading" class="msg assistant">
                  <div class="msg-content typing">AI 思考中...</div>
                </div>
              </div>
              <div class="chat-input">
                <el-input
                  v-model="chatInput"
                  placeholder="输入修改建议或问题..."
                  @keyup.enter="sendChat"
                  :disabled="chatLoading"
                >
                  <template #append>
                    <el-button @click="sendChat" :loading="chatLoading">发送</el-button>
                  </template>
                </el-input>
              </div>
            </div>
          </el-tab-pane>

          <!-- Characters -->
          <el-tab-pane label="角色" name="characters">
            <div class="char-panel">
              <div v-if="characters.length" class="char-list">
                <div v-for="(c, i) in characters" :key="i" class="char-card">
                  <div class="char-header">
                    <strong>{{ c.name }}</strong>
                    <el-tag size="small">{{ roleLabel(c.role) }}</el-tag>
                  </div>
                  <p v-if="c.description">{{ c.description }}</p>
                  <div v-if="c.traits && c.traits.length" class="char-traits">
                    <el-tag v-for="t in c.traits" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
                  </div>
                </div>
              </div>
              <el-empty v-else description="生成剧本后自动提取角色" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- Toggle right panel -->
      <button class="toggle-right" @click="showRightPanel = !showRightPanel">
        {{ showRightPanel ? '▶' : '◀' }}
      </button>
    </div>

    <!-- Version history drawer -->
    <el-drawer v-model="showHistory" title="历史版本" size="360px">
      <div v-if="versions.length">
        <div v-for="v in versions" :key="v.id" class="ver-row">
          <div>
            <strong>v{{ v.versionNumber }}</strong>
            <span class="ver-time">{{ formatDate(v.createdAt) }}</span>
          </div>
          <div>
            <el-button size="small" text type="primary" @click="loadVersion(v)">查看</el-button>
            <el-button size="small" text @click="downloadVersion(v.id)">下载</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import hljs from 'highlight.js/lib/core'
import yamlLang from 'highlight.js/lib/languages/yaml'
import 'highlight.js/styles/github.css'
import { getProject, splitChapters } from '@/api/projects'
import { listVersions, getLatest, getYamlUrl, saveEditedYaml } from '@/api/scripts'
import { sendMessage, getHistory } from '@/api/chat'

hljs.registerLanguage('yaml', yamlLang)

const route = useRoute()
const projectId = route.params.id

// Project
const projectTitle = ref('')
const projectStatus = ref('DRAFT')
const originalText = ref('')
const chapters = ref([])
const currentChapter = ref(0)
const splitting = ref(false)

// Generation
const generating = ref(false)
const progressMsg = ref('')
const yamlContent = ref('')
const latestVersion = ref(null)

// YAML editing
const editing = ref(false)
const editableYaml = ref('')
const saving = ref(false)

// Versions
const versions = ref([])
const showHistory = ref(false)

// Chat
const chatMessages = ref([])
const chatInput = ref('')
const chatLoading = ref(false)
const chatRef = ref(null)

// Characters (parsed from YAML)
const characters = ref([])

// UI
const rightTab = ref('chat')
const showRightPanel = ref(true)

const statusTag = computed(() =>
  projectStatus.value === 'COMPLETED' ? 'success' : projectStatus.value === 'PROCESSING' ? 'warning' : 'info')
const statusText = computed(() => ({ DRAFT: '草稿', PROCESSING: '处理中', COMPLETED: '已完成' })[projectStatus.value] || '')

const highlightedYaml = computed(() => {
  if (!yamlContent.value) return ''
  return hljs.highlight(yamlContent.value, { language: 'yaml' }).value
})

// Parse characters from YAML on content change
watch(yamlContent, () => {
  try {
    const lines = yamlContent.value.split('\n')
    const chars = []
    let inChars = false, current = null
    for (const line of lines) {
      if (line.startsWith('characters:')) { inChars = true; continue }
      if (inChars && line.match(/^\w/)) break // next top-level key
      if (inChars) {
        if (line.match(/^  - name:/)) {
          if (current) chars.push(current)
          current = { name: line.replace(/^  - name: ?/, '').replace(/"/g, '').trim() }
        } else if (current && line.includes('role:')) {
          current.role = line.replace(/.*role: ?/, '').replace(/"/g, '').trim()
        } else if (current && line.includes('description:')) {
          current.description = line.replace(/.*description: ?/, '').replace(/"/g, '').trim()
        } else if (current && line.includes('- ')) {
          if (!current.traits) current.traits = []
          current.traits.push(line.replace(/.*- ?/, '').replace(/"/g, '').trim())
        }
      }
    }
    if (current) chars.push(current)
    characters.value = chars
  } catch { characters.value = [] }
})

onMounted(async () => {
  try {
    const res = await getProject(projectId)
    const d = res.data.data
    projectTitle.value = d.project?.title || ''
    projectStatus.value = d.project?.status || 'DRAFT'
    originalText.value = d.originalText || ''

    const v = await getLatest(projectId)
    if (v.data.data) {
      latestVersion.value = v.data.data
      yamlContent.value = v.data.data.yamlContent
    }

    if (originalText.value && !chapters.value.length) await handleSplit()

    // Load chat history
    const h = await getHistory(projectId)
    chatMessages.value = (h.data.data || []).map(m => ({ role: m.role, content: m.content }))
  } catch {}
})

async function handleSplit() {
  splitting.value = true
  try {
    const r = await splitChapters(projectId)
    chapters.value = r.data.data.chapters || []
    if (chapters.value.length) currentChapter.value = 0
  } catch (e) { ElMessage.error(e.response?.data?.message || '分章失败') }
  finally { splitting.value = false }
}

function handleGenerate() {
  generating.value = true
  progressMsg.value = '正在连接...'
  yamlContent.value = ''

  const token = localStorage.getItem('token')
  const url = `/api/projects/${projectId}/generate/stream`

  fetch(url, { headers: { Authorization: `Bearer ${token}` } })
    .then(response => {
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      function read() {
        reader.read().then(({ done, value }) => {
          if (done) { generating.value = false; return }
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            if (line.startsWith('event:')) {
              const eventType = line.replace('event:', '').trim()
              const dataLine = lines[lines.indexOf(line) + 1]
              if (dataLine && dataLine.startsWith('data:')) {
                try {
                  const data = JSON.parse(dataLine.replace('data:', '').trim())
                  if (eventType === 'progress') {
                    progressMsg.value = data.message || ''
                  } else if (eventType === 'done') {
                    yamlContent.value = data.yamlContent || ''
                    latestVersion.value = { id: data.versionId, versionNumber: data.versionNumber }
                    projectStatus.value = 'COMPLETED'
                    progressMsg.value = '生成完成！'
                    ElMessage.success(`剧本 v${data.versionNumber} 生成成功`)
                  } else if (eventType === 'error') {
                    ElMessage.error(data || '生成失败')
                    generating.value = false
                  }
                } catch {}
              }
            }
          }
          if (generating.value) read()
        })
      }
      read()
    })
    .catch(e => {
      ElMessage.error('连接失败: ' + e.message)
      generating.value = false
    })
}

async function saveYaml() {
  if (!editableYaml.value.trim()) return
  saving.value = true
  try {
    const r = await saveEditedYaml(projectId, editableYaml.value)
    const v = r.data.data
    yamlContent.value = editableYaml.value
    latestVersion.value = { id: v.id, versionNumber: v.versionNumber }
    ElMessage.success(`已保存为 v${v.versionNumber}`)
    editing.value = false
    loadVersions()
  } catch (e) { ElMessage.error('保存失败') }
  finally { saving.value = false }
}

async function sendChat() {
  const msg = chatInput.value.trim()
  if (!msg || chatLoading.value) return
  chatInput.value = ''
  chatMessages.value.push({ role: 'user', content: msg })
  chatLoading.value = true
  await nextTick()
  if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight

  try {
    const r = await sendMessage(projectId, msg)
    chatMessages.value.push({ role: 'assistant', content: r.data.data.reply })
  } catch (e) { ElMessage.error('发送失败') }
  finally {
    chatLoading.value = false
    await nextTick()
    if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
  }
}

async function loadVersions() {
  try { versions.value = (await listVersions(projectId)).data.data || [] } catch {}
}

function loadVersion(v) {
  yamlContent.value = v.yamlContent
  editing.value = false
  showHistory.value = false
}

function downloadYaml() {
  if (latestVersion.value) window.open(getYamlUrl(latestVersion.value.id), '_blank')
}

function downloadVersion(id) { window.open(getYamlUrl(id), '_blank') }

function copyYaml() {
  navigator.clipboard.writeText(yamlContent.value)
    .then(() => ElMessage.success('已复制'))
    .catch(() => ElMessage.error('复制失败'))
}

function roleLabel(r) {
  return { protagonist: '主角', antagonist: '反派', supporting: '配角', minor: '次要' }[r] || r || ''
}

function formatDate(d) { return d ? new Date(d).toLocaleString('zh-CN') : '' }
</script>

<style scoped>
.editor-page { height: 100vh; display: flex; flex-direction: column; background: #f5f7fa; }

.topbar {
  display: flex; justify-content: space-between; align-items: center;
  height: 48px; padding: 0 16px; background: #fff; border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}
.topbar-left { display: flex; align-items: center; gap: 10px; }
.topbar-left h2 { font-size: 16px; margin: 0; color: #303133; font-weight: 600; }
.topbar-right { display: flex; gap: 8px; }

.action-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 16px; background: #fff; border-bottom: 1px solid #ebeef5; flex-shrink: 0;
}
.action-left { display: flex; align-items: center; gap: 12px; }
.hint { color: #909399; font-size: 13px; }

.progress-bar {
  padding: 10px 16px; background: #fff; border-bottom: 1px solid #ebeef5; flex-shrink: 0;
}
.progress-msg { margin: 8px 0 0; font-size: 13px; color: #909399; text-align: center; }

.chapter-bar {
  display: flex; gap: 4px; padding: 6px 16px; background: #fff;
  border-bottom: 1px solid #ebeef5; overflow-x: auto; flex-shrink: 0;
}
.chip {
  flex-shrink: 0; padding: 3px 10px; border: 1px solid #dcdfe6; border-radius: 4px;
  background: #fff; font-size: 12px; color: #606266; cursor: pointer; white-space: nowrap;
}
.chip:hover { color: #409eff; border-color: #409eff; }
.chip.active { background: #409eff; color: #fff; border-color: #409eff; }

.main { flex: 1; display: flex; overflow: hidden; position: relative; }
.col { display: flex; flex-direction: column; background: #fff; }
.col-left { width: 30%; min-width: 280px; border-right: 1px solid #ebeef5; }
.col-center { width: 45%; border-right: 1px solid #ebeef5; }
.col-wide { width: 70%; }
.col-right { width: 25%; min-width: 300px; }
.col-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 16px; border-bottom: 1px solid #ebeef5; flex-shrink: 0;
}
.col-title { font-size: 13px; font-weight: 600; color: #606266; }

.col-body { flex: 1; overflow: auto; padding: 16px; }
.col-body pre {
  margin: 0; white-space: pre-wrap; word-break: break-word;
  font-family: Georgia, 'Noto Serif SC', serif; font-size: 14px; line-height: 1.9; color: #303133;
}

/* YAML display */
.yaml-display pre {
  font-family: Menlo, Monaco, 'Courier New', monospace; font-size: 13px; line-height: 1.6;
}
.yaml-display code { white-space: pre-wrap; }
.yaml-editor {
  width: 100%; height: calc(100% - 48px); border: none; outline: none; resize: none;
  font-family: Menlo, Monaco, 'Courier New', monospace; font-size: 13px; line-height: 1.6;
  padding: 0; background: #fafbfc;
}
.editor-footer { display: flex; justify-content: flex-end; gap: 8px; padding-top: 8px; }

/* Right tabs */
.right-tabs { height: 100%; display: flex; flex-direction: column; }
.right-tabs :deep(.el-tabs__content) { flex: 1; overflow: hidden; }
.right-tabs :deep(.el-tab-pane) { height: 100%; }

/* Chat */
.chat-panel { height: 100%; display: flex; flex-direction: column; }
.chat-messages { flex: 1; overflow: auto; padding: 12px; }
.msg { margin-bottom: 12px; }
.msg.user .msg-content { background: #409eff; color: #fff; border-radius: 12px 12px 4px 12px; padding: 8px 12px; margin-left: 40px; font-size: 13px; }
.msg.assistant .msg-content { background: #f0f2f5; color: #303133; border-radius: 12px 12px 12px 4px; padding: 8px 12px; margin-right: 20px; font-size: 13px; }
.typing { color: #909399; font-style: italic; }
.chat-input { padding: 8px; border-top: 1px solid #ebeef5; }

/* Characters */
.char-panel { padding: 12px; overflow: auto; height: 100%; }
.char-card { padding: 10px 12px; background: #fafafa; border-radius: 8px; margin-bottom: 8px; border: 1px solid #ebeef5; }
.char-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.char-card p { font-size: 12px; color: #606266; margin: 4px 0; }
.char-traits { display: flex; gap: 4px; flex-wrap: wrap; margin-top: 4px; }

/* Toggle */
.toggle-right {
  position: absolute; right: 0; top: 50%; transform: translateY(-50%);
  width: 20px; height: 48px; border: 1px solid #ebeef5; border-right: none;
  background: #fff; cursor: pointer; font-size: 10px; color: #909399;
  display: flex; align-items: center; justify-content: center; border-radius: 4px 0 0 4px;
  z-index: 10;
}
.toggle-right:hover { background: #f0f2f5; }

/* Version */
.ver-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px; border-bottom: 1px solid #ebeef5;
}
.ver-time { display: block; font-size: 12px; color: #909399; }
</style>
