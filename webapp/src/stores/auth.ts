/**
 * 全局登录态管理
 * 单例响应式状态，不依赖 Pinia，任何组件调 useAuth() 共享同一份 state
 */
import { reactive, computed } from 'vue'
import type { UserVO, LoginDTO, RegisterDTO } from '@/types'
import { AUTH_ERROR_MAP } from '@/types'
import * as authApi from '@/api/auth'

interface AuthState {
  user: UserVO | null
  loading: boolean
}

const state = reactive<AuthState>({
  user: null,
  loading: true,
})

export function useAuth() {
  const isLoggedIn = computed(() => state.user !== null)

  /** 页面初始化时调：通过 Session 恢复登录态 */
  async function checkAuth() {
    state.loading = true
    try {
      const res = await authApi.getCurrentUser()
      if (res.code === 200 && res.data) {
        state.user = res.data
      } else {
        state.user = null
      }
    } catch {
      state.user = null
    } finally {
      state.loading = false
    }
  }

  /** 登录 */
  async function login(data: LoginDTO): Promise<string | null> {
    try {
      const res = await authApi.login(data)
      if (res.code === 200 && res.data) {
        state.user = res.data
        return null // 无错误
      }
      return AUTH_ERROR_MAP[res.code] || res.message || '登录失败'
    } catch {
      return '网络异常，请稍后重试'
    }
  }

  /** 注册 + 自动登录 */
  async function register(data: RegisterDTO): Promise<string | null> {
    try {
      const res = await authApi.register(data)
      if (res.code === 200 && res.data) {
        // 注册成功 → 自动登录
        const loginErr = await login({
          username: data.username,
          password: data.password,
        })
        return loginErr // 自动登录失败时才返回错误
      }
      return AUTH_ERROR_MAP[res.code] || res.message || '注册失败'
    } catch {
      return '网络异常，请稍后重试'
    }
  }

  /** 退出 */
  async function logout() {
    try {
      await authApi.logout()
    } finally {
      state.user = null
      // 退出登录：清空游戏本地数据（棋盘/分数/最高分），避免下一用户看到上一用户成绩
      localStorage.removeItem('game2048:board')
      localStorage.removeItem('game2048:score')
      localStorage.removeItem('game2048:best')
    }
  }

  return {
    user: computed(() => state.user),
    isLoggedIn,
    loading: computed(() => state.loading),
    checkAuth,
    login,
    register,
    logout,
  }
}
