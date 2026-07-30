<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { HotItem } from '@/types'
import { PLATFORM_MAP } from '@/types'
import { fetchHotList, fetchBaiduHot, fetchZhihuHot, fetchWeiboHot } from '@/api'

const platforms = Object.keys(PLATFORM_MAP)
const activePlatform = ref('weibo')
const list = ref<HotItem[]>([])
const loading = ref(false)
const errorMsg = ref('')

async function load(platform: string) {
  activePlatform.value = platform
  loading.value = true
  errorMsg.value = ''
  list.value = []

  try {
    let items: HotItem[]
    if (platform === 'baidu') {
      items = await fetchBaiduHot()
    } else if (platform === 'zhihu') {
      items = await fetchZhihuHot()
    } else if (platform === 'weibo') {
      items = await fetchWeiboHot()
    } else {
      items = await fetchHotList(platform)
    }
    // 按 normalizedHotScore 降序排列
    list.value = items.sort((a, b) => (b.normalizedHotScore ?? 0) - (a.normalizedHotScore ?? 0))
  } catch (e: any) {
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
      <!-- 加载中 -->
      <div v-if="loading" style="text-align:center;padding:24px;color:var(--color-text-secondary)">
        加载中...
      </div>

      <!-- 错误提示 -->
      <div v-else-if="errorMsg" style="text-align:center;padding:24px;color:#e74c3c;font-size:14px">
        {{ errorMsg }}
      </div>

      <!-- 无数据 -->
      <div v-else-if="list.length === 0" style="text-align:center;padding:24px;color:var(--color-text-muted)">
        暂无热榜数据
      </div>

      <!-- 热榜列表 -->
      <div
        v-for="item in list"
        :key="`${item.platform}-${item.rank}`"
        class="hot-item"
      >
        <span :class="rankClass(item.rank)">{{ item.rank }}</span>
        <div class="hot-info">
          <div class="hot-title">{{ item.title }}</div>
          <div class="hot-meta">
            <span class="hot-count">🔥 {{ item.normalizedHotScore ?? 0 }}</span>
            <span v-if="item.tag" class="hot-tag">{{ item.tag }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
