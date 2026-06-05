import api from './index'

export function sendMessage(projectId, message) {
  return api.post(`/chat/${projectId}`, { message })
}

export function getHistory(projectId) {
  return api.get(`/chat/${projectId}/history`)
}
