<template>
  <div class="editor-page">
    <el-container>
      <el-header class="editor-header">
        <div class="header-left">
          <el-button text @click="$router.push('/')">
            <el-icon><ArrowLeft /></el-icon> 返回
          </el-button>
          <h2>{{ projectTitle }}</h2>
          <el-tag v-if="projectStatus" :type="statusTag(projectStatus)">
            {{ statusText(projectStatus) }}
          </el-tag>
        </div>
        <div class="header-right">
          <el-button
            v-if="latestVersion"
            type="success"
            @click="downloadYaml"
          >
            <el-icon><Download /></el-icon> 下载 YAML
          </el-button>
        </div>
      </el-header>

      <el-main class="editor-main">
        <el-row :gutter="16" style="height: 100%">
          <!-- Left panel -->
          <el-col :span="12" style="height: 100%">
            <el-card class="panel-card">
              <template #header>
                <div class="panel-header">
                  <span>📖 原文内容</span>
                  <div class="panel-actions">
                    <el-button
                      size="small"
                      :loading="splitting"
                      @click="handleSplit"
                    >
                      分章预览
                    </el-button>
                    <el-button
                      type="primary"
                      size="small"
                      :loading="generating"
                      :disabled="generating"
                      @click="handleGenerate"
                    >
                      🎬 生成剧本
                    </el-button>
                  </div>
                </div>
              </template>
              <div class="text-display">
                <div v-if="chapters.length > 0" style="margin-bottom: 12px;">
                  <el-tag
                    v-for="(ch, i) in chapters"
                    :key="i"
                    style="margin: 2px; cursor: pointer;"
                    :type="currentChapter === i ? 'primary' : 'info'"
                    @click="currentChapter = i"
                  >
                    {{ ch.title }}
                  </el-tag>
                  <p style="color: #999; font-size: 12px; margin: 8px 0;">
                    共识别 {{ chapters.length }} 章
                  </p>
                </div>
                <div class="text-content">
                  <pre v-if="chapters.length > 0">{{ chapters[currentChapter]?.content }}</pre>
                  <pre v-else>{{ originalText }}</pre>
                </div>
              </div>
            </el-card>
          </el-col>

          <!-- Right panel -->
          <el-col :span="12" style="height: 100%">
            <el-card class="panel-card">
              <template #header>
                <el-tabs v-model="rightTab" class="panel-tabs">
                  <el-tab-pane label="剧本预览 (YAML)" name="preview" />
                  <el-tab-pane label="历史版本" name="history" />
                </el-tabs>
              </template>

              <!-- YAML Preview -->
              <div v-if="rightTab === 'preview'" class="yaml-preview">
                <div v-if="generating" class="generating-hint">
                  <el-icon class="is-loading" :size="32"><Loading /></el-icon>
                  <p>AI 正在生成剧本中...请耐心等待（可能需要 1-2 分钟）</p>
                </div>
                <div v-else-if="yamlContent" class="yaml-content">
                  <pre><code>{{ yamlContent }}</code></pre>
                </div>
                <el-empty v-else description="点击左侧「生成剧本」按钮开始" />
              </div>

              <!-- History -->
              <div v-else class="version-list">
                <el-timeline v-if="versions.length > 0">
                  <el-timeline-item
                    v-for="v in versions"
                    :key="v.id"
                    :timestamp="formatDate(v.createdAt)"
                    placement="top"
                  >
                    <el-card shadow="hover" size="small">
                      <div class="version-item">
                        <span>版本 v{{ v.versionNumber }}</span>
                        <div>
                          <el-button size="small" text @click="loadVersion(v)">查看</el-button>
                          <el-button size="small" text type="success" @click="downloadVersion(v.id)">
                            下载
                          </el-button>
                        </div>
                      </div>
                    </el-card>
                  </el-timeline-item>
                </el-timeline>
                <el-empty v-else description="暂无历史版本" />
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProject } from '@/api/projects'
import { splitChapters, generateScript } from '@/api/projects'
import { listVersions, getLatest, getYamlUrl } from '@/api/scripts'

const route = useRoute()
const projectId = route.params.id

const projectTitle = ref('')
const projectStatus = ref('')
const originalText = ref('')
const chapters = ref([])
const currentChapter = ref(0)
const splitting = ref(false)
const generating = ref(false)

const rightTab = ref('preview')
const yamlContent = ref('')
const latestVersion = ref(null)
const versions = ref([])

onMounted(async () => {
  try {
    const res = await getProject(projectId)
    const data = res.data.data
    projectTitle.value = data.project?.title || '未命名'
    projectStatus.value = data.project?.status || 'DRAFT'
    originalText.value = data.originalText || ''

    // Load existing script if any
    const vRes = await getLatest(projectId)
    if (vRes.data.data) {
      latestVersion.value = vRes.data.data
      yamlContent.value = vRes.data.data.yamlContent
    }
  } catch (e) {
    ElMessage.error('加载项目失败')
  }
})

async function handleSplit() {
  splitting.value = true
  try {
    const res = await splitChapters(projectId)
    const data = res.data.data
    chapters.value = data.chapters || []
    ElMessage.success(`成功识别 ${data.totalChapters} 个章节`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '分章失败，请检查文本格式')
  } finally {
    splitting.value = false
  }
}

async function handleGenerate() {
  generating.value = true
  rightTab.value = 'preview'
  try {
    const res = await generateScript(projectId)
    const data = res.data.data
    yamlContent.value = data.yamlContent
    latestVersion.value = { id: data.versionId, versionNumber: data.versionNumber }

    // Update project status
    projectStatus.value = 'COMPLETED'

    // Reload versions
    loadVersions()

    ElMessage.success(`剧本生成成功！版本 v${data.versionNumber}`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '生成失败，请重试')
  } finally {
    generating.value = false
  }
}

async function loadVersions() {
  try {
    const res = await listVersions(projectId)
    versions.value = res.data.data || []
  } catch (e) {
    // silent
  }
}

function loadVersion(version) {
  rightTab.value = 'preview'
  yamlContent.value = version.yamlContent
}

function downloadYaml() {
  if (latestVersion.value) {
    window.open(getYamlUrl(latestVersion.value.id), '_blank')
  }
}

function downloadVersion(versionId) {
  window.open(getYamlUrl(versionId), '_blank')
}

function statusTag(status) {
  return status === 'COMPLETED' ? 'success' : status === 'PROCESSING' ? 'warning' : 'info'
}

function statusText(status) {
  return status === 'COMPLETED' ? '已完成' : status === 'PROCESSING' ? '处理中' : '草稿'
}

function formatDate(dateStr) {
  return dateStr ? new Date(dateStr).toLocaleString('zh-CN') : ''
}
</script>

<style scoped>
.editor-page { height: 100vh; display: flex; flex-direction: column; }
.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  padding: 0 20px;
  height: 56px;
  flex-shrink: 0;
}
.header-left { display: flex; align-items: center; gap: 12px; }
.header-left h2 { font-size: 16px; margin: 0; }
.header-right { display: flex; gap: 8px; }
.editor-main { flex: 1; overflow: hidden; padding: 16px; }
.panel-card { height: 100%; display: flex; flex-direction: column; }
.panel-card :deep(.el-card__body) { flex: 1; overflow: hidden; padding: 0; }
.panel-header { display: flex; justify-content: space-between; align-items: center; }
.panel-actions { display: flex; gap: 8px; }
.panel-tabs { margin: -6px 0; }
.panel-tabs :deep(.el-tabs__header) { margin: 0; }
.text-display { padding: 12px; height: 100%; overflow: auto; }
.text-content { max-height: calc(100vh - 260px); overflow: auto; }
.text-content pre { white-space: pre-wrap; word-break: break-all; font-size: 14px; line-height: 1.8; color: #333; }
.yaml-preview { padding: 12px; height: 100%; overflow: auto; }
.yaml-content { max-height: calc(100vh - 240px); overflow: auto; }
.yaml-content pre { margin: 0; }
.yaml-content code {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  color: #2c3e50;
}
.generating-hint { text-align: center; padding: 60px 0; color: #909399; }
.generating-hint p { margin-top: 16px; }
.version-list { padding: 12px; max-height: calc(100vh - 240px); overflow: auto; }
.version-item { display: flex; justify-content: space-between; align-items: center; }
</style>
