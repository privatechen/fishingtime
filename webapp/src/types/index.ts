/** 热榜条目 */
export interface HotItem {
  platform: 'weibo' | 'baidu' | 'zhihu'
  rank: number
  title: string
  hot: string
  tag?: string
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
}
