<template>
  <div class="login-page">
    <div class="bg-shapes">
      <div class="shape shape-1"></div>
      <div class="shape shape-2"></div>
      <div class="shape shape-3"></div>
    </div>

    <div class="login-wrapper">
      <!-- Brand side -->
      <div class="brand-panel">
        <div class="brand-content">
          <div class="brand-icon">
            <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
              <rect width="64" height="64" rx="16" fill="url(#grad)" />
              <path d="M20 44V20l12 8-12 8z" fill="white" opacity="0.9" />
              <path d="M32 44V28l12 8v16L32 44z" fill="white" />
              <defs>
                <linearGradient id="grad" x1="0" y1="0" x2="64" y2="64">
                  <stop stop-color="#6C5CE7" />
                  <stop offset="1" stop-color="#a29bfe" />
                </linearGradient>
              </defs>
            </svg>
          </div>
          <h1>小说转剧本</h1>
          <p class="brand-desc">AI 驱动的剧本改编助手<br/>让每一部好故事都能走向银幕</p>
          <div class="feature-list">
            <div class="feature-item">
              <span class="feature-icon">📖</span> 智能分章
            </div>
            <div class="feature-item">
              <span class="feature-icon">🤖</span> AI 角色提取
            </div>
            <div class="feature-item">
              <span class="feature-icon">🎬</span> 结构化剧本
            </div>
            <div class="feature-item">
              <span class="feature-icon">📝</span> YAML 输出
            </div>
          </div>
        </div>
      </div>

      <!-- Form side -->
      <div class="form-panel">
        <div class="form-card">
          <h2>{{ activeTab === 'login' ? '欢迎回来' : '创建账户' }}</h2>
          <p class="form-subtitle">
            {{ activeTab === 'login' ? '登录你的账户继续创作' : '注册后即可开始改编剧本' }}
          </p>

          <div class="tab-switch">
            <button
              :class="['tab-btn', { active: activeTab === 'login' }]"
              @click="activeTab = 'login'"
            >登录</button>
            <button
              :class="['tab-btn', { active: activeTab === 'register' }]"
              @click="activeTab = 'register'"
            >注册</button>
          </div>

          <!-- Login form -->
          <form v-if="activeTab === 'login'" @submit.prevent="handleLogin" class="auth-form">
            <div class="input-group">
              <label>用户名</label>
              <div class="input-wrapper">
                <el-icon><User /></el-icon>
                <input v-model="loginForm.username" type="text" placeholder="请输入用户名" />
              </div>
            </div>
            <div class="input-group">
              <label>密码</label>
              <div class="input-wrapper">
                <el-icon><Lock /></el-icon>
                <input v-model="loginForm.password" :type="showPwd ? 'text' : 'password'" placeholder="请输入密码" @keyup.enter="handleLogin" />
                <button type="button" class="toggle-pwd" @click="showPwd = !showPwd">
                  <el-icon><View v-if="!showPwd" /><Hide v-else /></el-icon>
                </button>
              </div>
            </div>
            <button type="submit" class="submit-btn" :disabled="loading">
              {{ loading ? '登录中...' : '登 录' }}
            </button>
          </form>

          <!-- Register form -->
          <form v-else @submit.prevent="handleRegister" class="auth-form">
            <div class="input-group">
              <label>用户名</label>
              <div class="input-wrapper">
                <el-icon><User /></el-icon>
                <input v-model="registerForm.username" type="text" placeholder="3-50位字符" />
              </div>
            </div>
            <div class="input-group">
              <label>昵称 <span class="optional">(可选)</span></label>
              <div class="input-wrapper">
                <el-icon><EditPen /></el-icon>
                <input v-model="registerForm.nickname" type="text" placeholder="如何称呼你" />
              </div>
            </div>
            <div class="input-group">
              <label>密码</label>
              <div class="input-wrapper">
                <el-icon><Lock /></el-icon>
                <input v-model="registerForm.password" :type="showPwd ? 'text' : 'password'" placeholder="至少6位" @keyup.enter="handleRegister" />
                <button type="button" class="toggle-pwd" @click="showPwd = !showPwd">
                  <el-icon><View v-if="!showPwd" /><Hide v-else /></el-icon>
                </button>
              </div>
            </div>
            <button type="submit" class="submit-btn register-btn" :disabled="loading">
              {{ loading ? '注册中...' : '注 册' }}
            </button>
          </form>

          <p class="error-msg" v-if="errorMsg">{{ errorMsg }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { login, register } from '@/api/auth'

const router = useRouter()
const activeTab = ref('login')
const showPwd = ref(false)
const loading = ref(false)
const errorMsg = ref('')

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', nickname: '' })

function validate() {
  errorMsg.value = ''
  if (activeTab.value === 'login') {
    if (!loginForm.username) { errorMsg.value = '请输入用户名'; return false }
    if (!loginForm.password) { errorMsg.value = '请输入密码'; return false }
  } else {
    if (!registerForm.username || registerForm.username.length < 3) { errorMsg.value = '用户名至少3位'; return false }
    if (!registerForm.password || registerForm.password.length < 6) { errorMsg.value = '密码至少6位'; return false }
  }
  return true
}

async function handleLogin() {
  if (!validate()) return
  loading.value = true
  try {
    const res = await login(loginForm.username, loginForm.password)
    const { token, nickname } = res.data.data
    localStorage.setItem('token', token)
    localStorage.setItem('nickname', nickname || loginForm.username)
    router.push('/')
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '用户名或密码错误'
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!validate()) return
  loading.value = true
  try {
    const res = await register(registerForm.username, registerForm.password, registerForm.nickname)
    const { token, nickname } = res.data.data
    localStorage.setItem('token', token)
    localStorage.setItem('nickname', nickname || registerForm.username)
    router.push('/')
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0c0c1d 0%, #1a1a3e 50%, #0d0d2b 100%);
  overflow: hidden;
  position: relative;
}

/* Background shapes */
.bg-shapes { position: absolute; inset: 0; pointer-events: none; }
.shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.3;
}
.shape-1 { width: 400px; height: 400px; background: #6C5CE7; top: -100px; right: -100px; }
.shape-2 { width: 300px; height: 300px; background: #00B894; bottom: -50px; left: -50px; }
.shape-3 { width: 200px; height: 200px; background: #FD79A8; top: 50%; left: 60%; }

.login-wrapper {
  display: flex;
  width: 960px;
  min-height: 560px;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 25px 80px rgba(0,0,0,0.4);
  z-index: 1;
}

/* Brand panel */
.brand-panel {
  width: 420px;
  background: linear-gradient(135deg, #6C5CE7 0%, #4834d4 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}
.brand-content { text-align: center; color: white; }
.brand-icon { margin-bottom: 20px; }
.brand-content h1 {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px;
  letter-spacing: 2px;
}
.brand-desc {
  font-size: 14px;
  opacity: 0.85;
  line-height: 1.8;
  margin-bottom: 32px;
}
.feature-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  text-align: left;
}
.feature-item {
  font-size: 13px;
  opacity: 0.9;
  display: flex;
  align-items: center;
  gap: 6px;
}
.feature-icon { font-size: 16px; }

/* Form panel */
.form-panel {
  flex: 1;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}
.form-card { width: 100%; max-width: 340px; }
.form-card h2 { font-size: 24px; color: #2d3436; margin: 0 0 4px; }
.form-subtitle { color: #636e72; font-size: 14px; margin-bottom: 24px; }

.tab-switch {
  display: flex;
  background: #f1f2f6;
  border-radius: 10px;
  padding: 4px;
  margin-bottom: 24px;
}
.tab-btn {
  flex: 1;
  padding: 8px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #636e72;
  cursor: pointer;
  transition: all 0.2s;
}
.tab-btn.active {
  background: white;
  color: #6C5CE7;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.auth-form { display: flex; flex-direction: column; gap: 16px; }
.input-group { display: flex; flex-direction: column; gap: 6px; }
.input-group label { font-size: 13px; font-weight: 600; color: #2d3436; }
.optional { color: #b2bec3; font-weight: 400; font-size: 12px; }

.input-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 2px solid #e2e8f0;
  border-radius: 10px;
  padding: 0 14px;
  transition: border-color 0.2s;
}
.input-wrapper:focus-within { border-color: #6C5CE7; }
.input-wrapper .el-icon { color: #a0aec0; flex-shrink: 0; }
.input-wrapper input {
  flex: 1;
  border: none;
  outline: none;
  padding: 12px 0;
  font-size: 14px;
  color: #2d3436;
  background: transparent;
}
.input-wrapper input::placeholder { color: #b2bec3; }
.toggle-pwd {
  background: none;
  border: none;
  cursor: pointer;
  color: #a0aec0;
  padding: 0;
  display: flex;
}
.toggle-pwd:hover { color: #6C5CE7; }

.submit-btn {
  width: 100%;
  padding: 14px;
  border: none;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 700;
  color: white;
  cursor: pointer;
  transition: all 0.2s;
  margin-top: 8px;
  background: linear-gradient(135deg, #6C5CE7, #4834d4);
}
.submit-btn:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 8px 25px rgba(108,92,231,0.4); }
.submit-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.submit-btn.register-btn { background: linear-gradient(135deg, #00B894, #00a381); }
.submit-btn.register-btn:hover:not(:disabled) { box-shadow: 0 8px 25px rgba(0,184,148,0.4); }

.error-msg {
  color: #E17055;
  font-size: 13px;
  text-align: center;
  margin-top: 12px;
}

@media (max-width: 768px) {
  .login-wrapper { flex-direction: column; width: 90%; }
  .brand-panel { width: 100%; padding: 32px; }
  .form-panel { padding: 32px; }
}
</style>
