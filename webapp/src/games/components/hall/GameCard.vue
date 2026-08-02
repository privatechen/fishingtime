<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { GameConfig } from '@/games/config/games'

const props = defineProps<{
  game: GameConfig
  /** 个人最佳成绩（可选，仅登录/有本地记录时显示） */
  best?: number
}>()

const router = useRouter()
const emit = defineEmits<{ (e: 'toast', msg: string): void }>()

function handleClick() {
  if (props.game.status === 'available' && props.game.path) {
    router.push(props.game.path)
  } else {
    emit('toast', '敬请期待，正在开发中～')
  }
}
</script>

<template>
  <div class="game-card" @click="handleClick">
    <div class="game-card-cover">{{ game.cover }}</div>
    <div class="game-card-info">
      <h3 class="game-card-title">{{ game.title }}</h3>
      <p class="game-card-desc">{{ game.desc }}</p>
      <p v-if="game.status === 'available' && best !== undefined" class="game-card-best">
        个人最佳：{{ best }}
      </p>
    </div>
    <button
      class="game-card-btn"
      :class="game.status === 'available' ? 'btn-primary' : 'btn-disabled'"
    >
      {{ game.status === 'available' ? '开始游戏' : '敬请期待' }}
    </button>
  </div>
</template>

<style scoped>
.game-card {
  background: var(--color-card);
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
  box-shadow: var(--shadow);
}

.game-card:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-4px);
}

.game-card-cover {
  font-size: 56px;
  line-height: 1;
}

.game-card-info {
  text-align: center;
}

.game-card-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 4px;
}

.game-card-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.game-card-best {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-primary);
}

.game-card-btn {
  width: 100%;
  padding: 8px 16px;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-disabled {
  background: var(--color-hover);
  color: var(--color-text-muted);
  cursor: not-allowed;
}
</style>
