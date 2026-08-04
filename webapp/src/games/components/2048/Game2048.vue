<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Game2048Engine, type Direction, type BoardTile } from '@/games/engine/2048/Game2048Engine'
import { gameScoreStore } from '@/games/stores/gameScore'
import { useAuth } from '@/stores/auth'
import GameBoard from './GameBoard.vue'
import ScorePanel from './ScorePanel.vue'
import RankingPanel from './RankingPanel.vue'
import RuleDialog from './RuleDialog.vue'
import RestartDialog from './RestartDialog.vue'
import RegisterDialog from './RegisterDialog.vue'
import Header from '@/components/Header.vue'
import Footer from '@/components/Footer.vue'

const router = useRouter()
const { isLoggedIn, user } = useAuth()
const engine = new Game2048Engine()
const tiles = ref<BoardTile[]>([])
const newTileIds = ref<number[]>([])
const score = ref(0)
/** 当前游戏的最高分（本局峰值，重新开始后归零） */
const best = ref(0)
const showRule = ref(false)
const showRestart = ref(false)
const showRegister = ref(false)
const showWin = ref(false)
const showGameOver = ref(false)
const rankingKey = ref(0)
const toastMsg = ref('')
const toastVisible = ref(false)
let toastTimer: number | null = null

function syncUI() {
  tiles.value = engine.getTiles()
  newTileIds.value = []
  score.value = engine.getScore()
  best.value = Math.max(best.value, score.value)
  gameScoreStore.saveBoard(engine.getBoardValues())
  gameScoreStore.saveScore(score.value)
}

function handleMove(direction: Direction) {
  const result = engine.move(direction)
  if (!result.moved) return

  // 更新格子 + 弹出动画标记
  tiles.value = engine.getTiles()
  newTileIds.value = result.newTileIds
  score.value = engine.getScore()
  best.value = Math.max(best.value, score.value)
  gameScoreStore.saveBoard(engine.getBoardValues())
  gameScoreStore.saveScore(score.value)

  // 胜利检测
  if (!showWin.value && engine.getMaxTile() >= 2048) {
    showWin.value = true
  }
  // 结束检测
  if (engine.isGameOver()) {
    showGameOver.value = true
  }
}

function onKeydown(e: KeyboardEvent) {
  const map: Record<string, Direction> = {
    ArrowUp: 'up',
    ArrowDown: 'down',
    ArrowLeft: 'left',
    ArrowRight: 'right',
  }
  if (map[e.key]) {
    e.preventDefault()
    handleMove(map[e.key])
  }
}

function restartGame() {
  engine.init()
  gameScoreStore.clearCurrent()
  best.value = 0 // 本局最高分归零
  showWin.value = false
  showGameOver.value = false
  syncUI()
}

function showToast(msg: string) {
  toastMsg.value = msg
  toastVisible.value = true
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => (toastVisible.value = false), 2500)
}

// 刷新时重置游戏进度（本局最高分归零）
function startFresh() {
  engine.init()
  gameScoreStore.clearCurrent()
  best.value = 0
  syncUI()
}

async function submitScore(): Promise<boolean> {
  // 保存当前这局游戏分数到后端
  // 注意：发送 score.value（当前局分数），而不是 best.value（可能混入其他用户的 localStorage 最高分）
  try {
    const res = await fetch('/api/games/2048/score', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({ bestScore: score.value, maxTile: engine.getMaxTile() }),
    })
    const json = await res.json()
    return json.code === 200
  } catch {
    // 同步失败不影响本地游戏
    return false
  }
}

/** 保存成功后的统一处理：刷新排行榜 + 提示 */
async function saveAndRefresh() {
  const ok = await submitScore()
  if (ok) {
    rankingKey.value++
    showToast('保存成功')
  } else {
    showToast('保存失败')
  }
}

/** 点击"保存"：保存当前分数，不跳转 */
async function handleSave() {
  if (isLoggedIn.value) {
    // 已登录 → 直接保存并刷新排行榜
    await saveAndRefresh()
  } else {
    // 未登录 → 弹注册框，注册完成后回调保存
    showRegister.value = true
  }
}

/** 注册完成回调：保存分数并刷新排行榜，不跳转 */
async function handleRegistered() {
  showRegister.value = false
  await saveAndRefresh()
}

onMounted(() => {
  startFresh()
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  if (toastTimer) clearTimeout(toastTimer)
})
</script>

<template>
  <Header />
  <div class="game2048-page">
    <!-- 顶部栏 -->
    <div class="game-header">
      <button class="back-btn" @click="router.push('/games')">← 返回大厅</button>
      <button class="record-btn" @click="showToast('我的记录功能开发中')">我的记录</button>
    </div>

    <div class="game-layout">
      <!-- 左侧：棋盘 -->
      <div class="game-main">
        <ScorePanel :score="score" :best="best" />
        <GameBoard :tiles="tiles" :new-tile-ids="newTileIds" />
        <div class="game-actions">
          <button class="action-btn" @click="handleSave">保存</button>
          <button class="action-btn" @click="showRestart = true">重新开始</button>
          <button class="action-btn" @click="showRule = true">游戏规则</button>
        </div>
        <p class="game-tip">使用键盘方向键 ↑ ↓ ← → 移动方块</p>
      </div>

      <!-- 右侧：排行榜 -->
      <RankingPanel :refresh-key="rankingKey" />
    </div>
  </div>

  <!-- 弹窗 -->
  <RuleDialog :visible="showRule" @close="showRule = false" />
  <RestartDialog :visible="showRestart" @confirm="restartGame(); showRestart = false" @cancel="showRestart = false" />
  <RegisterDialog :visible="showRegister" @close="showRegister = false" @registered="handleRegistered" />

  <!-- 胜利弹窗 -->
  <div v-if="showWin" class="dialog-mask">
    <div class="dialog">
      <h3 class="dialog-title">🎉 恭喜达成 2048！</h3>
      <p class="dialog-body">太棒了，你已合成 2048！可以继续挑战更高分。</p>
      <button class="dialog-btn" @click="showWin = false">继续游戏</button>
    </div>
  </div>

  <!-- 游戏结束弹窗 -->
  <div v-if="showGameOver" class="dialog-mask">
    <div class="dialog">
      <h3 class="dialog-title">游戏结束</h3>
      <p class="dialog-body">棋盘已满，无法继续移动。最终得分：{{ score }}</p>
      <div class="dialog-actions">
        <button class="dialog-btn-secondary" @click="router.push('/games')">返回大厅</button>
        <button class="dialog-btn" @click="restartGame()">再来一局</button>
      </div>
    </div>
  </div>

  <!-- Toast -->
  <transition name="fade">
    <div v-if="toastVisible" class="game-toast">{{ toastMsg }}</div>
  </transition>

  <Footer />
</template>

<style scoped>
.game2048-page {
  max-width: var(--max-width);
  margin: 0 auto;
  padding: 20px;
  min-height: calc(100vh - var(--header-height));
}

.game-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.back-btn, .record-btn {
  background: none;
  border: none;
  font-size: 14px;
  cursor: pointer;
  color: var(--color-primary);
  padding: 8px 12px;
  border-radius: 8px;
}

.back-btn:hover, .record-btn:hover {
  background: var(--color-hover);
}

.game-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.game-main {
  flex: 1;
  max-width: 440px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.game-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  flex: 1;
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: 20px;
  background: var(--color-card);
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.action-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.game-tip {
  font-size: 12px;
  color: var(--color-text-muted);
  text-align: center;
}

/* 弹窗 */
.dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: #fff;
  border-radius: 16px;
  padding: 28px;
  width: 340px;
  max-width: 90%;
  text-align: center;
}

.dialog-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 12px;
}

.dialog-body {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 20px;
}

.dialog-btn {
  width: 100%;
  padding: 10px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
}

.dialog-actions {
  display: flex;
  gap: 12px;
}

.dialog-btn-secondary {
  flex: 1;
  padding: 10px;
  background: var(--color-hover);
  border: none;
  border-radius: 20px;
  cursor: pointer;
}

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

.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* 响应式 */
@media (max-width: 768px) {
  .game-layout {
    flex-direction: column;
  }
  .game-main {
    max-width: 100%;
  }
}
</style>
