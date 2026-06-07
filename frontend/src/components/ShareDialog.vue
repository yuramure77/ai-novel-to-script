<template>
  <el-dialog
    v-model="visible"
    title="👥 协作管理"
    width="500px"
    :close-on-click-modal="false"
    :append-to-body="true"
  >
    <!-- Add collaborator -->
    <div style="margin-bottom:16px;display:flex;gap:8px">
      <el-input
        v-model="addUser"
        placeholder="输入用户名"
        size="default"
        @keyup.enter="doAdd"
        style="flex:1"
      />
      <el-select v-model="addPerm" size="default" style="width:100px">
        <el-option label="管理员" value="ADMIN" />
        <el-option label="只读" value="READ" />
      </el-select>
      <el-button type="primary" size="default" @click="doAdd" :loading="adding">添加</el-button>
    </div>

    <!-- Collaborator list -->
    <div v-if="collabs.length" style="max-height:280px;overflow-y:auto">
      <div
        v-for="c in collabs"
        :key="c.id"
        style="display:flex;align-items:center;justify-content:space-between;padding:10px 0;border-bottom:1px solid var(--color-border)"
      >
        <div>
          <strong style="color:var(--color-text)">{{ c.username }}</strong>
          <span v-if="c.nickname" style="color:var(--color-text-muted);margin-left:6px;font-size:12px">({{ c.nickname }})</span>
        </div>
        <div style="display:flex;align-items:center;gap:8px">
          <el-select
            :model-value="c.permission"
            @change="(v) => doUpdate(c.id, v)"
            size="small"
            style="width:90px"
          >
            <el-option label="管理员" value="ADMIN" />
            <el-option label="只读" value="READ" />
          </el-select>
          <el-button size="small" type="danger" text @click="doRemove(c.id)">移除</el-button>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无协作者" :image-size="60" />

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listCollaborators, addCollaborator, updatePermission, removeCollaborator } from '@/api/collaborators'

const props = defineProps({ modelValue: Boolean, projectId: Number })
const emit = defineEmits(['update:modelValue'])
const visible = ref(false)
watch(() => props.modelValue, v => { visible.value = v; if (v) load() })
watch(visible, v => emit('update:modelValue', v))

const addUser = ref('')
const addPerm = ref('READ')
const adding = ref(false)
const collabs = ref([])

async function load() {
  try { collabs.value = (await listCollaborators(props.projectId)).data.data || [] }
  catch (e) { ElMessage.error(e.response?.data?.message || '加载协作者失败') }
}

async function doAdd() {
  if (!addUser.value.trim()) return
  adding.value = true
  try {
    await addCollaborator(props.projectId, addUser.value.trim(), addPerm.value)
    ElMessage.success('已添加')
    addUser.value = ''
    load()
  } catch (e) { ElMessage.error(e.response?.data?.message || '添加失败') }
  finally { adding.value = false }
}

async function doUpdate(collabId, perm) {
  try {
    await updatePermission(props.projectId, collabId, perm)
    ElMessage.success('权限已更新')
    load()
  } catch (e) { ElMessage.error(e.response?.data?.message || '更新失败') }
}

async function doRemove(collabId) {
  try {
    await removeCollaborator(props.projectId, collabId)
    ElMessage.success('已移除')
    load()
  } catch (e) { ElMessage.error(e.response?.data?.message || '移除失败') }
}
</script>
