<template>
  <div class="editor-shell">
    <!-- Top toolbar -->
    <header class="editor-topbar">
      <div class="topbar-left">
        <el-button text @click="$router.push('/')" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div class="project-info">
          <h2>{{ projectTitle }}</h2>
          <span class="project-meta">
            <el-tag :type="statusTag" size="small" effect="light" round>{{ statusText }}</el-tag>
            <span v-if="chapters.length">{{ chapters.length }} 章</span>
            <span v-if="sceneCount">· {{ sceneCount }} 个场景</span>
            <span v-if="charCount">· {{ charCount }} 个角色</span>
          </span>
        </div>
      </div>
      <div class="topbar-actions">
        <el-button
          v-if="latestVersion"
          size="default"
          @click="showHistory = true"
        >历史版本</el-button>
        <el-button
          v-if="latestVersion"
          type="success"
          size="default"
          @click="downloadYaml"
          :icon="Download"
        >下载 YAML</el-button>
      </div>
    </header>

    <!-- Main workspace -->
    <div class="workspace">
      <!-- Left: Source text -->
      <section class="panel panel-left">
        <div class="panel-header">
          <span class="panel-title">📖 原文</span>
          <div class="panel-header-actions">
            <el-button size="small" @click="handleSplit" :loading="splitting">
              分章预览
            </el-button>
          </div>
        </div>

        <!-- Chapter tabs -->
        <div v-if="chapters.length" class="chapter-tabs">
          <button
            v-for="(ch, i) in chapters"
            :key="i"
            :class="['chapter-chip', { active: currentChapter === i }]"
            @click="currentChapter = i"
          >
            {{ ch.title }}
          </button>
        </div>

        <div class="panel-body">
          <div v-if="chapters.length" class="text-content">
            <pre>{{ chapters[currentChapter]?.content }}</pre>
          </div>
          <div v-else class="text-content">
            <pre>{{ originalText }}</pre>
          </div>
        </div>
      </section>

      <!-- Center: Generation controls -->
      <section class="panel panel-center">
        <div class="panel-header">
          <span class="panel-title">🤖 AI 生成</span>
        </div>
        <div class="center-content">
          <!-- Not yet generated -->
          <div v-if="!yamlContent && !generating" class="generate-prompt">
            <div class="generate-icon">
              <svg width="80" height="80" viewBox="0 0 80 80" fill="none">
                <circle cx="40" cy="40" r="36" fill="#f0edff" />
                <path d="M40 20v40M20 40h40" stroke="#6C5CE7" stroke-width="3" stroke-linecap="round" />
              </svg>
            </div>
            <h3>准备生成剧本</h3>
            <p>AI 将自动完成以下步骤：</p>
            <div class="step-list">
              <div class="step-item">
                <span class="step-num">1</span> 识别章节结构
              </div>
              <div class="step-item">
                <span class="step-num">2</span> 提取角色信息
              </div>
              <div class="step-item">
                <span class="step-num">3</span> 分析场景与对白
              </div>
              <div class="step-item">
                <span class="step-num">4</span> 生成结构化 YAML
              </div>
            </div>
            <el-button
              type="primary"
              size="large"
              @click="handleGenerate"
              class="generate-btn"
            >🎬 开始生成</el-button>
          </div>

          <!-- Generating -->
          <div v-else-if="generating" class="generating-state">
            <div class="pulse-spinner"></div>
            <h3>AI 正在改编剧本...</h3>
            <p class="gen-detail">分析角色、提取对白、构建场景结构</p>
            <p class="gen-hint">通常需要 1-2 分钟，请耐心等待</p>
            <div class="progress-bar-container">
              <div class="progress-bar-fill"></div>
            </div>
          </div>

          <!-- Done: show stats -->
          <div v-else class="gen-done">
            <div class="done-icon">✅</div>
            <h3>剧本生成完成</h3>
            <div class="done-stats">
              <div class="done-stat">
                <span class="done-stat-num">{{ sceneCount }}</span>
                <span class="done-stat-label">场景</span>
              </div>
              <div class="done-stat">
                <span class="done-stat-num">{{ charCount }}</span>
                <span class="done-stat-label">角色</span>
              </div>
              <div class="done-stat">
                <span class="done-stat-num">{{ yamlLineCount }}</span>
                <span class="done-stat-label">行 YAML</span>
              </div>
              <div class="done-stat">
                <span class="done-stat-num">v{{ latestVersion?.versionNumber || 1 }}</span>
                <span class="done-stat-label">版本</span>
              </div>
            </div>
            <el-button type="primary" @click="handleGenerate" :loading="generating">
              重新生成
            </el-button>
          </div>
        </div>
      </section>

      <!-- Right: YAML Preview -->
      <section class="panel panel-right">
        <YamlViewer
          v-if="yamlContent"
          :content="yamlContent"
          @download="downloadYaml"
        />
        <div v-else class="empty-preview">
          <div class="empty-preview-icon">📄</div>
          <p>点击「开始生成」查看剧本</p>
        </div>
      </section>
    </div>

    <!-- Version history drawer -->
    <el-drawer v-model="showHistory" title="历史版本" size="360px">
      <el-timeline v-if="versions.length">
        <el-timeline-item
          v-for="v in versions"
          :key="v.id"
          :timestamp="formatDate(v.createdAt)"
          placement="top"
        >
          <div class="version-card" @click="loadVersion(v)">
            <strong>版本 v{{ v.versionNumber }}</strong>
            <div class="version-actions">
              <el-button size="small" text type="primary">查看</el-button>
              <el-button size="small" text type="success" @click.stop="downloadVersion(v.id)">下载</el-button>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无历史版本" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { getProject, splitChapters, generateScript } from '@/api/projects'
import { listVersions, getLatest, getYamlUrl } from '@/api/scripts'
import YamlViewer from '@/components/YamlViewer.vue'

const route = useRoute()
const projectId = route.params.id

const projectTitle = ref('')
const projectStatus = ref('DRAFT')
const originalText = ref('')
const chapters = ref([])
const currentChapter = ref(0)
const splitting = ref(false)
const generating = ref(false)

const yamlContent = ref('')
const latestVersion = ref(null)
const versions = ref([])
const showHistory = ref(false)

const statusTag = computed(() =>
  projectStatus.value === 'COMPLETED' ? 'success' :
  projectStatus.value === 'PROCESSING' ? 'warning' : 'info'
)
const statusText = computed(() =>
  projectStatus.value === 'COMPLETED' ? '已完成' :
  projectStatus.value === 'PROCESSING' ? '处理中' : '草稿'
)

// Parse YAML stats
const sceneCount = computed(() => (yamlContent.value.match(/^- id: SCENE_/gm) || []).length)
const charCount = computed(() => (yamlContent.value.match(/^- id: CHAR_/gm) || []).length)
const yamlLineCount = computed(() => yamlContent.value.split('\n').filter(Boolean).length)

onMounted(async () => {
  try {
    const res = await getProject(projectId)
    const data = res.data.data
    projectTitle.value = data.project?.title || ''
    projectStatus.value = data.project?.status || 'DRAFT'
    originalText.value = data.originalText || ''

    const vRes = await getLatest(projectId)
    if (vRes.data.data) {
      latestVersion.value = vRes.data.data
      yamlContent.value = vRes.data.data.yamlContent
    }
  } catch { ElMessage.error('加载项目失败') }
})

async function handleSplit() {
  splitting.value = true
  try {
    const res = await splitChapters(projectId)
    const data = res.data.data
    chapters.value = data.chapters || []
    ElMessage.success(`识别到 ${data.totalChapters} 个章节`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '分章失败')
  } finally { splitting.value = false }
}

async function handleGenerate() {
  generating.value = true
  try {
    const res = await generateScript(projectId)
    const data = res.data.data
    yamlContent.value = data.yamlContent
    latestVersion.value = { id: data.versionId, versionNumber: data.versionNumber }
    projectStatus.value = 'COMPLETED'
    loadVersions()
    ElMessage.success(`剧本 v${data.versionNumber} 生成成功！`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '生成失败')
  } finally { generating.value = false }
}

async function loadVersions() {
  try {
    const res = await listVersions(projectId)
    versions.value = res.data.data || []
  } catch {}
}

function loadVersion(v) {
  yamlContent.value = v.yamlContent
  showHistory.value = false
}

function downloadYaml() {
  if (latestVersion.value) window.open(getYamlUrl(latestVersion.value.id), '_blank')
}

function downloadVersion(id) { window.open(getYamlUrl(id), '_blank') }

function formatDate(d) {
  return d ? new Date(d).toLocaleString('zh-CN') : ''
}
</script>

<style scoped>
.editor-shell { height: 100vh; display: flex; flex-direction: column; background: #f8f9fb; }

/* Top bar */
.editor-topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #e8ecf1;
  flex-shrink: 0;
}
.topbar-left { display: flex; align-items: center; gap: 12px; }
.back-btn { font-size: 18px; }
.project-info h2 { font-size: 16px; margin: 0; color: #2d3436; }
.project-meta { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #a0aec0; margin-top: 2px; }

/* Workspace */
.workspace {
  flex: 1;
  display: flex;
  overflow: hidden;
  gap: 0;
}

/* Panels */
.panel {
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid #e8ecf1;
}
.panel-left { width: 35%; min-width: 300px; }
.panel-center { width: 25%; min-width: 240px; }
.panel-right { flex: 1; }
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e8ecf1;
  flex-shrink: 0;
}
.panel-title { font-size: 14px; font-weight: 600; color: #2d3436; }

/* Chapter chips */
.chapter-tabs {
  padding: 8px 12px;
  display: flex;
  gap: 6px;
  overflow-x: auto;
  border-bottom: 1px solid #f1f2f6;
  flex-shrink: 0;
}
.chapter-chip {
  flex-shrink: 0;
  padding: 6px 14px;
  border-radius: 20px;
  border: 1px solid #e2e8f0;
  background: white;
  font-size: 12px;
  color: #636e72;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.chapter-chip:hover { border-color: #6C5CE7; color: #6C5CE7; }
.chapter-chip.active { background: #6C5CE7; color: white; border-color: #6C5CE7; }

/* Text content */
.panel-body { flex: 1; overflow: auto; padding: 16px; }
.text-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Georgia', 'Noto Serif SC', serif;
  font-size: 14px;
  line-height: 1.9;
  color: #2d3436;
}

/* Center content */
.center-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.generate-prompt { text-align: center; }
.generate-icon { margin-bottom: 16px; }
.generate-prompt h3 { font-size: 18px; color: #2d3436; margin: 0 0 4px; }
.generate-prompt > p { color: #a0aec0; font-size: 13px; margin-bottom: 16px; }

.step-list {
  text-align: left;
  background: #f8f9fb;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 24px;
}
.step-item {
  padding: 6px 0;
  font-size: 13px;
  color: #636e72;
  display: flex;
  align-items: center;
  gap: 10px;
}
.step-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #f0edff;
  color: #6C5CE7;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
}
.generate-btn { width: 100%; height: 48px; font-size: 16px; font-weight: 700; }

/* Generating animation */
.generating-state { text-align: center; }
.pulse-spinner {
  width: 48px; height: 48px;
  margin: 0 auto 20px;
  border-radius: 50%;
  border: 4px solid #f0edff;
  border-top-color: #6C5CE7;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.generating-state h3 { font-size: 18px; color: #2d3436; margin: 0 0 8px; }
.gen-detail { font-size: 14px; color: #636e72; margin: 0 0 4px; }
.gen-hint { font-size: 12px; color: #a0aec0; margin-bottom: 24px; }
.progress-bar-container {
  width: 200px; height: 3px;
  background: #f1f2f6;
  border-radius: 2px;
  margin: 0 auto;
  overflow: hidden;
}
.progress-bar-fill {
  width: 60%;
  height: 100%;
  background: linear-gradient(90deg, #6C5CE7, #a29bfe);
  border-radius: 2px;
  animation: progress 1.5s ease-in-out infinite;
}
@keyframes progress {
  0% { width: 10%; }
  50% { width: 70%; }
  100% { width: 10%; }
}

/* Done state */
.gen-done { text-align: center; }
.done-icon { font-size: 48px; margin-bottom: 12px; }
.gen-done h3 { font-size: 18px; color: #2d3436; margin: 0 0 16px; }
.done-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 24px; }
.done-stat {
  background: #f8f9fb;
  border-radius: 10px;
  padding: 12px;
  text-align: center;
}
.done-stat-num { display: block; font-size: 22px; font-weight: 700; color: #6C5CE7; }
.done-stat-label { font-size: 12px; color: #a0aec0; }

/* Empty preview */
.empty-preview {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #a0aec0;
}
.empty-preview-icon { font-size: 48px; margin-bottom: 8px; }
.empty-preview p { font-size: 14px; }

/* Version card */
.version-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  cursor: pointer;
}
.version-actions { display: flex; gap: 4px; }
</style>
