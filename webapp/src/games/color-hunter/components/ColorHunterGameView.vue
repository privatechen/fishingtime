<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ColorHunterEngine,
  type HunterCell,
  type HunterResult,
} from '../engine/ColorHunterEngine'
import { COLOR_CSS, COLOR_NAME, ROUND_CONFIGS, roundLabel } from '../config/levels'
import { colorHunterStore } from '../stores/colorHunterStore'
import BackendRecord from '../../common/components/BackendRecord.vue'
import ChallengeRanking from '../../common/components/ChallengeRanking.vue'
import Header from '@/components/Header.vue'
import Footer from '@/components/Footer.vue'

type ViewState = 'intro' | 'playing' | 'result'

const router = useRouter()
const engine = new ColorHunterEngine()

const view = ref<ViewState>('intro')

// 游戏状态（响应式镜像引擎）
const cells = ref<HunterCell[]>([])
const targetColorName = ref('')
const remaining = ref(0)
const gridSize = ref(3)
const roundLabelText = ref('1-1')
const errorCount = ref(0)
const elapsed = ref(0)
const errorFlashIndex = ref<number | null>(null)
const showTransition = ref(false)
const transitionLevel = ref(0)
const transitionGrid = ref('')

// 结果
const result = ref<HunterResult | null>(null)
const bestFinalTime = ref(0)

// 记录与排行榜
const SCORE_URL = '/api/games/color-hunter/score'
const MY_BEST_URL = '/api/games/color-hunter/my-best'
const RANK_URL = '/api/games/color-hunter/rank'
const showRanking = ref(false)
const rankingKey = ref(0)

/** 时间制提交体（越小越好） */
function submitHunterBody(r: any): Record<string, unknown> {
  return {
    bestFinalTime: r.finalTime,
    bestActualTime: r.actualTime,
    lowestErrorCount: r.errorCount,
    fastestRound: r.fastestRound,
  }
}

/** 最佳成绩展示（毫秒 → 秒） */
function displayHunterBest(mb: any): string {
  return mb?.bestFinalTime != null ? `我的最佳 ${(mb.bestFinalTime / 1000).toFixed(2)}s` : ''
}

function formatHunterScore(value: number): string {
  return `${(value / 1000).toFixed(2)}s`
}

let timer: number | null = null
let transitionTimer: number | null = null
let errorTimer: number | null = null

function formatTime(ms: number): string {
  return `${(ms / 1000).toFixed(2)}s`
}

function syncState(): void {
  cells.value = engine.getCells().map((c) => ({ ...c }))
  targetColorName.value = COLOR_NAME[engine.getTargetColor()]
  remaining.value = engine.getRemaining()
  gridSize.value = engine.getGridSize()
  roundLabelText.value = roundLabel(engine.getRoundIndex())
  errorCount.value = engine.getErrorCount()
}

function startGame(): void {
  engine.start()
  view.value = 'playing'
  errorCount.value = 0
  elapsed.value = 0
  syncState()
  startTimer()
}

function startTimer(): void {
  stopTimer()
  timer = window.setInterval(() => {
    elapsed.value = engine.getElapsedTime()
  }, 100)
}

function stopTimer(): void {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

function handleCellClick(index: number): void {
  if (view.value !== 'playing' || showTransition.value) return
  const res = engine.handleCellClick(index)
  if (!res) return

  if (res.correct) {
    syncState()
    if (res.done) {
      if (engine.isFinished()) {
        finishGame()
      } else {
        handleTransition()
      }
    }
  } else {
    // 错误点击：短暂反馈，不改网格
    errorFlashIndex.value = index
    if (errorTimer !== null) clearTimeout(errorTimer)
    errorTimer = window.setTimeout(() => {
      errorFlashIndex.value = null
    }, 180)
  }
}

/** 本轮完成 → 过渡后开始下一轮（Level 切换显示轻量过渡，过渡时间不计入） */
function handleTransition(): void {
  const cur = engine.getRoundIndex()
  const next = ROUND_CONFIGS[cur + 1]
  const curCfg = ROUND_CONFIGS[cur]
  const goNext = (): void => {
    engine.startNextRound()
    syncState()
  }

  if (transitionTimer !== null) clearTimeout(transitionTimer)
  if (next.level !== curCfg.level) {
    showTransition.value = true
    transitionLevel.value = next.level
    transitionGrid.value = `${next.gridSize} × ${next.gridSize}`
    transitionTimer = window.setTimeout(() => {
      showTransition.value = false
      goNext()
    }, 700)
  } else {
    transitionTimer = window.setTimeout(goNext, 350)
  }
}

function finishGame(): void {
  stopTimer()
  const r = engine.finish()
  result.value = r
  colorHunterStore.saveResult(r)
  bestFinalTime.value = colorHunterStore.getBestFinalTime()
  view.value = 'result'
}

function backToHall(): void {
  router.push('/games')
}

onMounted(() => {
  bestFinalTime.value = colorHunterStore.getBestFinalTime()
})

onUnmounted(() => {
  stopTimer()
  if (transitionTimer !== null) clearTimeout(transitionTimer)
  if (errorTimer !== null) clearTimeout(errorTimer)
})
</script>

<template>
  <Header />
  <div class="hunter-page">
    <!-- 规则说明 -->
    <div v-if="view === 'intro'" class="hunter-card intro-card">
      <h1 class="intro-title">颜色猎手</h1>
      <p class="intro-subtitle">找出所有目标颜色</p>
      <p class="intro-desc">根据顶部提示，在方格中找出所有目标颜色。点中后会被标记；全部找完自动进入下一轮。</p>
      <p class="intro-desc">一共 3 个等级、9 轮挑战。</p>
      <p v-if="bestFinalTime > 0" class="intro-best">最佳成绩：{{ formatTime(bestFinalTime) }}</p>
      <button class="btn-start" @click="startGame">开始挑战</button>
    </div>

    <!-- 游戏中 -->
    <div v-else-if="view === 'playing'" class="play-area">
      <div class="play-topbar">
        <button class="top-btn" @click="backToHall">← 返回</button>
        <span class="game-name">颜色猎手</span>
        <span class="round-tag">{{ roundLabelText }}</span>
      </div>

      <div class="target-tip">
        <span>目标：{{ targetColorName }}</span>
      </div>
      <p class="remaining-tip">剩余：{{ remaining }}</p>

      <div class="grid-wrap">
        <div class="grid" :style="{ gridTemplateColumns: `repeat(${gridSize}, 1fr)` }">
          <button
            v-for="(cell, i) in cells"
            :key="i"
            class="grid-cell"
            :class="{
              selected: cell.selected,
              'error-flash': errorFlashIndex === i,
            }"
            :style="{ background: COLOR_CSS[cell.color] }"
            @click="handleCellClick(i)"
          >
            <span v-if="cell.selected" class="cell-check">✓</span>
          </button>
        </div>
      </div>

      <div class="bottom-info">
        <span>用时 {{ formatTime(elapsed) }}</span>
        <span>错误 {{ errorCount }}</span>
      </div>
    </div>

    <!-- 结果 -->
    <div v-else-if="view === 'result' && result" class="hunter-card result-card">
      <h2 class="result-title">挑战完成</h2>
      <p class="result-score">{{ formatTime(result.finalTime) }}</p>
      <div class="result-grid">
        <div class="result-item">
          <span>实际用时</span><b>{{ formatTime(result.actualTime) }}</b>
        </div>
        <div class="result-item">
          <span>错误次数</span><b class="text-bad">{{ result.errorCount }}</b>
        </div>
        <div class="result-item">
          <span>最快一轮</span><b>{{ formatTime(result.fastestRound) }}</b>
        </div>
        <div class="result-item">
          <span>Level 1</span><b>{{ formatTime(result.levelTimes[0]) }}</b>
        </div>
        <div class="result-item">
          <span>Level 2</span><b>{{ formatTime(result.levelTimes[1]) }}</b>
        </div>
        <div class="result-item">
          <span>Level 3</span><b>{{ formatTime(result.levelTimes[2]) }}</b>
        </div>
      </div>
      <BackendRecord
        :score-url="SCORE_URL"
        :my-best-url="MY_BEST_URL"
        :result="result"
        :submit-body="submitHunterBody"
        :best-display="displayHunterBest"
        @saved="rankingKey++"
      />
      <div class="result-actions">
        <button class="btn-secondary" @click="showRanking = true">查看排行榜</button>
        <button class="btn-secondary" @click="backToHall">返回小游戏</button>
        <button class="btn-primary" @click="startGame">再来一次</button>
      </div>
    </div>

    <!-- Level 切换过渡 -->
    <div v-if="showTransition" class="transition-mask">
      <div class="transition-box">
        <p class="transition-level">LEVEL {{ transitionLevel }}</p>
        <p class="transition-grid">{{ transitionGrid }}</p>
      </div>
    </div>

    <ChallengeRanking
      :visible="showRanking"
      :refresh-key="rankingKey"
      :rank-url="RANK_URL"
      score-field="bestFinalTime"
      :format-score="formatHunterScore"
      @close="showRanking = false"
    />
  </div>
  <Footer />
</template>

<style scoped>
.hunter-page {
  max-width: var(--max-width);
  margin: 0 auto;
  padding: 24px 20px;
  min-height: calc(100vh - var(--header-height));
  display: flex;
  flex-direction: column;
}

.hunter-card {
  background: var(--color-card);
  border-radius: 16px;
  box-shadow: var(--shadow);
  padding: 32px;
  max-width: 480px;
  margin: 0 auto;
  width: 100%;
  text-align: center;
}

/* 规则页 */
.intro-card {
  margin-top: 48px;
}
.intro-title {
  font-size: 30px;
  font-weight: 700;
  margin-bottom: 8px;
}
.intro-subtitle {
  color: var(--color-primary);
  margin-bottom: 16px;
}
.intro-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
  line-height: 1.6;
}
.intro-best {
  margin-top: 12px;
  color: var(--color-primary);
  font-size: 14px;
}
.btn-start {
  margin-top: 24px;
  width: 100%;
  padding: 12px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 24px;
  font-size: 16px;
  cursor: pointer;
  transition: opacity 0.2s;
}
.btn-start:hover {
  opacity: 0.9;
}

/* 游戏页 */
.play-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  max-width: 480px;
  margin: 0 auto;
  width: 100%;
}
.play-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.top-btn {
  background: none;
  border: none;
  color: var(--color-primary);
  cursor: pointer;
  font-size: 14px;
  padding: 6px 10px;
}
.game-name {
  font-size: 18px;
  font-weight: 700;
}
.round-tag {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-primary);
}
.target-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
}
.remaining-tip {
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 14px;
  margin: 6px 0 20px;
}

.grid-wrap {
  display: flex;
  justify-content: center;
}
.grid {
  display: grid;
  gap: 8px;
  width: min(100%, 400px);
  aspect-ratio: 1;
}
.grid-cell {
  border: none;
  border-radius: 10px;
  cursor: pointer;
  position: relative;
  transition: opacity 0.15s, box-shadow 0.15s;
  min-height: 44px;
}
.grid-cell:hover {
  box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.25);
}
.grid-cell.selected {
  opacity: 0.35;
  cursor: default;
}
.grid-cell.error-flash {
  box-shadow: 0 0 0 3px var(--color-danger);
}
.cell-check {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 800;
  color: #fff;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.6);
}

.bottom-info {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-top: 20px;
  color: var(--color-text-secondary);
  font-size: 14px;
}

/* 结果页 */
.result-card {
  margin-top: 48px;
}
.result-title {
  font-size: 26px;
  font-weight: 700;
  margin-bottom: 4px;
}
.result-score {
  font-size: 44px;
  font-weight: 800;
  color: var(--color-primary);
  margin: 12px 0 24px;
}
.result-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 28px;
}
.result-item {
  background: var(--color-bg);
  border-radius: 12px;
  padding: 14px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.result-item span {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.result-item b {
  font-size: 15px;
}
.text-bad {
  color: var(--color-danger);
}
.result-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
.btn-primary,
.btn-secondary {
  flex: 1;
  padding: 10px 16px;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
}
.btn-primary {
  background: var(--color-primary);
  color: #fff;
}
.btn-secondary {
  background: var(--color-hover);
  color: var(--color-text);
}

/* Level 切换过渡 */
.transition-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.transition-box {
  background: var(--color-card);
  border-radius: 16px;
  padding: 32px 48px;
  text-align: center;
}
.transition-level {
  font-size: 30px;
  font-weight: 800;
  color: var(--color-primary);
}
.transition-grid {
  font-size: 18px;
  color: var(--color-text-secondary);
  margin-top: 8px;
}
</style>
