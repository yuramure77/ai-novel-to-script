<template>
  <el-dialog
    v-model="visible"
    title="👥 协作管理"
    width="580px"
    :close-on-click-modal="false"
    :append-to-body="true"
  >
    <!-- Invite link section -->
    <div class="invite-section">
      <div class="invite-label">🔗 邀请链接</div>
      <div class="invite-row">
        <div class="link-display" v-if="inviteLink">
          <code class="link-text">{{ inviteLink }}</code>
        </div>
        <span v-else class="link-placeholder">点击下方按钮生成邀请链接</span>
        <el-button v-if="inviteLink" size="small" type="primary" @click="copyLink" style="flex-shrink:0">📋 复制</el-button>
      </div>
      <div class="invite-actions">
        <el-button size="small" @click="genLink" :loading="genLinkBusy" type="warning" plain>
          {{ inviteLink ? '🔄 重新生成' : '🔗 生成链接' }}
        </el-button>
        <span v-if="inviteLink" class="invite-hint">复制链接发送给他人，打开后自动加入为「只读」</span>
      </div>
    </div>

    <el-divider />

    <!-- Add collaborator by username -->
    <div class="add-row">
      <el-input v-model="addUser" placeholder="输入用户名添加协作者" size="default"
        @keyup.enter="doAdd" style="flex:1" />
      <el-select v-model="addPerm" size="default" style="width:100px">
        <el-option label="管理员" value="ADMIN" />
        <el-option label="只读" value="READ" />
      </el-select>
      <el-button type="primary" size="default" @click="doAdd" :loading="adding">添加</el-button>
    </div>

    <!-- Collaborator list -->
    <div v-if="collabs.length" class="collab-list">
      <div v-for="c in collabs" :key="c.id" class="collab-item">
        <div class="collab-user">
          <span class="collab-name">{{ c.username }}</span>
          <span v-if="c.nickname" class="collab-nick">({{ c.nickname }})</span>
          <span v-if="c.online" class="online-dot" title="在线">🟢</span>
          <span v-else class="offline-dot" title="离线">⚫</span>
        </div>
        <div class="collab-actions">
          <el-select :model-value="c.permission" @change="(v) => doUpdate(c.id, v)" size="small" style="width:90px">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="只读" value="READ" />
          </el-select>
          <el-button size="small" type="danger" text @click="doRemove(c.id)">移除</el-button>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无协作者" :image-size="50" />

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listCollaborators, addCollaborator, updatePermission, removeCollaborator } from '@/api/collaborators'
import api from '@/api/index'

const props = defineProps({ modelValue: Boolean, projectId: Number })
const emit = defineEmits(['update:modelValue'])
const visible = ref(false)
watch(() => props.modelValue, v => { visible.value = v; if (v) load() })
watch(visible, v => emit('update:modelValue', v))

const addUser = ref(''); const addPerm = ref('READ'); const adding = ref(false)
const collabs = ref([])
const inviteLink = ref('')
const genLinkBusy = ref(false)

async function load() {
  try { collabs.value = (await listCollaborators(props.projectId)).data.data || [] }
  catch (e) { ElMessage.error(e.response?.data?.message || '加载协作者失败') }
  try {
    const r = await api.post(`/projects/${props.projectId}/invite-link`)
    const token = r.data.data?.inviteToken
    if (token) inviteLink.value = window.location.origin + '/join?token=' + token
  } catch { /* owner only */ }
}

async function genLink() {
  genLinkBusy.value = true
  try {
    const url = inviteLink.value ? `/projects/${props.projectId}/invite-link/regenerate` : `/projects/${props.projectId}/invite-link`
    const r = inviteLink.value ? await api.post(url) : await api.post(url)
    const token = r.data.data?.inviteToken
    if (token) {
      inviteLink.value = window.location.origin + '/join?token=' + token
      ElMessage.success(inviteLink.value ? '链接已重新生成' : '链接已生成')
    }
  } catch (e) { ElMessage.error(e.response?.data?.message || '操作失败') }
  finally { genLinkBusy.value = false }
}

function copyLink() {
  // Fallback for non-HTTPS or browsers without clipboard API
  const text = inviteLink.value
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(text).then(() => ElMessage.success('已复制'))
      .catch(() => fallbackCopy(text))
  } else {
    fallbackCopy(text)
  }
}
function fallbackCopy(text) {
  const ta = document.createElement('textarea')
  ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0'
  document.body.appendChild(ta); ta.select()
  try { document.execCommand('copy'); ElMessage.success('已复制') }
  catch { ElMessage.error('复制失败，请手动选择链接') }
  finally { document.body.removeChild(ta) }
}

async function doAdd() {
  if (!addUser.value.trim()) return; adding.value = true
  try {
    await addCollaborator(props.projectId, addUser.value.trim(), addPerm.value)
    ElMessage.success('已添加'); addUser.value = ''; load()
  } catch (e) { ElMessage.error(e.response?.data?.message || '添加失败') }
  finally { adding.value = false }
}

async function doUpdate(collabId, perm) {
  try { await updatePermission(props.projectId, collabId, perm); ElMessage.success('权限已更新'); load() }
  catch (e) { ElMessage.error(e.response?.data?.message || '更新失败') }
}

async function doRemove(collabId) {
  try { await removeCollaborator(props.projectId, collabId); ElMessage.success('已移除'); load() }
  catch (e) { ElMessage.error(e.response?.data?.message || '移除失败') }
}
</script>

<style scoped>
.invite-section { margin-bottom: 4px }
.invite-label { font-size: 13px; font-weight: 600; color: var(--color-text); margin-bottom: 8px }
.invite-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px }
.link-display { flex: 1; overflow-x: auto; white-space: nowrap; background: var(--color-bg-alt, rgba(255,255,255,0.04)); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 8px 10px }
.link-text { font-family: var(--font-mono); font-size: 11px; color: var(--c-gold); word-break: keep-all }
.link-placeholder { font-size: 12px; color: var(--color-text-muted) }
.invite-actions { display: flex; align-items: center; gap: 10px }
.invite-hint { font-size: 11px; color: var(--color-text-muted) }
.add-row { display: flex; gap: 8px; margin-bottom: 16px }
.collab-list { max-height: 240px; overflow-y: auto }
.collab-item { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid var(--color-border) }
.collab-user { display: flex; align-items: center; gap: 6px }
.collab-name { font-weight: 600; color: var(--color-text); font-size: 13px }
.collab-nick { font-size: 11px; color: var(--color-text-muted) }
.online-dot, .offline-dot { font-size: 8px }
.offline-dot { opacity: 0.3 }
.collab-actions { display: flex; align-items: center; gap: 8px }

/* Frosted glass dialog — match create project style */
:deep(.el-overlay-dialog) {
  position: fixed !important; top: 0; left: 0; right: 0; bottom: 0;
  display: flex !important; align-items: center !important; justify-content: center !important;
  background: var(--overlay-glass-bg, rgba(0,0,0,0.12)) !important;
  backdrop-filter: var(--overlay-glass-blur, blur(4px));
  -webkit-backdrop-filter: var(--overlay-glass-blur, blur(4px));
}
:deep(.el-dialog) {
  background: var(--dialog-glass-bg, rgba(20,16,10,0.3)) !important;
  backdrop-filter: var(--dialog-glass-blur, blur(50px) saturate(150%));
  -webkit-backdrop-filter: var(--dialog-glass-blur, blur(50px) saturate(150%));
  border: 0.5px solid var(--dialog-glass-border, rgba(255,255,255,0.08)) !important;
  border-radius: 20px !important;
  box-shadow: var(--dialog-glass-shadow) !important;
  overflow: hidden;
}
:deep(.el-dialog__header) {
  background: transparent !important;
  border-bottom: 0.5px solid var(--dialog-glass-border, rgba(255,255,255,0.06));
}
:deep(.el-dialog__footer) {
  background: transparent !important;
  border-top: 0.5px solid var(--dialog-glass-border, rgba(255,255,255,0.06));
}
:deep(.el-input__wrapper) {
  background: var(--color-bg-alt, rgba(255,255,255,0.04));
  border-color: var(--color-border, rgba(255,255,255,0.1));
  box-shadow: none;
}
:deep(.el-select .el-input__wrapper) {
  background: var(--color-bg-alt, rgba(255,255,255,0.04));
}
</style>
