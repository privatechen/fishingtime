<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import Header from '@/components/Header.vue'
import Footer from '@/components/Footer.vue'
import { ExtremeFishingEngine } from '../engine/ExtremeFishingEngine'
import { EXTREME_FISHING_CONFIG } from '../config'
import type { GameResult, Selection } from '../engine/types'
import { extremeFishingStore } from '../stores/extremeFishingStore'
import FishingBoard from './FishingBoard.vue'
import BackendRecord from '../../common/components/BackendRecord.vue'
import ChallengeRanking from '../../common/components/ChallengeRanking.vue'

type ViewState = 'intro' | 'playing' | 'result'

const router = useRouter()
const engine = new ExtremeFishingEngine(EXTREME_FISHING_CONFIG)

const view = ref<ViewState>('intro')
const remainingSeconds = ref(EXTREME_FISHING_CONFIG.durationMs / 1000)
const result = ref<GameResult | null>(null)
const best = ref(extremeFishingStore.best())
const isNewBest = ref(false)
const tick = ref(0)

const board = computed(() => {
  void tick.value
  return engine.getState().board
})
const hud = computed(() => {
  void tick.value
  const s = engine.getState()
  return { score: s.score, combo: s.combo, pufferMistakes: s.pufferMistakes }
})

/** 撒网反馈（得分 / PERFECT / 河豚） */
const feedback = ref<{ text: string; kind: 'ok' | 'perfect' | 'bad' } | null>(null)

// ── 服务端成绩：保存 + 排行榜 ──
const SCORE_URL = '/api/games/extreme-fishing/score'
const MY_BEST_URL = '/api/games/extreme-fishing/my-best'
const RANK_URL = '/api/games/extreme-fishing/rank'
const showRanking = ref(false)
const rankingKey = ref(0)

let timer: number | null = null
let feedbackTimer: number | null = null

function startGame(): void {
  if (timer) return
  engine.start()
  tick.value++
  view.value = 'playing'
  remainingSeconds.value = Math.max(0, Math.ceil(engine.remainingTimeMs() / 1000))
  startTimer()
}

function startTimer(): void {
  stopTimer()
  timer = window.setInterval(() => {
    if (!engine.isRunning()) return
    const remain = engine.remainingTimeMs()
    const sec = Math.max(0, Math.ceil(remain / 1000))
    if (sec !== remainingSeconds.value) remainingSeconds.value = sec
    if (remain <= 0) showResult()
  }, 100)
}
function stopTimer(): void {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

function onNet(selection: Selection): void {
  if (view.value !== 'playing' || !engine.isRunning()) return
  const res = engine.resolveNet(selection)
  tick.value++
  if (res.type === 'puffer') {
    showFeedback('河豚！Combo 中断', 'bad')
  } else if (res.type === 'success' && res.perfect) {
    showFeedback(`PERFECT +${res.gained}`, 'perfect')
  } else if (res.type === 'success') {
    showFeedback(`+${res.gained}`, 'ok')
  }
  // 河豚失误达上限已提前结算
  if (engine.isFinished()) {
    showResult()
  }
}

function showFeedback(text: string, kind: 'ok' | 'perfect' | 'bad'): void {
  feedback.value = { text, kind }
  if (feedbackTimer) clearTimeout(feedbackTimer)
  feedbackTimer = window.setTimeout(() => (feedback.value = null), 700)
}

function showResult(): void {
  if (view.value === 'result') return
  stopTimer()
  const r = engine.finish()
  result.value = r
  const prevBest = extremeFishingStore.best()
  isNewBest.value = r.score > prevBest.score
  extremeFishingStore.saveResult(r)
  best.value = extremeFishingStore.best()
  view.value = 'result'
}

/** 回前台按真实时间重算；已超时直接结算（后台不暂停） */
function onVisibilityChange(): void {
  if (document.hidden) return
  if (view.value !== 'playing') return
  if (!engine.isRunning()) return
  remainingSeconds.value = Math.max(0, Math.ceil(engine.remainingTimeMs() / 1000))
  if (engine.remainingTimeMs() <= 0) showResult()
}

function backToHall(): void {
  router.push('/games')
}

/** 成绩提交体（与后端 DTO 对齐） */
function extremeSubmitBody(r: object): Record<string, unknown> {
  const g = r as GameResult
  return {
    score: g.score,
    caughtFish: g.caughtFish,
    perfectCount: g.perfectCount,
    maxCombo: g.maxCombo,
    pufferMistakes: g.pufferMistakes,
  }
}
function extremeBestDisplay(mb: any): string {
  if (mb?.bestScore == null) return ''
  return `我的最佳 ${mb.bestScore} 分`
}
function formatRankScore(v: number): string {
  return String(v)
}

onMounted(() => {
  best.value = extremeFishingStore.best()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  stopTimer()
  if (feedbackTimer) clearTimeout(feedbackTimer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <Header />
  <div class="fish-page">
    <!-- 规则说明 -->
    <div v-if="view === 'intro'" class="fish-card intro-card">
      <h1 class="intro-title">极限捞鱼</h1>
      <p class="intro-rule">拖动框出渔网，框住鱼，别框河豚。</p>
      <p class="intro-rule">网得越密，分越高；连续撒网有 Combo。</p>
      <p class="intro-subtitle">30 秒，看你能捞多少！</p>
      <p v-if="best.score > 0" class="intro-best">最佳纪录：{{ best.score }} 分</p>
      <button class="btn-start" @click="startGame">开始游戏</button>
    </div>

    <!-- 游戏中 -->
    <div v-else-if="view === 'playing'" class="play-area">
      <div class="play-topbar">
        <button class="top-btn" @click="backToHall">← 返回</button>
        <span class="game-name">极限捞鱼</span>
        <span class="top-placeholder" />
      </div>

      <div class="hud">
        <span class="hud-item">
          得分<br /><b>{{ hud.score }}</b>
        </span>
        <span class="timer-big" :class="{ 'timer-hurry': remainingSeconds <= 5 }">
          {{ remainingSeconds }}s
        </span>
        <span class="hud-item">
          河豚失误<br /><b>{{ hud.pufferMistakes }}/3</b>
        </span>
      </div>

      <div class="board-wrap">
        <FishingBoard
          :board="board"
          :rows="EXTREME_FISHING_CONFIG.rows"
          :cols="EXTREME_FISHING_CONFIG.cols"
          :disabled="view !== 'playing'"
          @net="onNet"
        />
        <div v-if="feedback" class="net-feedback" :class="`fb-${feedback.kind}`">
          {{ feedback.text }}
        </div>
      </div>

      <p class="play-hint">按住拖动撒网 · 框住鱼，别框河豚</p>
    </div>

    <!-- 结果 -->
    <div v-else-if="view === 'result' && result" class="fish-card result-card">
      <h2 class="result-title">{{ result.endedBy === 'puffer' ? '挑战失败' : '时间到！' }}</h2>
      <p v-if="result.endedBy === 'puffer'" class="result-fail">
        河豚失误 {{ result.pufferMistakes }} 次，本局提前结束
      </p>
      <p class="result-score">{{ result.score }}<small> 分</small></p>
      <div class="result-grid">
        <div class="result-item"><span>捕获鱼</span><b>{{ result.caughtFish }} 条</b></div>
        <div class="result-item"><span>PERFECT NET</span><b>{{ result.perfectCount }} 次</b></div>
        <div class="result-item"><span>最高 Combo</span><b>×{{ result.maxCombo }}</b></div>
        <div class="result-item"><span>河豚失误</span><b>{{ result.pufferMistakes }} 次</b></div>
      </div>
      <p v-if="isNewBest" class="result-new">新纪录！</p>
      <p v-if="best.score > 0" class="result-best">最佳纪录：{{ best.score }} 分</p>

      <BackendRecord
        :manual="true"
        :score-url="SCORE_URL"
        :my-best-url="MY_BEST_URL"
        :result="result"
        :submit-body="extremeSubmitBody"
        :best-display="extremeBestDisplay"
        @saved="rankingKey++"
      />

      <div class="result-actions">
        <button class="btn-secondary" @click="showRanking = true">查看排行榜</button>
        <button class="btn-secondary" @click="backToHall">返回小游戏</button>
        <button class="btn-primary" @click="startGame">再来一局</button>
      </div>
    </div>

    <ChallengeRanking
      :visible="showRanking"
      :refresh-key="rankingKey"
      :rank-url="RANK_URL"
      score-field="score"
      :format-score="formatRankScore"
      @close="showRanking = false"
    />
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
  margin-bottom: 16px;
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
.top-placeholder {
  width: 56px;
}

/* HUD：左侧得分、中间大倒计时、右侧河豚失误 */
.hud {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.hud-item {
  flex: 1;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}
.hud-item b {
  font-size: 20px;
  color: var(--color-text);
}
.timer-big {
  flex: 1;
  text-align: center;
  font-size: 30px;
  font-weight: 800;
  color: var(--color-text);
  line-height: 1.2;
  transition: transform 0.2s, color 0.2s;
}
.timer-hurry {
  color: var(--color-danger);
  transform: scale(1.15);
}

.board-wrap {
  position: relative;
}
.net-feedback {
  position: absolute;
  top: 40%;
  left: 50%;
  transform: translateX(-50%);
  font-size: 22px;
  font-weight: 800;
  padding: 8px 20px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow);
  pointer-events: none;
  z-index: 10;
  white-space: nowrap;
}
.fb-ok {
  color: #2d8cf0;
}
.fb-perfect {
  color: #f39c12;
}
.fb-bad {
  color: var(--color-danger);
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
}
.result-fail {
  margin-top: 8px;
  font-size: 14px;
  color: var(--color-danger);
}
.result-score {
  font-size: 56px;
  font-weight: 800;
  color: var(--color-primary);
  margin: 8px 0 20px;
}
.result-score small {
  font-size: 22px;
  font-weight: 600;
}
.result-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
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
</style>
