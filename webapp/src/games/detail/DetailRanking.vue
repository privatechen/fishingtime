<script setup lang="ts">
import { ref, watch } from 'vue'

interface RankItem {
  rank: number
  nickname: string
  /** 答对题数 */
  score: number
  /** 累计答题用时（毫秒） */
  secondaryScore: number | null
  me?: boolean
}

const props = defineProps<{
  visible: boolean
  refreshKey: number
}>()
const emit = defineEmits<{ (e: 'close'): void }>()

const period = ref<'TODAY' | 'ALL'>('TODAY')
const rankList = ref<RankItem[]>([])
const loading = ref(false)
const errorMsg = ref('')

function fmtTime(ms: number | null): string {
  if (ms == null) return '-'
  return `${(ms / 1000).toFixed(1)}s`
}

async function load() {
  if (!props.visible) return
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await fetch(`/api/games/detail/leaderboard?period=${period.value}`, {
      credentials: 'same-origin',
    })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      rankList.value = json.data.items ?? []
    } else {
      rankList.value = []
    }
  } catch {
    errorMsg.value = '排行榜加载失败'
  } finally {
    loading.value = false
  }
}

watch(() => props.visible, (v) => { if (v) load() })
watch(() => props.refreshKey, () => { if (props.visible) load() })
watch(period, () => load())
</script>

<template>
  <div v-if="visible" class="dialog-mask" @click.self="emit('close')">
    <div class="dialog">
      <div class="dialog-header">
        <h3 class="dialog-title">细节排行榜</h3>
        <button class="dialog-close" @click="emit('close')">✕</button>
      </div>

      <div class="period-tabs">
        <button :class="{ active: period === 'TODAY' }" @click="period = 'TODAY'">今日榜</button>
        <button :class="{ active: period === 'ALL' }" @click="period = 'ALL'">总榜</button>
      </div>

      <div class="rank-legend">先比答对题数，相同再比用时</div>

      <div v-if="loading" class="rank-state">加载中...</div>
      <div v-else-if="errorMsg" class="rank-state">{{ errorMsg }}</div>
      <div v-else-if="rankList.length === 0" class="rank-state">暂无排行数据，快去挑战吧</div>
      <div v-else class="rank-list">
        <div
          v-for="item in rankList"
          :key="period + item.rank"
          class="rank-item"
          :class="{ 'rank-me': item.me }"
        >
          <span class="rank-no" :class="{ 'rank-top': item.rank <= 3 }">{{ item.rank }}</span>
          <span class="rank-name">{{ item.nickname }}</span>
          <span class="rank-score">答对 {{ item.score }} · {{ fmtTime(item.secondaryScore) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
}

.dialog {
  background: #fff;
  border-radius: 16px;
  padding: 20px 24px 24px;
  width: 380px;
  max-width: 90%;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.dialog-title {
  font-size: 17px;
  font-weight: 700;
}

.dialog-close {
  background: none;
  border: none;
  font-size: 16px;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 4px 8px;
}

.period-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}

.period-tabs button {
  flex: 1;
  padding: 7px 0;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-card);
  font-size: 14px;
  cursor: pointer;
  color: var(--color-text-secondary);
}

.period-tabs button.active {
  border-color: var(--color-primary);
  color: var(--color-primary);
  font-weight: 600;
}

.rank-legend {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: 8px;
}

.rank-state {
  text-align: center;
  color: var(--color-text-muted);
  padding: 32px 0;
  font-size: 13px;
}

.rank-list {
  overflow-y: auto;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 14px;
}

.rank-item:last-child {
  border-bottom: none;
}

.rank-me {
  background: #f0f7ff;
  border-radius: 8px;
}

.rank-no {
  width: 30px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.rank-top {
  color: var(--color-primary);
}

.rank-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-score {
  font-weight: 600;
  color: var(--color-primary);
  font-size: 13px;
}
</style>
