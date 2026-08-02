<script setup lang="ts">
import { ref } from 'vue'
import { games } from '@/games/config/games'
import { gameScoreStore } from '@/games/stores/gameScore'
import GameCard from './GameCard.vue'
import Header from '@/components/Header.vue'
import Footer from '@/components/Footer.vue'

const toastMsg = ref('')
const toastVisible = ref(false)
let toastTimer: number | null = null

/** 每个游戏的个人最佳（当前用本地最高分） */
function bestFor(gameId: string): number | undefined {
  if (gameId === '2048') return gameScoreStore.loadBest()
  return undefined
}

function showToast(msg: string) {
  toastMsg.value = msg
  toastVisible.value = true
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => (toastVisible.value = false), 2500)
}

function handleMyRecords() {
  showToast('功能开发中，敬请期待～')
}
</script>

<template>
  <Header />
  <div class="games-page">
    <div class="games-header">
      <div>
        <h1 class="games-title">摸鱼游戏厅</h1>
        <p class="games-subtitle">工作累了？玩一局，轻松一下。</p>
      </div>
      <button class="records-link" @click="handleMyRecords">我的游戏记录 &gt;</button>
    </div>

    <!-- 响应式 Grid：配置驱动，不写死列数 -->
    <div class="games-grid">
      <GameCard
        v-for="game in games"
        :key="game.id"
        :game="game"
        :best="bestFor(game.id)"
        @toast="showToast"
      />
    </div>
  </div>

  <!-- Toast -->
  <transition name="fade">
    <div v-if="toastVisible" class="game-toast">{{ toastMsg }}</div>
  </transition>

  <Footer />
</template>

<style scoped>
.games-page {
  max-width: var(--max-width);
  margin: 0 auto;
  padding: 24px 20px;
  min-height: calc(100vh - var(--header-height));
}

.games-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
}

.games-title {
  font-size: 28px;
  font-weight: 700;
}

.games-subtitle {
  color: var(--color-text-secondary);
  margin-top: 4px;
}

.records-link {
  background: none;
  border: none;
  color: var(--color-primary);
  font-size: 14px;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 8px;
  transition: background 0.2s;
}

.records-link:hover {
  background: var(--color-hover);
}

/* 响应式 Grid：自动换列 */
.games-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 24px;
}

/* Toast */
.game-toast {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  padding: 10px 20px;
  border-radius: 20px;
  font-size: 14px;
  z-index: 999;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
