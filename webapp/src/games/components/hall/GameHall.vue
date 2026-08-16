<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { games } from '@/games/config/games'
import { useAuth } from '@/stores/auth'
import GameCard from './GameCard.vue'
import Header from '@/components/Header.vue'
import Footer from '@/components/Footer.vue'

const { isLoggedIn } = useAuth()

const toastMsg = ref('')
const toastVisible = ref(false)
/** 当前用户各游戏最高分（登录后从后端加载） */
const bestMap = ref<Record<string, number>>({})
let toastTimer: number | null = null

/** 登录用户加载各游戏个人最佳；游客不展示 */
async function loadBestScores() {
  bestMap.value = {}
  if (!isLoggedIn.value) return
  try {
    const res = await fetch('/api/games/2048/my-best', { credentials: 'same-origin' })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      bestMap.value['2048'] = json.data.bestScore ?? 0
    }
  } catch {
    // 加载失败不展示
  }
  try {
    const res = await fetch('/api/games/color-focus/my-best', { credentials: 'same-origin' })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      bestMap.value['color-focus'] = json.data.bestScore ?? 0
    }
  } catch {
    // 加载失败不展示
  }
  try {
    const res = await fetch('/api/games/direction-trap/my-best', { credentials: 'same-origin' })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      bestMap.value['direction-trap'] = json.data.bestScore ?? 0
    }
  } catch {
    // 加载失败不展示
  }
  try {
    // 颜色猎手为时间制：best_final_time 毫秒 → 大厅显示秒（1 位小数）
    const res = await fetch('/api/games/color-hunter/my-best', { credentials: 'same-origin' })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      bestMap.value['color-hunter'] = Math.round((json.data.bestFinalTime ?? 0) / 100) / 10
    }
  } catch {
    // 加载失败不展示
  }
  try {
    // 《细节》最佳：答对题数
    const res = await fetch('/api/games/detail/my-best', { credentials: 'same-origin' })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      bestMap.value['detail'] = json.data.bestCorrectCount ?? 0
    }
  } catch {
    // 加载失败不展示
  }
}

/** 每个游戏的个人最佳（游客不显示） */
function bestFor(gameId: string): number | undefined {
  const v = bestMap.value[gameId]
  return v !== undefined ? v : undefined
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

onMounted(loadBestScores)
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
