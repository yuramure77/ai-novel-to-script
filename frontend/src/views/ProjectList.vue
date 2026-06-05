<template>
  <div class="app">
    <!-- Top bar -->
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

    <main class="main">
      <!-- Hero area -->
      <div class="welcome">
        <h1>我的剧本项目</h1>
        <p>每个项目都是一部待拍摄的电影</p>
      </div>

      <!-- Quick start card (always visible) -->
      <div class="quick-start" @click="showCreate=true">
        <div class="qs-icon">＋</div>
        <div class="qs-text">
          <strong>创建新项目</strong>
          <span>粘贴小说或上传文件开始改编</span>
        </div>
      </div>

      <!-- Projects -->
      <div v-if="projects.length" class="grid">
        <div v-for="p in projects" :key="p.id" class="card" @click="$router.push(`/project/${p.id}`)">
          <div class="card-top-bar" :class="p.status.toLowerCase()"></div>
          <div class="card-inner">
            <div class="card-head">
              <h3>{{ p.title }}</h3>
              <el-tag :type="p.status==='COMPLETED'?'success':p.status==='PROCESSING'?'warning':'info'" size="small" round effect="dark">
                {{ {DRAFT:'草稿',PROCESSING:'处理中',COMPLETED:'已完成'}[p.status] }}
              </el-tag>
            </div>
            <div class="card-info">
              <span>🎞️ {{ p.chapterCount }} 章</span>
              <span>{{ fmt(p.updatedAt) }}</span>
            </div>
          </div>
          <div class="card-hover" @click.stop>
            <el-button size="small" round @click="$router.push(`/project/${p.id}`)">打开</el-button>
            <el-popconfirm title="确定删除？" @confirm="del(p.id)"><template #ref><el-button size="small" type="danger" round plain>删除</el-button></template></el-popconfirm>
          </div>
        </div>
      </div>

      <el-empty v-else-if="!loading" description="还没有项目，点击上方卡片开始" />
    </main>

    <!-- Create dialog -->
    <el-dialog v-model="showCreate" title="新建项目" width="640px" :close-on-click-modal="false">
      <el-form :model="form" :rules="rules" ref="fr" label-position="top">
        <el-form-item label="项目名称" prop="title">
          <el-input v-model="form.title" placeholder="如：《三体》改编剧本" size="large" />
        </el-form-item>
        <el-form-item label="小说原文" prop="originalText">
          <el-upload :auto-upload="false" :show-file-list="false" accept=".txt,.epub,.docx" :on-change="onFile" drag>
            <div class="drop-zone"><span style="font-size:28px">📁</span><p>拖拽或点击上传 TXT / EPUB / DOCX</p></div>
          </el-upload>
          <span v-if="uploading" style="color:var(--color-text-muted);font-size:12px">解析中...</span>
          <el-input v-model="form.originalText" type="textarea" placeholder="或直接粘贴小说原文..." :rows="10" style="margin-top:10px" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="showCreate=false">取消</el-button><el-button type="warning" :loading="creating" @click="create">创建项目</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listProjects, createProject, deleteProject } from '@/api/projects'
import { uploadFile } from '@/api/files'

const router = useRouter(); const projects = ref([]); const loading = ref(false)
const showCreate = ref(false); const creating = ref(false); const uploading = ref(false)
const fr = ref(null); const dark = ref(false)
const nickname = ref(localStorage.getItem('nickname')||'用户')
const form = reactive({title:'',originalText:''})
const rules = {title:[{required:true,message:'请输入项目名称'}],originalText:[{required:true,message:'请粘贴或上传原文'}]}

onMounted(async()=>{dark.value=document.documentElement.classList.contains('dark');await load()})
async function load(){loading.value=true;try{projects.value=(await listProjects()).data.data||[]}catch{}finally{loading.value=false}}

async function create(){
  if(!await fr.value.validate().catch(()=>false))return;creating.value=true
  try{const r=await createProject(form.title,form.originalText);ElMessage.success('创建成功');showCreate.value=false;form.title='';form.originalText='';router.push(`/project/${r.data.data.id}`)}
  catch(e){ElMessage.error(e.response?.data?.message||'创建失败')}finally{creating.value=false}
}
async function del(id){try{await deleteProject(id);ElMessage.success('已删除');load()}catch{ElMessage.error('删除失败')}}

async function onFile(f){uploading.value=true;try{const r=await uploadFile(f.raw);form.originalText=r.data.data.text;if(!form.title&&r.data.data.filename)form.title=r.data.data.filename.replace(/\.[^.]+$/,'')+' 改编';ElMessage.success('解析成功')}catch(e){ElMessage.error(e.response?.data?.message||'解析失败')}finally{uploading.value=false}}

function tDark(v){document.documentElement.classList.toggle('dark',v);localStorage.setItem('dark',v?'1':'0')}
function logout(){localStorage.clear();router.push('/login')}
function fmt(d){return d?new Date(d).toLocaleDateString('zh-CN'):''}
</script>

<style scoped>
.app{min-height:100vh;background:var(--color-bg)}
.topbar{display:flex;justify-content:space-between;align-items:center;height:56px;padding:0 24px;background:linear-gradient(180deg,var(--color-surface),var(--color-bg));border-bottom:1px solid var(--color-border)}
.tl{display:flex;align-items:baseline;gap:14px}
.logo{font-size:18px;font-weight:800;color:var(--c-gold);text-decoration:none;letter-spacing:2px;font-family:var(--font-serif)}
.nav-slogan{font-size:11px;color:var(--color-text-muted);font-style:italic;letter-spacing:1px}
.tr{display:flex;align-items:center;gap:12px}
.user{color:var(--color-text-secondary);font-size:13px}

.main{max-width:1100px;margin:0 auto;padding:32px 24px}
.welcome{margin-bottom:24px}
.welcome h1{font-size:28px;font-weight:800;color:var(--c-gold);margin:0;font-family:var(--font-serif);letter-spacing:2px}
.welcome p{color:var(--color-text-muted);margin:4px 0 0;font-size:14px}

.quick-start{
  background:var(--color-surface);border:2px dashed var(--color-border);border-radius:var(--radius-lg);
  padding:20px 24px;display:flex;align-items:center;gap:16px;cursor:pointer;
  transition:all var(--transition);margin-bottom:24px
}
.quick-start:hover{border-color:var(--c-gold);box-shadow:var(--shadow-gold)}
.qs-icon{width:48px;height:48px;border-radius:50%;background:linear-gradient(135deg,var(--c-gold),var(--c-amber));color:var(--c-darker);display:flex;align-items:center;justify-content:center;font-size:24px;font-weight:700}
.qs-text{display:flex;flex-direction:column;gap:2px}
.qs-text strong{font-size:16px;color:var(--color-text)}
.qs-text span{font-size:12px;color:var(--color-text-muted)}

.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(320px,1fr));gap:16px}
.card{
  background:var(--color-surface);border:1px solid var(--color-border);border-radius:var(--radius-lg);
  overflow:hidden;cursor:pointer;transition:all var(--transition);position:relative
}
.card:hover{transform:translateY(-3px);box-shadow:var(--shadow-md);border-color:var(--c-gold)}
.card-top-bar{height:3px}
.card-top-bar.completed{background:var(--color-success)}
.card-top-bar.processing{background:var(--color-warning)}
.card-top-bar.draft{background:var(--c-gold)}
.card-inner{padding:16px 20px}
.card-head{display:flex;justify-content:space-between;align-items:center}
.card-head h3{font-size:16px;color:var(--color-text);margin:0;font-weight:600}
.card-info{display:flex;justify-content:space-between;color:var(--color-text-muted);font-size:12px;margin-top:10px}
.card-hover{
  position:absolute;top:12px;right:12px;display:flex;gap:6px;opacity:0;transition:opacity var(--transition)
}
.card:hover .card-hover{opacity:1}

.drop-zone{text-align:center;padding:20px;cursor:pointer}
.drop-zone p{color:var(--color-text-muted);font-size:13px;margin:8px 0 0}
</style>
