<template>
  <el-dialog
    v-model="visible"
    title="👥 协作管理"
    width="520px"
    :close-on-click-modal="false"
    :append-to-body="true"
  >
    <!-- Invite link section -->
    <div class="invite-section">
      <div class="invite-label">🔗 邀请链接</div>
      <div class="invite-row">
        <el-input :model-value="inviteLink" readonly size="default" placeholder="点击生成邀请链接...">
          <template #append>
            <el-button v-if="inviteLink" @click="copyLink" type="primary" size="default">📋 复制</el-button>
          </template>
        </el-input>
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
    if (token) inviteLink.value = window.location.origin + '/#/join?token=' + token
  } catch { /* owner only */ }
}

async function genLink() {
  genLinkBusy.value = true
  try {
    const url = inviteLink.value ? `/projects/${props.projectId}/invite-link/regenerate` : `/projects/${props.projectId}/invite-link`
    const r = inviteLink.value ? await api.post(url) : await api.post(url)
    const token = r.data.data?.inviteToken
    if (token) {
      inviteLink.value = window.location.origin + '/#/join?token=' + token
      ElMessage.success(inviteLink.value ? '链接已重新生成' : '链接已生成')
    }
  } catch (e) { ElMessage.error(e.response?.data?.message || '操作失败') }
  finally { genLinkBusy.value = false }
}

function copyLink() {
  navigator.clipboard.writeText(inviteLink.value).then(() => ElMessage.success('已复制到剪贴板'))
    .catch(() => ElMessage.error('复制失败'))
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
.invite-row { margin-bottom: 6px }
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
</style>
