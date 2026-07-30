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
