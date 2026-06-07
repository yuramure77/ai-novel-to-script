<template>
  <div class="editor" @keydown="onKeydown">
    <!-- Top bar -->
    <header class="tb">
      <div class="tbl">
        <el-button text size="small" @click="$router.push('/projects')" style="color:var(--c-gold)">← 返回</el-button>
        <h2 v-if="!renaming" @dblclick="startRename" title="双击重命名">{{ projectTitle }}</h2>
        <el-input v-else v-model="renameTitle" size="small" style="width:240px" @blur="doRename" @keyup.enter="doRename" ref="renameRef" />
        <el-tag :type="stTag" size="small" round>{{ stText }}</el-tag>
        <el-tag v-if="isReadOnly" type="info" size="small" round>👁 只读</el-tag>
      </div>
      <div class="tbr">
        <el-switch v-model="dark" inline-prompt active-text="🌙" inactive-text="☀️" @change="tDark" size="small" />
        <el-dropdown v-if="latestVersion" @command="expCmd">
          <el-button size="small" type="warning" plain>📥 导出 ▾</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="yaml">YAML</el-dropdown-item>
              <el-dropdown-item command="markdown">Markdown</el-dropdown-item>
              <el-dropdown-item command="fountain">Fountain</el-dropdown-item>
              <el-dropdown-item command="txt">TXT</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button size="small" v-if="latestVersion" @click="showHist=true" type="warning" plain>📜 历史</el-button>
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
        <el-button size="small" type="warning" @click="doSplit" :loading="splitting" :disabled="isReadOnly">⚡ 分章</el-button>
        <span v-if="chapters.length" class="hint">{{ chapters.length }} 章</span>
        <span v-if="!isReadOnly" class="hint" style="color:var(--color-text-muted);margin-left:8px">Ctrl+F 搜索 · Ctrl+Enter 生成 · Ctrl+S 保存</span>
      </div>
      <el-button v-if="!isReadOnly" size="default" type="warning" @click="doGen()" :loading="gen" :disabled="gen" style="font-weight:700;letter-spacing:1px">
        {{ gen ? progressMsg : '🎬 生成剧本' }}
      </el-button>
      <el-tag v-else type="info" size="small">只读模式 — 仅可查看</el-tag>
    </div>

    <!-- Progress -->
    <div v-if="gen" class="prog"><el-progress :percentage="progressPct" :stroke-width="2" :show-text="true"/><p class="prog-msg">{{ progressMsg }}</p></div>

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
          <div>
            <el-button v-if="!isReadOnly" size="small" text @click="toggleEdit">{{edit?'预览':'编辑'}}</el-button>
            <el-button size="small" text @click="cpyY" v-if="yaml">复制</el-button>
          </div>
        </div>
        <!-- YAML chapter index -->
        <div v-if="yamlChapters.length && !edit" class="yaml-chaps">
          <button :class="['ychip',{on:yamlChap===0}]" @click="yamlChap=0">全部</button>
          <button v-for="ch in yamlChapters" :key="ch.num" :class="['ychip',{on:yamlChap===ch.num}]" @click="yamlChap=ch.num">
            {{ ch.label }} <small>({{ ch.count }}场)</small>
          </button>
        </div>
        <div class="cb">
          <div v-if="!yaml&&!gen" class="emp"><el-empty :description="isReadOnly?'暂无剧本内容':'点击「生成剧本」开始'"/></div>
          <textarea v-else-if="edit" v-model="ey" class="ye" spellcheck="false"></textarea>
          <div v-else class="yv" :key="yaml" style="animation: revealYaml 0.4s ease-out"><pre v-html="hyFiltered"></pre></div>
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
                <!-- Welcome hint -->
                <div v-if="!cmsgs.length" class="chat-welcome">
                  <div class="cw-avatar">🤖</div>
                  <p>我是你的剧本编辑助手</p>
                  <p class="cw-hint">可以帮你优化对话、调整节奏、润色台词<br/>也可以直接让我修改 YAML</p>
                </div>

                <div v-for="(m,i) in cmsgs" :key="i">
                  <div :class="['msg-row', m.role]">
                    <!-- Avatar -->
                    <div class="msg-avatar" :class="m.role">
                      {{ m.role === 'user' ? '👤' : '🤖' }}
                    </div>
                    <!-- Bubble -->
                    <div class="msg-body">
                      <div :class="['bubble', m.role]" v-html="renderMd(m.content)"></div>
                      <!-- Action buttons for YAML patches -->
                      <div v-if="m.actions?.length" class="patch-actions">
                        <el-button
                          v-for="a in m.actions"
                          :key="a.action"
                          :type="a.style"
                          size="small"
                          @click="doAction(a, m, i)"
                          round
                        >{{ a.label }}</el-button>
                      </div>
                      <div v-if="m.applied" class="action-feedback applied">✅ 已应用 · 请在右侧编辑区确认</div>
                      <div v-if="m.discarded" class="action-feedback discarded">🗑️ 已忽略</div>
                    </div>
                  </div>
                </div>

                <!-- Typing indicator -->
                <div v-if="cw" class="msg-row assistant">
                  <div class="msg-avatar assistant">🤖</div>
                  <div class="msg-body">
                    <div class="bubble assistant typing-bubble">
                      <span class="dot"></span><span class="dot"></span><span class="dot"></span>
                    </div>
                  </div>
                </div>
              </div>

              <div class="chat-input-bar">
                <el-input
                  v-model="ci"
                  placeholder="输入修改建议或问题，回车发送..."
                  @keyup.enter="snd"
                  :disabled="cw"
                  size="default"
                  class="chat-input-inner"
                />
                <el-button type="warning" @click="snd" :loading="cw" size="default" round>发送</el-button>
              </div>
            </div>
          </el-tab-pane>

          <!-- Characters + Image gen -->
          <el-tab-pane label="角色" name="chars">
            <div class="chars">
              <div v-if="chars.length" class="clist">
                <div v-for="(c,i) in chars" :key="i" class="ccard">
                  <img v-if="c.image" :src="c.image" class="cimg" @click="previewImg(c.image, c.prompt)" title="点击放大查看" />
                  <div v-else class="cimgplc" @click="genCharImg(c,i)">点击生成形象</div>
                  <div class="chead"><strong>{{ c.name }}</strong><el-tag size="small" round>{{ rl(c.role) }}</el-tag></div>
                  <p v-if="c.description">{{ c.description }}</p>
                  <div v-if="c.traits?.length" class="ctraits">
                    <el-tag v-for="t in c.traits" :key="t" size="small" effect="plain" round>{{ t }}</el-tag>
                  </div>
                  <div v-if="c.image" style="display:flex;gap:8px;margin-top:4px">
                    <el-button size="small" text type="warning" @click="genCharImg(c,i)">🔄 重新生成</el-button>
                    <el-button size="small" text @click="showImgVer('CHARACTER', i)">📋 历史</el-button>
                  </div>
                </div>
              </div>
              <el-empty v-else description="生成后自动提取" />
            </div>
          </el-tab-pane>

          <!-- Scene images gallery -->
          <el-tab-pane label="场景图" name="scenes">
            <div class="scenes">
              <div v-if="sceneImgs.length">
                <div class="scene-gallery-header">
                  <span>🎬 {{ sceneImgs.length }} 个场景</span>
                  <el-button size="small" type="warning" @click="genAllScenes" :loading="genAllScenesBusy" :disabled="isReadOnly">
                    🎨 批量生成全部场景图
                  </el-button>
                </div>
                <div class="scene-cards">
                  <div v-for="(s,i) in sceneImgs" :key="i" class="scene-card">
                    <div class="scene-card-img" @click="s.image ? previewImg(s.image, s.prompt) : genScnImg(s,i)">
                      <img v-if="s.image" :src="s.image" />
                      <div v-else class="scene-card-placeholder">
                        <span>🎥</span>
                        <span>点击生成</span>
                      </div>
                      <div class="scene-card-overlay">
                        <span>{{ s.image ? '🔍 放大' : '🎨 生成' }}</span>
                      </div>
                    </div>
                    <div class="scene-card-info">
                      <div class="scene-card-num">场景 {{ i+1 }}</div>
                      <div class="scene-card-title">{{ s.title || '未命名场景' }}</div>
                      <div class="scene-card-meta">
                        <span v-if="s.location">📍 {{ s.location }}</span>
                        <span v-if="s.time">🕐 {{ s.time }}</span>
                      </div>
                      <div class="scene-card-actions">
                        <el-button size="small" text type="warning" @click="genScnImg(s,i)" :loading="false">
                          {{ s.image ? '🔄' : '🎨' }} {{ s.image ? '重新生成' : 'AI 生成' }}
                        </el-button>
                        <el-button v-if="s.image" size="small" text @click="showImgVer('SCENE', i)">📋</el-button>
                      </div>
                    </div>
                  </div>
                </div>
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

    <!-- Image preview dialog -->
    <el-dialog v-model="showPreview" title="图片预览" width="auto" :close-on-click-modal="true" :append-to-body="true">
      <img :src="previewUrl" style="max-width:90vw;max-height:80vh;border-radius:8px" @click="showPreview=false" />
      <p v-if="previewPrompt" style="color:var(--color-text-muted);font-size:12px;margin-top:8px;max-width:90vw">📝 {{ previewPrompt }}</p>
    </el-dialog>

    <!-- Image version history dialog -->
    <el-dialog v-model="showVerDialog" title="图片版本历史" width="480px" :append-to-body="true">
      <div v-if="imgVersions.length">
        <div v-for="v in imgVersions" :key="v.id" class="ver-item"
             style="display:flex;align-items:center;justify-content:space-between;padding:10px;border-bottom:1px solid var(--color-border);cursor:pointer"
             @click="applyVersion(v)">
          <div>
            <img :src="v.url" style="width:60px;height:60px;object-fit:cover;border-radius:6px" />
            <span style="margin-left:10px;font-size:12px;color:var(--color-text-muted)">{{ fmt(v.createdAt) }}</span>
          </div>
          <el-button size="small" type="primary" text>应用</el-button>
        </div>
      </div>
      <el-empty v-else description="暂无历史版本" />
      <template #footer><el-button @click="showVerDialog=false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import hljs from 'highlight.js/lib/core'
import yl from 'highlight.js/lib/languages/yaml'
import 'highlight.js/styles/monokai.css'
import { getProject, splitChapters } from '@/api/projects'
import { listVersions, getLatest, getYamlUrl, saveEditedYaml } from '@/api/scripts'
import { sendMessage, getHistory } from '@/api/chat'
import api from '@/api/index'

hljs.registerLanguage('yaml', yl)
const route = useRoute(); const pid = route.params.id

// Core state
const projectTitle = ref(''); const projectStatus = ref('DRAFT')
const originalText = ref(''); const chapters = ref([])
const permission = ref('ADMIN')  // current user's permission for this project
const ac = ref(0); const splitting = ref(false)
const gen = ref(false); const progressMsg = ref(''); const progressPct = ref(0)
const totalChapters = ref(0)
const yaml = ref(''); const latestVersion = ref(null)
const edit = ref(false); const ey = ref(''); const saveB = ref(false)
const vers = ref([]); const showHist = ref(false)
const yamlChap = ref(0); const yamlChapters = ref([])

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

// Image restore (survive server restart)
async function restoreImages() {
  try {
    const r = await api.get('/ai/image/restore', { params: { projectId: pid } })
    const data = r.data.data
    // Restore character images
    if (data.characters) {
      for (const [idx, img] of Object.entries(data.characters)) {
        const i = Number(idx)
        if (chars.value[i]) chars.value[i] = { ...chars.value[i], image: img.url, prompt: img.prompt || '' }
      }
    }
    // Restore scene images
    if (data.scenes) {
      for (const [idx, img] of Object.entries(data.scenes)) {
        const i = Number(idx)
        if (sceneImgs.value[i]) sceneImgs.value[i] = { ...sceneImgs.value[i], image: img.url, prompt: img.prompt || '' }
      }
    }
  } catch (e) { console.warn('Image restore skipped:', e) }
}

// Image preview
const showPreview = ref(false); const previewUrl = ref(''); const previewPrompt = ref('')
function previewImg(url, prompt) { previewUrl.value = url; previewPrompt.value = prompt || ''; showPreview.value = true }

// Image version history
const showVerDialog = ref(false); const imgVersions = ref([])
const verType = ref(''); const verIndex = ref(0)
async function showImgVer(type, index) {
  verType.value = type; verIndex.value = index
  try {
    const r = await api.get('/ai/image/versions', { params: { projectId: pid, type, index } })
    imgVersions.value = r.data.data || []
    showVerDialog.value = true
  } catch { ElMessage.error('加载版本失败') }
}
function applyVersion(v) {
  if (verType.value === 'CHARACTER') {
    chars.value[verIndex.value] = { ...chars.value[verIndex.value], image: v.url, prompt: v.prompt }
  } else {
    sceneImgs.value[verIndex.value] = { ...sceneImgs.value[verIndex.value], image: v.url, prompt: v.prompt }
  }
  showVerDialog.value = false
  ElMessage.success('已切换版本')
}

// UI
const rt = ref('chat'); const showR = ref(true); const dark = ref(false)
const renaming = ref(false); const renameTitle = ref(''); const renameRef = ref(null)

// Computed
const stTag = computed(()=>projectStatus.value==='COMPLETED'?'success':projectStatus.value==='PROCESSING'?'warning':'info')
const stText = computed(()=>({DRAFT:'草稿',PROCESSING:'处理中',COMPLETED:'已完成'})[projectStatus.value]||'')
const isReadOnly = computed(() => permission.value === 'READ')
const isOwner = computed(() => permission.value === 'ADMIN')
const hy = computed(()=>yaml.value?hljs.highlight(yaml.value,{language:'yaml'}).value:'')
const hyFiltered = computed(()=>{
  if(!yaml.value||yamlChap.value===0)return hy.value
  // Extract only the selected chapter's scenes
  const lines = yaml.value.split('\n')
  const out = []
  let inScene = false, currentChapter = 0, headerDone = false
  for(let i=0;i<lines.length;i++){
    const l = lines[i]
    // Copy header (everything before first scene)
    if(!headerDone){
      out.push(l)
      if(l.match(/^scenes:/)) headerDone = true
      continue
    }
    // Track chapter
    const cm = l.match(/^\s{4}chapter:\s*(\d+)/)
    if(cm) currentChapter = parseInt(cm[1])
    // Track scene start
    if(l.match(/^\s{2}- id: SCENE_/)) inScene = true
    // Include if in selected chapter
    if(currentChapter === yamlChap.value) out.push(l)
    // Stop at next top-level key
    if(l.match(/^\w/) && headerDone && currentChapter !== yamlChap.value) break
  }
  return hljs.highlight(out.join('\n'),{language:'yaml'}).value
})

// Parse YAML chapter index (handles both indented and unindented formats)
watch(yaml,()=>{
  const chs = {}; let cur = 0
  for(const l of yaml.value.split('\n')){
    const m = l.match(/^\s*chapter:\s*(\d+)/)
    if(m){cur=parseInt(m[1]);chs[cur]=(chs[cur]||0)+1}
  }
  yamlChapters.value = Object.entries(chs).map(([n,c])=>({num:parseInt(n),count:c,label:'第'+n+'章'}))
})

// Parse characters from YAML (handles both AI-generated and demo formats)
watch(yaml,()=>{
  try{
    const out=[]; let cur=null; const lines=yaml.value.split('\n'); let inChars=false
    for(let i=0;i<lines.length;i++){
      const l=lines[i]
      // Detect characters section
      if(l.match(/^characters\s*:/)){inChars=true;continue}
      if(inChars && l.match(/^\w/)){inChars=false;continue}
      if(!inChars)continue
      // Match name: field — with or without leading dash (AI: "- name:" vs demo: "    - name:")
      const nm=l.match(/^\s*-?\s*name:\s*"?([^"\n]+)/)
      if(nm){if(cur)out.push(cur);cur={name:nm[1].trim()};continue}
      if(!cur)continue
      const r=l.match(/^\s*role:\s*"?(\w+)/);if(r)cur.role=r[1]
      const d=l.match(/^\s*description:\s*"?([^"\n]+)/);if(d)cur.description=d[1].replace(/^"|"$/g,'')
      const t=l.match(/^\s*-\s*"?([^"\n]+)/);if(t&&!l.includes('name:')&&!l.includes('role:')&&!l.includes('description:')&&!l.includes('id:')){cur.traits??=[];cur.traits.push(t[1].replace(/^"|"$/g,'').trim())}
    }
    if(cur)out.push(cur)
    chars.value=out.filter(c=>c.name)
  }catch(e){console.warn('char parse:',e)}
})

watch(yaml,()=>{
  try{
    const out=[]; const txt=yaml.value
    // Split scenes: AI format "- title:" / "- scene_id:" / demo format "  - id: SCENE_"
    const re=/(?:^|\n)\s*- (?:id: |title: |scene_id: )/
    let lastIdx=0; const matches=[...txt.matchAll(new RegExp(re,'g'))]
    for(let i=0;i<matches.length;i++){
      const start=matches[i].index+(matches[i][0].startsWith('\n')?1:0)
      const end=i+1<matches.length?matches[i+1].index:txt.length
      const s=txt.substring(start,end)
      const loc=s.match(/location:\s*"?([^"\n]+)/)
      const time=s.match(/time:\s*"?([^"\n]+)/)
      const desc=s.match(/(?:description|title):\s*"?([^"\n]+)/)
      out.push({title:(desc?desc[1]:loc?loc[1]:'')+' · '+(time?time[1]:''),description:desc?desc[1]:loc?loc[1]:'',location:loc?loc[1]:'',time:time?time[1]:''})
    }
    sceneImgs.value = out
  }catch{}
})

onMounted(async()=>{
  dark.value = document.documentElement.classList.contains('light')
  try{
    const r = await getProject(pid); const d = r.data.data
    projectTitle.value=d.project?.title||'';projectStatus.value=d.project?.status||'DRAFT';originalText.value=d.originalText||''
    permission.value = d.permission || d.project?.permission || 'ADMIN'
    const v=await getLatest(pid)
    if(v.data.data){latestVersion.value=v.data.data;yaml.value=v.data.data.yamlContent}
    if(originalText.value&&!chapters.value.length)await doSplit()
    cmsgs.value=((await getHistory(pid)).data.data||[]).map(m=>({role:m.role,content:m.content}))
    // Restore saved images after YAML parsing
    await nextTick()
    restoreImages()
  }catch{}
})

// Split
async function doSplit(){splitting.value=true;try{const r=await splitChapters(pid);chapters.value=r.data.data.chapters||[];if(chapters.value.length)ac.value=0}catch(e){ElMessage.error(e.response?.data?.message||'分章失败')}finally{splitting.value=false}}

// Generate SSE
function doGen(){
  gen.value=true;progressMsg.value='连接中...';yaml.value=''
  fetch(`/api/projects/${pid}/generate/stream`,{headers:{Authorization:`Bearer ${localStorage.getItem('token')}`}})
    .then(r=>{const reader=r.body.getReader(),dec=new TextDecoder();let buf=''
      function processLines(lines){
        for(let i=0;i<lines.length;i++){
          if(!lines[i].startsWith('event:'))continue
          const ev=lines[i].replace('event:','').trim(), dl=lines[i+1]
          // If data line is in the next chunk, put event line back in buffer
          if(!dl?.startsWith('data:')){
            buf = lines.slice(i).join('\n') + (buf?'\n'+buf:'')
            return
          }
          try{const d=JSON.parse(dl.replace('data:','').trim())
            if(ev==='progress'){progressMsg.value=d.message||'';if(d.percent)progressPct.value=d.percent}
            else if(ev==='chapter_done'){
              if(d.yamlContent){
                yaml.value=d.yamlContent
                latestVersion.value={id:d.versionId,versionNumber:d.versionNumber}
              }
              if(d.percent)progressPct.value=d.percent
              progressMsg.value='生成中...'
            }
            else if(ev==='done'){
              if(d.yamlContent){
                yaml.value=d.yamlContent
                latestVersion.value={id:d.versionId,versionNumber:d.versionNumber}
                projectStatus.value='COMPLETED'
              }
              gen.value=false;ElMessage.success('生成完成')
              if(d.totalChapters) totalChapters.value = d.totalChapters
              fetchGenResult()
            }
            else if(ev==='error'){const msg=typeof d==='string'?d:d.message||'生成失败';ElMessage.error(msg);gen.value=false}
          }catch(e){console.warn('SSE parse:',e,dl?.substring(0,80))}
        }
      }
      function rd(){reader.read().then(({done,value})=>{
        if(done){
          if(buf.trim()){const lines=buf.split('\n');processLines(lines)}
          // If gen is still true, 'done' event was missed — fetch from API
          if(gen.value){fetchGenResult();gen.value=false}
          return
        }
        buf+=dec.decode(value,{stream:true});const lines=buf.split('\n');buf=lines.pop()||''
        processLines(lines)
        if(gen.value)rd()
      }).catch(e=>{console.error('SSE read error:',e);gen.value=false})}
      rd()
    }).catch(e=>{ElMessage.error('连接失败');gen.value=false})
}

// Fetch latest version after generation (backup for SSE data)
async function fetchGenResult(){
  // Retry up to 3 times with delay, in case DB hasn't committed yet
  for(let attempt=0;attempt<3;attempt++){
    try{
      await new Promise(r=>setTimeout(r,500))
      const v=await getLatest(pid)
      if(v.data.data?.yamlContent){
        yaml.value=v.data.data.yamlContent
        latestVersion.value={id:v.data.data.id,versionNumber:v.data.data.versionNumber}
        projectStatus.value='COMPLETED'
        return
      }
    }catch(e){console.warn('fetchGenResult attempt '+attempt, e)}
  }
}

// YAML edit
function toggleEdit(){if(!edit.value){ey.value=yaml.value};edit.value=!edit.value}
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
    // Force Vue reactivity by replacing the message object
    cmsgs.value.splice(i, 1, { ...msg, actions: [], applied: true })
    ElMessage.success('已应用修改，请确认后保存 (Ctrl+S)')
  } else if(a.action==='discard'){
    cmsgs.value.splice(i, 1, { ...msg, actions: [], discarded: true })
  }
}

// Markdown rendering
function renderMd(t){
  if(!t)return''
  let h = t.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')

  // Code blocks first (before other transformations)
  h = h.replace(/```yaml:patch([\s\S]*?)```/g,'<div class="patch-block"><div class="patch-label">📝 建议修改</div><pre><code>$1</code></pre></div>')
  h = h.replace(/```(\w*)\n?([\s\S]*?)```/g,(_,lang,code)=>{
    try{return'<pre><code>'+hljs.highlight(code.trim(),{language:lang||'yaml'}).value+'</code></pre>'}catch{return'<pre><code>'+code.trim()+'</code></pre>'}
  })

  // Headings
  h = h.replace(/^#### (.+)$/gm,'<h5>$1</h5>')
  h = h.replace(/^### (.+)$/gm,'<h4>$1</h4>')
  h = h.replace(/^## (.+)$/gm,'<h3>$1</h3>')
  h = h.replace(/^# (.+)$/gm,'<h2>$1</h2>')

  // Bold and italic
  h = h.replace(/\*\*\*(.+?)\*\*\*/g,'<strong><em>$1</em></strong>')
  h = h.replace(/\*\*(.+?)\*\*/g,'<strong>$1</strong>')
  h = h.replace(/\*(.+?)\*/g,'<em>$1</em>')

  // Inline code
  h = h.replace(/`([^`]+)`/g,'<code>$1</code>')

  // Unordered lists
  h = h.replace(/^[\-\*] (.+)$/gm,'<li>$1</li>')
  h = h.replace(/(<li>.*<\/li>\n?)+/g,'<ul>$&</ul>')

  // Ordered lists
  h = h.replace(/^\d+\. (.+)$/gm,'<li>$1</li>')

  // Horizontal rules
  h = h.replace(/^---$/gm,'<hr>')

  // Line breaks
  h = h.replace(/\n\n/g,'</p><p>')
  h = h.replace(/\n/g,'<br>')

  // Wrap in paragraph
  h = '<p>'+h+'</p>'
  h = h.replace(/<p><h([2-5])>/g,'<h$1>').replace(/<\/h([2-5])><\/p>/g,'</h$1>')
  h = h.replace(/<p><ul>/g,'<ul>').replace(/<\/ul><\/p>/g,'</ul>')
  h = h.replace(/<p><pre>/g,'<pre>').replace(/<\/pre><\/p>/g,'</pre>')
  h = h.replace(/<p><div/g,'<div').replace(/\/div><\/p>/g,'/div>')
  h = h.replace(/<p><\/p>/g,'')

  return h
}

// Character image
async function genCharImg(c,i){
  try{
    ElMessage.info('正在生成 '+(c.name||'角色')+' 的形象...')
    const r=await api.post('/ai/image/character',{
      projectId: Number(pid), charIndex: i,
      name: c.name, description: c.description, traits: c.traits || []
    })
    chars.value[i] = {...chars.value[i], image: r.data.data.url, prompt: r.data.data.prompt}
  }catch{ElMessage.error('生成失败')}
}

// Scene image
async function genScnImg(s,i){
  try{
    ElMessage.info('正在生成场景图...')
    const r=await api.post('/ai/image/scene',{
      projectId: Number(pid), sceneIndex: i,
      description: s.description, location: s.location, time: s.time, mood: ''
    })
    sceneImgs.value[i] = {...sceneImgs.value[i], image: r.data.data.url, prompt: r.data.data.prompt}
  }catch{ElMessage.error('生成失败')}
}

// Batch generate all scene images
const genAllScenesBusy = ref(false)
async function genAllScenes() {
  genAllScenesBusy.value = true
  let done = 0
  for (let i = 0; i < sceneImgs.value.length; i++) {
    if (!sceneImgs.value[i].image) {
      try {
        const s = sceneImgs.value[i]
        const r = await api.post('/ai/image/scene', {
          projectId: Number(pid), sceneIndex: i,
          description: s.description, location: s.location, time: s.time, mood: ''
        })
        sceneImgs.value[i] = { ...sceneImgs.value[i], image: r.data.data.url, prompt: r.data.data.prompt }
        done++
      } catch { /* skip failed, continue next */ }
    }
  }
  genAllScenesBusy.value = false
  ElMessage.success(`已生成 ${done} 张场景图`)
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
function startRename(){if(isReadOnly.value)return;renaming.value=true;renameTitle.value=projectTitle.value;nextTick(()=>renameRef.value?.focus())}
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
  if((e.ctrlKey||e.metaKey)&&e.key==='Enter'&&!gen.value&&!isReadOnly.value){e.preventDefault();doGen()}
  if((e.ctrlKey||e.metaKey)&&e.key==='s'&&edit.value&&!isReadOnly.value){e.preventDefault();svY()}
  if(e.key==='Escape'&&searchOn.value)searchOn.value=false
}

function tDark(v){document.documentElement.classList.toggle('light',v);localStorage.setItem('light',v?'1':'0')}
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
.act{display:flex;justify-content:space-between;align-items:center;padding:10px 14px;background:linear-gradient(180deg,var(--color-surface),var(--color-bg));border-bottom:1px solid var(--color-border);flex-shrink:0}
.actl{display:flex;align-items:center;gap:12px}
.hint{color:var(--color-text-muted);font-size:11px}
.prog{padding:8px 14px;background:linear-gradient(180deg,var(--color-surface),var(--color-bg));border-bottom:1px solid var(--color-border-light);flex-shrink:0}
.chaps{display:flex;gap:6px;padding:8px 12px;background:linear-gradient(180deg,var(--color-surface),var(--color-bg));border-bottom:1px solid var(--color-border-light);overflow-x:auto;flex-shrink:0;align-items:center}
.ch{flex-shrink:0;padding:4px 14px;border:1px solid var(--color-border);border-radius:16px;background:var(--color-surface);font-size:12px;font-weight:500;color:var(--color-text-secondary);cursor:pointer;white-space:nowrap;transition:all .2s ease;font-family:var(--font-serif)}
.ch:hover{color:var(--c-gold);border-color:var(--c-gold);box-shadow:0 0 8px rgba(212,168,83,0.15)}
.ch.on{background:linear-gradient(135deg,var(--c-gold),var(--c-amber));color:var(--c-darker);border-color:transparent;font-weight:700;box-shadow:0 2px 8px rgba(212,168,83,0.3)}
.main{flex:1;display:flex;overflow:hidden;position:relative}
.col{display:flex;flex-direction:column;background:rgba(255,255,255,0.06);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px)}
.cl{width:30%;min-width:260px;border-right:1px solid var(--color-border);background:rgba(255,255,255,0.04)}
.cc{width:45%;border-right:1px solid var(--color-border);background:rgba(255,255,255,0.09)}
.cc.wide{width:70%}
.cr{width:25%;min-width:300px;background:rgba(255,255,255,0.03)}
.ct{display:flex;justify-content:space-between;align-items:center;padding:8px 14px;font-size:12px;font-weight:600;color:var(--color-text-secondary);border-bottom:1px solid var(--color-border-light);flex-shrink:0;background:rgba(255,255,255,0.03);backdrop-filter:blur(4px);-webkit-backdrop-filter:blur(4px)}
.cb{flex:1;overflow:auto;padding:12px}
.cb pre{margin:0;white-space:pre-wrap;word-break:break-word;font-family:var(--font-serif);font-size:14px;line-height:1.9;color:var(--color-text)}
.emp{flex:1;display:flex;align-items:center;justify-content:center}
.ye{width:100%;height:100%;border:none;outline:none;resize:none;font-family:var(--font-mono);font-size:13px;line-height:1.6;background:var(--color-bg-alt);color:var(--color-text);padding:12px;border-radius:var(--radius)}
.eb{display:flex;justify-content:flex-end;gap:8px;padding:8px 12px;border-top:1px solid var(--color-border-light);flex-shrink:0}
.yv pre{font-family:var(--font-mono);font-size:13px;line-height:1.6;margin:0}
.yv code{white-space:pre-wrap}

/* YAML chapter chips */
.yaml-chaps{display:flex;gap:4px;padding:6px 10px;overflow-x:auto;flex-shrink:0;border-bottom:1px solid var(--color-border-light)}
.ychip{flex-shrink:0;padding:3px 10px;border:1px solid var(--color-border);border-radius:12px;background:var(--color-surface);font-size:11px;color:var(--color-text-secondary);cursor:pointer;white-space:nowrap;transition:all var(--transition)}
.ychip:hover{color:var(--c-gold);border-color:var(--c-gold)}
.ychip.on{background:var(--c-gold);color:var(--c-darker);border-color:var(--c-gold);font-weight:700}
.ychip small{font-weight:400;opacity:0.7}

/* YAML reveal animation */
@keyframes revealYaml { from { opacity: 0; max-height: 0; } to { opacity: 1; max-height: 9999px; } }

/* Override highlight.js blue → gold */
.yv :deep(.hljs-string){color:#e6c874}
.yv :deep(.hljs-attr){color:#d4a853}
.yv :deep(.hljs-literal){color:#d4a853}
.yv :deep(.hljs-number){color:#e6c874}
.yv :deep(.hljs-title){color:#d4a853}
.yv :deep(.hljs-built_in){color:#c8b896}
.yv :deep(.hljs-type){color:#d4a853}
.yv :deep(.hljs-meta){color:#8b7b65}
.rtt{height:100%;display:flex;flex-direction:column}
.rtt :deep(.el-tabs__header){margin:0;padding:0 8px}
.rtt :deep(.el-tabs__nav-wrap)::after{height:1px;background:var(--color-border-light)}
.rtt :deep(.el-tabs__nav){display:flex;justify-content:space-around}
.rtt :deep(.el-tabs__item){flex:1;text-align:center;font-size:12px;font-weight:500;color:var(--color-text-muted);height:36px;line-height:36px;padding:0 4px;transition:all .2s}
.rtt :deep(.el-tabs__item:hover){color:var(--c-gold)}
.rtt :deep(.el-tabs__item.is-active){color:var(--c-gold);font-weight:700}
.rtt :deep(.el-tabs__active-bar){background:linear-gradient(90deg,var(--c-gold),var(--c-amber));height:2px;border-radius:1px}
.rtt :deep(.el-tabs__content){flex:1;overflow:hidden}
.rtt :deep(.el-tab-pane){height:100%}

/* Chat — polished */
.chat{height:100%;display:flex;flex-direction:column}
.cmsgs{flex:1;overflow:auto;padding:12px 10px}
.chat-welcome{text-align:center;padding:24px 12px}
.cw-avatar{font-size:40px;margin-bottom:8px}
.chat-welcome p{font-size:13px;color:var(--color-text-secondary);margin:0}
.cw-hint{font-size:12px!important;color:var(--color-text-muted)!important;margin-top:6px!important;line-height:1.6}

.msg-row{display:flex;gap:8px;margin-bottom:14px;align-items:flex-start}
.msg-row.user{flex-direction:row-reverse}
.msg-avatar{width:30px;height:30px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:15px;flex-shrink:0}
.msg-avatar.user{background:var(--c-amber)}.msg-avatar.assistant{background:var(--color-surface-hover)}
.msg-body{max-width:85%;display:flex;flex-direction:column}

.bubble{padding:10px 14px;font-size:13px;line-height:1.6;word-break:break-word}
.bubble.user{background:linear-gradient(135deg,var(--c-gold),var(--c-amber));color:var(--c-darker);border-radius:16px 16px 4px 16px}
.bubble.assistant{background:var(--color-bg-alt);color:var(--color-text);border-radius:16px 16px 16px 4px;border:1px solid var(--color-border-light)}
.bubble :deep(pre){background:var(--color-surface);border:1px solid var(--color-border);border-radius:6px;padding:10px;margin:8px 0;font-size:12px;overflow:auto;max-height:200px}
.bubble :deep(code){font-family:var(--font-mono);font-size:12px;background:rgba(0,0,0,0.06);padding:1px 5px;border-radius:3px}
.bubble.user :deep(code){background:rgba(0,0,0,0.15);color:var(--c-darker)}
.bubble :deep(.patch-block){border:2px solid var(--color-success);border-radius:8px;margin:8px 0;overflow:hidden}
.bubble :deep(.patch-label){background:var(--color-success);color:#fff;padding:4px 10px;font-size:11px;font-weight:600}
.bubble :deep(.patch-block pre){margin:0;border:none;border-radius:0;background:rgba(0,0,0,0.03)}
.bubble :deep(h1),.bubble :deep(h2),.bubble :deep(h3),.bubble :deep(h4),.bubble :deep(h5){color:var(--c-gold);margin:8px 0 4px;line-height:1.3}
.bubble :deep(h2){font-size:17px}.bubble :deep(h3){font-size:15px}.bubble :deep(h4){font-size:14px}.bubble :deep(h5){font-size:13px}
.bubble :deep(strong){color:var(--c-gold-light);font-weight:700}
.bubble :deep(ul),.bubble :deep(ol){padding-left:16px;margin:4px 0}
.bubble :deep(li){margin:2px 0;line-height:1.5}
.bubble :deep(hr){border:none;border-top:1px solid var(--color-border);margin:10px 0}
.bubble :deep(p){margin:4px 0}
.bubble.user :deep(h2),.bubble.user :deep(h3),.bubble.user :deep(h4),.bubble.user :deep(strong){color:var(--c-darker)}

/* Typing dots */
.typing-bubble{display:flex;align-items:center;gap:4px;padding:14px 18px}
.dot{width:7px;height:7px;border-radius:50%;background:var(--color-text-muted);animation:dotBounce 1.4s infinite both}
.dot:nth-child(2){animation-delay:0.2s}.dot:nth-child(3){animation-delay:0.4s}
@keyframes dotBounce{0%,80%,100%{transform:scale(0.6);opacity:0.4}40%{transform:scale(1);opacity:1}}

.patch-actions{display:flex;gap:6px;margin-top:6px;padding-left:4px}
.action-feedback{font-size:12px;margin-top:6px;padding:4px 10px;border-radius:20px;display:inline-block}
.action-feedback.applied{color:#7b9b6a;background:rgba(123,155,106,0.1);border:1px solid rgba(123,155,106,0.2)}
.action-feedback.discarded{color:var(--color-text-muted);background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.06)}

.chat-input-bar{display:flex;gap:8px;padding:10px;border-top:1px solid var(--color-border-light);align-items:center;background:var(--color-surface)}
.chat-input-inner{flex:1}
.chat-input-inner :deep(.el-input__wrapper){background:var(--color-bg-alt);border-color:var(--color-border);box-shadow:none}
.chat-input-inner :deep(.el-input__inner){color:var(--color-text)}
.chat-input-inner :deep(.el-input__inner::placeholder){color:var(--color-text-muted)}
.chat-input-bar :deep(.el-button--warning){background:var(--c-gold);border-color:var(--c-gold);color:var(--c-darker);font-weight:700}
.chat-input-bar :deep(.el-button--warning):hover{background:var(--c-gold-light)}

/* Characters */
.chars{padding:8px;overflow:auto;height:100%}
.ccard{background:var(--color-bg-alt);border-radius:var(--radius);margin-bottom:8px;border:1px solid var(--color-border-light);overflow:hidden}
.cimg{width:100%;height:160px;object-fit:cover;cursor:pointer}
.cimgplc{width:100%;height:80px;background:var(--color-bg);display:flex;align-items:center;justify-content:center;font-size:12px;color:var(--color-text-muted);cursor:pointer;border-radius:var(--radius) var(--radius) 0 0}
.cimgplc:hover{background:var(--color-surface-hover)}
.chead{display:flex;justify-content:space-between;align-items:center;padding:6px 10px}
.ccard p{padding:0 10px;font-size:12px;color:var(--color-text-secondary);margin:0 0 4px}
.ctraits{display:flex;gap:4px;flex-wrap:wrap;padding:0 10px 8px}

/* Scene gallery */
.scenes{padding:10px 14px;overflow-y:auto;height:100%}
.scene-gallery-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;font-size:13px;color:var(--color-text-secondary)}
.scene-cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:12px}
.scene-card{background:var(--color-surface);border:1px solid var(--color-border);border-radius:var(--radius-lg);overflow:hidden;transition:all var(--transition)}
.scene-card:hover{border-color:var(--c-gold);box-shadow:0 4px 16px rgba(212,168,83,0.12);transform:translateY(-2px)}
.scene-card-img{position:relative;width:100%;aspect-ratio:16/9;overflow:hidden;cursor:pointer;background:var(--color-bg)}
.scene-card-img img{width:100%;height:100%;object-fit:cover;transition:transform .3s ease}
.scene-card:hover .scene-card-img img{transform:scale(1.05)}
.scene-card-placeholder{width:100%;height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;font-size:24px;color:var(--color-text-muted);border:2px dashed var(--color-border)}
.scene-card-placeholder span:last-child{font-size:12px}
.scene-card-overlay{position:absolute;inset:0;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;opacity:0;transition:opacity .2s ease;color:#fff;font-size:14px;font-weight:600}
.scene-card-img:hover .scene-card-overlay{opacity:1}
.scene-card-info{padding:10px 12px}
.scene-card-num{font-size:10px;color:var(--c-gold);font-weight:700;letter-spacing:1px;text-transform:uppercase;margin-bottom:2px}
.scene-card-title{font-size:13px;font-weight:600;color:var(--color-text);margin-bottom:4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.scene-card-meta{display:flex;gap:8px;font-size:11px;color:var(--color-text-muted);margin-bottom:6px}
.scene-card-actions{display:flex;gap:4px;justify-content:flex-end}

/* Research */
.research{padding:10px}
.research :deep(.el-input__wrapper){background:var(--color-bg-alt);border-color:var(--color-border);box-shadow:none}
.research :deep(.el-input__inner){color:var(--color-text)}
.research :deep(.el-button--primary){background:var(--c-gold);border-color:var(--c-gold);color:var(--c-darker)}
.research :deep(.el-button--primary):hover{background:var(--c-gold-light)}
.sres{margin-top:12px;padding:12px;background:var(--color-bg-alt);border-radius:var(--radius-lg);font-size:13px;line-height:1.7;max-height:400px;overflow:auto;border:1px solid var(--color-border-light)}
.sres :deep(h1),.sres :deep(h2),.sres :deep(h3),.sres :deep(h4){color:var(--c-gold);margin:12px 0 6px}
.sres :deep(strong){color:var(--c-gold-light)}
.sres :deep(code){background:rgba(212,168,83,0.1);padding:2px 6px;border-radius:3px;font-size:12px}
.sres :deep(ul),.sres :deep(ol){padding-left:18px;margin:6px 0}
.sres :deep(li){margin:4px 0}
.sres :deep(hr){border:none;border-top:1px solid var(--color-border);margin:12px 0}

.tgr{position:absolute;right:0;top:50%;transform:translateY(-50%);width:18px;height:40px;border:1px solid var(--color-border);border-right:none;background:var(--color-surface);cursor:pointer;font-size:9px;color:var(--color-text-muted);display:flex;align-items:center;justify-content:center;border-radius:4px 0 0 4px;z-index:10}
.tgr:hover{background:var(--color-surface-hover)}
.vr{display:flex;justify-content:space-between;align-items:center;padding:10px;border-bottom:1px solid var(--color-border-light)}
.vt{display:block;font-size:11px;color:var(--color-text-muted)}

@media (max-width: 768px) {
  .editor{height:auto;min-height:100vh;padding:0 8px 40px}
  .tb{flex-wrap:wrap;height:auto;padding:8px 10px;gap:6px;border-radius:var(--radius) var(--radius) 0 0}
  .tbl h2{font-size:14px}
  .tbr{flex-wrap:wrap}
  .sbar{padding:6px 10px}
  .act{flex-wrap:wrap;gap:8px;padding:6px 10px}
  .actl{flex-wrap:wrap}
  .hint{font-size:10px}
  .chaps{padding:4px 10px}
  .ch{font-size:10px;padding:2px 8px}
  .main{flex-direction:column;overflow:visible;gap:16px;padding:10px 0}
  .cl,.cc,.cr{width:100%!important;min-width:auto!important;max-height:45vh;border-right:none;border-radius:var(--radius-lg);border:1px solid var(--color-border);overflow:hidden;background:rgba(255,255,255,0.06);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px)}
  .cr{max-height:none}
  .toggle-r{display:none}
  .col-title{font-size:11px;padding:5px 10px}
  .col-body{padding:10px}
  .col-body pre{font-size:13px;line-height:1.7}
  .cbb :deep(pre){max-height:140px}
  .chatmsgs{max-height:300px}
  .msg-row.user .msg-body{max-width:90%}
  .bubble{font-size:12px;padding:8px 10px}
  .msg-avatar{width:24px;height:24px;font-size:12px}
  .bottom-bar,.editbar{padding:6px 10px}
}
</style>
