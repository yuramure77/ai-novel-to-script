<template>
  <div class="script-editor">
    <el-container>
      <el-header>
        <el-button @click="$router.push('/')">← 返回</el-button>
        <h2>{{ projectTitle }}</h2>
      </el-header>
      <el-main>
        <el-tabs>
          <el-tab-pane label="原始文本">原著内容</el-tab-pane>
          <el-tab-pane label="剧本预览">YAML 预览</el-tab-pane>
          <el-tab-pane label="历史版本">版本列表</el-tab-pane>
        </el-tabs>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
// Placeholder — will be fully implemented in PR #5
</script>
