<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { fetchCommonHot, type CommonHotCluster } from '@/api'
import { PLATFORM_MAP } from '@/types'

const clusters = ref<CommonHotCluster[]>([])
const loading = ref(true)
const listRef = ref<HTMLElement | null>(null)
const showScrollbar = ref(false)
const thumbHeight = ref(36)
const thumbTop = ref(0)

function updateScrollbar() {
  const el = listRef.value
  if (!el) return

  const { clientHeight, scrollHeight, scrollTop } = el
  showScrollbar.value = scrollHeight > clientHeight + 1

  if (!showScrollbar.value) {
    thumbTop.value = 0
    return
  }

  const trackHeight = clientHeight - 8
  const ratio = clientHeight / scrollHeight
  const height = Math.max(32, Math.round(trackHeight * ratio))
  const maxThumbTop = Math.max(0, trackHeight - height)
  const maxScrollTop = Math.max(1, scrollHeight - clientHeight)

  thumbHeight.value = height
  thumbTop.value = Math.round((scrollTop / maxScrollTop) * maxThumbTop)
}

onMounted(async () => {
  try {
    clusters.value = (await fetchCommonHot()).slice(0, 10)
  } catch (e) {
    console.warn('[共同热点] 加载失败', e)
    clusters.value = []
  } finally {
    loading.value = false
    await nextTick()
    updateScrollbar()
  }
})
</script>

<template>
  <div class="card">
    <div class="sidebar-card-header">🌐 全网共同热点</div>
    <div class="common-hot-scroll-wrap">
      <div
        ref="listRef"
        class="sidebar-card-body common-hot-list"
        @scroll="updateScrollbar"
      >
        <div
          v-for="cluster in clusters"
          :key="cluster.title"
          class="common-hot-item"
        >
          <div class="common-hot-title">{{ cluster.title }}</div>
          <div class="common-hot-ranks">
            <div
              v-for="entry in cluster.items"
              :key="`${entry.platform}-${entry.hotItem.rank}-${entry.hotItem.title}`"
              class="common-hot-rank"
            >
              <span class="common-hot-rank-name">{{ PLATFORM_MAP[entry.platform]?.name || entry.platform }}</span>
              <span class="common-hot-rank-value">#{{ entry.hotItem.rank ?? '-' }}</span>
            </div>
          </div>
        </div>

        <div v-if="!loading && clusters.length === 0" class="common-hot-empty">
          暂无跨平台共同热点
        </div>
      </div>

      <!-- 不依赖系统原生滚动条，始终显式展示 -->
      <div v-if="showScrollbar" class="common-hot-scroll-track" aria-hidden="true">
        <div
          class="common-hot-scroll-thumb"
          :style="{ height: `${thumbHeight}px`, transform: `translateY(${thumbTop}px)` }"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.common-hot-scroll-wrap {
  position: relative;
}

.common-hot-list {
  padding-top: 4px;
  padding-bottom: 4px;
  padding-right: 12px;
  max-height: 250px;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;

  /* 隐藏系统滚动条，改用右侧显式滚动条，避免 macOS/Chrome 自动隐藏 */
  scrollbar-width: none;
}

.common-hot-list::-webkit-scrollbar {
  display: none;
}

.common-hot-scroll-track {
  position: absolute;
  top: 4px;
  right: 4px;
  bottom: 4px;
  width: 5px;
  border-radius: 999px;
  background: rgba(120, 120, 120, 0.10);
  pointer-events: none;
}

.common-hot-scroll-thumb {
  width: 100%;
  border-radius: 999px;
  background: rgba(100, 100, 100, 0.45);
  transition: transform 40ms linear;
}

.common-hot-item + .common-hot-item {
  border-top: 1px solid var(--color-border);
}

.common-hot-item {
  padding: 14px 0;
}

.common-hot-title {
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.common-hot-ranks {
  gap: 22px;
}

.common-hot-rank-value {
  font-size: 16px;
}

.common-hot-empty {
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-muted);
}
</style>
