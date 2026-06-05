import api from './index'

export function listVersions(projectId) {
  return api.get(`/scripts/project/${projectId}/versions`)
}

export function getLatest(projectId) {
  return api.get(`/scripts/project/${projectId}/latest`)
}

export function getYamlUrl(versionId) {
  return `/api/scripts/${versionId}/yaml`
}

export function saveEditedYaml(projectId, yamlContent) {
  return api.post(`/scripts/project/${projectId}/save`, { yamlContent })
}
