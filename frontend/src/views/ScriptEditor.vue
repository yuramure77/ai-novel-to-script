<template>
  <div class="editor" @keydown="onKeydown">
    <!-- Top bar -->
    <header class="tb">
      <div class="tbl">
        <el-button text size="small" @click="$router.push('/projects')">← 返回</el-button>
        <h2 v-if="!renaming" @dblclick="startRename" title="双击重命名">{{ projectTitle }}</h2>
        <el-input v-else v-model="renameTitle" size="small" style="width:240px" @blur="doRename" @keyup.enter="doRename" ref="renameRef" />
        <el-tag :type="stTag" size="small" round>{{ stText }}</el-tag>
      </div>
      <div class="tbr">
        <el-switch v-model="dark" inline-prompt active-text="🌙" inactive-text="☀️" @change="tDark" size="small" />
        <el-dropdown v-if="latestVersion" @command="expCmd">
          <el-button size="small">导出 ▾</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="yaml">YAML</el-dropdown-item>
              <el-dropdown-item command="markdown">Markdown</el-dropdown-item>
              <el-dropdown-item command="fountain">Fountain</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button size="small" v-if="latestVersion" @click="showHist=true">历史</el-button>
      </div>
    </header>

    <!-- Search bar -->
    <div v-if="searchOn" class="sbar">
      <el-input v-model="sq" size="small" placeholder="搜索原文..." clearable @input="doSearch" ref="sr">
        <template #prefix>🔍</template>
        <template #suffix><span style="color:var(--color-text-muted);font-size:11px">{{ sc }} 匹配</span></template>
      </el-input>
    </div>

    <!-- Actions -->
    <div class="act">
      <div class="actl">
        <el-button size="small" @click="doSplit" :loading="splitting">分章</el-button>
        <span v-if="chapters.length" class="hint">{{ chapters.length }} 章</span>
        <span class="hint" style="color:var(--color-text-muted);margin-left:8px">Ctrl+F 搜索 · Ctrl+Enter 生成 · Ctrl+S 保存</span>
      </div>
      <el-button type="primary" @click="doGen" :loading="gen" :disabled="gen">
        {{ gen ? progressMsg : '生成剧本' }}
      </el-button>
    </div>

    <!-- Progress -->
    <div v-if="gen" class="prog"><el-progress :percentage="100" :indeterminate="true" :stroke-width="2" :show-text="false"/></div>

    <!-- Chapters -->
    <div v-if="chapters.length" class="chaps">
      <button v-for="(c,i) in chapters" :key="i" :class="['ch',{on:ac===i}]" @click="ac=i">{{ c.title }}</button>
    </div>

    <!-- Main panels -->
    <div class="main">
      <!-- Left: text -->
      <div class="col cl">
        <div class="ct">📖 原文</div>
        <div class="cb" ref="tp">
          <pre v-html="hl(chapters.length?chapters[ac]?.content||originalText:originalText)"></pre>
        </div>
      </div>

      <!-- Center: YAML -->
      <div class="col cc" :class="{wide:!showR}">
        <div class="ct"><span>📄 剧本</span>
          <div><el-button size="small" text @click="edit=!edit">{{edit?'预览':'编辑'}}</el-button>
            <el-button size="small" text @click="cpyY" v-if="yaml">复制</el-button></div>
        </div>
        <div class="cb">
          <div v-if="!yaml&&!gen" class="emp"><el-empty description="点击「生成剧本」开始"/></div>
          <textarea v-else-if="edit" v-model="ey" class="ye" spellcheck="false"></textarea>
          <div v-else class="yv"><pre v-html="hy"></pre></div>
        </div>
        <div v-if="edit" class="eb"><el-button size="small" @click="edit=false;ey=yaml">取消</el-button>
          <el-button size="small" type="primary" :loading="saveB" @click="svY">保存 (Ctrl+S)</el-button></div>
      </div>

      <!-- Right: Tabs -->
      <div class="col cr" v-if="showR">
        <el-tabs v-model="rt" class="rtt">
          <!-- AI Chat -->
          <el-tab-pane label="AI 助手" name="chat">
            <div class="chat">
              <div class="cmsgs" ref="cbx">
                <div v-for="(m,i) in cmsgs" :key="i" :class="['cm',m.role]">
                  <!-- Assistant bubble with markdown -->
                  <div class="cbb" v-html="renderMd(m.content,m.actions)"></div>
                  <!-- Action buttons -->
                  <div v-if="m.actions?.length" class="cactions">
                    <el-button v-for="a in m.actions" :key="a.action" :type="a.style" size="small"
                      @click="doAction(a,m,i)">{{ a.label }}</el-button>
                  </div>
                </div>
                <div v-if="cw" class="cm assistant"><div class="cbb ty">思考中...</div></div>
              </div>
              <div class="ci">
                <el-input v-model="ci" placeholder="输入修改建议或问题..." @keyup.enter="snd" :disabled="cw" size="small">
                  <template #append><el-button @click="snd" :loading="cw" size="small">发送</el-button></template>
                </el-input>
              </div>
            </div>
          </el-tab-pane>

          <!-- Characters + Image gen -->
          <el-tab-pane label="角色" name="chars">
            <div class="chars">
              <div v-if="chars.length" class="clist">
                <div v-for="(c,i) in chars" :key="i" class="ccard">
                  <img v-if="c.image" :src="c.image" class="cimg" @click="genCharImg(c,i)" :title="'点击重新生成 ' + c.name + ' 的形象图'" />
                  <div v-else class="cimgplc" @click="genCharImg(c,i)">点击生成形象</div>
                  <div class="chead"><strong>{{ c.name }}</strong><el-tag size="small" round>{{ rl(c.role) }}</el-tag></div>
                  <p v-if="c.description">{{ c.description }}</p>
                  <div v-if="c.traits?.length" class="ctraits">
                    <el-tag v-for="t in c.traits" :key="t" size="small" effect="plain" round>{{ t }}</el-tag>
                  </div>
                </div>
              </div>
              <el-empty v-else description="生成后自动提取" />
            </div>
          </el-tab-pane>

          <!-- Scene images carousel -->
          <el-tab-pane label="场景图" name="scenes">
            <div class="scenes">
              <div v-if="sceneImgs.length">
                <el-carousel :interval="4000" arrow="always" height="220px" indicator-position="outside">
                  <el-carousel-item v-for="(s,i) in sceneImgs" :key="i">
                    <div class="carousel-slide">
                      <div class="simgt">场景 {{ i+1 }}: {{ s.title }}</div>
                      <img v-if="s.image" :src="s.image" class="simg" @click="genScnImg(s,i)" title="点击重新生成" />
                      <div v-else class="simgplc" @click="genScnImg(s,i)">点击生成场景图</div>
                      <el-button size="small" text @click="genScnImg(s,i)" v-if="!s.image" style="margin-top:8px">AI 生成</el-button>
                    </div>
                  </el-carousel-item>
                </el-carousel>
                <p style="font-size:11px;color:var(--color-text-muted);text-align:center;margin-top:8px">💡 点击图片重新生成 · 免费 AI 生图</p>
              </div>
              <el-empty v-else description="生成剧本后可预览场景图" />
            </div>
          </el-tab-pane>

          <!-- Search / Research -->
          <el-tab-pane label="研究" name="research">
            <div class="research">
              <p style="font-size:13px;color:var(--color-text-secondary)">搜索剧本创作相关参考资料</p>
              <el-input v-model="rq" placeholder="如：唐代宫廷礼仪 长安城布局" size="small" @keyup.enter="doSrch" />
              <el-button size="small" type="primary" :loading="srching" @click="doSrch" style="margin-top:8px;width:100%">搜索</el-button>
              <div v-if="sres" class="sres" v-html="renderMd(sres)"></div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <button class="tgr" @click="showR=!showR">{{showR?'▶':'◀'}}</button>
    </div>

    <!-- History drawer -->
    <el-drawer v-model="showHist" title="历史版本" size="360px">
      <div v-if="vers.length"><div v-for="v in vers" :key="v.id" class="vr">
        <div><strong>v{{v.versionNumber}}</strong><span class="vt">{{fmt(v.createdAt)}}</span></div>
        <div><el-button size="small" text type="primary" @click="ldVer(v)">查看</el-button>
          <el-button size="small" text @click="dl(v.id)">下载</el-button></div>
      </div></div><el-empty v-else/>
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
const route = useRoute(); const pid = route.params.id

// Core state
const projectTitle = ref(''); const projectStatus = ref('DRAFT')
const originalText = ref(''); const chapters = ref([])
const ac = ref(0); const splitting = ref(false)
const gen = ref(false); const progressMsg = ref('')
const yaml = ref(''); const latestVersion = ref(null)
const edit = ref(false); const ey = ref(''); const saveB = ref(false)
const vers = ref([]); const showHist = ref(false)

// Chat
const cmsgs = ref([]); const ci = ref(''); const cw = ref(false); const cbx = ref(null)

// Characters
const chars = ref([])

// Scenes (for image gen)
const sceneImgs = ref([])

// Search
const searchOn = ref(false); const sq = ref(''); const sc = ref(0); const sr = ref(null); const tp = ref(null)

// Research
const rq = ref(''); const srching = ref(false); const sres = ref('')

// UI
const rt = ref('chat'); const showR = ref(true); const dark = ref(false)
const renaming = ref(false); const renameTitle = ref(''); const renameRef = ref(null)

// Computed
const stTag = computed(()=>projectStatus.value==='COMPLETED'?'success':projectStatus.value==='PROCESSING'?'warning':'info')
const stText = computed(()=>({DRAFT:'草稿',PROCESSING:'处理中',COMPLETED:'已完成'})[projectStatus.value]||'')
const hy = computed(()=>yaml.value?hljs.highlight(yaml.value,{language:'yaml'}).value:'')

// Parse characters from YAML
watch(yaml,()=>{
  try{
    const out=[]; let cur=null
    for(const l of yaml.value.split('\n')){
      if(l.startsWith('characters:'))continue
      if(/^\w/.test(l)&&!l.startsWith('  '))break
      const nm=l.match(/^\s{2}-\s*name:\s*"?([^"\n]+)/)
      if(nm){if(cur)out.push(cur);cur={name:nm[1].trim()}}
      else if(cur){
        const r=l.match(/role:\s*"?(\w+)/);if(r)cur.role=r[1]
        const d=l.match(/description:\s*"?([^"\n]+)/);if(d)cur.description=d[1]
        const t=l.match(/^\s{6}-\s*"?([^"\n]+)/);if(t){cur.traits??=[];cur.traits.push(t[1])}
      }
    }
    if(cur)out.push(cur); chars.value = out
  }catch{}
})

watch(yaml,()=>{
  try{
    const out=[];
    const sp=yaml.value.split(/(?=  - id: SCENE_)/g)
    for(const s of sp){
      const loc=s.match(/location:\s*"?([^"\n]+)/)
      const time=s.match(/time:\s*"?([^"\n]+)/)
      const desc=s.match(/description:\s*"?([^"\n]+)/)
      out.push({title:(loc?loc[1]:'')+' · '+(time?time[1]:''),description:desc?desc[1]:'',location:loc?loc[1]:'',time:time?time[1]:''})
    }
    sceneImgs.value = out
  }catch{}
})

onMounted(async()=>{
  dark.value = document.documentElement.classList.contains('dark')
  try{
    const r = await getProject(pid); const d = r.data.data
    projectTitle.value=d.project?.title||'';projectStatus.value=d.project?.status||'DRAFT';originalText.value=d.originalText||''
    const v=await getLatest(pid)
    if(v.data.data){latestVersion.value=v.data.data;yaml.value=v.data.data.yamlContent}
    if(originalText.value&&!chapters.value.length)await doSplit()
    cmsgs.value=((await getHistory(pid)).data.data||[]).map(m=>({role:m.role,content:m.content}))
  }catch{}
})

// Split
async function doSplit(){splitting.value=true;try{const r=await splitChapters(pid);chapters.value=r.data.data.chapters||[];if(chapters.value.length)ac.value=0}catch(e){ElMessage.error(e.response?.data?.message||'分章失败')}finally{splitting.value=false}}

// Generate SSE
function doGen(){
  gen.value=true;progressMsg.value='连接中...';yaml.value=''
  fetch(`/api/projects/${pid}/generate/stream`,{headers:{Authorization:`Bearer ${localStorage.getItem('token')}`}})
    .then(r=>{const reader=r.body.getReader(),dec=new TextDecoder();let buf=''
      function rd(){reader.read().then(({done,value})=>{
        if(done){gen.value=false;return}
        buf+=dec.decode(value,{stream:true});const lines=buf.split('\n');buf=lines.pop()||''
        for(let i=0;i<lines.length;i++){
          if(!lines[i].startsWith('event:'))continue
          const ev=lines[i].replace('event:','').trim(),dl=lines[i+1]
          if(!dl?.startsWith('data:'))continue
          try{const d=JSON.parse(dl.replace('data:','').trim())
            if(ev==='progress')progressMsg.value=d.message||''
            else if(ev==='done'){yaml.value=d.yamlContent||'';latestVersion.value={id:d.versionId,versionNumber:d.versionNumber};projectStatus.value='COMPLETED';ElMessage.success('v'+d.versionNumber+' 生成成功')}
            else if(ev==='error'){ElMessage.error(d||'失败');gen.value=false}
          }catch{}
        }
        if(gen.value)rd()
      })}
      rd()
    }).catch(e=>{ElMessage.error('连接失败');gen.value=false})
}

// YAML edit
async function svY(){if(!ey.value.trim())return;saveB.value=true;try{const r=await saveEditedYaml(pid,ey.value);yaml.value=ey.value;latestVersion.value={id:r.data.data.id,versionNumber:r.data.data.versionNumber};ElMessage.success('v'+r.data.data.versionNumber+' 已保存');edit.value=false;loadV()}catch{ElMessage.error('保存失败')}finally{saveB.value=false}}

// Chat
async function snd(){
  const m=ci.value.trim();if(!m||cw.value)return;ci.value=''
  cmsgs.value.push({role:'user',content:m});cw.value=true
  await nextTick();cbx.value&&(cbx.value.scrollTop=cbx.value.scrollHeight)
  try{
    const r=await sendMessage(pid,m);const d=r.data.data
    cmsgs.value.push({role:'assistant',content:d.reply,actions:d.actions||[],yamlPatch:d.yamlPatch||null})
  }catch{ElMessage.error('发送失败')}
  finally{cw.value=false;await nextTick();cbx.value&&(cbx.value.scrollTop=cbx.value.scrollHeight)}
}

// Actions
function doAction(a,msg,i){
  if(a.action==='apply_yaml'&&msg.yamlPatch){
    ey.value = msg.yamlPatch; yaml.value = msg.yamlPatch; edit.value = true
    ElMessage.success('已应用修改，请确认后保存 (Ctrl+S)')
  } else if(a.action==='discard'){
    cmsgs.value[i].actions = []
  }
}

// Markdown rendering
function renderMd(t,actions){
  if(!t)return''
  let h = t.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
  h = h.replace(/```yaml:patch([\s\S]*?)```/g,'<div class="patch-block"><div class="patch-label">📝 建议修改的 YAML</div><pre><code>$1</code></pre></div>')
  h = h.replace(/```yaml([\s\S]*?)```/g,'<pre><code>$1</code></pre>')
  h = h.replace(/```(\w*)([\s\S]*?)```/g,'<pre><code>$2</code></pre>')
  h = h.replace(/`([^`]+)`/g,'<code style="background:var(--color-bg-alt);padding:1px 5px;border-radius:3px;font-size:12px">$1</code>')
  h = h.replace(/\*\*([^*]+)\*\*/g,'<strong>$1</strong>')
  h = h.replace(/\*([^*]+)\*/g,'<em>$1</em>')
  h = h.replace(/\n/g,'<br>')
  // Highlight YAML blocks with hljs
  h = h.replace(/<pre><code>([\s\S]*?)<\/code><\/pre>/g,(_,c)=>{
    try{return'<pre><code>'+hljs.highlight(c,{language:'yaml'}).value+'</code></pre>'}catch{return'<pre><code>'+c+'</code></pre>'}
  })
  return h
}

// Character image
async function genCharImg(c,i){
  try{
    ElMessage.info('正在生成 '+(c.name||'角色')+' 的形象...')
    const r=await api.post('/ai/image/character',{name:c.name,description:c.description,traits:c.traits||[]})
    chars.value[i] = {...chars.value[i], image: r.data.data.url}
  }catch{ElMessage.error('生成失败')}
}

// Scene image
async function genScnImg(s,i){
  try{
    ElMessage.info('正在生成场景图...')
    const r=await api.post('/ai/image/scene',{description:s.description,location:s.location,time:s.time,mood:''})
    sceneImgs.value[i] = {...sceneImgs.value[i], image: r.data.data.url}
  }catch{ElMessage.error('生成失败')}
}

// Research
async function doSrch(){
  if(!rq.value.trim())return;srching.value=true
  try{const r=await api.post('/ai/search',{query:rq.value});sres.value=r.data.data.result}catch{ElMessage.error('搜索失败')}
  finally{srching.value=false}
}

// Versions
async function loadV(){try{vers.value=(await listVersions(pid)).data.data||[]}catch{}}
function ldVer(v){yaml.value=v.yamlContent;edit.value=false;showHist.value=false}
function dl(id){window.open(getYamlUrl(id),'_blank')}
function cpyY(){navigator.clipboard.writeText(yaml.value).then(()=>ElMessage.success('已复制')).catch(()=>{})}

// Export
function expCmd(cmd){
  if(!latestVersion.value)return
  if(cmd==='yaml')window.open(getYamlUrl(latestVersion.value.id),'_blank')
  else window.open(`/api/export/${latestVersion.value.id}/${cmd}`,'_blank')
}

// Rename
function startRename(){renaming.value=true;renameTitle.value=projectTitle.value;nextTick(()=>renameRef.value?.focus())}
async function doRename(){
  if(!renameTitle.value.trim()){renaming.value=false;return}
  try{const r=await api.put(`/projects/${pid}/rename`,{title:renameTitle.value});projectTitle.value=r.data.data.title;ElMessage.success('已重命名')}catch{ElMessage.error('失败')}
  finally{renaming.value=false}
}

// Search
function doSearch(){sc.value=0;const q=sq.value.toLowerCase();if(!q)return;const txt=(tp.value?.textContent||'').toLowerCase();sc.value=(txt.match(new RegExp(q.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'),'g'))||[]).length}
function hl(t){if(!t||!sq.value)return esc(t);const re=new RegExp(`(${sq.value.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')})`,'gi');return esc(t).replace(re,'<mark style="background:#fde68a;padding:0 2px;border-radius:2px">$1</mark>')}
function esc(s){return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}

// Keys
function onKeydown(e){
  if((e.ctrlKey||e.metaKey)&&e.key==='f'){e.preventDefault();searchOn.value=!searchOn.value;if(searchOn.value)nextTick(()=>sr.value?.focus())}
  if((e.ctrlKey||e.metaKey)&&e.key==='Enter'&&!gen.value){e.preventDefault();doGen()}
  if((e.ctrlKey||e.metaKey)&&e.key==='s'&&edit.value){e.preventDefault();svY()}
  if(e.key==='Escape'&&searchOn.value)searchOn.value=false
}

function tDark(v){document.documentElement.classList.toggle('dark',v);localStorage.setItem('dark',v?'1':'0')}
function rl(r){return{protagonist:'主角',antagonist:'反派',supporting:'配角',minor:'次要'}[r]||r||''}
function fmt(d){return d?new Date(d).toLocaleString('zh-CN'):''}
</script>

<style scoped>
.editor{height:100vh;display:flex;flex-direction:column;background:var(--color-bg)}
.tb{display:flex;justify-content:space-between;align-items:center;height:48px;padding:0 12px;background:var(--color-surface);border-bottom:1px solid var(--color-border);flex-shrink:0}
.tbl{display:flex;align-items:center;gap:8px}
.tbl h2{font-size:15px;margin:0;color:var(--color-text);font-weight:600;cursor:pointer}
.tbr{display:flex;align-items:center;gap:6px}
.sbar{padding:8px 12px;background:var(--color-surface);border-bottom:1px solid var(--color-border);flex-shrink:0}
.act{display:flex;justify-content:space-between;align-items:center;padding:8px 12px;background:var(--color-surface);border-bottom:1px solid var(--color-border-light);flex-shrink:0}
.actl{display:flex;align-items:center;gap:10px}
.hint{color:var(--color-text-muted);font-size:11px}
.prog{padding:6px 12px;background:var(--color-surface);border-bottom:1px solid var(--color-border-light);flex-shrink:0}
.chaps{display:flex;gap:4px;padding:6px 12px;background:var(--color-surface);border-bottom:1px solid var(--color-border-light);overflow-x:auto;flex-shrink:0}
.ch{flex-shrink:0;padding:3px 10px;border:1px solid var(--color-border);border-radius:12px;background:var(--color-surface);font-size:11px;color:var(--color-text-secondary);cursor:pointer;white-space:nowrap;transition:all var(--transition)}
.ch:hover{color:var(--color-primary);border-color:var(--color-primary)}
.ch.on{background:var(--color-primary);color:#fff;border-color:var(--color-primary)}
.main{flex:1;display:flex;overflow:hidden;position:relative}
.col{display:flex;flex-direction:column;background:var(--color-surface)}
.cl{width:30%;min-width:260px;border-right:1px solid var(--color-border)}
.cc{width:45%;border-right:1px solid var(--color-border)}
.cc.wide{width:70%}
.cr{width:25%;min-width:300px}
.ct{display:flex;justify-content:space-between;align-items:center;padding:6px 12px;font-size:12px;font-weight:600;color:var(--color-text-secondary);border-bottom:1px solid var(--color-border-light);flex-shrink:0}
.cb{flex:1;overflow:auto;padding:12px}
.cb pre{margin:0;white-space:pre-wrap;word-break:break-word;font-family:var(--font-serif);font-size:14px;line-height:1.9;color:var(--color-text)}
.emp{flex:1;display:flex;align-items:center;justify-content:center}
.ye{width:100%;height:100%;border:none;outline:none;resize:none;font-family:var(--font-mono);font-size:13px;line-height:1.6;background:var(--color-bg-alt);color:var(--color-text);padding:12px;border-radius:var(--radius)}
.eb{display:flex;justify-content:flex-end;gap:8px;padding:8px 12px;border-top:1px solid var(--color-border-light);flex-shrink:0}
.yv pre{font-family:var(--font-mono);font-size:13px;line-height:1.6;margin:0}
.yv code{white-space:pre-wrap}
.rtt{height:100%;display:flex;flex-direction:column;padding:0 6px}
.rtt :deep(.el-tabs__content){flex:1;overflow:hidden}
.rtt :deep(.el-tab-pane){height:100%}

/* Chat */
.chat{height:100%;display:flex;flex-direction:column}
.cmsgs{flex:1;overflow:auto;padding:8px}
.cm{margin-bottom:8px}
.cm.user .cbb{background:var(--color-primary);color:#fff;border-radius:12px 12px 4px 12px;padding:8px 12px;margin-left:32px;font-size:13px}
.cm.assistant .cbb{background:var(--color-bg-alt);color:var(--color-text);border-radius:12px 12px 12px 4px;padding:8px 12px;margin-right:12px;font-size:13px;line-height:1.6}
.cbb :deep(pre){background:var(--color-surface);border:1px solid var(--color-border);border-radius:6px;padding:10px;margin:8px 0;font-size:12px;overflow:auto;max-height:200px}
.cbb :deep(code){font-family:var(--font-mono);font-size:12px}
.cbb :deep(.patch-block){border:2px solid var(--color-success);border-radius:8px;margin:8px 0;overflow:hidden}
.cbb :deep(.patch-label){background:var(--color-success);color:#fff;padding:4px 10px;font-size:11px;font-weight:600}
.cbb :deep(.patch-block pre){margin:0;border:none;border-radius:0;background:#f0fdf4}
.ty{color:var(--color-text-muted);font-style:italic}
.cactions{display:flex;gap:6px;margin-top:6px;margin-left:12px}
.ci{padding:8px;border-top:1px solid var(--color-border-light)}

/* Characters */
.chars{padding:8px;overflow:auto;height:100%}
.ccard{background:var(--color-bg-alt);border-radius:var(--radius);margin-bottom:8px;border:1px solid var(--color-border-light);overflow:hidden}
.cimg{width:100%;height:160px;object-fit:cover;cursor:pointer}
.cimgplc{width:100%;height:80px;background:var(--color-bg);display:flex;align-items:center;justify-content:center;font-size:12px;color:var(--color-text-muted);cursor:pointer;border-radius:var(--radius) var(--radius) 0 0}
.cimgplc:hover{background:var(--color-surface-hover)}
.chead{display:flex;justify-content:space-between;align-items:center;padding:6px 10px}
.ccard p{padding:0 10px;font-size:12px;color:var(--color-text-secondary);margin:0 0 4px}
.ctraits{display:flex;gap:4px;flex-wrap:wrap;padding:0 10px 8px}

/* Scene images carousel */
.scenes{padding:8px;overflow:auto;height:100%}
.carousel-slide{text-align:center;padding:4px}
.simgt{font-size:12px;color:var(--color-text-secondary);margin-bottom:6px}
.simg{width:100%;height:160px;object-fit:cover;border-radius:var(--radius);cursor:pointer}
.simgplc{width:100%;height:120px;background:var(--color-bg);display:flex;align-items:center;justify-content:center;font-size:12px;color:var(--color-text-muted);cursor:pointer;border-radius:var(--radius);border:2px dashed var(--color-border)}
.simgplc:hover{background:var(--color-surface-hover);border-color:var(--color-primary)}

/* Research */
.research{padding:8px}
.sres{margin-top:12px;padding:10px;background:var(--color-bg-alt);border-radius:var(--radius);font-size:13px;line-height:1.6;max-height:400px;overflow:auto}

.tgr{position:absolute;right:0;top:50%;transform:translateY(-50%);width:18px;height:40px;border:1px solid var(--color-border);border-right:none;background:var(--color-surface);cursor:pointer;font-size:9px;color:var(--color-text-muted);display:flex;align-items:center;justify-content:center;border-radius:4px 0 0 4px;z-index:10}
.tgr:hover{background:var(--color-surface-hover)}
.vr{display:flex;justify-content:space-between;align-items:center;padding:10px;border-bottom:1px solid var(--color-border-light)}
.vt{display:block;font-size:11px;color:var(--color-text-muted)}
</style>
