import api from './index'

export function listCollaborators(projectId) {
  return api.get(`/projects/${projectId}/collaborators`)
}

export function addCollaborator(projectId, username, permission) {
  return api.post(`/projects/${projectId}/collaborators`, { username, permission })
}

export function updatePermission(projectId, collabId, permission) {
  return api.put(`/projects/${projectId}/collaborators/${collabId}`, { permission })
}

export function removeCollaborator(projectId, collabId) {
  return api.delete(`/projects/${projectId}/collaborators/${collabId}`)
}

export function listSharedProjects() {
  return api.get('/projects/shared')
}
