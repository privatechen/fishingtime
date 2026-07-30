/**
 * API 接口层
 * 当前使用 Mock 数据，后续替换为真实 Axios 请求
 */
import { hotData } from '@/mock/hot'
import { communityData, commonHotData } from '@/mock/community'
import { gameData } from '@/mock/game'
import type { HotItem, CommunityPost, GameItem } from '@/types'

/** 模拟延迟 */
function delay(ms = 200): Promise<void> {
  return new Promise((r) => setTimeout(r, ms))
}

export async function fetchHotList(platform: string = 'weibo'): Promise<HotItem[]> {
  await delay()
  return hotData[platform] || []
}

/** 从后端真实接口获取百度热榜 */
export async function fetchBaiduHot(): Promise<HotItem[]> {
  const res = await fetch('/api/hot/baidu', { credentials: 'same-origin' })
  const json = await res.json()
  if (json.code === 200 && Array.isArray(json.data)) {
    return json.data.map((item: any, index: number) => ({
      platform: 'baidu' as const,
      rank: item.rank ?? (index + 1),
      title: item.title ?? '',
      hot: item.hotScore ?? '',
      tag: '',
      normalizedHotScore: item.normalizedHotScore ?? 0,
    }))
  }
  throw new Error(json.message || '热榜加载失败')
}

/** 从后端真实接口获取知乎热榜 */
export async function fetchZhihuHot(): Promise<HotItem[]> {
  const res = await fetch('/api/hot/zhihu', { credentials: 'same-origin' })
  const json = await res.json()
  if (json.code === 200 && Array.isArray(json.data)) {
    return json.data.map((item: any, index: number) => ({
      platform: 'zhihu' as const,
      rank: item.rank ?? (index + 1),
      title: item.title ?? '',
      hot: item.hotScore ?? '',
      tag: '',
      normalizedHotScore: item.normalizedHotScore ?? 0,
    }))
  }
  throw new Error(json.message || '热榜加载失败')
}

/** 从后端真实接口获取微博热搜 */
export async function fetchWeiboHot(): Promise<HotItem[]> {
  const res = await fetch('/api/hot/weibo', { credentials: 'same-origin' })
  const json = await res.json()
  if (json.code === 200 && Array.isArray(json.data)) {
    return json.data.map((item: any, index: number) => ({
      platform: 'weibo' as const,
      rank: item.rank ?? (index + 1),
      title: item.title ?? '',
      hot: item.hotScore ?? '',
      tag: item.summary ?? '',
      normalizedHotScore: item.normalizedHotScore ?? 0,
    }))
  }
  throw new Error(json.message || '热榜加载失败')
}

export async function fetchAllPlatforms(): Promise<Record<string, HotItem[]>> {
  await delay(300)
  return hotData
}

export async function fetchCommunityRecommend(): Promise<CommunityPost[]> {
  await delay()
  return communityData
}

export async function fetchCommonHot() {
  await delay()
  return commonHotData
}

export async function fetchGameList(): Promise<GameItem[]> {
  await delay()
  return gameData
}
