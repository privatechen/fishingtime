/**
 * 《细节》管理后台登录态（独立于普通用户登录）
 * token 存 localStorage（V1 不过期；后端重启后需重新登录）
 */
import { reactive, computed } from 'vue'

const TOKEN_KEY = 'fishingtime:adminToken'

const state = reactive<{ token: string }>({
  token: typeof localStorage !== 'undefined' ? (localStorage.getItem(TOKEN_KEY) || '') : '',
})

export function useAdminAuth() {
  const isAdmin = computed(() => !!state.token)

  async function login(username: string, password: string): Promise<string | null> {
    try {
      const res = await fetch('/api/games/detail/admin/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ username, password }),
      })
      const json = await res.json()
      if (json.code === 200 && json.data?.token) {
        state.token = json.data.token
        localStorage.setItem(TOKEN_KEY, json.data.token)
        return null
      }
      return json.message || '登录失败'
    } catch {
      return '网络异常，请稍后重试'
    }
  }

  function logout() {
    state.token = ''
    localStorage.removeItem(TOKEN_KEY)
  }

  return { isAdmin, token: computed(() => state.token), login, logout }
}
