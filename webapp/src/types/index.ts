/** 热榜条目 */
export interface HotItem {
  platform: 'weibo' | 'baidu' | 'zhihu' | 'hupu' | 'douyin' | 'kuaishou' | 'toutiao'
  rank: number
  title: string
  hot?: string
  tag?: string
  /** 详情链接 */
  url?: string
  /** 统一热度值，用于排序 */
  normalizedHotScore?: number
  /** 回复数（虎扑） */
  replyCount?: number
  /** 浏览数（虎扑） */
  viewCount?: number
  /** 作者（虎扑） */
  author?: string
  /** 发布时间（虎扑） */
  publishTime?: string
}

/** 社区推荐帖子 */
export interface CommunityPost {
  id: number
  title: string
  commentCount: number
  createdAt?: string
}

/** 小游戏 */
export interface GameItem {
  id: number
  name: string
  description: string
  icon?: string
}

/** 平台信息 */
export const PLATFORM_MAP: Record<string, { name: string; color: string }> = {
  weibo: { name: '微博热搜', color: '#e74c3c' },
  baidu: { name: '百度热搜', color: '#2d8cf0' },
  zhihu: { name: '知乎热榜', color: '#1a7ae0' },
  douyin: { name: '抖音热榜', color: '#161823' },
  kuaishou: { name: '快手热榜', color: '#ff4906' },
  toutiao: { name: '头条热榜', color: '#ff5c38' },
}

/* ============ Auth ============ */

/** 统一 API 响应 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T | null
}

/** 登录请求 */
export interface LoginDTO {
  username: string
  password: string
}

/** 注册请求 */
export interface RegisterDTO {
  username: string
  password: string
  nickname: string
  email?: string
}

/** 用户信息（后端返回） */
export interface UserVO {
  id: number
  username: string
  nickname: string
}

/** 错误码 → 前端显示文案 */
export const AUTH_ERROR_MAP: Record<number, string> = {
  1001: '该用户名已被注册',
  1002: '账号不存在',
  1003: '用户名或密码错误',
  1004: '账号已被禁用',
  400: '请求参数校验失败',
  5000: '系统异常，请稍后重试',
}
