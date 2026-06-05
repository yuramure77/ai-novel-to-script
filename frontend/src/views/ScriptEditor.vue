<template>
  <div class="editor" @keydown="onKeydown">
    <!-- Top bar -->
    <header class="topbar">
      <div class="tl">
        <el-button text size="small" @click="$router.push('/')">← 返回</el-button>
        <h2 v-if="!renaming" @dblclick="startRename">{{ projectTitle }}</h2>
        <el-input v-else v-model="renameTitle" size="small" style="width:240px" @blur="doRename" @keyup.enter="doRename" ref="renameRef" />
        <el-tag :type="statusTag" size="small" round>{{ statusText }}</el-tag>
      </div>
      <div class="tr">
        <el-switch v-model="darkMode" inline-prompt active-text="🌙" inactive-text="☀️" @change="toggleDark" size="small" style="margin-right:8px" />
        <el-dropdown v-if="latestVersion" @command="handleExport">
          <el-button size="small">导出 ▾</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="yaml">下载 YAML</el-dropdown-item>
              <el-dropdown-item command="markdown">导出 Markdown</el-dropdown-item>
              <el-dropdown-item command="fountain">导出 Fountain</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button size="small" v-if="latestVersion" @click="showHistory = true">历史</el-button>
      </div>
    </header>

    <!-- Search -->
    <div v-if="searchVisible" class="search-bar">
      <el-input
        v-model="searchQuery"
        size="small"
        placeholder="搜索..."
        clearable
        @input="doSearch"
        ref="searchRef"
      >
        <template #prefix>🔍</template>
        <template #suffix>
          <span style="color:var(--color-text-muted);font-size:12px">{{ searchResults }} 个匹配</span>
        </template>
      </el-input>
    </div>

    <!-- Action bar -->
    <div class="actions">
      <div class="al">
        <el-button size="small" @click="handleSplit" :loading="splitting">分章</el-button>
        <span v-if="chapters.length" class="hint">{{ chapters.length }} 章</span>
        <span class="hint" style="color:var(--color-text-muted)">| Ctrl+F 搜索  Ctrl+Enter 生成  Ctrl+S 保存</span>
      </div>
      <el-button type="primary" size="default" @click="handleGenerate" :loading="generating" :disabled="generating">
        {{ generating ? progressMsg : '生成剧本' }}
      </el-button>
    </div>

    <!-- Progress -->
    <div v-if="generating" class="prog">
      <el-progress :percentage="100" :indeterminate="true" :stroke-width="2" :show-text="false" />
    </div>

    <!-- Chapter tabs -->
    <div v-if="chapters.length" class="chapters">
      <button v-for="(ch,i) in chapters" :key="i" :class="['chip',{active:activeChapter===i}]" @click="activeChapter=i">{{ ch.title }}</button>
    </div>

    <!-- Main -->
    <div class="main">
      <!-- Left: source -->
      <div class="col col-l">
        <div class="col-title">📖 原文</div>
        <div class="col-body" ref="textPanel">
          <pre v-if="chapters.length" v-html="highlightText(chapters[activeChapter]?.content || originalText)"></pre>
          <pre v-else v-html="highlightText(originalText)"></pre>
        </div>
      </div>

      <!-- Center: YAML -->
      <div class="col col-c" :class="{wide:!showRight}">
        <div class="col-title">
          <span>📄 剧本</span>
          <div>
            <el-button size="small" text @click="editing=!editing">{{ editing?'预览':'编辑' }}</el-button>
            <el-button size="small" text @click="copyYaml" v-if="yamlContent">复制</el-button>
          </div>
        </div>
        <div class="col-body">
          <div v-if="!yamlContent&&!generating" class="empty"><el-empty description="点击「生成剧本」开始" /></div>
          <textarea v-else-if="editing" v-model="editableYaml" class="yamledit" spellcheck="false"></textarea>
          <div v-else class="yamlview"><pre v-html="highlightedYaml"></pre></div>
        </div>
        <div v-if="editing" class="editbar">
          <el-button size="small" @click="editing=false;editableYaml=yamlContent">取消</el-button>
          <el-button size="small" type="primary" :loading="saving" @click="saveYaml">保存 (Ctrl+S)</el-button>
        </div>
      </div>

      <!-- Right: chat + chars -->
      <div class="col col-r" v-if="showRight">
        <el-tabs v-model="rightTab" class="rtabs">
          <el-tab-pane label="AI 助手" name="chat">
            <div class="chat">
              <div class="chatmsgs" ref="chatBox">
                <div v-for="(m,i) in chatMsgs" :key="i" :class="['cmsg',m.role]"><div class="cbub">{{ m.content }}</div></div>
                <div v-if="chatWait" class="cmsg assistant"><div class="cbub typing">思考中...</div></div>
              </div>
              <div class="chatinp">
                <el-input v-model="chatInput" placeholder="输入修改建议..." @keyup.enter="sendChat" :disabled="chatWait" size="small">
                  <template #append><el-button @click="sendChat" :loading="chatWait" size="small">发送</el-button></template>
                </el-input>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="角色" name="chars">
            <div class="chars">
              <div v-if="characters.length" class="charlist">
                <div v-for="(c,i) in characters" :key="i" class="charc">
                  <div class="charhead">
                    <strong>{{ c.name }}</strong>
                    <el-tag size="small" round>{{ roleLabel(c.role) }}</el-tag>
                  </div>
                  <p v-if="c.description">{{ c.description }}</p>
                  <div v-if="c.traits?.length" class="chartraits">
                    <el-tag v-for="t in c.traits" :key="t" size="small" type="info" effect="plain" round>{{ t }}</el-tag>
                  </div>
                </div>
              </div>
              <el-empty v-else description="生成后自动提取" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <button class="toggle-r" @click="showRight=!showRight">{{ showRight?'▶':'◀' }}</button>
    </div>

    <!-- Drawer -->
    <el-drawer v-model="showHistory" title="历史版本" size="360px">
      <div v-if="versions.length">
        <div v-for="v in versions" :key="v.id" class="ver">
          <div><strong>v{{ v.versionNumber }}</strong><span class="vt">{{ fmt(v.createdAt) }}</span></div>
          <div>
            <el-button size="small" text type="primary" @click="loadVer(v)">查看</el-button>
            <el-button size="small" text @click="dl(v.id)">下载</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import hljs from 'highlight.js/lib/core'
import yl from 'highlight.js/lib/languages/yaml'
import 'highlight.js/styles/github.css'
import { getProject, splitChapters } from '@/api/projects'
import { listVersions, getLatest, getYamlUrl, saveEditedYaml } from '@/api/scripts'
import { sendMessage, getHistory } from '@/api/chat'
import api from '@/api/index'

hljs.registerLanguage('yaml', yl)
const route = useRoute()
const pid = route.params.id

// State
const projectTitle = ref(''); const projectStatus = ref('DRAFT')
const originalText = ref(''); const chapters = ref([])
const activeChapter = ref(0); const splitting = ref(false)
const generating = ref(false); const progressMsg = ref('')
const yamlContent = ref(''); const latestVersion = ref(null)
const editing = ref(false); const editableYaml = ref(''); const saving = ref(false)
const versions = ref([]); const showHistory = ref(false)
const chatMsgs = ref([]); const chatInput = ref(''); const chatWait = ref(false); const chatBox = ref(null)
const characters = ref([]); const rightTab = ref('chat'); const showRight = ref(true)
const darkMode = ref(false)

// Rename
const renaming = ref(false); const renameTitle = ref(''); const renameRef = ref(null)

// Search
const searchVisible = ref(false); const searchQuery = ref(''); const searchResults = ref(0)
const searchRef = ref(null); const textPanel = ref(null)

// Computed
const statusTag = computed(() => projectStatus.value==='COMPLETED'?'success':projectStatus.value==='PROCESSING'?'warning':'info')
const statusText = computed(() => ({DRAFT:'草稿',PROCESSING:'处理中',COMPLETED:'已完成'})[projectStatus.value]||'')
const highlightedYaml = computed(() => yamlContent.value ? hljs.highlight(yamlContent.value,{language:'yaml'}).value : '')

// Watch YAML → parse characters
watch(yamlContent, () => {
  try {
    const out = []; let cur = null
    for (const line of yamlContent.value.split('\n')) {
      if (line.startsWith('characters:')) continue
      if (/^\w/.test(line) && !line.startsWith('  ')) break
      const nm = line.match(/^\s{2}-\s*name:\s*"?([^"\n]+)/)
      if (nm) { if (cur) out.push(cur); cur = { name: nm[1].trim() } }
      else if (cur) {
        const r = line.match(/role:\s*"?(\w+)/); if (r) cur.role = r[1]
        const d = line.match(/description:\s*"?([^"\n]+)/); if (d) cur.description = d[1]
        const t = line.match(/^\s{6}-\s*"?([^"\n]+)/); if (t) { cur.traits??=[]; cur.traits.push(t[1]) }
      }
    }
    if (cur) out.push(cur)
    characters.value = out
  } catch {}
})

onMounted(async () => {
  darkMode.value = document.documentElement.classList.contains('dark')
  try {
    const r = await getProject(pid); const d = r.data.data
    projectTitle.value = d.project?.title||''; projectStatus.value = d.project?.status||'DRAFT'
    originalText.value = d.originalText||''
    const v = await getLatest(pid)
    if (v.data.data) { latestVersion.value = v.data.data; yamlContent.value = v.data.data.yamlContent }
    if (originalText.value && !chapters.value.length) await handleSplit()
    chatMsgs.value = ((await getHistory(pid)).data.data||[]).map(m=>({role:m.role,content:m.content}))
  } catch {}
})

// Split
async function handleSplit() {
  splitting.value = true
  try { const r = await splitChapters(pid); chapters.value = r.data.data.chapters||[]; if(chapters.value.length) activeChapter.value = 0 }
  catch(e) { ElMessage.error(e.response?.data?.message||'分章失败') }
  finally { splitting.value = false }
}

// Generate (SSE stream)
function handleGenerate() {
  generating.value = true; progressMsg.value = '连接中...'; yamlContent.value = ''
  fetch(`/api/projects/${pid}/generate/stream`,{headers:{Authorization:`Bearer ${localStorage.getItem('token')}`}})
    .then(r=>{const reader=r.body.getReader(),dec=new TextDecoder();let buf=''
      function read(){reader.read().then(({done,value})=>{
        if(done){generating.value=false;return}
        buf+=dec.decode(value,{stream:true}); const lines=buf.split('\n'); buf=lines.pop()||''
        for(let i=0;i<lines.length;i++){
          if(!lines[i].startsWith('event:'))continue
          const ev=lines[i].replace('event:','').trim(), dataLine=lines[i+1]
          if(!dataLine?.startsWith('data:'))continue
          try{const d=JSON.parse(dataLine.replace('data:','').trim())
            if(ev==='progress') progressMsg.value = d.message||''
            else if(ev==='done'){
              yamlContent.value=d.yamlContent||''; latestVersion.value={id:d.versionId,versionNumber:d.versionNumber}
              projectStatus.value='COMPLETED'; progressMsg.value='完成'; ElMessage.success(`v${d.versionNumber} 生成成功`)
            } else if(ev==='error'){ ElMessage.error(d||'失败'); generating.value=false }
          } catch{}
        }
        if(generating.value) read()
      })}
      read()
    }).catch(e=>{ElMessage.error('连接失败');generating.value=false})
}

// YAML edit
async function saveYaml() {
  if(!editableYaml.value.trim())return
  saving.value=true
  try{const r=await saveEditedYaml(pid,editableYaml.value);yamlContent.value=editableYaml.value;latestVersion.value={id:r.data.data.id,versionNumber:r.data.data.versionNumber};ElMessage.success(`已保存 v${r.data.data.versionNumber}`);editing.value=false;loadVersions()}
  catch{ElMessage.error('保存失败')}
  finally{saving.value=false}
}

// Chat
async function sendChat() {
  const m=chatInput.value.trim(); if(!m||chatWait.value)return
  chatInput.value=''; chatMsgs.value.push({role:'user',content:m}); chatWait.value=true
  await nextTick(); chatBox.value&&(chatBox.value.scrollTop=chatBox.value.scrollHeight)
  try{const r=await sendMessage(pid,m);chatMsgs.value.push({role:'assistant',content:r.data.data.reply})}
  catch{ElMessage.error('发送失败')}
  finally{chatWait.value=false;await nextTick();chatBox.value&&(chatBox.value.scrollTop=chatBox.value.scrollHeight)}
}

// Versions
async function loadVersions() { try{versions.value=(await listVersions(pid)).data.data||[]}catch{} }
function loadVer(v){yamlContent.value=v.yamlContent;editing.value=false;showHistory.value=false}
function dl(id){window.open(getYamlUrl(id),'_blank')}
function copyYaml(){navigator.clipboard.writeText(yamlContent.value).then(()=>ElMessage.success('已复制')).catch(()=>ElMessage.error('失败'))}

// Export
function handleExport(cmd) {
  if(!latestVersion.value)return
  if(cmd==='yaml'){window.open(getYamlUrl(latestVersion.value.id),'_blank')}
  else if(cmd==='markdown'){window.open(`/api/export/${latestVersion.value.id}/markdown`,'_blank')}
  else if(cmd==='fountain'){window.open(`/api/export/${latestVersion.value.id}/fountain`,'_blank')}
}

// Rename
function startRename(){renaming.value=true;renameTitle.value=projectTitle.value;nextTick(()=>renameRef.value?.focus())}
async function doRename(){
  if(!renameTitle.value.trim()){renaming.value=false;return}
  try{const r=await api.put(`/projects/${pid}/rename`,{title:renameTitle.value});projectTitle.value=r.data.data.title;ElMessage.success('已重命名')}
  catch{ElMessage.error('重命名失败')}
  finally{renaming.value=false}
}

// Search
function doSearch(){
  searchResults.value = 0
  const q = searchQuery.value.toLowerCase()
  if (!q) return
  const panel = textPanel.value
  if (!panel) return
  const text = panel.textContent||''
  searchResults.value = (text.toLowerCase().match(new RegExp(q.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'),'g'))||[]).length
}
function highlightText(t){
  if(!t||!searchQuery.value) return escapeHtml(t||'')
  const re = new RegExp(`(${searchQuery.value.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')})`,'gi')
  return escapeHtml(t).replace(re,'<mark style="background:#fde68a;padding:0 2px;border-radius:2px">$1</mark>')
}
function escapeHtml(s){return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}

// Keyboard shortcuts
function onKeydown(e){
  if((e.ctrlKey||e.metaKey)&&e.key==='f'){e.preventDefault();searchVisible.value=!searchVisible.value;if(searchVisible.value)nextTick(()=>searchRef.value?.focus())}
  if((e.ctrlKey||e.metaKey)&&e.key==='Enter'&&!generating.value){e.preventDefault();handleGenerate()}
  if((e.ctrlKey||e.metaKey)&&e.key==='s'&&editing.value){e.preventDefault();saveYaml()}
  if(e.key==='Escape'&&searchVisible.value){searchVisible.value=false}
}

// Dark mode
function toggleDark(v){document.documentElement.classList.toggle('dark',v);localStorage.setItem('dark',v?'1':'0')}

// Helpers
function roleLabel(r){return{protagonist:'主角',antagonist:'反派',supporting:'配角',minor:'次要'}[r]||r||''}
function fmt(d){return d?new Date(d).toLocaleString('zh-CN'):''}
</script>

<style scoped>
.editor{height:100vh;display:flex;flex-direction:column;background:var(--color-bg)}
.topbar{display:flex;justify-content:space-between;align-items:center;height:48px;padding:0 16px;background:var(--color-surface);border-bottom:1px solid var(--color-border);flex-shrink:0}
.tl{display:flex;align-items:center;gap:10px}
.tl h2{font-size:15px;margin:0;color:var(--color-text);font-weight:600;cursor:pointer}
.tr{display:flex;align-items:center;gap:6px}
.search-bar{padding:8px 16px;background:var(--color-surface);border-bottom:1px solid var(--color-border);flex-shrink:0}
.actions{display:flex;justify-content:space-between;align-items:center;padding:8px 16px;background:var(--color-surface);border-bottom:1px solid var(--color-border-light);flex-shrink:0}
.al{display:flex;align-items:center;gap:12px}
.hint{color:var(--color-text-muted);font-size:12px}
.prog{padding:8px 16px;background:var(--color-surface);border-bottom:1px solid var(--color-border-light);flex-shrink:0}
.chapters{display:flex;gap:4px;padding:6px 16px;background:var(--color-surface);border-bottom:1px solid var(--color-border-light);overflow-x:auto;flex-shrink:0}
.chip{flex-shrink:0;padding:4px 12px;border:1px solid var(--color-border);border-radius:20px;background:var(--color-surface);font-size:12px;color:var(--color-text-secondary);cursor:pointer;white-space:nowrap;transition:all var(--transition)}
.chip:hover{color:var(--color-primary);border-color:var(--color-primary)}
.chip.active{background:var(--color-primary);color:#fff;border-color:var(--color-primary)}
.main{flex:1;display:flex;overflow:hidden;position:relative}
.col{display:flex;flex-direction:column;background:var(--color-surface)}
.col-l{width:30%;min-width:260px;border-right:1px solid var(--color-border)}
.col-c{width:45%;border-right:1px solid var(--color-border)}
.col-c.wide{width:70%}
.col-r{width:25%;min-width:300px}
.col-title{display:flex;justify-content:space-between;align-items:center;padding:6px 14px;font-size:12px;font-weight:600;color:var(--color-text-secondary);border-bottom:1px solid var(--color-border-light);flex-shrink:0}
.col-body{flex:1;overflow:auto;padding:14px 16px}
.col-body pre{margin:0;white-space:pre-wrap;word-break:break-word;font-family:var(--font-serif);font-size:14px;line-height:1.9;color:var(--color-text)}
.empty{flex:1;display:flex;align-items:center;justify-content:center}
.yamledit{width:100%;height:calc(100% - 0px);border:none;outline:none;resize:none;font-family:var(--font-mono);font-size:13px;line-height:1.6;background:var(--color-bg-alt);color:var(--color-text);padding:12px;border-radius:var(--radius)}
.editbar{display:flex;justify-content:flex-end;gap:8px;padding:8px 14px;border-top:1px solid var(--color-border-light);flex-shrink:0}
.yamlview pre{font-family:var(--font-mono);font-size:13px;line-height:1.6;margin:0}
.yamlview code{white-space:pre-wrap}
.rtabs{height:100%;display:flex;flex-direction:column;padding:0 8px}
.rtabs :deep(.el-tabs__content){flex:1;overflow:hidden}
.rtabs :deep(.el-tab-pane){height:100%}
.chat{height:100%;display:flex;flex-direction:column}
.chatmsgs{flex:1;overflow:auto;padding:10px}
.cmsg{margin-bottom:10px}
.cmsg.user .cbub{background:var(--color-primary);color:#fff;border-radius:12px 12px 4px 12px;padding:8px 12px;margin-left:36px;font-size:13px}
.cmsg.assistant .cbub{background:var(--color-bg-alt);color:var(--color-text);border-radius:12px 12px 12px 4px;padding:8px 12px;margin-right:16px;font-size:13px}
.typing{color:var(--color-text-muted);font-style:italic}
.chatinp{padding:8px;border-top:1px solid var(--color-border-light)}
.chars{padding:10px;overflow:auto;height:100%}
.charc{padding:10px 12px;background:var(--color-bg-alt);border-radius:var(--radius);margin-bottom:8px;border:1px solid var(--color-border-light)}
.charhead{display:flex;justify-content:space-between;align-items:center;margin-bottom:4px}
.charc p{font-size:12px;color:var(--color-text-secondary);margin:4px 0}
.chartraits{display:flex;gap:4px;flex-wrap:wrap;margin-top:4px}
.toggle-r{position:absolute;right:0;top:50%;transform:translateY(-50%);width:18px;height:40px;border:1px solid var(--color-border);border-right:none;background:var(--color-surface);cursor:pointer;font-size:9px;color:var(--color-text-muted);display:flex;align-items:center;justify-content:center;border-radius:4px 0 0 4px;z-index:10}
.toggle-r:hover{background:var(--color-surface-hover)}
.ver{display:flex;justify-content:space-between;align-items:center;padding:10px;border-bottom:1px solid var(--color-border-light)}
.vt{display:block;font-size:11px;color:var(--color-text-muted)}
</style>
