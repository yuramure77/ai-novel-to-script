import api from './index'

export function login(username, password) {
  return api.post('/auth/login', { username, password })
}

export function register(username, password, nickname) {
  return api.post('/auth/register', { username, password, nickname })
}
