<template>
  <div class="home film-grain">
    <!-- Film perforations -->
    <div class="film-strip left"></div>
    <div class="film-strip right"></div>

    <!-- Spotlight overlay -->
    <div class="spotlight"></div>

    <!-- Hero section -->
    <section class="hero parallax">
      <div class="hero-content">
        <div class="clapper">🎬</div>
        <h1 class="title">剧本工坊</h1>
        <p class="slogan">从文字到银幕，只差一个回车</p>
        <p class="hero-desc">
          粘贴小说 → AI 自动分章提取角色 → 实时生成结构化剧本<br/>
          混元 AI 生成角色形象与场景图 · 邀请链接多人协作
        </p>
        <div class="hero-actions">
          <button class="btn-primary" @click="startCreating">
            <span>🎥</span> 开始创作
          </button>
          <button class="btn-secondary" @click="tryDemo">
            <span>✨</span> 快速体验
          </button>
        </div>
        <div class="format-badges">
          <span class="f-badge">.txt</span>
          <span class="f-badge">.epub</span>
          <span class="f-badge">.docx</span>
          <span class="f-badge">📋 粘贴</span>
        </div>
        <p class="format-hint">支持 TXT / EPUB / DOCX 文件上传或直接粘贴原文</p>
        <div class="hero-stats">
          <div class="stat"><strong>7级</strong><span>智能分章</span></div>
          <div class="stat"><strong>SSE</strong><span>流式生成</span></div>
          <div class="stat"><strong>断点</strong><span>任务续传</span></div>
          <div class="stat"><strong>4格式</strong><span>多端导出</span></div>
        </div>
      </div>
    </section>

    <!-- Feature Grid -->
    <section class="features">
      <h2>核心功能</h2>
      <div class="feature-grid">
        <div class="feat-card">
          <div class="feat-icon">📖</div>
          <h3>7 级智能分章</h3>
          <p>中文·英文·罗马数字·日文·编号·分隔线·段落智能回退，覆盖中日英小说格式</p>
        </div>
        <div class="feat-card">
          <div class="feat-icon">🤖</div>
          <h3>AI 剧本生成</h3>
          <p>DeepSeek 大模型逐章分析，自动提取角色性格、场景 Beats、对白与动作指导</p>
        </div>
        <div class="feat-card">
          <div class="feat-icon">⚡</div>
          <h3>断点续传</h3>
          <p>GenerationPlan 任务制持久化，中断后自动从断点续写，已完成章节秒级恢复</p>
        </div>
        <div class="feat-card">
          <div class="feat-icon">🖼️</div>
          <h3>AI 角色生图</h3>
          <p>DeepSeek 造型师 + 混元 hy-image-lite，年龄性别智能识别，反同质化提示词</p>
        </div>
        <div class="feat-card">
          <div class="feat-icon">🎥</div>
          <h3>场景图渐进生成</h3>
          <p>剧本生成同时后台生成场景图，SSE 实时推送，完成即出现在画廊</p>
        </div>
        <div class="feat-card">
          <div class="feat-icon">👥</div>
          <h3>多人协作</h3>
          <p>邀请链接一键分享，管理员/只读权限，在线用户实时感知，三层安全防护</p>
        </div>
        <div class="feat-card">
          <div class="feat-icon">📄</div>
          <h3>多格式导出</h3>
          <p>YAML 结构化 · Markdown 阅读 · Fountain 剧本 · TXT 纯文本，一键下载</p>
        </div>
        <div class="feat-card">
          <div class="feat-icon">🎨</div>
          <h3>沉浸式体验</h3>
          <p>暗色/亮色双主题 · 毛玻璃弹窗 · 可拖拽面板 · 全局快捷键 · 图片版本回滚</p>
        </div>
      </div>
    </section>

    <!-- Footer -->
    <footer class="footer">
      <p>AI 小说转剧本工具 · XEngineer 新工科计划</p>
    </footer>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createProject } from '@/api/projects'

const router = useRouter()
const token = localStorage.getItem('token')

async function startCreating() {
  if (!token) { router.push('/login'); return }
  router.push('/projects')
}

async function tryDemo() {
  if (!token) { router.push('/login'); return }
  try {
    const sample = `第一章 长安夜雨

大唐天宝年间，长安城笼罩在连绵的秋雨之中。

李云站在城东一间破旧的客栈窗前，望着雨幕中朦胧的街灯，目光深沉。

"客官，您的酒。"伙计推门进来，把一壶浊酒放在桌上。

李云没有回头，只是低声道："十年了...该来的终究要来。"\n\n第二章 柳巷旧事\n\n柳巷的第三棵老槐树下，一座灰瓦白墙的老宅静静矗立在雨中。李云推开虚掩的木门，院子里杂草丛生。正屋里亮着一盏油灯，床上躺着一个瘦弱的妇人。\n\n第三章 夜袭\n\n三道黑影从窗外翻入，手中长剑直取床上老妇！李云怒喝一声，青锋剑化作一道青光，将三道剑影齐齐架住。他认出这是黑风寨的刀法——江湖上最臭名昭著的杀手组织。`

    const r = await createProject('快速体验 - 长安旧事', sample)
    router.push(`/project/${r.data.data.id}`)
  } catch (e) {
    ElMessage.error('创建失败，请先登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.home {
  min-height: 100vh;
  background: radial-gradient(ellipse at 50% 30%, #2a1f10 0%, #1a1410 40%, #0d0a06 100%);
  position: relative;
  overflow: hidden;
}

/* Film strip perforations */
.film-strip {
  position: fixed; top: 0; bottom: 0; width: 24px; z-index: 100;
  background: repeating-linear-gradient(
    to bottom,
    transparent, transparent 18px,
    var(--c-darker) 18px, var(--c-darker) 24px
  );
  pointer-events: none;
}
.film-strip.left { left: 0; border-right: 1px solid #2a1f10; }
.film-strip.right { right: 0; border-left: 1px solid #2a1f10; }

/* Spotlight */
.spotlight {
  position: absolute;
  top: -200px; left: 50%; transform: translateX(-50%);
  width: 600px; height: 600px;
  background: radial-gradient(circle, rgba(212,168,83,0.08) 0%, transparent 70%);
  animation: spotlight 3s ease-in-out infinite alternate;
  pointer-events: none;
}

/* Hero */
.hero {
  display: flex; align-items: center; justify-content: center;
  min-height: 100vh; text-align: center; padding: 0 40px;
  position: relative; z-index: 1;
}
.hero-content {
  animation: fadeInUp 0.4s ease-out;
}
.clapper { font-size: 64px; margin-bottom: 16px; filter: drop-shadow(0 0 20px rgba(212,168,83,0.3)); }
.title {
  font-size: 56px; font-weight: 800; color: var(--c-gold);
  letter-spacing: 8px; margin: 0 0 12px;
  text-shadow: 0 0 60px rgba(212,168,83,0.3);
  font-family: var(--font-serif);
}
.slogan {
  font-size: 20px; color: var(--c-gold-light);
  font-style: italic; letter-spacing: 4px; margin: 0 0 24px;
}
.hero-desc {
  font-size: 15px; color: var(--color-text-secondary);
  line-height: 1.8; margin: 0 0 40px;
}

/* Buttons */
.hero-actions {
  display: flex; gap: 16px; justify-content: center; margin-bottom: 48px;
}
.btn-primary, .btn-secondary {
  padding: 16px 40px; border: none; border-radius: var(--radius-lg);
  font-size: 17px; font-weight: 700; cursor: pointer; transition: all var(--transition);
  display: flex; align-items: center; gap: 8px; font-family: inherit;
}
.btn-primary {
  background: linear-gradient(135deg, var(--c-gold), var(--c-amber));
  color: var(--c-darker); box-shadow: 0 4px 24px rgba(212,168,83,0.3);
}
.btn-primary:hover { transform: translateY(-2px); box-shadow: 0 8px 32px rgba(212,168,83,0.5); }
.btn-secondary {
  background: transparent; color: var(--c-gold);
  border: 2px solid var(--c-gold); box-shadow: 0 0 12px rgba(212,168,83,0.08);
}
.btn-secondary:hover { background: rgba(212,168,83,0.08); transform: translateY(-2px); }

/* Format badges */
.format-badges { display: flex; gap: 12px; justify-content: center; margin-bottom: 12px; }
.f-badge {
  padding: 8px 20px; border: 2px solid var(--c-gold); border-radius: 24px;
  color: var(--c-gold-light); font-size: 15px; font-weight: 700;
  font-family: var(--font-mono); letter-spacing: 1px;
  background: rgba(212,168,83,0.08);
}
.f-badge:hover { background: rgba(212,168,83,0.15); transform: translateY(-1px); transition: all var(--transition); }
.format-hint { font-size: 13px; color: var(--color-text-muted); margin-bottom: 32px; }

/* Stats */
.hero-stats {
  display: flex; gap: 48px; justify-content: center;
}
.stat { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.stat strong { font-size: 28px; color: var(--c-gold); font-family: var(--font-serif); }
.stat span { font-size: 12px; color: var(--color-text-muted); letter-spacing: 1px; }

/* Features */
.features {
  padding: 80px 40px 80px; text-align: center; position: relative; z-index: 1;
}
.features h2 {
  font-size: 28px; color: var(--c-gold); margin-bottom: 48px;
  letter-spacing: 4px; font-family: var(--font-serif);
}
.feature-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px; max-width: 1100px; margin: 0 auto;
}
.feat-card {
  background: var(--color-surface); border: 1px solid var(--color-border);
  border-radius: var(--radius-lg); padding: 32px 24px;
  text-align: left; transition: all var(--transition);
}
.feat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-gold); border-color: var(--c-gold);
}
.feat-card .feat-icon { font-size: 40px; margin-bottom: 12px; display: block; }
.feat-card h3 { font-size: 16px; color: var(--c-gold); margin: 0 0 8px; font-weight: 700; }
.feat-card p { font-size: 13px; color: var(--color-text-muted); line-height: 1.7; margin: 0; }

/* Footer */
.footer {
  text-align: center; padding: 24px;
  font-size: 12px; color: var(--color-text-muted);
  border-top: 1px solid var(--color-border);
}

@media (max-width: 800px) {
  .title { font-size: 32px; letter-spacing: 4px; }
  .slogan { font-size: 16px; letter-spacing: 2px; }
  .hero { min-height: auto; padding: 60px 20px 40px; }
  .hero-desc { font-size: 13px; }
  .hero-actions { flex-direction: column; align-items: center; }
  .btn-primary, .btn-secondary { width: 80%; justify-content: center; padding: 14px 20px; font-size: 15px; }
  .hero-stats { gap: 20px; flex-wrap: wrap; }
  .stat strong { font-size: 22px; }
  .format-badges { flex-wrap: wrap; gap: 8px; }
  .f-badge { padding: 6px 14px; font-size: 13px; }
  .format-hint { font-size: 11px; }
  .features { padding: 40px 20px; }
  .features h2 { font-size: 22px; }
  .feature-grid { grid-template-columns: 1fr; gap: 12px; }
  .feat-card { padding: 24px 20px; }
  .film-strip { display: none; }
  .spotlight { width: 300px; height: 300px; top: -100px; }
}
</style>
