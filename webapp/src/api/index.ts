/**
 * API 接口层
 */
import { hotData } from '@/mock/hot'
import { communityData } from '@/mock/community'
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

/** 后端热榜接口返回格式 */
interface HotApiResult {
  data: HotItem[]
  updateTime?: string
  nextRefreshTime?: string
}

/**
 * 通用热榜请求 — 调用 /api/hot/{platform}
 * 返回数据 + 缓存元信息
 */
async function fetchHotFromApi(platform: string, mapper: (item: any, index: number) => HotItem): Promise<HotApiResult> {
  const res = await fetch(`/api/hot/${platform}`, { credentials: 'same-origin' })
  const json = await res.json()
  if (json.code === 200 && Array.isArray(json.data)) {
    return {
      data: json.data.map(mapper),
      updateTime: json.updateTime,
      nextRefreshTime: json.nextRefreshTime,
    }
  }
  throw new Error(json.message || '热榜加载失败')
}

export async function fetchBaiduHot(): Promise<HotApiResult> {
  return fetchHotFromApi('baidu', (item: any, index: number) => ({
    platform: 'baidu' as const,
    rank: item.rank ?? (index + 1),
    title: item.title ?? '',
    hot: item.hotScore ?? '',
    url: item.url ?? '',
    tag: '',
    normalizedHotScore: item.normalizedHotScore ?? 0,
  }))
}

export async function fetchZhihuHot(): Promise<HotApiResult> {
  return fetchHotFromApi('zhihu', (item: any, index: number) => ({
    platform: 'zhihu' as const,
    rank: item.rank ?? (index + 1),
    title: item.title ?? '',
    hot: item.hotScore ?? '',
    url: item.url ?? '',
    tag: '',
    normalizedHotScore: item.normalizedHotScore ?? 0,
  }))
}

export async function fetchWeiboHot(): Promise<HotApiResult> {
  return fetchHotFromApi('weibo', (item: any, index: number) => ({
    platform: 'weibo' as const,
    rank: item.rank ?? (index + 1),
    title: item.title ?? '',
    hot: item.hotScore ?? '',
    url: item.url ?? '',
    tag: item.summary ?? '',
    normalizedHotScore: item.normalizedHotScore ?? 0,
  }))
}

export async function fetchHupuHot(): Promise<HotApiResult> {
  return fetchHotFromApi('hupu', (item: any, index: number) => ({
    platform: 'hupu' as const,
    rank: item.rank ?? (index + 1),
    title: item.title ?? '',
    url: item.url ?? '',
    replyCount: item.replyCount,
    viewCount: item.viewCount,
    author: item.author,
    publishTime: item.publishTime,
    // 虎扑按回复时间排序，无热度值
    normalizedHotScore: undefined,
  }))
}

export async function fetchToutiaoHot(): Promise<HotApiResult> {
  return fetchHotFromApi('toutiao', (item: any, index: number) => ({
    platform: 'toutiao' as const,
    rank: item.rank ?? (index + 1),
    title: item.title ?? '',
    hot: item.hotScore ?? '',
    url: item.url ?? '',
    normalizedHotScore: item.normalizedHotScore ?? 0,
  }))
}

export async function fetchDouyinHot(): Promise<HotApiResult> {
  return fetchHotFromApi('douyin', (item: any, index: number) => ({
    platform: 'douyin' as const,
    rank: item.rank ?? (index + 1),
    title: item.title ?? '',
    hot: item.hotScore ?? '',
    url: item.url ?? '',
    normalizedHotScore: item.normalizedHotScore ?? 0,
  }))
}

export async function fetchKuaishouHot(): Promise<HotApiResult> {
  return fetchHotFromApi('kuaishou', (item: any, index: number) => ({
    platform: 'kuaishou' as const,
    rank: item.rank ?? (index + 1),
    title: item.title ?? '',
    hot: item.hotScore ?? '',
    url: item.url ?? '',
    normalizedHotScore: item.normalizedHotScore ?? 0,
  }))
}

export async function fetchAllPlatforms(): Promise<Record<string, HotItem[]>> {
  await delay(300)
  return hotData
}

export async function fetchCommunityRecommend(): Promise<CommunityPost[]> {
  await delay()
  return communityData
}

export interface CommonHotPlatformItem {
  platform: string
  similarityScore?: number
  hotItem: {
    rank?: number
    title?: string
    url?: string
  }
}

export interface CommonHotCluster {
  title: string
  sourceCount: number
  items: CommonHotPlatformItem[]
}

/**
 * 全网共同热点：至少两个不同平台命中才由后端返回。
 * 右侧模块最多加载 10 个热点簇，超过可视区域后在卡片内部滚动查看。
 */
export async function fetchCommonHot(): Promise<CommonHotCluster[]> {
  const res = await fetch('/api/hot/similar/clusters', { credentials: 'same-origin' })
  const json = await res.json()
  if (json.code === 200 && Array.isArray(json.data)) {
    return json.data
      .filter((cluster: CommonHotCluster) => cluster.sourceCount >= 2 && Array.isArray(cluster.items) && cluster.items.length >= 2)
      .slice(0, 10)
  }
  throw new Error(json.message || '共同热点加载失败')
}

export async function fetchGameList(): Promise<GameItem[]> {
  await delay()
  return gameData
}
