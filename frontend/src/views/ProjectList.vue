<template>
  <div class="project-list-page">
    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <el-icon :size="28" color="#667eea"><VideoCamera /></el-icon>
          <h1>AI 小说转剧本</h1>
        </div>
        <div class="header-right">
          <span class="user-name">{{ nickname }}</span>
          <el-button text @click="handleLogout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main-content">
        <div class="toolbar">
          <h2>我的项目</h2>
          <el-button type="primary" size="large" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon> 新建项目
          </el-button>
        </div>

        <!-- Empty state -->
        <el-empty v-if="projects.length === 0 && !loading" description="还没有项目，点击上方按钮创建" />

        <!-- Project grid -->
        <div v-else class="project-grid">
          <el-card
            v-for="project in projects"
            :key="project.id"
            class="project-card"
            shadow="hover"
            @click="openProject(project.id)"
          >
            <div class="card-header">
              <h3>{{ project.title }}</h3>
              <el-tag :type="statusTag(project.status)" size="small">
                {{ statusText(project.status) }}
              </el-tag>
            </div>
            <div class="card-meta">
              <span><el-icon><Document /></el-icon> {{ project.chapterCount }} 章</span>
              <span>{{ formatDate(project.updatedAt) }}</span>
            </div>
            <div class="card-actions" @click.stop>
              <el-button text type="primary" @click="openProject(project.id)">打开</el-button>
              <el-popconfirm title="确定删除此项目？" @confirm="handleDelete(project.id)">
                <template #reference>
                  <el-button text type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </div>
          </el-card>
        </div>
      </el-main>
    </el-container>

    <!-- Create project dialog -->
    <el-dialog
      v-model="showCreateDialog"
      title="新建项目"
      width="650px"
      :close-on-click-modal="false"
    >
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
        <el-form-item label="项目标题" prop="title">
          <el-input
            v-model="createForm.title"
            placeholder="如：《三体》改编剧本"
            size="large"
          />
        </el-form-item>
        <el-form-item label="小说原文" prop="originalText">
          <el-input
            v-model="createForm.originalText"
            type="textarea"
            placeholder="请粘贴小说原文（至少3章），支持「第X章」和「Chapter X」格式自动分章..."
            :rows="12"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creatingProject" @click="handleCreate">
          创建并分章
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listProjects, createProject, deleteProject } from '@/api/projects'

const router = useRouter()
const projects = ref([])
const loading = ref(false)
const showCreateDialog = ref(false)
const creatingProject = ref(false)
const nickname = localStorage.getItem('nickname') || '用户'

const createForm = reactive({ title: '', originalText: '' })
const createRules = {
  title: [{ required: true, message: '请输入项目标题', trigger: 'blur' }],
  originalText: [{ required: true, message: '请粘贴小说原文', trigger: 'blur' }]
}
const createFormRef = ref(null)

onMounted(() => { fetchProjects() })

async function fetchProjects() {
  loading.value = true
  try {
    const res = await listProjects()
    projects.value = res.data.data || []
  } catch (e) {
    ElMessage.error('加载项目列表失败')
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return

  creatingProject.value = true
  try {
    await createProject(createForm.title, createForm.originalText)
    ElMessage.success('项目创建成功')
    showCreateDialog.value = false
    createForm.title = ''
    createForm.originalText = ''
    fetchProjects()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  } finally {
    creatingProject.value = false
  }
}

async function handleDelete(id) {
  try {
    await deleteProject(id)
    ElMessage.success('删除成功')
    fetchProjects()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

function openProject(id) {
  router.push(`/project/${id}`)
}

function handleLogout() {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  localStorage.removeItem('nickname')
  router.push('/login')
}

function statusTag(status) {
  return status === 'COMPLETED' ? 'success' : status === 'PROCESSING' ? 'warning' : 'info'
}

function statusText(status) {
  return status === 'COMPLETED' ? '已完成' : status === 'PROCESSING' ? '处理中' : '草稿'
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  padding: 0 24px;
  height: 60px;
}
.header-left { display: flex; align-items: center; gap: 10px; }
.header-left h1 { font-size: 18px; color: #333; margin: 0; }
.header-right { display: flex; align-items: center; gap: 12px; }
.user-name { color: #666; }
.main-content { max-width: 1200px; margin: 0 auto; padding: 24px; }
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.toolbar h2 { margin: 0; font-size: 20px; }
.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}
.project-card {
  cursor: pointer;
  transition: transform 0.2s;
}
.project-card:hover { transform: translateY(-2px); }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-header h3 { margin: 0; font-size: 16px; color: #333; }
.card-meta {
  display: flex;
  justify-content: space-between;
  color: #999;
  font-size: 13px;
  margin: 12px 0;
}
.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
}
</style>
