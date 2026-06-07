<template>
  <div class="join-page">
    <div class="join-card">
      <div v-if="loading" class="join-status">
        <span class="join-spinner">⏳</span>
        <h2>正在加入项目...</h2>
      </div>
      <div v-else-if="success" class="join-status">
        <span class="join-icon">✅</span>
        <h2>已加入「{{ projectTitle }}」</h2>
        <p>权限：只读</p>
        <el-button type="warning" @click="$router.push('/project/'+projectId)">进入项目</el-button>
      </div>
      <div v-else class="join-status">
        <span class="join-icon">❌</span>
        <h2>加入失败</h2>
        <p>{{ error }}</p>
        <el-button @click="$router.push('/projects')">返回项目列表</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api/index'

const route = useRoute()
const loading = ref(true); const success = ref(false); const error = ref('')
const projectId = ref(null); const projectTitle = ref('')

onMounted(async () => {
  const token = route.query.token
  if (!token) { error.value = '缺少邀请令牌'; loading.value = false; return }
  try {
    const r = await api.post('/collaborations/join?token=' + encodeURIComponent(token))
    const d = r.data.data
    projectId.value = d.projectId
    projectTitle.value = d.title
    success.value = true
  } catch (e) {
    error.value = e.response?.data?.message || '邀请链接无效或已失效'
  } finally { loading.value = false }
})
</script>

<style scoped>
.join-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--color-bg) }
.join-card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 40px 60px; text-align: center; max-width: 400px }
.join-status h2 { color: var(--color-text); font-size: 18px; margin: 12px 0 4px }
.join-status p { color: var(--color-text-muted); font-size: 13px; margin: 4px 0 16px }
.join-icon, .join-spinner { font-size: 40px }
</style>
