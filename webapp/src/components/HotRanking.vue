<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { HotItem } from '@/types'
import { PLATFORM_MAP } from '@/types'
import { fetchHotList, fetchBaiduHot, fetchZhihuHot, fetchWeiboHot, fetchHupuHot } from '@/api'

const platforms = Object.keys(PLATFORM_MAP)
const activePlatform = ref('weibo')
const list = ref<HotItem[]>([])
const loading = ref(false)
const errorMsg = ref('')

/** 缓存有效期判断：当前时间 < nextRefreshTime + 10s 缓冲 */
function isCacheValid(cached: { nextRefreshTime?: string }): boolean {
  if (!cached.nextRefreshTime) return false
  const deadline = new Date(cached.nextRefreshTime).getTime() + 10_000
  return Date.now() < deadline
}

/** 平台缓存 */
const cache = new Map<string, { data: HotItem[]; nextRefreshTime?: string }>()

async function load(platform: string) {
  activePlatform.value = platform
  errorMsg.value = ''

  // 缓存有效 → 直接展示，不请求
  const cached = cache.get(platform)
  if (cached && isCacheValid(cached)) {
    list.value = cached.data
    return
  }

  loading.value = true
  list.value = []

  try {
    let result: { data: HotItem[]; nextRefreshTime?: string }

    if (platform === 'baidu') {
      result = await fetchBaiduHot()
    } else if (platform === 'zhihu') {
      result = await fetchZhihuHot()
    } else if (platform === 'weibo') {
      result = await fetchWeiboHot()
    } else if (platform === 'hupu') {
      result = await fetchHupuHot()
    } else {
      result = { data: await fetchHotList(platform) }
    }

    // 有热度值的平台按 normalizedHotScore 降序排列；虎扑无热度值保持原顺序
    if (result.data.some((item) => item.normalizedHotScore !== undefined)) {
      result.data.sort((a, b) => (b.normalizedHotScore ?? 0) - (a.normalizedHotScore ?? 0))
    }

    // 更新缓存
    cache.set(platform, {
      data: result.data,
      nextRefreshTime: result.nextRefreshTime,
    })

    list.value = result.data
  } catch (e: any) {
    // 请求失败但缓存还在 → 保留缓存，不显示错误
    if (cached) {
      list.value = cached.data
      loading.value = false
      return
    }
    errorMsg.value = '热榜加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function rankClass(rank: number): string {
  if (rank <= 3) return `hot-rank top${rank}`
  return 'hot-rank'
}

onMounted(() => load('weibo'))
</script>

<template>
  <div class="card">
    <div class="card-header">🔥 热榜</div>
    <div class="hot-tabs">
      <button
        v-for="p in platforms"
        :key="p"
        class="hot-tab"
        :class="{ active: activePlatform === p }"
        @click="load(p)"
      >
        {{ PLATFORM_MAP[p].name }}
      </button>
    </div>
    <div class="hot-list">
      <div v-if="loading" style="text-align:center;padding:24px;color:var(--color-text-secondary)">
        加载中...
      </div>

      <div v-else-if="errorMsg" style="text-align:center;padding:24px;color:#e74c3c;font-size:14px">
        {{ errorMsg }}
      </div>

      <div v-else-if="list.length === 0" style="text-align:center;padding:24px;color:var(--color-text-muted)">
        暂无热榜数据
      </div>

      <div
        v-for="item in list"
        :key="`${item.platform}-${item.rank}`"
        class="hot-item"
      >
        <span :class="rankClass(item.rank)">{{ item.rank }}</span>
        <div class="hot-info">
          <a v-if="item.url" :href="item.url" target="_blank" rel="noopener noreferrer" class="hot-title-link">
            <span class="hot-title">{{ item.title }}</span>
          </a>
          <span v-else class="hot-title">{{ item.title }}</span>
          <div class="hot-meta">
            <!-- 有热度值才显示 🔥 -->
            <span v-if="item.normalizedHotScore !== undefined" class="hot-count">🔥 {{ item.normalizedHotScore }}</span>
            <!-- 虎扑：显示回复/浏览/作者/时间 -->
            <template v-if="item.platform === 'hupu'">
              <span v-if="item.replyCount !== undefined" class="hot-count">💬 {{ item.replyCount }}</span>
              <span v-if="item.viewCount !== undefined" class="hot-count">👁 {{ item.viewCount }}</span>
              <span v-if="item.publishTime" class="hot-count">{{ item.publishTime }}</span>
            </template>
            <span v-if="item.tag && item.platform !== 'hupu'" class="hot-tag">{{ item.tag }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
