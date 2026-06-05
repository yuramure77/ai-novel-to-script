<template>
  <div class="yaml-viewer">
    <div class="yaml-header">
      <span>剧本 YAML</span>
      <div class="yaml-header-actions">
        <el-button size="small" text @click="copyYaml">复制</el-button>
        <el-button size="small" text @click="$emit('download')">下载</el-button>
      </div>
    </div>
    <div class="yaml-body">
      <pre><code v-html="highlightedCode"></code></pre>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import hljs from 'highlight.js/lib/core'
import yaml from 'highlight.js/lib/languages/yaml'
import 'highlight.js/styles/github.css'

hljs.registerLanguage('yaml', yaml)

const props = defineProps({
  content: { type: String, default: '' }
})

defineEmits(['download'])

const highlightedCode = computed(() => {
  if (!props.content) return ''
  return hljs.highlight(props.content, { language: 'yaml' }).value
})

async function copyYaml() {
  try {
    await navigator.clipboard.writeText(props.content)
    ElMessage.success('已复制')
  } catch { ElMessage.error('复制失败') }
}
</script>

<style scoped>
.yaml-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.yaml-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;
  color: #606266;
  font-weight: 500;
  flex-shrink: 0;
}
.yaml-header-actions {
  display: flex;
  gap: 4px;
}
.yaml-body {
  flex: 1;
  overflow: auto;
  padding: 16px 20px;
  background: #fafbfc;
}
.yaml-body pre {
  margin: 0;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}
.yaml-body code {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
