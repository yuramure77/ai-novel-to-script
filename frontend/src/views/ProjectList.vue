<template>
  <div class="project-list">
    <el-container>
      <el-header>
        <h2>我的项目</h2>
        <el-button type="primary" @click="createProject">新建项目</el-button>
      </el-header>
      <el-main>
        <el-table :data="projects" style="width: 100%">
          <el-table-column prop="title" label="项目名称" />
          <el-table-column prop="status" label="状态" />
          <el-table-column prop="createdAt" label="创建时间" />
          <el-table-column label="操作">
            <template #default>
              <el-button type="primary" link>打开</el-button>
              <el-button type="danger" link>删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
// Placeholder — will be fully implemented in PR #5
</script>
