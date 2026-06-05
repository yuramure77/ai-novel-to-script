import api from './index'

export const listFolders = () => api.get('/folders')
export const createFolder = (name) => api.post('/folders', { name })
export const renameFolder = (id, name) => api.put(`/folders/${id}`, { name })
export const deleteFolder = (id) => api.delete(`/folders/${id}`)
export const moveProject = (projectId, folderId) => api.put(`/projects/${projectId}/move`, { folderId })
