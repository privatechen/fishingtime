<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ColorFocusEngine,
  COLORS,
  type ColorName,
  type GameResult,
  type Question,
} from '../engine/ColorFocusEngine'
import { colorFocusStore } from '../stores/colorFocusStore'
import { useAuth } from '@/stores/auth'
import ColorFocusRanking from './ColorFocusRanking.vue'
import Header from '@/components/Header.vue'
import Footer from '@/components/Footer.vue'

type ViewState = 'intro' | 'playing' | 'result'

/** 后端 my-best 返回（只需展示用到的字段） */
interface MyBest {
  bestScore: number
  maxStreak: number | null
}

const router = useRouter()
const engine = new ColorFocusEngine()
const { isLoggedIn, register, login } = useAuth()

const view = ref<ViewState>('intro')

// 游戏中 UI 状态
const remainingMs = ref(0)
const currentQuestion = ref<Question | null>(null)
const currentRuleText = ref('')
const rulePulse = ref(false)
const score = ref(0)
const streak = ref(0)
const feedback = ref<{ correct: boolean; answer: ColorName } | null>(null)
const locked = ref(false)

// 结果
const result = ref<GameResult | null>(null)
const highestScore = ref(0)

// 记录与排行榜
const showRanking = ref(false)
const rankingKey = ref(0)
const recordState = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
const myBest = ref<MyBest | null>(null)
const authMode = ref<'register' | 'login'>('register')
const authUsername = ref('')
const authPassword = ref('')
const authConfirm = ref('')
const authError = ref('')
const authSubmitting = ref(false)

let timer: number | null = null
let feedbackTimer: number | null = null
let lastRuleText = ''

const RULE_TEXT: Record<string, string> = {
  LOOK_COLOR: '看颜色',
  LOOK_TEXT: '看文字',
}

const COLOR_CSS: Record<ColorName, string> = {
  红: '#e74c3c',
  黄: '#f7b500',
  绿: '#27ae60',
  蓝: '#2d8cf0',
}

const remainingSeconds = computed(() => Math.max(0, Math.ceil(remainingMs.value / 1000)))

function colorToCss(color: ColorName): string {
  return COLOR_CSS[color]
}

function pct(value: number): string {
  return `${Math.round(value * 100)}%`
}

function startGame(): void {
  engine.start()
  view.value = 'playing'
  const first = engine.getQuestion()
  currentQuestion.value = first
  // 规则标签绑定当前题的 rule（不做实时刷新，避免 20-40s 随机阶段闪变）
  lastRuleText = first ? RULE_TEXT[first.rule] : ''
  currentRuleText.value = lastRuleText
  score.value = engine.getScore()
  streak.value = engine.getStreak()
  feedback.value = null
  locked.value = false
  authError.value = ''
  startTimer()
}

function startTimer(): void {
  stopTimer()
  timer = window.setInterval(() => {
    if (!engine.isRunning()) return
    const remain = engine.remainingTimeMs()
    remainingMs.value = remain
    score.value = engine.getScore()
    if (remain <= 0) {
      finishGame()
    }
  }, 100)
}

function stopTimer(): void {
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

function handleAnswer(color: ColorName): void {
  if (locked.value || view.value !== 'playing' || !engine.isRunning()) return
  const res = engine.answer(color)
  if (!res) return

  // 反馈 + 短暂锁定，防止快速双击重复计分
  locked.value = true
  feedback.value = { correct: res.correct, answer: color }
  streak.value = engine.getStreak()
  score.value = engine.getScore()

  // 规则随下一题更新：切换粒度 = 每次作答后一次，标签始终与当前题一致
  currentQuestion.value = res.newQuestion
  updateRuleFrom(res.newQuestion)

  if (feedbackTimer !== null) clearTimeout(feedbackTimer)
  feedbackTimer = window.setTimeout(() => {
    feedback.value = null
    locked.value = false
  }, 180)
}

/** 按当前题更新规则标签；仅当规则变化时触发一次闪动提醒 */
function updateRuleFrom(question: Question | null): void {
  const ruleText = question ? RULE_TEXT[question.rule] : ''
  if (!ruleText) return
  if (ruleText !== lastRuleText) {
    lastRuleText = ruleText
    rulePulse.value = true
    window.setTimeout(() => (rulePulse.value = false), 300)
  }
  currentRuleText.value = ruleText
}

function finishGame(): void {
  if (!engine.isRunning()) return
  stopTimer()
  const r = engine.finish()
  result.value = r
  colorFocusStore.saveResult(r)
  highestScore.value = colorFocusStore.getHighestScore()
  view.value = 'result'
  // 无保存按钮：40s 结束自动触发记录
  if (isLoggedIn.value) {
    void saveScore()
  } else {
    recordState.value = 'idle'
  }
}

async function saveScore(): Promise<void> {
  if (!result.value) return
  recordState.value = 'saving'
  const ok = await submitScore()
  if (ok) {
    recordState.value = 'saved'
    rankingKey.value++
    myBest.value = await loadMyBest()
  } else {
    recordState.value = 'error'
  }
}

async function submitScore(): Promise<boolean> {
  if (!result.value) return false
  try {
    const res = await fetch('/api/games/color-focus/score', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({
        bestScore: result.value.score,
        bestAccuracy: result.value.accuracy,
        bestAvgReaction: result.value.avgReactionTime,
        maxStreak: result.value.maxStreak,
      }),
    })
    const json = await res.json()
    return json.code === 200
  } catch {
    return false
  }
}

async function loadMyBest(): Promise<MyBest | null> {
  try {
    const res = await fetch('/api/games/color-focus/my-best', { credentials: 'same-origin' })
    const json = await res.json()
    return json.code === 200 ? (json.data ?? null) : null
  } catch {
    return null
  }
}

function switchAuthMode(mode: 'register' | 'login'): void {
  authMode.value = mode
  authError.value = ''
}

/** 注册/登录成功后自动保存本局成绩（无保存按钮，认证即保存） */
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
    await saveScore()
  } finally {
    authSubmitting.value = false
  }
}

function backToHall(): void {
  router.push('/games')
}

function feedbackClass(color: ColorName): string {
  if (!feedback.value || feedback.value.answer !== color) return ''
  return feedback.value.correct ? 'answer-ok' : 'answer-bad'
}

onMounted(() => {
  highestScore.value = colorFocusStore.getHighestScore()
})

onUnmounted(() => {
  stopTimer()
  if (feedbackTimer !== null) clearTimeout(feedbackTimer)
})
</script>

<template>
  <Header />
  <div class="color-page">
    <!-- 规则说明 -->
    <div v-if="view === 'intro'" class="color-card intro-card">
      <h1 class="intro-title">选颜色</h1>
      <p class="intro-subtitle">看清每一题的要求</p>
      <div class="rule-item">
        <b>看颜色</b><span>请选择字体实际显示的颜色</span>
      </div>
      <div class="rule-item">
        <b>看文字</b><span>请选择文字写的颜色</span>
      </div>
      <p class="intro-duration">挑战时间：40 秒</p>
      <p v-if="highestScore > 0" class="intro-best">历史最高分：{{ highestScore }}</p>
      <button class="btn-start" @click="startGame">开始挑战</button>
    </div>

    <!-- 游戏中 -->
    <div v-else-if="view === 'playing'" class="play-area">
      <div class="play-topbar">
        <button class="top-btn" @click="backToHall">← 返回</button>
        <span class="game-name">选颜色</span>
        <span class="timer" :class="{ 'timer-hurry': remainingSeconds <= 5 && remainingSeconds > 0 }">
          {{ remainingSeconds }}s
        </span>
      </div>

      <div class="rule-tag" :class="{ 'rule-pulse': rulePulse }">{{ currentRuleText }}</div>

      <div class="question-area">
        <span
          v-if="currentQuestion"
          class="question-word"
          :style="{ color: colorToCss(currentQuestion.fontColor) }"
        >
          {{ currentQuestion.word }}
        </span>
      </div>

      <div class="answers">
        <button
          v-for="c in COLORS"
          :key="c"
          class="answer-btn"
          :class="feedbackClass(c)"
          @click="handleAnswer(c)"
        >
          {{ c }}
        </button>
      </div>

      <div class="bottom-info">
        <span>得分 {{ score }}</span>
        <span v-if="streak >= 2" class="streak-tip">连对 {{ streak }}</span>
      </div>
    </div>

    <!-- 结果（40s 结束自动进入，自动触发记录/注册） -->
    <div v-else-if="view === 'result' && result" class="color-card result-card">
      <h2 class="result-title">{{ result.title }}</h2>
      <p class="result-score">{{ result.score }}</p>
      <div class="result-grid">
        <div class="result-item">
          <span>答题</span><b>{{ result.totalCount }}</b>
        </div>
        <div class="result-item">
          <span>正确</span><b class="text-ok">{{ result.correctCount }}</b>
        </div>
        <div class="result-item">
          <span>错误</span><b class="text-bad">{{ result.wrongCount }}</b>
        </div>
        <div class="result-item">
          <span>正确率</span><b>{{ pct(result.accuracy) }}</b>
        </div>
        <div class="result-item">
          <span>平均反应</span><b>{{ result.avgReactionTime.toFixed(2) }}s</b>
        </div>
        <div class="result-item">
          <span>最高连对</span><b>{{ result.maxStreak }}</b>
        </div>
      </div>

      <!-- 记录状态区：已登录自动保存，未登录内嵌注册/登录 -->
      <div class="record-area">
        <template v-if="isLoggedIn">
          <p v-if="recordState === 'saving'" class="record-tip">正在自动保存成绩...</p>
          <p v-else-if="recordState === 'saved'" class="record-tip record-ok">
            ✓ 已自动保存 · 我的最高分 {{ myBest?.bestScore ?? result.score }}
            <template v-if="myBest?.maxStreak"> · 最高连对 {{ myBest.maxStreak }}</template>
          </p>
          <p v-else-if="recordState === 'error'" class="record-tip record-error">成绩保存失败，请稍后重试</p>
        </template>
        <template v-else>
          <p class="record-tip">本局成绩未记录，注册后可保存成绩并参与排行。</p>
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
          <button class="auth-submit" :disabled="authSubmitting" @click="handleAuthSubmit">
            {{ authSubmitting ? '提交中...' : authMode === 'register' ? '注册并保存' : '登录并保存' }}
          </button>
        </template>
      </div>

      <div class="result-actions">
        <button class="btn-secondary" @click="showRanking = true">查看排行榜</button>
        <button class="btn-secondary" @click="backToHall">返回小游戏</button>
        <button class="btn-primary" @click="startGame">再来一次</button>
      </div>
    </div>
  </div>

  <ColorFocusRanking :visible="showRanking" :refresh-key="rankingKey" @close="showRanking = false" />
  <Footer />
</template>

<style scoped>
.color-page {
  max-width: var(--max-width);
  margin: 0 auto;
  padding: 24px 20px;
  min-height: calc(100vh - var(--header-height));
  display: flex;
  flex-direction: column;
}

/* ── 通用卡片 ── */
.color-card {
  background: var(--color-card);
  border-radius: 16px;
  box-shadow: var(--shadow);
  padding: 32px;
  max-width: 480px;
  margin: 0 auto;
  width: 100%;
  text-align: center;
}

/* ── 规则说明页 ── */
.intro-card {
  margin-top: 48px;
}
.intro-title {
  font-size: 30px;
  font-weight: 700;
  margin-bottom: 8px;
}
.intro-subtitle {
  color: var(--color-text-secondary);
  margin-bottom: 24px;
}
.rule-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 15px;
}
.rule-item b {
  color: var(--color-primary);
}
.rule-item span {
  color: var(--color-text-secondary);
}
.intro-duration {
  margin-top: 16px;
  color: var(--color-text-secondary);
  font-size: 14px;
}
.intro-best {
  margin-top: 8px;
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

/* ── 游戏中 ── */
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
.timer {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text);
  min-width: 56px;
  text-align: right;
  transition: transform 0.2s, color 0.2s;
}
.timer-hurry {
  color: var(--color-danger);
  transform: scale(1.15);
}

.rule-tag {
  align-self: center;
  color: var(--color-primary);
  font-weight: 700;
  font-size: 17px;
  margin-bottom: 32px;
}
.rule-pulse {
  animation: rulePulse 0.3s ease;
}
@keyframes rulePulse {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.12);
  }
  100% {
    transform: scale(1);
  }
}

.question-area {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 160px;
  background: var(--color-bg);
  border-radius: 16px;
  margin-bottom: 32px;
}
.question-word {
  font-size: 96px;
  font-weight: 800;
  line-height: 1;
  user-select: none;
}

.answers {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
.answer-btn {
  padding: 20px 0;
  border: 2px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-card);
  font-size: 22px;
  font-weight: 600;
  color: var(--color-text);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, transform 0.1s;
}
.answer-btn:hover {
  border-color: var(--color-primary);
}
.answer-btn:active {
  transform: scale(0.97);
}
.answer-ok {
  border-color: #2ecc71;
  background: #eafaf1;
}
.answer-bad {
  border-color: #e74c3c;
  background: #fdecea;
}

.bottom-info {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-top: 20px;
  color: var(--color-text-secondary);
  font-size: 14px;
}
.streak-tip {
  color: var(--color-primary);
  font-weight: 600;
}

/* ── 结果页 ── */
.result-card {
  margin-top: 48px;
}
.result-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-primary);
  margin-bottom: 4px;
}
.result-score {
  font-size: 56px;
  font-weight: 800;
  margin: 12px 0 24px;
}
.result-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 20px;
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
.text-ok {
  color: #27ae60;
}
.text-bad {
  color: #e74c3c;
}

/* ── 记录状态区 / 认证 ── */
.record-area {
  margin-bottom: 20px;
  padding: 16px;
  background: var(--color-bg);
  border-radius: 12px;
  text-align: left;
}
.record-tip {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 12px;
  text-align: center;
}
.record-ok {
  color: #27ae60;
}
.record-error {
  color: var(--color-danger);
}
.auth-mode {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
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
.auth-form input,
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
.auth-submit {
  width: 100%;
  padding: 9px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 18px;
  font-size: 14px;
  cursor: pointer;
}
.auth-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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
</style>
