import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
  { path: '/projects', name: 'ProjectList', component: () => import('@/views/ProjectList.vue'), meta: { requiresAuth: true } },
  { path: '/project/:id', name: 'ScriptEditor', component: () => import('@/views/ScriptEditor.vue'), meta: { requiresAuth: true } },
  { path: '/', name: 'Home', component: () => import('@/views/Home.vue') }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) next('/login')
  else if (to.path === '/login' && token) next('/projects')
  else next()
})

export default router
