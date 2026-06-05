<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="brand-icon">🎬</div>
        <h1>小说转剧本</h1>
        <p>AI 辅助剧本改编工具</p>
      </div>

      <el-tabs v-model="tab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form :model="lf" :rules="lr" ref="lfr" @keyup.enter="login">
            <el-form-item prop="username"><el-input v-model="lf.username" placeholder="用户名" size="large" /></el-form-item>
            <el-form-item prop="password"><el-input v-model="lf.password" type="password" placeholder="密码" show-password size="large" /></el-form-item>
            <el-button type="primary" size="large" :loading="loading" @click="login" style="width:100%">登录</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="rf" :rules="rr" ref="rfr" @keyup.enter="register">
            <el-form-item prop="username"><el-input v-model="rf.username" placeholder="用户名（3-50位" size="large" /></el-form-item>
            <el-form-item prop="nickname"><el-input v-model="rf.nickname" placeholder="昵称（可选）" size="large" /></el-form-item>
            <el-form-item prop="password"><el-input v-model="rf.password" type="password" placeholder="密码（至少6位）" show-password size="large" /></el-form-item>
            <el-button type="primary" size="large" :loading="loading" @click="register" style="width:100%">注册</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as authApi from '@/api/auth'

const router = useRouter()
const tab = ref('login')
const loading = ref(false)
const lfr = ref(null); const rfr = ref(null)

const lf = reactive({ username: '', password: '' })
const rf = reactive({ username: '', password: '', nickname: '' })

const lr = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const rr = {
  username: [{ required: true, min: 3, max: 50, message: '用户名3-50位', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少6位', trigger: 'blur' }]
}

async function login() {
  if (!await lfr.value.validate().catch(() => false)) return
  loading.value = true
  try {
    const r = await authApi.login(lf.username, lf.password)
    const d = r.data.data
    localStorage.setItem('token', d.token)
    localStorage.setItem('nickname', d.nickname || lf.username)
    router.push('/')
  } catch (e) { ElMessage.error(e.response?.data?.message || '登录失败') }
  finally { loading.value = false }
}

async function register() {
  if (!await rfr.value.validate().catch(() => false)) return
  loading.value = true
  try {
    const r = await authApi.register(rf.username, rf.password, rf.nickname)
    const d = r.data.data
    localStorage.setItem('token', d.token)
    localStorage.setItem('nickname', d.nickname || rf.username)
    router.push('/')
  } catch (e) { ElMessage.error(e.response?.data?.message || '注册失败') }
  finally { loading.value = false }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea15, #764ba215, #f1f5f9);
}
.login-card {
  width: 420px;
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  padding: 40px 36px;
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--color-border);
}
.brand { text-align: center; margin-bottom: 24px; }
.brand-icon { font-size: 40px; margin-bottom: 8px; }
.brand h1 { font-size: 22px; font-weight: 700; color: var(--color-text); margin: 0 0 4px; }
.brand p { font-size: 14px; color: var(--color-text-muted); margin: 0; }
</style>
