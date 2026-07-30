/**
 * 认证 API — 调用后端真实接口
 * 不走 Mock，使用 fetch + credentials: 'same-origin'（Session 基于 Cookie）
 */
import type { ApiResponse, LoginDTO, RegisterDTO, UserVO } from '@/types'

const BASE = '/api/auth'

async function request<T>(url: string, options?: RequestInit): Promise<ApiResponse<T>> {
  const res = await fetch(url, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  return res.json()
}

export async function login(data: LoginDTO): Promise<ApiResponse<UserVO>> {
  return request<UserVO>(`${BASE}/login`, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function register(data: RegisterDTO): Promise<ApiResponse<UserVO>> {
  return request<UserVO>(`${BASE}/register`, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function logout(): Promise<ApiResponse<null>> {
  return request<null>(`${BASE}/logout`, { method: 'POST' })
}

export async function getCurrentUser(): Promise<ApiResponse<UserVO>> {
  return request<UserVO>(`${BASE}/current-user`)
}
