/**
 * 《细节》管理身份判断
 *
 * 管理身份 = 当前登录用户用户名 == 配置的 admin-user（默认 admin），
 * 由后端 /admin/status 返回；不依赖独立 admin 登录。
 */
import { reactive, computed } from 'vue'

const state = reactive<{ isAdmin: boolean }>({ isAdmin: false })

export function useAdminAuth() {
  const isAdmin = computed(() => state.isAdmin)

  /** 向后端确认当前用户是否管理员（登录态变化后调用） */
  async function checkAdmin() {
    try {
      const res = await fetch('/api/games/detail/admin/status', { credentials: 'same-origin' })
      const json = await res.json()
      state.isAdmin = json.code === 200 && !!json.data?.isAdmin
    } catch {
      state.isAdmin = false
    }
  }

  return { isAdmin, checkAdmin }
}
