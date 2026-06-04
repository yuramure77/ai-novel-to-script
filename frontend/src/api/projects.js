import api from './index'

export function listProjects() {
  return api.get('/projects')
}

export function getProject(id) {
  return api.get(`/projects/${id}`)
}

export function createProject(title, originalText) {
  return api.post('/projects', { title, originalText })
}

export function deleteProject(id) {
  return api.delete(`/projects/${id}`)
}

export function splitChapters(id) {
  return api.post(`/projects/${id}/split`)
}

export function generateScript(id) {
  return api.post(`/projects/${id}/generate`)
}
