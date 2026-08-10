<script setup lang="ts">
import { ref, watch } from 'vue'

interface RankItem {
  rank: number
  nickname: string
  [key: string]: unknown
}

const props = defineProps<{
  visible: boolean
  refreshKey: number
  /** 排行榜接口 */
  rankUrl: string
  /** 排行数值字段名（默认 bestScore；颜色猎手为 bestFinalTime） */
  scoreField?: string
  /** 排行数值格式化（默认原样；颜色猎手毫秒转秒） */
  formatScore?: (value: number) => string
}>()
const emit = defineEmits<{ (e: 'close'): void }>()

function defaultFormat(value: number): string {
  return String(value)
}

const rankList = ref<RankItem[]>([])
const loading = ref(false)
const errorMsg = ref('')

async function load() {
  if (!props.visible) return
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await fetch(props.rankUrl, { credentials: 'same-origin' })
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
}

// 打开时拉取；refreshKey 变化时重拉（提交分数后刷新）
watch(() => props.visible, (v) => { if (v) load() })
watch(() => props.refreshKey, () => { if (props.visible) load() })
</script>

<template>
  <div v-if="visible" class="dialog-mask" @click.self="emit('close')">
    <div class="dialog">
      <div class="dialog-header">
        <h3 class="dialog-title">排行榜 Top20</h3>
        <button class="dialog-close" @click="emit('close')">✕</button>
      </div>

      <div v-if="loading" class="rank-state">加载中...</div>
      <div v-else-if="errorMsg" class="rank-state">{{ errorMsg }}</div>
      <div v-else-if="rankList.length === 0" class="rank-state">暂无排行数据，快去挑战吧</div>
      <div v-else class="rank-list">
        <div v-for="item in rankList" :key="item.rank" class="rank-item">
          <span class="rank-no" :class="{ 'rank-top': item.rank <= 3 }">{{ item.rank }}</span>
          <span class="rank-name">{{ item.nickname }}</span>
          <span class="rank-score">
            {{ props.formatScore ? props.formatScore(item[props.scoreField ?? 'bestScore'] as number) : defaultFormat(item[props.scoreField ?? 'bestScore'] as number) }}
          </span>
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
  width: 360px;
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
}
</style>
