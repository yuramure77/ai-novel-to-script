<template>
  <div class="app">
    <header class="topbar">
      <div class="tl">
        <router-link to="/" class="logo">🎬 剧本工坊</router-link>
        <span class="nav-slogan">从文字到银幕，只差一个回车</span>
      </div>
      <div class="tr">
        <el-switch v-model="dark" inline-prompt active-text="🌙" inactive-text="☀️" @change="tDark" size="small" />
        <span class="user">{{ nickname }}</span>
        <el-button text size="small" @click="logout" style="color:var(--color-text-muted)">退出</el-button>
      </div>
    </header>

    <div class="layout">
      <!-- Folder sidebar -->
      <aside class="sidebar">
        <div class="sidebar-header">
          <h3>📁 文件夹</h3>
          <el-button size="small" text @click="showAddFolder=true">＋</el-button>
        </div>
        <div class="folder-list">
          <div
            :class="['folder-item', { active: currentFolder === null }]"
            @click="currentFolder = null; load()"
          >
            <span class="folder-dot" style="background:#8b7b65"></span>
            全部项目
            <span class="folder-count">{{ projects.length }}</span>
          </div>
          <div
            v-for="f in folders"
            :key="f.id"
            :class="['folder-item', { active: currentFolder === f.id }]"
            @click="currentFolder = f.id; load()"
          >
            <span class="folder-dot" :style="{background: f.color || '#d4a853'}"></span>
            {{ f.name }}
            <span class="folder-count">{{ folderCounts[f.id] || 0 }}</span>
            <div class="folder-actions" @click.stop>
              <el-dropdown trigger="click">
                <el-button size="small" text style="font-size:11px">⋯</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="renameF(f)">重命名</el-dropdown-item>
                    <el-dropdown-item @click="delF(f.id)" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </div>

        <!-- Add folder input -->
        <div v-if="showAddFolder" class="add-folder">
          <el-input v-model="newFolderName" size="small" placeholder="文件夹名" @keyup.enter="addF" ref="folderInput" />
          <el-button size="small" @click="addF" type="primary">确定</el-button>
          <el-button size="small" @click="showAddFolder=false">取消</el-button>
        </div>
      </aside>

      <!-- Main -->
      <main class="main">
        <div class="main-header">
          <h2>{{ currentFolder ? folderName : '全部项目' }}</h2>
          <el-button type="warning" @click="showCreate=true">＋ 新建项目</el-button>
        </div>

        <!-- Quick start -->
        <div class="quick-start" @click="showCreate=true">
          <div class="qs-icon">＋</div>
          <div class="qs-text"><strong>创建新项目</strong><span>粘贴小说或上传文件</span></div>
        </div>

        <!-- Project grid -->
        <div v-if="filtered.length" class="grid stagger-in">
          <div v-for="p in filtered" :key="p.id" class="card" @click="$router.push(`/project/${p.id}`)">
            <div class="card-top-bar" :class="p.status.toLowerCase()"></div>
            <div class="card-inner">
              <div class="card-head">
                <h3>{{ p.title }}</h3>
              </div>
              <div class="card-info">
                <span>
                  <el-tag :type="p.status==='COMPLETED'?'success':p.status==='PROCESSING'?'warning':'info'" size="small" round effect="dark">
                    {{ {DRAFT:'草稿',PROCESSING:'处理中',COMPLETED:'已完成'}[p.status] }}
                  </el-tag>
                  🎞️ {{ p.chapterCount }} 章
                </span>
                <span>{{ fmt(p.updatedAt) }}</span>
              </div>
            </div>
            <div class="card-hover" @click.stop>
              <!-- Move to folder -->
              <el-dropdown trigger="click" @command="(fid) => moveP(p.id, fid)">
                <el-button size="small" round>📁</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :command="null">未分类</el-dropdown-item>
                    <el-dropdown-item v-for="f in folders" :key="f.id" :command="f.id">{{ f.name }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-popconfirm title="确定删除？" @confirm="del(p.id)"><template #ref><el-button size="small" type="danger" round plain>删除</el-button></template></el-popconfirm>
            </div>
          </div>
        </div>
        <el-empty v-else description="此文件夹暂无项目" />
      </main>
    </div>

    <!-- Create dialog -->
    <el-dialog v-model="showCreate" title="新建项目" :width="dialogWidth" :close-on-click-modal="false" :append-to-body="false">
      <el-form :model="form" :rules="rules" ref="fr" label-position="top">
        <el-form-item label="项目名称" prop="title">
          <el-input v-model="form.title" placeholder="如：《三体》改编剧本" size="large" />
        </el-form-item>
        <el-form-item label="小说原文" prop="originalText">
          <el-upload :auto-upload="false" :show-file-list="false" accept=".txt,.epub,.docx" :on-change="onFile" drag>
            <div class="drop-zone">
              <span style="font-size:28px">📁</span>
              <p>拖拽文件到此处 或 点击上传</p>
              <div class="format-tags">
                <span class="ftag">TXT</span>
                <span class="ftag">EPUB</span>
                <span class="ftag">DOCX</span>
              </div>
            </div>
          </el-upload>
          <span v-if="uploading" style="color:var(--color-text-muted);font-size:12px">解析中...</span>
          <el-input v-model="form.originalText" type="textarea" placeholder="或直接粘贴小说原文..." :rows="6" style="margin-top:10px" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="showCreate=false">取消</el-button><el-button type="warning" :loading="creating" @click="create">创建</el-button></template>
    </el-dialog>

    <!-- Rename folder dialog -->
    <el-dialog v-model="showRename" title="重命名文件夹" width="400px">
      <el-input v-model="renameFolderName" placeholder="新名称" @keyup.enter="doRenameF" />
      <template #footer><el-button @click="showRename=false">取消</el-button><el-button type="primary" @click="doRenameF">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listProjects, createProject, deleteProject } from '@/api/projects'
import { uploadFile } from '@/api/files'
import { listFolders, createFolder, renameFolder, deleteFolder, moveProject } from '@/api/folders'

const router = useRouter()
const projects = ref([]); const loading = ref(false)
const showCreate = ref(false); const creating = ref(false); const uploading = ref(false)
const fr = ref(null); const dark = ref(false)
const nickname = ref(localStorage.getItem('nickname') || '用户')
const form = reactive({ title: '', originalText: '' })
const rules = { title: [{ required: true, message: '请输入项目名称' }], originalText: [{ required: true, message: '请粘贴或上传原文' }] }

// Folders
const folders = ref([])
const currentFolder = ref(null)
const showAddFolder = ref(false); const newFolderName = ref(''); const folderInput = ref(null)
const showRename = ref(false); const renameFolderName = ref(''); const renameTarget = ref(null)
const folderCounts = computed(() => {
  const c = {}
  for (const p of projects.value) { if (p.folderId) c[p.folderId] = (c[p.folderId] || 0) + 1 }
  return c
})
const folderName = computed(() => {
  const f = folders.value.find(f => f.id === currentFolder.value)
  return f ? f.name : ''
})
const dialogWidth = ref('640px')
function onResize() { dialogWidth.value = window.innerWidth < 768 ? '95%' : '640px' }
const filtered = computed(() => {
  if (currentFolder.value === null) return projects.value
  return projects.value.filter(p => p.folderId === currentFolder.value)
})

onMounted(async () => {
  dark.value = document.documentElement.classList.contains('light')
  onResize(); window.addEventListener('resize', onResize)
  await load()
  await loadFolders()
})
onUnmounted(() => window.removeEventListener('resize', onResize))

async function load() {
  loading.value = true
  try { projects.value = (await listProjects()).data.data || [] } catch {} finally { loading.value = false }
}
async function loadFolders() {
  try { folders.value = (await listFolders()).data.data || [] } catch {}
}

async function create() {
  if (!await fr.value.validate().catch(() => false)) return; creating.value = true
  try { const r = await createProject(form.title, form.originalText); ElMessage.success('创建成功'); showCreate.value = false; form.title = ''; form.originalText = ''; router.push(`/project/${r.data.data.id}`) }
  catch (e) { ElMessage.error(e.response?.data?.message || '创建失败') } finally { creating.value = false }
}
async function del(id) { try { await deleteProject(id); ElMessage.success('已删除'); load() } catch { ElMessage.error('删除失败') } }

// File
async function onFile(f) {
  uploading.value = true
  try { const r = await uploadFile(f.raw); form.originalText = r.data.data.text; if (!form.title) form.title = r.data.data.filename.replace(/\.[^.]+$/, '') + ' 改编'; ElMessage.success('解析成功') }
  catch (e) { ElMessage.error(e.response?.data?.message || '解析失败') } finally { uploading.value = false }
}

// Folders
async function addF() {
  if (!newFolderName.value.trim()) return
  try { await createFolder(newFolderName.value.trim()); ElMessage.success('文件夹已创建'); showAddFolder.value = false; newFolderName.value = ''; loadFolders() }
  catch { ElMessage.error('创建失败') }
}
function renameF(f) { renameTarget.value = f; renameFolderName.value = f.name; showRename.value = true }
async function doRenameF() {
  if (!renameFolderName.value.trim()) return
  try { await renameFolder(renameTarget.value.id, renameFolderName.value.trim()); ElMessage.success('已重命名'); showRename.value = false; loadFolders() }
  catch { ElMessage.error('重命名失败') }
}
async function delF(id) { try { await deleteFolder(id); ElMessage.success('已删除'); loadFolders(); load() } catch { ElMessage.error('删除失败') } }
async function moveP(projectId, folderId) {
  try { await moveProject(projectId, folderId); load() } catch { ElMessage.error('移动失败') }
}

// Misc
function tDark(v) { document.documentElement.classList.toggle('light', v); localStorage.setItem('light', v ? '1' : '0') }
function logout() { localStorage.clear(); router.push('/login') }
function fmt(d) { return d ? new Date(d).toLocaleDateString('zh-CN') : '' }
</script>

<style scoped>
.app { min-height: 100vh; background: var(--color-bg) }
.topbar { display: flex; justify-content: space-between; align-items: center; height: 56px; padding: 0 24px; background: linear-gradient(180deg, var(--color-surface), var(--color-bg)); border-bottom: 1px solid var(--color-border) }
.tl { display: flex; align-items: baseline; gap: 14px }
.logo { font-size: 18px; font-weight: 800; color: var(--c-gold); text-decoration: none; letter-spacing: 2px; font-family: var(--font-serif) }
.nav-slogan { font-size: 11px; color: var(--color-text-muted); font-style: italic; letter-spacing: 1px }
.tr { display: flex; align-items: center; gap: 12px }
.user { color: var(--color-text-secondary); font-size: 13px }

/* Layout */
.layout { display: flex; max-width: 1280px; margin: 0 auto }

/* Sidebar */
.sidebar { width: 220px; flex-shrink: 0; padding: 24px 16px; border-right: 1px solid var(--color-border); min-height: calc(100vh - 56px) }
.sidebar-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px }
.sidebar-header h3 { font-size: 13px; color: var(--color-text-muted); margin: 0; letter-spacing: 1px }
.folder-list { display: flex; flex-direction: column; gap: 2px }
.folder-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: var(--radius); cursor: pointer;
  font-size: 13px; color: var(--color-text-secondary); transition: all var(--transition); position: relative
}
.folder-item:hover { background: var(--color-surface-hover) }
.folder-item.active { background: var(--color-surface-hover); color: var(--c-gold); font-weight: 600 }
.folder-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0 }
.folder-count { margin-left: auto; font-size: 11px; color: var(--color-text-muted) }
.folder-actions { opacity: 0; transition: opacity var(--transition) }
.folder-item:hover .folder-actions { opacity: 1 }

.add-folder { display: flex; gap: 4px; margin-top: 8px; align-items: center }

/* Main */
.main { flex: 1; padding: 24px }
.main-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px }
.main-header h2 { font-size: 22px; font-weight: 800; color: var(--c-gold); margin: 0; font-family: var(--font-serif) }

.quick-start {
  background: var(--color-surface); border: 2px dashed var(--color-border); border-radius: var(--radius-lg);
  padding: 16px 20px; display: flex; align-items: center; gap: 14px; cursor: pointer;
  transition: all var(--transition); margin-bottom: 20px
}
.quick-start:hover { border-color: var(--c-gold); box-shadow: var(--shadow-gold) }
.qs-icon { width: 44px; height: 44px; border-radius: 50%; background: linear-gradient(135deg, var(--c-gold), var(--c-amber)); color: var(--c-darker); display: flex; align-items: center; justify-content: center; font-size: 22px; font-weight: 700 }
.qs-text { display: flex; flex-direction: column; gap: 2px }
.qs-text strong { font-size: 15px; color: var(--color-text) }
.qs-text span { font-size: 12px; color: var(--color-text-muted) }

.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px }
.card {
  background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-lg);
  overflow: hidden; cursor: pointer; transition: all var(--transition); position: relative
}
.card:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); border-color: var(--c-gold) }
.card-top-bar { height: 3px }
.card-top-bar.completed { background: var(--color-success) }
.card-top-bar.processing { background: var(--color-warning) }
.card-top-bar.draft { background: var(--c-gold) }
.card-inner { padding: 14px 18px }
.card-head { display: flex; justify-content: space-between; align-items: center }
.card-head h3 { font-size: 15px; color: var(--color-text); margin: 0; font-weight: 600 }
.card-info { display: flex; justify-content: space-between; color: var(--color-text-muted); font-size: 12px; margin-top: 10px }
.card-hover { position: absolute; top: 10px; right: 10px; display: flex; gap: 4px; opacity: 0; transition: opacity var(--transition) }
.card:hover .card-hover { opacity: 1 }

.drop-zone { text-align: center; padding: 20px; cursor: pointer }
.drop-zone p { color: var(--color-text-muted); font-size: 13px; margin: 8px 0 0 }
.format-tags { display: flex; gap: 8px; justify-content: center; margin-top: 10px }
.ftag {
  padding: 4px 14px; border: 1px solid var(--color-border); border-radius: 14px;
  font-size: 11px; font-weight: 700; color: var(--c-gold);
  font-family: var(--font-mono); background: rgba(212,168,83,0.06)
}
:deep(.el-dialog__body) { max-height: 60vh; overflow-y: auto; }
:deep(.el-overlay-dialog) {
  position: fixed !important; top: 0; left: 0; right: 0; bottom: 0;
  display: flex !important; align-items: center !important; justify-content: center !important;
  background: rgba(0,0,0,0.3) !important;
  backdrop-filter: blur(6px); -webkit-backdrop-filter: blur(6px);
}
:deep(.el-dialog) {
  background: rgba(30,28,26,0.88) !important;
  backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(212,168,83,0.15) !important;
  box-shadow: 0 8px 40px rgba(0,0,0,0.5), 0 0 80px rgba(212,168,83,0.08) !important;
}
@media (max-width: 768px) {
  :deep(.el-dialog) {
    width: 95% !important; max-width: 95vw !important; min-width: 0 !important;
    margin: 0 !important; box-sizing: border-box !important;
  }
  :deep(.el-dialog__body) {
    max-height: 55vh; padding: 10px 12px;
    overflow-x: hidden !important; overflow-y: auto !important;
  }
  :deep(.el-dialog__header) { padding: 14px 14px 0; }
  :deep(.el-dialog__footer) { padding: 8px 14px 14px; display: flex; gap: 6px; justify-content: stretch; }
  :deep(.el-dialog__footer) .el-button { margin-left: 0 !important; margin-right: 0 !important; flex: 1; min-width: 0; white-space: nowrap; }
  :deep(.el-dialog__footer) .el-button + .el-button { margin-left: 0 !important; }
  :deep(.el-upload) { display: block !important; width: 100% !important; }
  :deep(.el-upload-dragger) { width: 100% !important; min-width: 0 !important; height: auto !important; padding: 12px 6px !important; box-sizing: border-box !important; }
  :deep(.el-form-item) { max-width: 100%; }
  :deep(.el-form-item__label) { font-size: 13px; max-width: 100%; word-break: break-all; }
  :deep(.el-form-item__content) { max-width: 100%; }
  :deep(.el-textarea__inner) { font-size: 14px; max-width: 100%; box-sizing: border-box; }
}
@media (max-width: 400px) {
  :deep(.el-dialog) { width: 100% !important; border-radius: 0 !important; margin: 0 !important; }
  :deep(.el-dialog__body) { max-height: calc(100vh - 140px); padding: 8px 10px; }
  :deep(.el-dialog__header) { padding: 10px 10px 0; }
  :deep(.el-dialog__footer) { padding: 6px 10px 10px; }
}

@media (max-width: 768px) {
  .topbar { padding: 0 12px; }
  .logo { font-size: 15px; }
  .nav-slogan { display: none; }
  .layout { flex-direction: column; }
  .sidebar { width: 100%; min-height: auto; padding: 12px; border-right: none; border-bottom: 1px solid var(--color-border); }
  .sidebar-header { margin-bottom: 8px; }
  .folder-list { flex-direction: row; flex-wrap: wrap; gap: 4px; }
  .folder-item { font-size: 12px; padding: 4px 10px; }
  .main { padding: 16px 12px; }
  .main-header h2 { font-size: 18px; }
  .grid { grid-template-columns: 1fr; }
  .card-hover { opacity: 1; }
  .card:hover .card-hover { opacity: 1; }
}
</style>
