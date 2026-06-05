<template>
  <div class="editor-page">
    <!-- Top bar -->
    <header class="editor-topbar">
      <div class="topbar-left">
        <el-button text @click="$router.push('/')">← 返回</el-button>
        <h2>{{ projectTitle }}</h2>
        <el-tag
          :type="projectStatus === 'COMPLETED' ? 'success' : projectStatus === 'PROCESSING' ? 'warning' : 'info'"
          size="small"
        >
          {{ statusText }}
        </el-tag>
      </div>
      <div class="topbar-right">
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
        >下载 YAML</el-button>
      </div>
    </header>

    <!-- Action bar -->
    <div class="action-bar">
      <div class="action-left">
        <el-button size="small" @click="handleSplit" :loading="splitting">分章预览</el-button>
        <span v-if="chapters.length" class="chapter-hint">
          共 {{ chapters.length }} 章
        </span>
      </div>
      <div>
        <el-button
          type="primary"
          @click="handleGenerate"
          :loading="generating"
          :disabled="generating"
        >
          {{ generating ? '正在生成...' : '生成剧本' }}
        </el-button>
      </div>
    </div>

    <!-- Chapter tabs -->
    <div v-if="chapters.length" class="chapter-bar">
      <button
        v-for="(ch, i) in chapters"
        :key="i"
        :class="['chapter-tab', { active: currentChapter === i }]"
        @click="currentChapter = i"
      >
        {{ ch.title }}
      </button>
    </div>

    <!-- Generation progress -->
    <div v-if="generating" class="generating-bar">
      <el-progress :percentage="100" :indeterminate="true" :stroke-width="3" :show-text="false" />
      <p>AI 正在分析角色、提取对白、构建场景...</p>
    </div>

    <!-- Main content -->
    <div class="editor-main">
      <!-- Left: Source text -->
      <div class="panel panel-left">
        <div class="panel-body">
          <div v-if="chapters.length" class="text-content">
            <pre>{{ chapters[currentChapter]?.content || originalText }}</pre>
          </div>
          <div v-else class="text-content">
            <pre>{{ originalText }}</pre>
          </div>
        </div>
      </div>

      <!-- Right: YAML Preview -->
      <div class="panel panel-right">
        <YamlViewer
          v-if="yamlContent"
          :content="yamlContent"
          @download="downloadYaml"
        />
        <div v-else class="empty-preview">
          <el-empty description="点击「生成剧本」开始改编" />
        </div>
      </div>
    </div>

    <!-- Version history drawer -->
    <el-drawer v-model="showHistory" title="历史版本" size="360px">
      <div v-if="versions.length">
        <div
          v-for="v in versions"
          :key="v.id"
          class="version-item"
        >
          <div class="version-info">
            <strong>版本 v{{ v.versionNumber }}</strong>
            <span class="version-time">{{ formatDate(v.createdAt) }}</span>
          </div>
          <div class="version-actions">
            <el-button size="small" text type="primary" @click="loadVersion(v)">查看</el-button>
            <el-button size="small" text @click="downloadVersion(v.id)">下载</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无历史版本" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
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

const statusText = computed(() =>
  projectStatus.value === 'COMPLETED' ? '已完成' :
  projectStatus.value === 'PROCESSING' ? '处理中' : '草稿'
)

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

    if (originalText.value && !chapters.value.length) {
      await handleSplit()
    }
  } catch { /* ignore */ }
})

async function handleSplit() {
  splitting.value = true
  try {
    const res = await splitChapters(projectId)
    const data = res.data.data
    chapters.value = data.chapters || []
    if (chapters.value.length) {
      currentChapter.value = 0
    }
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
    ElMessage.success(`剧本生成成功 (v${data.versionNumber})`)
    loadVersions()
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
.editor-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

/* Top bar */
.editor-topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 52px;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.topbar-left h2 {
  font-size: 16px;
  margin: 0;
  color: #303133;
  font-weight: 600;
}
.topbar-right {
  display: flex;
  gap: 8px;
}

/* Action bar */
.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}
.action-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.chapter-hint {
  color: #909399;
  font-size: 13px;
}

/* Chapter bar */
.chapter-bar {
  display: flex;
  gap: 4px;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  overflow-x: auto;
  flex-shrink: 0;
}
.chapter-tab {
  flex-shrink: 0;
  padding: 4px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}
.chapter-tab:hover {
  color: #409eff;
  border-color: #409eff;
}
.chapter-tab.active {
  background: #409eff;
  color: #fff;
  border-color: #409eff;
}

/* Generating bar */
.generating-bar {
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}
.generating-bar p {
  margin: 8px 0 0;
  font-size: 13px;
  color: #909399;
  text-align: center;
}

/* Main content */
.editor-main {
  flex: 1;
  display: flex;
  overflow: hidden;
  gap: 0;
}
.panel {
  display: flex;
  flex-direction: column;
  background: #fff;
}
.panel-left {
  width: 45%;
  border-right: 1px solid #ebeef5;
}
.panel-right {
  flex: 1;
}
.panel-body {
  flex: 1;
  overflow: auto;
  padding: 16px;
}
.text-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Georgia', 'Noto Serif SC', serif;
  font-size: 14px;
  line-height: 1.9;
  color: #303133;
}
.empty-preview {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Version drawer */
.version-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #ebeef5;
}
.version-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.version-time {
  font-size: 12px;
  color: #909399;
}
.version-actions {
  display: flex;
  gap: 4px;
}
</style>
