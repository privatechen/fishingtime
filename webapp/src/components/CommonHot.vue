<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fetchCommonHot, type CommonHotCluster } from '@/api'

const clusters = ref<CommonHotCluster[]>([])
const loading = ref(true)

const platformName: Record<string, string> = {
  weibo: '微博',
  baidu: '百度',
  zhihu: '知乎',
  hupu: '虎扑',
  toutiao: '头条',
}

onMounted(async () => {
  try {
    clusters.value = await fetchCommonHot()
  } catch (e) {
    console.warn('[共同热点] 加载失败', e)
    clusters.value = []
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="card">
    <div class="sidebar-card-header">🌐 全网共同热点</div>
    <div class="sidebar-card-body common-hot-list">
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
            <span class="common-hot-rank-name">{{ platformName[entry.platform] || entry.platform }}</span>
            <span class="common-hot-rank-value">#{{ entry.hotItem.rank ?? '-' }}</span>
          </div>
        </div>
      </div>

      <div v-if="!loading && clusters.length === 0" class="common-hot-empty">
        暂无跨平台共同热点
      </div>
    </div>
  </div>
</template>

<style scoped>
.common-hot-list {
  padding-top: 4px;
  padding-bottom: 4px;
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
