<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import Header from '@/components/Header.vue'
import Footer from '@/components/Footer.vue'
import { FishGameEngine } from '../engine/FishGameEngine'
import {
  BOARD_ROWS,
  BOARD_COLS,
  GAME_DURATION_MS,
  POOL_TRANSITION_MS,
  EXIT_ANIMATION_MS,
  DIFFICULTY,
  MISTAKES_LIMIT,
} from '../config'
import type { PoolResult } from '../engine/types'
import { fishBreakoutStore } from '../stores/fishBreakoutStore'
import FishBoard from './FishBoard.vue'
import BackendRecord from '../../common/components/BackendRecord.vue'
import ChallengeRanking from '../../common/components/ChallengeRanking.vue'

type ViewState = 'intro' | 'countdown' | 'playing' | 'result'

const router = useRouter()

/** 开发模式标识（模板中不可直接访问 import.meta，这里暴露到 setup 绑定） */
const isDev = import.meta.env.DEV

const engine = new FishGameEngine({
  rows: BOARD_ROWS,
  cols: BOARD_COLS,
  difficulty: DIFFICULTY,
  durationMs: GAME_DURATION_MS,
  mistakesLimit: MISTAKES_LIMIT,
})

const view = ref<ViewState>('intro')
const countdown = ref(3)
/** 剩余秒数：仅在整秒变化时更新，避免每 100ms 触发一次整页重渲染 */
const remainingSeconds = ref(GAME_DURATION_MS / 1000)
const result = ref<PoolResult | null>(null)
const best = ref(fishBreakoutStore.best())
const isNewBest = ref(false)

// ── 服务端成绩：保存 + 排行榜 ──
const SCORE_URL = '/api/games/fish-breakout/score'
const MY_BEST_URL = '/api/games/fish-breakout/my-best'
const RANK_URL = '/api/games/fish-breakout/rank'
const showRanking = ref(false)
const rankingKey = ref(0)

/** 鱼群突围成绩提交体 */
function fishBreakoutSubmitBody(r: object): Record<string, unknown> {
  const res = r as PoolResult
  return {
    clearedPools: res.clearedPools,
    releasedFish: res.releasedFish,
    mistakes: res.mistakes,
    duration: res.duration,
  }
}
/** 我的最佳展示文案 */
function fishBreakoutBestDisplay(mb: any): string {
  if (mb?.bestClearedPools == null) return ''
  return `我的最佳：清空 ${mb.bestClearedPools} 池 · 放生 ${mb.bestReleasedFish} 条`
}
/** 排行榜数值：清空池数 */
function formatRankScore(v: number): string {
  return `${v} 池`
}

const animatingIds = ref<string[]>([])
const mistakeId = ref<string | null>(null)
const poolTransition = ref(false)

// 引擎是普通类（非响应式），用 tick 触发 computed 重算
const tick = ref(0)

const board = computed(() => {
  void tick.value
  return engine.getState().currentBoard
})
const hud = computed(() => {
  void tick.value
  const s = engine.getState()
  return { clearedPools: s.clearedPools, releasedFish: s.releasedFish, mistakes: s.mistakes }
})
const renderFishes = computed(() => {
  void tick.value
  const b = board.value
  if (!b) return []
  return b.fishes.filter((f) => f.status === 'ACTIVE' || animatingIds.value.includes(f.id))
})
const accuracy = computed(() => {
  const r = result.value
  if (!r) return 0
  return r.releasedFish + r.mistakes > 0 ? r.releasedFish / (r.releasedFish + r.mistakes) : 0
})

// ── debug（仅开发环境）──
const showDebug = ref(false)
const highlightFree = ref(false)
const freeIds = computed(() => {
  // 仅 debug 高亮时才计算（canExit 是 O(n²)，生产环境不做无用功）
  if (!highlightFree.value) return []
  void tick.value
  const b = board.value
  if (!b) return []
  return b.fishes.filter((f) => f.status === 'ACTIVE' && engine.canExit(f.id)).map((f) => f.id)
})
const solutionOrder = computed(() => board.value?.solutionOrder ?? [])

let timer: number | null = null
let countdownTimer: number | null = null
/** 每条鱼独立离场计时器：快速连续点击互不干扰，避免鱼卡在隐形状态 */
const exitTimers = new Map<string, number>()
let poolTimer: number | null = null
let mistakeTimer: number | null = null

function startGame(): void {
  if (countdownTimer || timer) return
  view.value = 'countdown'
  countdown.value = 3
  clearInterval(countdownTimer ?? undefined)
  countdownTimer = window.setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(countdownTimer ?? undefined)
      countdownTimer = null
      beginPlaying()
    }
  }, 1000)
}

function beginPlaying(): void {
  engine.start()
  tick.value++
  remainingSeconds.value = Math.max(0, Math.ceil(engine.remainingTimeMs() / 1000))
  view.value = 'playing'
  poolTransition.value = false
  animatingIds.value = []
  mistakeId.value = null
  startTimer()
}

function startTimer(): void {
  stopTimer()
  timer = window.setInterval(() => {
    if (!engine.isRunning()) return
    const remain = engine.remainingTimeMs()
    // 整秒变化时才更新显示，减少无谓重渲染
    const sec = Math.max(0, Math.ceil(remain / 1000))
    if (sec !== remainingSeconds.value) remainingSeconds.value = sec
    if (remain <= 0) {
      showResult()
    }
  }, 100)
}

function stopTimer(): void {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

function onCellTap(id: string): void {
  if (view.value !== 'playing' || poolTransition.value) return
  const res = engine.tapFish(id)
  if (res === 'ignore') return
  tick.value++

  // 失误达上限已在引擎内提前结算
  if (engine.isFinished()) {
    showResult()
    return
  }

  if (res === 'fail') {
    mistakeId.value = id
    clearTimeout(mistakeTimer ?? undefined)
    mistakeTimer = window.setTimeout(() => (mistakeId.value = null), 200)
    return
  }

  // success：立即逻辑移除已在引擎内完成，此处只播离场动画（每条鱼独立计时器）
  animatingIds.value = [...animatingIds.value, id]
  clearTimeout(exitTimers.get(id))
  exitTimers.set(
    id,
    window.setTimeout(() => {
      exitTimers.delete(id)
      animatingIds.value = animatingIds.value.filter((x) => x !== id)
      // 若最后一鱼放生导致清池，进入池切换过渡
      if (engine.getState().status === 'transitioning') {
        poolTransition.value = true
        clearTimeout(poolTimer ?? undefined)
        poolTimer = window.setTimeout(() => {
          engine.startNextPool()
          tick.value++
          poolTransition.value = false
        }, POOL_TRANSITION_MS)
      }
    }, EXIT_ANIMATION_MS),
  )
}

/** 结算并进入结果页（时间到 / 失误达上限，引擎 finish 幂等） */
function showResult(): void {
  if (view.value === 'result') return
  stopTimer()
  const r = engine.finish()
  result.value = r
  const prevBest = fishBreakoutStore.best()
  isNewBest.value =
    r.clearedPools > prevBest.clearedPools ||
    (r.clearedPools === prevBest.clearedPools && r.releasedFish > prevBest.releasedFish) ||
    (r.clearedPools === prevBest.clearedPools &&
      r.releasedFish === prevBest.releasedFish &&
      r.mistakes < prevBest.mistakes)
  fishBreakoutStore.saveResult(r)
  best.value = fishBreakoutStore.best()
  view.value = 'result'
}

/** 回前台立即按真实时间重算；已超时直接结算（PRD §20） */
function onVisibilityChange(): void {
  if (document.hidden) return
  if (view.value !== 'playing') return
  if (!engine.isRunning()) return
  remainingSeconds.value = Math.max(0, Math.ceil(engine.remainingTimeMs() / 1000))
  if (engine.remainingTimeMs() <= 0) {
    showResult()
  }
}

function backToHall(): void {
  router.push('/games')
}

// ── debug 面板操作 ──
function debugRegenerate(): void {
  engine.debugRegeneratePool()
  tick.value++
}
function debugExtendTime(): void {
  engine.debugExtendTime(15_000)
  tick.value++
}

onMounted(() => {
  best.value = fishBreakoutStore.best()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  stopTimer()
  clearInterval(countdownTimer ?? undefined)
  exitTimers.forEach((t) => clearTimeout(t))
  exitTimers.clear()
  clearTimeout(poolTimer ?? undefined)
  clearTimeout(mistakeTimer ?? undefined)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <Header />
  <div class="fish-page">
    <!-- 规则说明（单一规则页：含全部规则 + 开始按钮） -->
    <div v-if="view === 'intro'" class="fish-card intro-card">
      <h1 class="intro-title">鱼群突围</h1>
      <p class="intro-rule">① 点击前方没有其他鱼的鱼儿，它就会游出去。</p>
      <p class="intro-rule">② 被挡住的鱼需要等前面的鱼先走，别点错。</p>
      <p class="intro-rule">③ 失误 {{ MISTAKES_LIMIT }} 次，本局提前结束。</p>
      <p class="intro-subtitle">30 秒，看你能清空几池！</p>
      <p v-if="best.clearedPools > 0" class="intro-best">
        最佳纪录：清空 {{ best.clearedPools }} 池 / 放生 {{ best.releasedFish }} 条
      </p>
      <button class="btn-start" @click="startGame">开始游戏</button>
    </div>

    <!-- 3、2、1 倒计时 -->
    <div v-else-if="view === 'countdown'" class="countdown-view">
      <div class="countdown-num">{{ countdown }}</div>
      <p class="countdown-tip">准备，找出能出去的鱼</p>
    </div>

    <!-- 游戏中 -->
    <div v-else-if="view === 'playing'" class="play-area">
      <div class="play-topbar">
        <button class="top-btn" @click="backToHall">← 返回</button>
        <span class="game-name">鱼群突围</span>
        <span class="timer" :class="{ 'timer-hurry': remainingSeconds <= 5 && remainingSeconds > 0 }">
          {{ remainingSeconds }}s
        </span>
      </div>

      <div class="hud">
        <span>清空 <b>{{ hud.clearedPools }}</b> 池</span>
        <span>放生 <b>{{ hud.releasedFish }}</b> 条</span>
        <span>失误 <b>{{ hud.mistakes }}</b></span>
      </div>

      <div class="board-wrap">
        <FishBoard
          :rows="BOARD_ROWS"
          :cols="BOARD_COLS"
          :fishes="renderFishes"
          :animating-ids="animatingIds"
          :mistake-id="mistakeId"
          :highlight-free="highlightFree"
          :free-ids="freeIds"
          @tap="onCellTap"
        />
        <!-- 清池过渡层 -->
        <div v-if="poolTransition" class="pool-mask">
          <span>鱼池清空！</span>
        </div>
      </div>

      <p class="play-hint">点击前方没有其他鱼的鱼儿，让它游出去</p>
    </div>

    <!-- 结果 -->
    <div v-else-if="view === 'result' && result" class="fish-card result-card">
      <h2 class="result-title">{{ result.endedBy === 'mistakes' ? '挑战失败' : '时间到！' }}</h2>
      <p v-if="result.endedBy === 'mistakes'" class="result-fail-tip">
        失误 {{ result.mistakes }} 次，本局提前结束
      </p>
      <p class="result-score">{{ result.clearedPools }}<small> 池</small></p>
      <div class="result-grid">
        <div class="result-item"><span>成功放生</span><b>{{ result.releasedFish }} 条</b></div>
        <div class="result-item"><span>失误</span><b>{{ result.mistakes }} 次</b></div>
        <div class="result-item"><span>准确率</span><b>{{ Math.round(accuracy * 100) }}%</b></div>
      </div>
      <p v-if="isNewBest" class="result-new">新纪录！</p>
      <p class="result-best">最佳纪录：清空 {{ best.clearedPools }} 池 / 放生 {{ best.releasedFish }} 条</p>

      <!-- 服务端成绩：手动保存 + 我的最佳 -->
      <BackendRecord
        :manual="true"
        :score-url="SCORE_URL"
        :my-best-url="MY_BEST_URL"
        :result="result"
        :submit-body="fishBreakoutSubmitBody"
        :best-display="fishBreakoutBestDisplay"
        @saved="rankingKey++"
      />

      <div class="result-actions">
        <button class="btn-secondary" @click="showRanking = true">查看排行榜</button>
        <button class="btn-secondary" @click="backToHall">返回小游戏</button>
        <button class="btn-primary" @click="startGame">再来一局</button>
      </div>
    </div>

    <!-- 排行榜弹层 -->
    <ChallengeRanking
      :visible="showRanking"
      :refresh-key="rankingKey"
      :rank-url="RANK_URL"
      score-field="clearedPools"
      :format-score="formatRankScore"
      @close="showRanking = false"
    />

    <!-- 开发模式调试面板 -->
    <details v-if="showDebug" class="debug-panel">
      <summary>Debug（仅开发）</summary>
      <div class="debug-row">
        <label><input type="checkbox" v-model="highlightFree" /> 高亮可出鱼</label>
        <button @click="debugRegenerate">重新生成当前池</button>
        <button @click="debugExtendTime">+15s</button>
      </div>
      <div class="debug-solution">
        移除顺序：{{ solutionOrder.join(' → ') || '—' }}
      </div>
    </details>
    <button v-if="isDev" class="debug-toggle" @click="showDebug = !showDebug">
      {{ showDebug ? '关闭 Debug' : 'Debug' }}
    </button>
  </div>
  <Footer />
</template>

<style scoped>
.fish-page {
  max-width: var(--max-width);
  margin: 0 auto;
  padding: 24px 20px;
  min-height: calc(100vh - var(--header-height));
  display: flex;
  flex-direction: column;
}

.fish-card {
  background: var(--color-card);
  border-radius: 16px;
  box-shadow: var(--shadow);
  padding: 32px;
  max-width: 480px;
  margin: 48px auto 0;
  width: 100%;
  text-align: center;
}
.intro-title {
  font-size: 30px;
  font-weight: 700;
  margin-bottom: 8px;
}
.intro-rule {
  color: var(--color-text-secondary);
  line-height: 1.8;
  margin-bottom: 6px;
  text-align: left;
}
.intro-subtitle {
  color: var(--color-primary);
  font-weight: 600;
  margin-top: 10px;
  margin-bottom: 16px;
}
.intro-best {
  color: var(--color-text-secondary);
  font-size: 14px;
  margin-bottom: 8px;
}
.btn-start {
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

/* 倒计时 */
.countdown-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}
.countdown-num {
  font-size: 120px;
  font-weight: 800;
  color: var(--color-primary);
  line-height: 1;
}
.countdown-tip {
  color: var(--color-text-secondary);
}

/* 游戏中 */
.play-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  max-width: 520px;
  margin: 0 auto;
  width: 100%;
}
.play-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
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
.timer {
  font-size: 18px;
  font-weight: 700;
  min-width: 56px;
  text-align: right;
}
.timer-hurry {
  color: var(--color-danger);
}

.hud {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-bottom: 16px;
  font-size: 14px;
  color: var(--color-text-secondary);
}
.hud b {
  color: var(--color-text);
  font-size: 16px;
}

.board-wrap {
  position: relative;
}
.pool-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(240, 242, 245, 0.6);
  border-radius: 16px;
  font-size: 24px;
  font-weight: 700;
  color: var(--color-primary);
  pointer-events: none;
}

.play-hint {
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 13px;
  margin-top: 16px;
}

/* 结果 */
.result-card {
  margin-top: 40px;
}
.result-title {
  font-size: 26px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 8px;
}
.result-score {
  font-size: 60px;
  font-weight: 800;
  color: var(--color-primary);
  margin: 8px 0 20px;
}
.result-score small {
  font-size: 24px;
  font-weight: 600;
}
.result-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
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
  font-size: 18px;
}
.result-new {
  color: #27ae60;
  font-weight: 700;
  margin-bottom: 8px;
}
.result-fail-tip {
  color: var(--color-danger);
  font-weight: 600;
  margin-bottom: 12px;
}
.result-best {
  color: var(--color-text-secondary);
  font-size: 14px;
  margin-bottom: 20px;
}
.result-actions {
  display: flex;
  gap: 12px;
}
.result-actions button {
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

/* debug */
.debug-toggle {
  position: fixed;
  right: 12px;
  bottom: 12px;
  z-index: 90;
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text-secondary);
  border-radius: 10px;
  cursor: pointer;
}
.debug-panel {
  margin-top: 16px;
  padding: 10px 12px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.debug-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin: 6px 0;
}
.debug-row button {
  padding: 2px 8px;
  font-size: 12px;
}
.debug-solution {
  word-break: break-all;
}
</style>
