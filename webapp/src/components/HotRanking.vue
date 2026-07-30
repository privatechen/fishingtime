<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import type { HotItem } from '@/types'
import { PLATFORM_MAP } from '@/types'
import { fetchHotList } from '@/api'

const platforms = Object.keys(PLATFORM_MAP)
const activePlatform = ref('weibo')
const list = ref<HotItem[]>([])
const loading = ref(false)

async function load(platform: string) {
  loading.value = true
  activePlatform.value = platform
  list.value = await fetchHotList(platform)
  loading.value = false
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
      <div
        v-for="item in list"
        :key="`${item.platform}-${item.rank}`"
        class="hot-item"
      >
        <span :class="rankClass(item.rank)">{{ item.rank }}</span>
        <div class="hot-info">
          <div class="hot-title">{{ item.title }}</div>
          <div class="hot-meta">
            <span class="hot-count">🔥 {{ item.hot }}</span>
            <span v-if="item.tag" class="hot-tag">{{ item.tag }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
