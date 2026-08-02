<script setup lang="ts">
import { ref, onMounted } from 'vue'

interface RankItem {
  rank: number
  nickname: string
  bestScore: number
}

const rankList = ref<RankItem[]>([])
const loading = ref(false)
const errorMsg = ref('')

onMounted(async () => {
  loading.value = true
  try {
    const res = await fetch('/api/games/2048/rank', { credentials: 'same-origin' })
    const json = await res.json()
    if (json.code === 200 && Array.isArray(json.data)) {
      rankList.value = json.data
    } else {
      rankList.value = []
    }
  } catch {
    errorMsg.value = '排行榜加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="ranking-panel">
    <div class="ranking-title">排行榜 Top20</div>

    <div v-if="loading" class="ranking-state">加载中...</div>
    <div v-else-if="errorMsg" class="ranking-state">{{ errorMsg }}</div>
    <div v-else-if="rankList.length === 0" class="ranking-state">暂无排行数据</div>

    <div v-else class="ranking-list">
      <div v-for="item in rankList" :key="item.rank" class="ranking-item">
        <span class="ranking-rank">{{ item.rank }}</span>
        <span class="ranking-name">{{ item.nickname }}</span>
        <span class="ranking-score">{{ item.bestScore }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ranking-panel {
  background: var(--color-card);
  border-radius: 12px;
  padding: 16px;
  box-shadow: var(--shadow);
}

.ranking-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}

.ranking-state {
  text-align: center;
  color: var(--color-text-muted);
  padding: 24px 0;
  font-size: 13px;
}

.ranking-list {
  max-height: 380px;
  overflow-y: auto;
}

.ranking-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 14px;
}

.ranking-item:last-child {
  border-bottom: none;
}

.ranking-rank {
  width: 28px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.ranking-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ranking-score {
  font-weight: 600;
  color: var(--color-primary);
}
</style>
