<template>
  <div class="login-page">
    <div class="spotlight-bg"></div>
    <div class="film-strip left"></div>
    <div class="film-strip right"></div>

    <div class="login-card">
      <div class="brand">
        <div class="clapper">🎬</div>
        <h1>剧本工坊</h1>
        <p class="slogan">从文字到银幕，只差一个回车</p>
      </div>

      <el-tabs v-model="tab" stretch class="tabs">
        <el-tab-pane label="登录" name="login">
          <el-form :model="lf" :rules="lr" ref="lfr" @keyup.enter="login">
            <el-form-item prop="username"><el-input v-model="lf.username" placeholder="用户名" size="large" /></el-form-item>
            <el-form-item prop="password"><el-input v-model="lf.password" type="password" placeholder="密码" show-password size="large" /></el-form-item>
            <el-button type="warning" size="large" :loading="loading" @click="login" style="width:100%;font-weight:700">登录</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="rf" :rules="rr" ref="rfr" @keyup.enter="register">
            <el-form-item prop="username"><el-input v-model="rf.username" placeholder="用户名" size="large" /></el-form-item>
            <el-form-item prop="nickname"><el-input v-model="rf.nickname" placeholder="昵称（可选）" size="large" /></el-form-item>
            <el-form-item prop="password"><el-input v-model="rf.password" type="password" placeholder="密码" show-password size="large" /></el-form-item>
            <el-button type="warning" size="large" :loading="loading" @click="register" style="width:100%;font-weight:700">注册</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <div class="back-home">
        <router-link to="/">← 返回首页</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as authApi from '@/api/auth'

const router = useRouter()
const tab = ref('login'); const loading = ref(false)
const lfr = ref(null); const rfr = ref(null)
const lf = reactive({ username: '', password: '' })
const rf = reactive({ username: '', password: '', nickname: '' })
const lr = { username: [{ required: true, message: '请输入用户名', trigger: 'blur' }], password: [{ required: true, message: '请输入密码', trigger: 'blur' }] }
const rr = { username: [{ required: true, min: 3, max: 50, message: '用户名3-50位', trigger: 'blur' }], password: [{ required: true, min: 6, message: '密码至少6位', trigger: 'blur' }] }

async function login() {
  if (!await lfr.value.validate().catch(() => false)) return; loading.value = true
  try { const r = await authApi.login(lf.username, lf.password); const d = r.data.data; localStorage.setItem('token', d.token); localStorage.setItem('nickname', d.nickname || lf.username); router.push('/projects') }
  catch(e) { ElMessage.error(e.response?.data?.message || '登录失败') } finally { loading.value = false }
}
async function register() {
  if (!await rfr.value.validate().catch(() => false)) return; loading.value = true
  try { const r = await authApi.register(rf.username, rf.password, rf.nickname); const d = r.data.data; localStorage.setItem('token', d.token); localStorage.setItem('nickname', d.nickname || rf.username); router.push('/projects') }
  catch(e) { ElMessage.error(e.response?.data?.message || '注册失败') } finally { loading.value = false }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: radial-gradient(ellipse at 50% 30%, #2a1f10 0%, #1a1410 40%, #0d0a06 100%);
  position: relative; overflow: hidden;
}
.spotlight-bg {
  position: absolute; top: -300px; left: 50%; transform: translateX(-50%);
  width: 700px; height: 700px;
  background: radial-gradient(circle, rgba(212,168,83,0.06) 0%, transparent 70%);
  animation: spotlight 4s ease-in-out infinite alternate; pointer-events: none;
}
.film-strip {
  position: fixed; top: 0; bottom: 0; width: 20px; z-index: 100;
  background: repeating-linear-gradient(to bottom, transparent, transparent 16px, #0d0a06 16px, #0d0a06 20px);
  pointer-events: none;
}
.film-strip.left { left: 0; border-right: 1px solid #2a1f10; }
.film-strip.right { right: 0; border-left: 1px solid #2a1f10; }

.login-card {
  width: 440px; background: var(--color-surface); border: 1px solid var(--color-border);
  border-radius: var(--radius-xl); padding: 40px 36px; box-shadow: var(--shadow-lg);
  position: relative; z-index: 1;
}
.brand { text-align: center; margin-bottom: 24px; }
.clapper { font-size: 48px; margin-bottom: 8px; }
.brand h1 { font-size: 26px; font-weight: 800; color: var(--c-gold); letter-spacing: 6px; margin: 0 0 6px; font-family: var(--font-serif); }
.slogan { font-size: 14px; color: var(--c-gold-light); font-style: italic; letter-spacing: 2px; margin: 0; }
.tabs { margin-top: 8px; }
.back-home { text-align: center; margin-top: 20px; }
.back-home a { color: var(--color-text-muted); font-size: 12px; text-decoration: none; }
.back-home a:hover { color: var(--c-gold); }

@media (max-width: 768px) {
  .login-card { width: 92%; padding: 28px 20px; margin: 16px; }
  .brand h1 { font-size: 22px; }
  .clapper { font-size: 36px; }
  .film-strip { display: none; }
}
</style>
