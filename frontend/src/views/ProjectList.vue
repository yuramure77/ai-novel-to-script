<template>
  <div class="app-shell">
    <!-- Sidebar -->
    <aside class="sidebar">
      <div class="sidebar-brand" @click="$router.push('/')">
        <svg width="36" height="36" viewBox="0 0 64 64" fill="none">
          <rect width="64" height="64" rx="14" fill="url(#sg)" />
          <path d="M20 44V20l12 8-12 8z" fill="white" opacity="0.9" />
          <path d="M32 44V28l12 8v16L32 44z" fill="white" />
          <defs><linearGradient id="sg" x1="0" y1="0" x2="64" y2="64"><stop stop-color="#6C5CE7"/><stop offset="1" stop-color="#a29bfe"/></linearGradient></defs>
        </svg>
        <span class="brand-name">剧本工坊</span>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/" class="nav-item active">
          <el-icon><Folder /></el-icon> 我的项目
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="user-info">
          <el-avatar :size="32" style="background: #6C5CE7">{{ nickname[0] }}</el-avatar>
          <div class="user-meta">
            <span class="user-name">{{ nickname }}</span>
            <span class="user-plan">个人版</span>
          </div>
        </div>
        <el-button text @click="handleLogout" style="color: #636e72; font-size: 12px;">退出</el-button>
      </div>
    </aside>

    <!-- Main content -->
    <main class="main-area">
      <!-- Top bar -->
      <header class="top-bar">
        <div>
          <h2>我的项目</h2>
          <p class="top-subtitle">{{ stats.total }} 个项目 · {{ stats.completed }} 个已完成</p>
        </div>
        <el-button type="primary" size="large" @click="showCreate = true" class="create-btn">
          <el-icon><Plus /></el-icon> 新建项目
        </el-button>
      </header>

      <!-- Stats row -->
      <div class="stats-row" v-if="projects.length">
        <div class="stat-card">
          <span class="stat-num">{{ stats.total }}</span>
          <span class="stat-label">全部项目</span>
        </div>
        <div class="stat-card completed">
          <span class="stat-num">{{ stats.completed }}</span>
          <span class="stat-label">已完成</span>
        </div>
        <div class="stat-card processing">
          <span class="stat-num">{{ stats.processing }}</span>
          <span class="stat-label">处理中</span>
        </div>
        <div class="stat-card draft">
          <span class="stat-num">{{ stats.draft }}</span>
          <span class="stat-label">草稿</span>
        </div>
      </div>

      <!-- Empty state -->
      <div v-if="!projects.length && !loading" class="empty-state">
        <div class="empty-illustration">
          <svg width="160" height="120" viewBox="0 0 160 120" fill="none">
            <rect x="20" y="10" width="120" height="80" rx="8" fill="#f1f2f6" stroke="#e2e8f0" stroke-width="2"/>
            <line x1="40" y1="30" x2="100" y2="30" stroke="#cbd5e0" stroke-width="3" stroke-linecap="round"/>
            <line x1="40" y1="44" x2="120" y2="44" stroke="#cbd5e0" stroke-width="3" stroke-linecap="round"/>
            <line x1="40" y1="58" x2="80" y2="58" stroke="#cbd5e0" stroke-width="3" stroke-linecap="round"/>
            <circle cx="140" cy="110" r="16" fill="#6C5CE7" opacity="0.15"/>
            <circle cx="145" cy="115" r="8" fill="#6C5CE7"/>
            <path d="M143 115h4M145 113v4" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </div>
        <h3>开始你的第一个剧本</h3>
        <p>粘贴小说原文，AI 将自动识别角色与场景，生成结构化剧本</p>
        <el-button type="primary" size="large" @click="showCreate = true">创建第一个项目</el-button>
      </div>

      <!-- Project grid -->
      <div v-else class="project-grid">
        <article
          v-for="project in projects"
          :key="project.id"
          class="project-card"
          @click="$router.push(`/project/${project.id}`)"
        >
          <div class="card-accent" :class="project.status.toLowerCase()"></div>
          <div class="card-body">
            <div class="card-top">
              <h3>{{ project.title }}</h3>
              <el-tag
                :type="project.status === 'COMPLETED' ? 'success' : project.status === 'PROCESSING' ? 'warning' : 'info'"
                size="small"
                effect="light"
                round
              >
                {{ statusMap[project.status] }}
              </el-tag>
            </div>
            <div class="card-stats">
              <span><el-icon><Notebook /></el-icon> {{ project.chapterCount }} 章</span>
              <span>{{ timeAgo(project.updatedAt) }}</span>
            </div>
          </div>
          <div class="card-hover-actions">
            <el-button size="small" round @click.stop="$router.push(`/project/${project.id}`)">
              打开编辑
            </el-button>
            <el-popconfirm title="删除后不可恢复" @confirm="handleDelete(project.id)" @click.stop>
              <template #reference>
                <el-button size="small" type="danger" round plain>删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </article>
      </div>
    </main>

    <!-- Create dialog -->
    <el-dialog v-model="showCreate" title="新建项目" width="600px" :close-on-click-modal="false" class="create-dialog">
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="项目名称" prop="title">
          <el-input v-model="form.title" placeholder="如：《三体》改编剧本" size="large" />
        </el-form-item>
        <el-form-item label="小说原文" prop="originalText">
          <div class="textarea-wrapper">
            <el-input
              v-model="form.originalText"
              type="textarea"
              placeholder="粘贴小说原文到此处...
支持「第X章」「Chapter X」格式自动分章
至少需要 3 章内容..."
              :rows="14"
              class="novel-textarea"
            />
            <span class="char-count">{{ form.originalText.length }} 字</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">
          创建项目
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listProjects, createProject, deleteProject } from '@/api/projects'

const router = useRouter()
const projects = ref([])
const loading = ref(false)
const showCreate = ref(false)
const creating = ref(false)
const formRef = ref(null)

const nickname = ref(localStorage.getItem('nickname') || '用户')

const form = reactive({ title: '', originalText: '' })
const rules = {
  title: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  originalText: [{ required: true, message: '请粘贴小说原文', trigger: 'blur' }]
}

const statusMap = { DRAFT: '草稿', PROCESSING: '处理中', COMPLETED: '已完成' }

const stats = computed(() => {
  const total = projects.value.length
  const completed = projects.value.filter(p => p.status === 'COMPLETED').length
  const processing = projects.value.filter(p => p.status === 'PROCESSING').length
  const draft = projects.value.filter(p => p.status === 'DRAFT').length
  return { total, completed, processing, draft }
})

onMounted(() => fetchProjects())

async function fetchProjects() {
  loading.value = true
  try {
    const res = await listProjects()
    projects.value = res.data.data || []
  } catch {
    // silent
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    const res = await createProject(form.title, form.originalText)
    ElMessage.success('项目创建成功')
    showCreate.value = false
    form.title = ''; form.originalText = ''
    fetchProjects()
    router.push(`/project/${res.data.data.id}`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function handleDelete(id) {
  try {
    await deleteProject(id)
    ElMessage.success('已删除')
    fetchProjects()
  } catch { ElMessage.error('删除失败') }
}

function handleLogout() {
  localStorage.clear()
  router.push('/login')
}

function timeAgo(dateStr) {
  if (!dateStr) return ''
  const diff = Date.now() - new Date(dateStr).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
}
</script>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
  background: #f8f9fb;
}

/* Sidebar */
.sidebar {
  width: 220px;
  background: #fff;
  border-right: 1px solid #e8ecf1;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  height: 100vh;
}
.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px;
  cursor: pointer;
}
.brand-name { font-size: 16px; font-weight: 700; color: #2d3436; }
.sidebar-nav { flex: 1; padding: 12px; }
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  color: #636e72;
  text-decoration: none;
  transition: all 0.15s;
}
.nav-item:hover { background: #f1f2f6; color: #2d3436; }
.nav-item.active { background: #f0edff; color: #6C5CE7; font-weight: 600; }
.sidebar-footer {
  padding: 16px;
  border-top: 1px solid #e8ecf1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.user-info { display: flex; align-items: center; gap: 8px; }
.user-meta { display: flex; flex-direction: column; }
.user-name { font-size: 13px; font-weight: 600; color: #2d3436; }
.user-plan { font-size: 11px; color: #a0aec0; }

/* Main area */
.main-area { flex: 1; padding: 32px; max-width: 1200px; }
.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}
.top-bar h2 { font-size: 24px; margin: 0; color: #2d3436; }
.top-subtitle { color: #a0aec0; font-size: 13px; margin: 4px 0 0; }
.create-btn { height: 44px; padding: 0 24px; font-weight: 600; }

/* Stats */
.stats-row { display: flex; gap: 12px; margin-bottom: 24px; }
.stat-card {
  flex: 1;
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border: 1px solid #e8ecf1;
}
.stat-num { font-size: 28px; font-weight: 700; color: #2d3436; }
.stat-label { font-size: 12px; color: #a0aec0; }
.stat-card.completed .stat-num { color: #00B894; }
.stat-card.processing .stat-num { color: #FDCB6E; }
.stat-card.draft .stat-num { color: #6C5CE7; }

/* Empty */
.empty-state {
  text-align: center;
  padding: 80px 20px;
}
.empty-illustration { margin-bottom: 24px; }
.empty-state h3 { font-size: 20px; color: #2d3436; margin: 0 0 8px; }
.empty-state p { color: #a0aec0; margin-bottom: 24px; }

/* Project cards */
.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.project-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e8ecf1;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}
.project-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 40px rgba(0,0,0,0.08);
  border-color: #d0d5dd;
}
.card-accent {
  height: 4px;
}
.card-accent.completed { background: #00B894; }
.card-accent.processing { background: #FDCB6E; }
.card-accent.draft { background: #6C5CE7; }
.card-body { padding: 20px; }
.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.card-top h3 { font-size: 16px; margin: 0; color: #2d3436; }
.card-stats {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #a0aec0;
  align-items: center;
}
.card-hover-actions {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  gap: 6px;
  opacity: 0;
  transition: opacity 0.2s;
}
.project-card:hover .card-hover-actions { opacity: 1; }

/* Create dialog */
.create-dialog :deep(.el-dialog) { border-radius: 16px; }
.textarea-wrapper { position: relative; width: 100%; }
.novel-textarea :deep(textarea) {
  font-family: 'Georgia', 'Noto Serif SC', serif;
  line-height: 1.8;
  font-size: 14px;
}
.char-count {
  position: absolute;
  bottom: 8px;
  right: 12px;
  font-size: 12px;
  color: #a0aec0;
  background: rgba(255,255,255,0.9);
  padding: 2px 8px;
  border-radius: 4px;
}
</style>
