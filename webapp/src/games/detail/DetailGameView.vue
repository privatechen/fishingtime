<script setup lang="ts">
/**
 * 《细节》游戏视图 — 状态机：READY → OBSERVING → DRAWING → ANSWERING → RESULT → REVIEW → NEXT → FINISHED
 *
 * - 观察 10 秒（图片加载完成才开始计时；真实时间戳防 setInterval 漂移）
 * - 图片消失 → 6 张盲选题号卡 → 选中后服务端抽题
 * - 题目出现开始答题计时，上限 8 秒，超时自动作答（服务端判超时）
 * - 每题结束可回看图片（回看不计入答题用时）
 * - 5 轮结算：答对数优先、同分比累计用时；登录则保存成绩并展示今日/总排名
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import Header from '@/components/Header.vue'
import Footer from '@/components/Footer.vue'
import { useAuth } from '@/stores/auth'
import {
  detailApi,
  type DetailStartResponse,
  type DetailDrawResponse,
  type DetailAnswerResponse,
  type DetailFinishResponse,
} from './detailSession'
import DetailRanking from './DetailRanking.vue'

const OBSERVE_MS = 10_000
const ANSWER_MS = 8_000

type Phase = 'intro' | 'observing' | 'drawing' | 'answering' | 'result' | 'review' | 'finished'

const router = useRouter()
const { isLoggedIn, login, register } = useAuth()

const phase = ref<Phase>('intro')
const session = ref<DetailStartResponse | null>(null)
const roundIndex = ref(0)
const remainingMs = ref(0)
const imageReady = ref(false)
const imageError = ref(false)
const reloadKey = ref(0)
const drawResp = ref<DetailDrawResponse | null>(null)
const answerResp = ref<DetailAnswerResponse | null>(null)
const finishResp = ref<DetailFinishResponse | null>(null)
const submitting = ref(false)
const answerError = ref('')
const finishError = ref('')
const showRanking = ref(false)
const rankingKey = ref(0)
const showQuitConfirm = ref(false)
const roundResults = ref<Array<{ correct: boolean; elapsedMs: number }>>([])

let ticker: number | null = null
let observeEndAt = 0
let answerEndAt = 0

const currentRound = computed(() => session.value?.rounds[roundIndex.value] ?? null)
const roundNum = computed(() => roundIndex.value + 1)
const remainingSeconds = computed(() => Math.max(0, Math.ceil(remainingMs.value / 1000)))
const elapsedDisplay = computed(() => {
  const v = answerResp.value?.elapsedMs ?? 0
  return (v / 1000).toFixed(1)
})
const correctCountNow = computed(() => roundResults.value.filter((r) => r?.correct).length)
/** 是否在游戏中（非开始页/结算页） */
const inGame = computed(() => phase.value !== 'intro' && phase.value !== 'finished')
/** 已作答轮次的累计用时（实时成绩展示） */
const liveElapsedText = computed(() => {
  const ms = roundResults.value.reduce((s, r) => s + (r?.elapsedMs ?? 0), 0)
  return (ms / 1000).toFixed(1)
})

// ────────────── 通用计时 ──────────────

function startTicker(onTick: () => void): void {
  stopTicker()
  ticker = window.setInterval(() => onTick(), 100)
}

function stopTicker(): void {
  if (ticker !== null) {
    clearInterval(ticker)
    ticker = null
  }
}

// ────────────── 开局 / 观察 ──────────────

async function startGame(): Promise<void> {
  submitting.value = true
  answerError.value = ''
  finishError.value = ''
  try {
    const s = await detailApi.start()
    session.value = s
    roundIndex.value = 0
    roundResults.value = []
    finishResp.value = null
    drawResp.value = null
    answerResp.value = null
    imageReady.value = false
    imageError.value = false
    phase.value = 'observing'
  } catch {
    answerError.value = '开局失败，请重试'
  } finally {
    submitting.value = false
  }
}

function onImageLoad(): void {
  if (phase.value !== 'observing' || imageReady.value) return
  imageReady.value = true
  // 图片加载完成才开始观察倒计时（A23）
  observeEndAt = performance.now() + OBSERVE_MS
  startTicker(() => {
    remainingMs.value = observeEndAt - performance.now()
    if (remainingMs.value <= 0) {
      stopTicker()
      // 图片隐藏 → 进入盲选题号
      phase.value = 'drawing'
      drawResp.value = null
      answerResp.value = null
    }
  })
}

function onImageError(): void {
  imageError.value = true
  imageReady.value = false
  stopTicker()
}

function retryImage(): void {
  imageError.value = false
  reloadKey.value++
}

// ────────────── 抽题 / 作答 ──────────────

function pickNumber(n: number): void {
  if (phase.value !== 'drawing' || submitting.value || !session.value) return
  submitting.value = true
  answerError.value = ''
  detailApi
    .draw(session.value.sessionId, roundNum.value, n)
    .then((resp) => {
      drawResp.value = resp
      phase.value = 'answering'
      beginAnswer()
    })
    .catch(() => {
      answerError.value = '抽题失败，请重试'
    })
    .finally(() => {
      submitting.value = false
    })
}

function beginAnswer(): void {
  // 题目出现开始答题计时，上限 8 秒；超时自动作答（服务端按超时判错）
  answerEndAt = performance.now() + ANSWER_MS
  startTicker(() => {
    remainingMs.value = answerEndAt - performance.now()
    if (remainingMs.value <= 0) {
      stopTicker()
      void submitAnswer(null)
    }
  })
}

async function submitAnswer(option: string | null): Promise<void> {
  if (!session.value || submitting.value) return
  submitting.value = true
  answerError.value = ''
  try {
    const resp = await detailApi.answer(session.value.sessionId, roundNum.value, option)
    stopTicker()
    answerResp.value = resp
    roundResults.value[roundIndex.value] = { correct: resp.correct, elapsedMs: resp.elapsedMs }
    phase.value = 'result'
  } catch {
    // 答题接口失败不判错（A24）：服务端计时仍在继续，超时由服务端判定
    answerError.value = '提交失败，请重试'
  } finally {
    submitting.value = false
  }
}

// ────────────── 回看 / 下一轮 ──────────────

function goReview(): void {
  phase.value = 'review'
}

function goNextRound(): void {
  if (roundIndex.value + 1 < (session.value?.rounds.length ?? 0)) {
    roundIndex.value++
    drawResp.value = null
    answerResp.value = null
    imageReady.value = false
    imageError.value = false
    phase.value = 'observing'
  } else {
    void finishGame()
  }
}

// ────────────── 中途结束 / 放弃 ──────────────

/** 结束并保存：停掉进行中的计时，提前结算（未作答且未抽题的轮次不计入） */
function endAndSave(): void {
  stopTicker()
  void finishGame()
}

/** 顶部「返回」：弹确认框，防止误触丢失进度 */
function quitGame(): void {
  showQuitConfirm.value = true
}

function confirmQuit(): void {
  showQuitConfirm.value = false
  stopTicker()
  backToHall()
}

function cancelQuit(): void {
  showQuitConfirm.value = false
}

// ────────────── 结算 ──────────────

async function finishGame(): Promise<void> {
  if (!session.value || submitting.value) return
  submitting.value = true
  finishError.value = ''
  try {
    const resp = await detailApi.finish(session.value.sessionId)
    finishResp.value = resp
    rankingKey.value++
    phase.value = 'finished'
  } catch {
    finishError.value = '结算失败，请重试'
  } finally {
    submitting.value = false
  }
}

// ────────────── 未登录补存（注册/登录后重新 finish） ──────────────

const authMode = ref<'register' | 'login'>('register')
const authUsername = ref('')
const authPassword = ref('')
const authConfirm = ref('')
const authError = ref('')
const authSubmitting = ref(false)

function switchAuthMode(mode: 'register' | 'login'): void {
  authMode.value = mode
  authError.value = ''
}

async function handleAuthSubmit(): Promise<void> {
  authError.value = ''
  if (!authUsername.value.trim()) {
    authError.value = '请输入账号'
    return
  }
  if (!authPassword.value) {
    authError.value = '请输入密码'
    return
  }
  if (authMode.value === 'register' && authPassword.value !== authConfirm.value) {
    authError.value = '两次输入的密码不一致'
    return
  }
  authSubmitting.value = true
  try {
    const err =
      authMode.value === 'register'
        ? await register({
            username: authUsername.value.trim(),
            password: authPassword.value,
            nickname: authUsername.value.trim(),
          })
        : await login({ username: authUsername.value.trim(), password: authPassword.value })
    if (err) {
      authError.value = err
      return
    }
    // 登录成功后对本局重新结算，补存成绩
    await finishGame()
  } finally {
    authSubmitting.value = false
  }
}

function backToHall(): void {
  router.push('/games')
}

onMounted(() => {
  const auth = useAuth()
  if (auth.isLoggedIn.value === false) {
    void auth.checkAuth()
  }
})

onUnmounted(stopTicker)
</script>

<template>
  <Header />
  <div class="detail-page">
    <!-- ─────── 实时成绩条 ─────── -->
    <div v-if="inGame" class="live-strip">
      <span>已答对 <b>{{ correctCountNow }}</b> 题</span>
      <span class="live-dot">·</span>
      <span>累计用时 <b>{{ liveElapsedText }}</b>s</span>
    </div>

    <!-- ─────── 开始页 ─────── -->
    <div v-if="phase === 'intro'" class="card intro-card">
      <h1 class="intro-title">细节</h1>
      <p class="intro-subtitle">看 10 秒 → 图片消失 → 盲选题号 → 看你能记住多少细节</p>
      <div class="rule-list">
        <div class="rule-item"><b>看图</b><span>每张图观察 10 秒</span></div>
        <div class="rule-item"><b>抽题</b><span>图片消失后，盲选一个题号（1~6）</span></div>
        <div class="rule-item"><b>作答</b><span>四选一，每题限时 8 秒</span></div>
        <div class="rule-item"><b>计分</b><span>答对记 1 题，答错/超时不计</span></div>
        <div class="rule-item"><b>排名</b><span>先比答对题数，相同再比累计用时（仅统计题目出现到提交的耗时）</span></div>
      </div>
      <p class="intro-duration">共 5 轮 · 每轮约 20 秒 · 中途可随时结束并保存</p>
      <button class="btn-primary" :disabled="submitting" @click="startGame">
        {{ submitting ? '开局中...' : '开始游戏' }}
      </button>
      <button class="btn-secondary" @click="showRanking = true">查看排行榜</button>
      <p v-if="answerError" class="error-text">{{ answerError }}</p>
    </div>

    <!-- ─────── 观察阶段 ─────── -->
    <div v-else-if="phase === 'observing'" class="observe-area">
      <div class="topbar">
        <button class="top-btn" @click="quitGame">← 返回</button>
        <span class="game-name">第 {{ roundNum }}/5 轮</span>
        <!-- 观察倒计时统一用图片上叠加的大号数字，顶部栏不再重复显示 -->
        <span class="timer"></span>
      </div>
      <p class="observe-tip">仔细观察，记住细节，题目稍后揭晓</p>
      <div class="image-wrap">
        <img
          v-if="currentRound"
          :key="reloadKey"
          :src="currentRound.imageUrl"
          class="detail-image"
          @load="onImageLoad"
          @error="onImageError"
        />
        <!-- 图片上叠加的大号观察倒计时 -->
        <div
          v-if="imageReady"
          class="countdown-overlay"
          :class="{ 'countdown-hurry': remainingSeconds <= 3 && remainingSeconds > 0 }"
        >
          {{ remainingSeconds }}
        </div>
        <div v-if="!imageReady && !imageError" class="image-state">图片加载中...</div>
        <div v-if="imageError" class="image-state">
          <p>图片加载失败</p>
          <button class="btn-secondary" @click="retryImage">重试</button>
        </div>
      </div>
    </div>

    <!-- ─────── 盲选题号 ─────── -->
    <div v-else-if="phase === 'drawing'" class="drawing-area">
      <div class="topbar">
        <button class="top-btn" @click="quitGame">← 返回</button>
        <span class="game-name">第 {{ roundNum }}/5 轮</span>
        <span class="timer"></span>
      </div>
      <p class="drawing-tip">图片已隐藏，选一个题号（答案藏在里面）</p>
      <div class="number-grid">
        <button
          v-for="n in 6"
          :key="n"
          class="number-card"
          :disabled="submitting"
          @click="pickNumber(n)"
        >
          {{ n }}
        </button>
      </div>
      <p v-if="answerError" class="error-text">{{ answerError }}</p>
    </div>

    <!-- ─────── 作答阶段 ─────── -->
    <div v-else-if="phase === 'answering' && drawResp" class="answer-area">
      <div class="topbar">
        <button class="top-btn" @click="quitGame">← 返回</button>
        <span class="game-name">第 {{ roundNum }}/5 轮</span>
        <span class="timer" :class="{ 'timer-hurry': remainingSeconds <= 3 }">{{ remainingSeconds }}s</span>
      </div>
      <p class="question-text">{{ drawResp.questionText }}</p>
      <div class="options">
        <button
          v-for="(opt, i) in drawResp.options"
          :key="i"
          class="option-btn"
          :disabled="submitting"
          @click="submitAnswer(drawResp.optionKeys[i])"
        >
          {{ opt }}
        </button>
      </div>
      <p v-if="answerError" class="error-text">{{ answerError }}</p>
    </div>

    <!-- ─────── 结果反馈 ─────── -->
    <div v-else-if="phase === 'result' && answerResp" class="card result-card">
      <h2 class="result-title" :class="answerResp.correct ? 'text-ok' : 'text-bad'">
        {{ answerResp.correct ? '答对啦' : '答错了' }}
      </h2>
      <p class="result-line">正确答案：{{ answerResp.correctAnswer }}</p>
      <p class="result-line">本题用时：{{ elapsedDisplay }}s</p>
      <p class="result-now">目前答对 {{ correctCountNow }}/5</p>
      <div class="result-actions">
        <button class="btn-secondary" @click="goReview">回看图片</button>
        <button class="btn-primary" @click="goNextRound">
          {{ roundNum < 5 ? '下一张' : '查看结算' }}
        </button>
      </div>
    </div>

    <!-- ─────── 回看图片 ─────── -->
    <div v-else-if="phase === 'review'" class="observe-area">
      <div class="topbar">
        <button class="top-btn" @click="quitGame">← 返回</button>
        <span class="game-name">回看 · 第 {{ roundNum }}/5 轮</span>
        <span class="timer"></span>
      </div>
      <div class="image-wrap">
        <img v-if="currentRound" :src="currentRound.imageUrl" class="detail-image" />
      </div>
      <div class="review-bar">
        <span v-if="answerResp" :class="answerResp.correct ? 'text-ok' : 'text-bad'">
          正确答案：{{ answerResp.correctAnswer }}
        </span>
        <button class="btn-primary" @click="goNextRound">
          {{ roundNum < 5 ? '下一张' : '查看结算' }}
        </button>
      </div>
    </div>

    <!-- ─────── 结算页 ─────── -->
    <div v-else-if="phase === 'finished' && finishResp" class="card result-card finish-card">
      <h2 class="result-title">结算</h2>
      <div class="finish-score">
        {{ finishResp.correctCount }}<span class="finish-total">/{{ finishResp.answeredCount }}</span>
      </div>
      <p class="result-line">答对 {{ finishResp.correctCount }} · 作答 {{ finishResp.answeredCount }} 题</p>
      <p class="result-line">累计答题用时：{{ (finishResp.answerTimeMs / 1000).toFixed(1) }}s</p>

      <!-- 每轮明细：计分过程透明 -->
      <div v-if="finishResp.rounds && finishResp.rounds.length" class="round-detail">
        <div class="round-detail-title">每轮明细</div>
        <div v-for="r in finishResp.rounds" :key="r.round" class="round-row" :class="{ 'row-skip': !r.played }">
          <span class="round-no">第 {{ r.round }} 轮</span>
          <span v-if="!r.played" class="round-mark">未开始</span>
          <span v-else class="round-mark" :class="r.correct ? 'text-ok' : 'text-bad'">
            {{ r.timeout ? '超时' : r.correct ? '✓ 答对' : '✗ 答错' }}
          </span>
          <span v-if="r.played" class="round-time">{{ (r.elapsedMs / 1000).toFixed(1) }}s</span>
        </div>
      </div>
      <p class="score-note">答对记 1 题，答错/超时不加；先比答对题数，相同再比累计用时（只统计题目出现到提交的耗时）</p>

      <div v-if="finishResp.saved" class="finish-ranks">
        <div class="rank-chip" v-if="finishResp.todayRank != null">
          今日排名 <b>第 {{ finishResp.todayRank }} 名</b>
        </div>
        <div class="rank-chip" v-if="finishResp.allRank != null">
          总排名 <b>第 {{ finishResp.allRank }} 名</b>
        </div>
        <p v-if="finishResp.bestCorrectCount != null" class="best-line">
          历史最佳：{{ finishResp.bestCorrectCount }}/5 ·
          {{ (finishResp.bestAnswerTimeMs ?? 0) / 1000 }}s
        </p>
      </div>

      <!-- 未登录：本局成绩未保存 → 注册/登录后补存 -->
      <div v-else class="record-area">
        <p class="record-tip">本局成绩未保存，注册后可保存成绩并参与排行。</p>
        <div class="auth-mode">
          <button :class="{ active: authMode === 'register' }" @click="switchAuthMode('register')">
            注册
          </button>
          <button :class="{ active: authMode === 'login' }" @click="switchAuthMode('login')">
            登录
          </button>
        </div>
        <input v-model="authUsername" type="text" placeholder="账号" />
        <input v-model="authPassword" type="password" placeholder="密码" />
        <input
          v-if="authMode === 'register'"
          v-model="authConfirm"
          type="password"
          placeholder="确认密码"
        />
        <p v-if="authError" class="auth-error">{{ authError }}</p>
        <button class="btn-primary" :disabled="authSubmitting" @click="handleAuthSubmit">
          {{ authSubmitting ? '提交中...' : authMode === 'register' ? '注册并保存' : '登录并保存' }}
        </button>
      </div>

      <p v-if="finishError" class="error-text">{{ finishError }}</p>

      <div class="result-actions finish-actions">
        <button class="btn-secondary" @click="showRanking = true">查看排行榜</button>
        <button class="btn-primary" @click="startGame">再来一局</button>
      </div>
      <button class="btn-link" @click="backToHall">返回小游戏大厅</button>
    </div>
  </div>

  <!-- 结束并保存：游戏中固定底部 -->
  <div v-if="inGame" class="save-bar">
    <button class="btn-save" @click="endAndSave">结束并保存</button>
  </div>

  <!-- 放弃确认弹窗 -->
  <div v-if="showQuitConfirm" class="dialog-mask" @click.self="cancelQuit">
    <div class="dialog quit-dialog">
      <h3 class="dialog-title">放弃本局？</h3>
      <p class="dialog-text">
        已作答进度将<b>不会保存</b>。如需保留成绩，请点「结束并保存」。
      </p>
      <div class="dialog-actions">
        <button class="btn-secondary" @click="cancelQuit">继续游戏</button>
        <button class="btn-danger" @click="confirmQuit">放弃返回</button>
      </div>
    </div>
  </div>

  <Footer />

  <DetailRanking :visible="showRanking" :refresh-key="rankingKey" @close="showRanking = false" />
</template>

<style scoped>
.detail-page {
  max-width: var(--max-width);
  margin: 0 auto;
  /* 底部留白给固定的「结束并保存」栏 */
  padding: 24px 20px 96px;
  min-height: calc(100vh - var(--header-height));
  display: flex;
  flex-direction: column;
}

.card {
  background: var(--color-card);
  border-radius: 16px;
  box-shadow: var(--shadow);
  padding: 32px;
  max-width: 480px;
  margin: 0 auto;
  width: 100%;
  text-align: center;
}

/* ── 开始页 ── */
.intro-card {
  margin-top: 48px;
}
.intro-title {
  font-size: 32px;
  font-weight: 800;
  color: var(--color-primary);
  margin-bottom: 8px;
}
.intro-subtitle {
  color: var(--color-text-secondary);
  margin-bottom: 20px;
}
.rule-list {
  text-align: left;
  margin-bottom: 20px;
}
.rule-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 15px;
}
.rule-item b {
  color: var(--color-primary);
}
.rule-item span {
  color: var(--color-text-secondary);
}
.intro-duration {
  color: var(--color-text-secondary);
  font-size: 13px;
  margin-bottom: 20px;
}

/* ── 顶部栏 ── */
.topbar {
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
  font-size: 17px;
  font-weight: 700;
}
.timer {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text);
  min-width: 48px;
  text-align: right;
}
.timer-hurry {
  color: var(--color-danger);
  transform: scale(1.15);
}

/* ── 观察阶段 ── */
.observe-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  max-width: 900px;
  margin: 0 auto;
  width: 100%;
}
.observe-tip {
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 14px;
  margin-bottom: 12px;
}
.image-wrap {
  position: relative;
  background: var(--color-bg);
  border-radius: 14px;
  overflow: hidden;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.detail-image {
  width: 100%;
  max-height: 62vh;
  object-fit: contain;
  display: block;
}
.image-state {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--color-text-secondary);
}

/* ── 盲选题号 ── */
.drawing-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  max-width: 480px;
  margin: 0 auto;
  width: 100%;
}
.drawing-tip {
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 15px;
  margin-bottom: 24px;
}
.number-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.number-card {
  aspect-ratio: 1;
  border: 2px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-card);
  font-size: 30px;
  font-weight: 700;
  color: var(--color-primary);
  cursor: pointer;
  transition: border-color 0.15s, transform 0.1s;
}
.number-card:hover {
  border-color: var(--color-primary);
}
.number-card:active {
  transform: scale(0.95);
}
.number-card:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ── 作答阶段 ── */
.answer-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  max-width: 480px;
  margin: 0 auto;
  width: 100%;
}
.question-text {
  font-size: 20px;
  font-weight: 600;
  text-align: center;
  margin: 16px 0 28px;
}
.options {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
.option-btn {
  padding: 22px 8px;
  border: 2px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-card);
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  cursor: pointer;
  transition: border-color 0.15s, transform 0.1s;
}
.option-btn:hover {
  border-color: var(--color-primary);
}
.option-btn:active {
  transform: scale(0.97);
}
.option-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ── 结果 / 结算 ── */
.result-card {
  margin-top: 40px;
}
.result-title {
  font-size: 30px;
  font-weight: 800;
  margin-bottom: 12px;
}
.text-ok {
  color: #27ae60;
}
.text-bad {
  color: #e74c3c;
}
.result-line {
  color: var(--color-text);
  font-size: 15px;
  margin-bottom: 8px;
}
.result-now {
  color: var(--color-text-secondary);
  font-size: 13px;
  margin-bottom: 20px;
}
.result-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 16px;
}
.review-bar {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.finish-card {
  max-width: 420px;
}
.finish-score {
  font-size: 56px;
  font-weight: 800;
  margin: 8px 0 0;
}
.finish-total {
  font-size: 24px;
  color: var(--color-text-secondary);
}
.finish-ranks {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rank-chip {
  background: var(--color-bg);
  border-radius: 10px;
  padding: 10px;
  font-size: 14px;
  color: var(--color-text-secondary);
}
.rank-chip b {
  color: var(--color-primary);
}
.best-line {
  color: var(--color-text-secondary);
  font-size: 13px;
}
.finish-actions {
  margin-top: 20px;
}

/* ── 未登录补存 ── */
.record-area {
  margin-top: 16px;
  padding: 14px;
  background: var(--color-bg);
  border-radius: 12px;
  text-align: left;
}
.record-tip {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 10px;
  text-align: center;
}
.auth-mode {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.auth-mode button {
  flex: 1;
  padding: 6px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-card);
  font-size: 13px;
  cursor: pointer;
  color: var(--color-text-secondary);
}
.auth-mode button.active {
  border-color: var(--color-primary);
  color: var(--color-primary);
  font-weight: 600;
}
.record-area input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
  margin-bottom: 8px;
}
.record-area input:focus {
  border-color: var(--color-primary);
}
.auth-error {
  color: var(--color-danger);
  font-size: 13px;
  margin-bottom: 8px;
}

/* ── 通用按钮 / 文本 ── */
.btn-primary,
.btn-secondary {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 20px;
  font-size: 15px;
  cursor: pointer;
  margin-bottom: 10px;
}
.btn-primary {
  background: var(--color-primary);
  color: #fff;
}
.btn-primary:hover {
  opacity: 0.9;
}
.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-secondary {
  background: var(--color-hover);
  color: var(--color-text);
}
.btn-link {
  background: none;
  border: none;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 13px;
  padding: 8px;
}
.error-text {
  color: var(--color-danger);
  font-size: 13px;
  text-align: center;
  margin-top: 8px;
}

/* ── 实时成绩条 ── */
.live-strip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--color-card);
  border-radius: 10px;
  padding: 8px 16px;
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--color-text-secondary);
}
.live-strip b {
  color: var(--color-primary);
}
.live-dot {
  color: var(--color-border);
}

/* ── 图片上大号观察倒计时 ── */
.countdown-overlay {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 30px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s, background 0.2s;
}
.countdown-hurry {
  background: rgba(231, 76, 60, 0.85);
  transform: scale(1.12);
}

/* ── 底部结束并保存 ── */
.save-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 20px calc(10px + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(6px);
  border-top: 1px solid var(--color-border);
  z-index: 900;
}
.btn-save {
  display: block;
  width: 100%;
  max-width: 480px;
  margin: 0 auto;
  padding: 12px;
  border: none;
  border-radius: 20px;
  background: var(--color-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}
.btn-save:hover {
  opacity: 0.9;
}

/* ── 结算页每轮明细 ── */
.round-detail {
  margin-top: 16px;
  background: var(--color-bg);
  border-radius: 12px;
  padding: 12px 14px;
  text-align: left;
}
.round-detail-title {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}
.round-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 0;
  font-size: 14px;
  border-bottom: 1px solid var(--color-border);
}
.round-row:last-child {
  border-bottom: none;
}
.round-no {
  width: 64px;
  color: var(--color-text-secondary);
}
.round-mark {
  flex: 1;
  font-weight: 600;
}
.round-time {
  color: var(--color-text-secondary);
  font-size: 13px;
}
.row-skip {
  opacity: 0.5;
}
.score-note {
  margin-top: 12px;
  font-size: 12px;
  color: var(--color-text-muted);
}

/* ── 退出确认弹窗 ── */
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
  padding: 20px 24px;
  width: 320px;
  max-width: 90%;
  text-align: center;
}
.quit-dialog .dialog-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 12px;
}
.dialog-text {
  color: var(--color-text-secondary);
  font-size: 14px;
  margin-bottom: 20px;
  line-height: 1.6;
}
.dialog-actions {
  display: flex;
  gap: 12px;
}
.dialog-actions button {
  flex: 1;
  padding: 10px;
  border: none;
  border-radius: 18px;
  font-size: 14px;
  cursor: pointer;
}
.btn-danger {
  background: var(--color-danger);
  color: #fff;
}
</style>
