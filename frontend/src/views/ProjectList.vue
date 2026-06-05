<template>
  <div class="project-list-page">
    <header class="navbar">
      <div class="navbar-left">
        <h1>小说转剧本</h1>
      </div>
      <div class="navbar-right">
        <span class="navbar-user">{{ nickname }}</span>
        <el-button text @click="handleLogout">退出</el-button>
      </div>
    </header>

    <main class="content">
      <div class="content-header">
        <h2>我的项目</h2>
        <el-button type="primary" @click="showCreate = true">新建项目</el-button>
      </div>

      <el-empty v-if="!projects.length && !loading" description="暂无项目，点击上方按钮创建" />

      <div v-else class="project-grid">
        <el-card
          v-for="project in projects"
          :key="project.id"
          class="project-card"
          shadow="hover"
          @click="$router.push(`/project/${project.id}`)"
        >
          <div class="card-header">
            <h3>{{ project.title }}</h3>
            <el-tag
              :type="project.status === 'COMPLETED' ? 'success' : project.status === 'PROCESSING' ? 'warning' : 'info'"
              size="small"
            >
              {{ statusMap[project.status] }}
            </el-tag>
          </div>
          <div class="card-info">
            <span>{{ project.chapterCount }} 章</span>
            <span>{{ formatDate(project.updatedAt) }}</span>
          </div>
          <div class="card-actions" @click.stop>
            <el-button size="small" @click="$router.push(`/project/${project.id}`)">打开</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(project.id)">
              <template #reference>
                <el-button size="small" type="danger" plain>删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </el-card>
      </div>
    </main>

    <el-dialog v-model="showCreate" title="新建项目" width="600px" :close-on-click-modal="false">
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="项目名称" prop="title">
          <el-input v-model="form.title" placeholder="如：《三体》改编剧本" />
        </el-form-item>
        <el-form-item label="小说原文" prop="originalText">
          <div style="display: flex; gap: 8px; margin-bottom: 8px;">
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept=".txt,.epub,.docx"
              :on-change="handleFileChange"
            >
              <el-button size="small">上传文件 (txt/epub/docx)</el-button>
            </el-upload>
            <span v-if="uploading" style="color: #909399; font-size: 13px; line-height: 32px;">解析中...</span>
          </div>
          <el-input
            v-model="form.originalText"
            type="textarea"
            placeholder="粘贴小说原文，或上传文件自动填充..."
            :rows="14"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建项目</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listProjects, createProject, deleteProject } from '@/api/projects'
import { uploadFile } from '@/api/files'

const router = useRouter()
const projects = ref([])
const loading = ref(false)
const showCreate = ref(false)
const creating = ref(false)
const uploading = ref(false)
const formRef = ref(null)
const nickname = ref(localStorage.getItem('nickname') || '用户')

const form = reactive({ title: '', originalText: '' })
const rules = {
  title: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  originalText: [{ required: true, message: '请粘贴小说原文', trigger: 'blur' }]
}

const statusMap = { DRAFT: '草稿', PROCESSING: '处理中', COMPLETED: '已完成' }

onMounted(() => fetchProjects())

async function fetchProjects() {
  loading.value = true
  try {
    const res = await listProjects()
    projects.value = res.data.data || []
  } catch { projects.value = [] } finally { loading.value = false }
}

async function handleCreate() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    const res = await createProject(form.title, form.originalText)
    ElMessage.success('创建成功')
    showCreate.value = false
    form.title = ''; form.originalText = ''
    router.push(`/project/${res.data.data.id}`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  } finally { creating.value = false }
}

async function handleDelete(id) {
  try {
    await deleteProject(id)
    ElMessage.success('已删除')
    fetchProjects()
  } catch { ElMessage.error('删除失败') }
}

async function handleFileChange(file) {
  uploading.value = true
  try {
    const res = await uploadFile(file.raw)
    const text = res.data.data.text
    form.originalText = text
    if (!form.title && res.data.data.filename) {
      form.title = res.data.data.filename.replace(/\.[^.]+$/, '') + ' 改编'
    }
    ElMessage.success('文件解析成功')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '解析失败')
  } finally { uploading.value = false }
}

function handleLogout() {
  localStorage.clear()
  router.push('/login')
}

function formatDate(d) {
  if (!d) return ''
  return new Date(d).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.project-list-page {
  min-height: 100vh;
  background: #f5f7fa;
}
.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 56px;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.navbar-left h1 {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}
.navbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.navbar-user {
  color: #606266;
  font-size: 14px;
}
.content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}
.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.content-header h2 {
  font-size: 20px;
  color: #303133;
  margin: 0;
  font-weight: 600;
}
.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.project-card {
  cursor: pointer;
}
.project-card:hover {
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-header h3 {
  font-size: 16px;
  color: #303133;
  margin: 0;
}
.card-info {
  display: flex;
  justify-content: space-between;
  color: #909399;
  font-size: 13px;
  margin: 12px 0;
}
.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}
</style>
