<template>
  <div class="yaml-viewer">
    <div class="yaml-toolbar">
      <span class="yaml-label">剧本 YAML</span>
      <div class="yaml-actions">
        <el-button size="small" text @click="copyYaml">
          <el-icon><CopyDocument /></el-icon> 复制
        </el-button>
        <el-button size="small" text type="success" @click="$emit('download')">
          <el-icon><Download /></el-icon> 下载
        </el-button>
      </div>
    </div>
    <div class="yaml-content" ref="codeRef">
      <pre><code v-html="highlightedCode"></code></pre>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import hljs from 'highlight.js/lib/core'
import yaml from 'highlight.js/lib/languages/yaml'
import 'highlight.js/styles/github-dark.css'

hljs.registerLanguage('yaml', yaml)

const props = defineProps({
  content: { type: String, default: '' }
})

defineEmits(['download'])

const codeRef = ref(null)

const highlightedCode = computed(() => {
  if (!props.content) return ''
  return hljs.highlight(props.content, { language: 'yaml' }).value
})

async function copyYaml() {
  try {
    await navigator.clipboard.writeText(props.content)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}
</script>

<style scoped>
.yaml-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #0d1117;
  border-radius: 8px;
  overflow: hidden;
}
.yaml-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: #161b22;
  border-bottom: 1px solid #30363d;
}
.yaml-label {
  color: #8b949e;
  font-size: 13px;
  font-weight: 600;
}
.yaml-actions {
  display: flex;
  gap: 4px;
}
.yaml-actions .el-button {
  color: #8b949e !important;
}
.yaml-actions .el-button:hover {
  color: #58a6ff !important;
}
.yaml-content {
  flex: 1;
  overflow: auto;
  padding: 16px;
}
.yaml-content pre {
  margin: 0;
  font-family: 'JetBrains Mono', 'Fira Code', 'Menlo', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.7;
}
.yaml-content :deep(.hljs-string)  { color: #a5d6ff; }
.yaml-content :deep(.hljs-attr)    { color: #79c0ff; }
.yaml-content :deep(.hljs-bullet)  { color: #ff7b72; }
.yaml-content :deep(.hljs-literal) { color: #79c0ff; }
.yaml-content :deep(.hljs-meta)    { color: #8b949e; }
.yaml-content :deep(.hljs-number)  { color: #a5d6ff; }
.yaml-content :deep(.hljs-comment) { color: #8b949e; font-style: italic; }
</style>
