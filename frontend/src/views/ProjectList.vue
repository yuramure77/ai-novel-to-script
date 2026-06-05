<template>
  <div class="app">
    <header class="navbar">
      <div class="nav-left">
        <h1>🎬 小说转剧本</h1>
      </div>
      <div class="nav-right">
        <el-switch v-model="darkMode" inline-prompt active-text="🌙" inactive-text="☀️" @change="toggleDark" size="small" />
        <span class="user">{{ nickname }}</span>
        <el-button text size="small" @click="logout">退出</el-button>
      </div>
    </header>

    <main class="content">
      <div class="page-header">
        <h2>我的项目</h2>
        <el-button type="primary" @click="showCreate = true">新建项目</el-button>
      </div>

      <el-empty v-if="!projects.length && !loading" description="暂无项目" />

      <div v-else class="grid">
        <div
          v-for="p in projects"
          :key="p.id"
          class="card"
          @click="$router.push(`/project/${p.id}`)"
        >
          <div class="card-status" :class="p.status.toLowerCase()"></div>
          <div class="card-body">
            <div class="card-top">
              <h3>{{ p.title }}</h3>
              <el-tag :type="p.status==='COMPLETED'?'success':p.status==='PROCESSING'?'warning':'info'" size="small" round>
                {{ {DRAFT:'草稿',PROCESSING:'处理中',COMPLETED:'已完成'}[p.status] }}
              </el-tag>
            </div>
            <div class="card-info">
              <span>{{ p.chapterCount }} 章</span>
              <span>{{ fmt(p.updatedAt) }}</span>
            </div>
          </div>
          <div class="card-actions" @click.stop>
            <el-button size="small" @click="$router.push(`/project/${p.id}`)">打开</el-button>
            <el-popconfirm title="确定删除？" @confirm="del(p.id)"><template #ref><el-button size="small" type="danger" plain>删除</el-button></template></el-popconfirm>
          </div>
        </div>
      </div>
    </main>

    <el-dialog v-model="showCreate" title="新建项目" width="620px" :close-on-click-modal="false">
      <el-form :model="form" :rules="rules" ref="fr" label-position="top">
        <el-form-item label="项目名称" prop="title">
          <el-input v-model="form.title" placeholder="如：《三体》改编剧本" size="large" />
        </el-form-item>
        <el-form-item label="小说原文" prop="originalText">
          <div class="upload-row">
            <el-upload :auto-upload="false" :show-file-list="false" accept=".txt,.epub,.docx" :on-change="onFile" drag>
              <div class="drop-zone">
                <span style="font-size:24px">📁</span>
                <p>拖拽或点击上传<br><small>txt / epub / docx</small></p>
              </div>
            </el-upload>
            <span v-if="uploading" style="color:var(--color-text-muted)">解析中...</span>
          </div>
          <el-input v-model="form.originalText" type="textarea" placeholder="粘贴小说原文，或上传文件..." :rows="12" style="margin-top:8px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="create">创建</el-button>
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
const fr = ref(null)
const darkMode = ref(false)
const nickname = ref(localStorage.getItem('nickname') || '用户')

const form = reactive({ title: '', originalText: '' })
const rules = {
  title: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  originalText: [{ required: true, message: '请粘贴原文或上传文件', trigger: 'blur' }]
}

onMounted(async () => {
  darkMode.value = document.documentElement.classList.contains('dark')
  await load()
})

async function load() {
  loading.value = true
  try { projects.value = (await listProjects()).data.data || [] } catch {}
  finally { loading.value = false }
}

async function create() {
  if (!await fr.value.validate().catch(() => false)) return
  creating.value = true
  try {
    const r = await createProject(form.title, form.originalText)
    ElMessage.success('创建成功')
    showCreate.value = false
    form.title = ''; form.originalText = ''
    router.push(`/project/${r.data.data.id}`)
  } catch (e) { ElMessage.error(e.response?.data?.message || '创建失败') }
  finally { creating.value = false }
}

async function del(id) {
  try { await deleteProject(id); ElMessage.success('已删除'); load() }
  catch { ElMessage.error('删除失败') }
}

async function onFile(file) {
  uploading.value = true
  try {
    const r = await uploadFile(file.raw)
    form.originalText = r.data.data.text
    if (!form.title && r.data.data.filename) {
      form.title = r.data.data.filename.replace(/\.[^.]+$/, '') + ' 改编'
    }
    ElMessage.success('解析成功')
  } catch (e) { ElMessage.error(e.response?.data?.message || '解析失败') }
  finally { uploading.value = false }
}

function toggleDark(v) {
  document.documentElement.classList.toggle('dark', v)
  localStorage.setItem('dark', v ? '1' : '0')
}

function logout() { localStorage.clear(); router.push('/login') }
function fmt(d) { return d ? new Date(d).toLocaleDateString('zh-CN') : '' }
</script>

<style scoped>
.app { min-height: 100vh; background: var(--color-bg); }
.navbar {
  display: flex; justify-content: space-between; align-items: center; height: 52px;
  padding: 0 24px; background: var(--color-surface); border-bottom: 1px solid var(--color-border);
}
.nav-left h1 { font-size: 18px; font-weight: 700; color: var(--color-text); margin: 0; }
.nav-right { display: flex; align-items: center; gap: 12px; }
.user { color: var(--color-text-secondary); font-size: 14px; }
.content { max-width: 1200px; margin: 0 auto; padding: 32px 24px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 22px; font-weight: 700; color: var(--color-text); margin: 0; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
.card {
  background: var(--color-surface); border-radius: var(--radius-lg); border: 1px solid var(--color-border);
  overflow: hidden; cursor: pointer; transition: all var(--transition);
}
.card:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); }
.card-status { height: 3px; }
.card-status.completed { background: var(--color-success); }
.card-status.processing { background: var(--color-warning); }
.card-status.draft { background: var(--color-primary); }
.card-body { padding: 16px 20px; }
.card-top { display: flex; justify-content: space-between; align-items: center; }
.card-top h3 { font-size: 15px; color: var(--color-text); margin: 0; font-weight: 600; }
.card-info { display: flex; justify-content: space-between; color: var(--color-text-muted); font-size: 12px; margin-top: 10px; }
.card-actions {
  display: flex; justify-content: flex-end; gap: 8px;
  padding: 10px 20px; border-top: 1px solid var(--color-border-light);
}
.drop-zone {
  text-align: center; padding: 16px; border: 2px dashed var(--color-border);
  border-radius: var(--radius); cursor: pointer; transition: border-color var(--transition);
}
.drop-zone:hover { border-color: var(--color-primary); }
.drop-zone p { margin: 8px 0 0; color: var(--color-text-muted); font-size: 13px; }
.drop-zone small { color: var(--color-text-muted); font-size: 11px; }
</style>
