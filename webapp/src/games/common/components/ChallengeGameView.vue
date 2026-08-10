<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import type { BaseQuestion, ChallengeEngine, ChallengeResult } from '../engine/ChallengeEngine'
import type { ChallengeStore } from '../stores/challengeStore'
import Header from '@/components/Header.vue'
import Footer from '@/components/Footer.vue'

export interface IntroRule {
  label: string
  desc: string
}
export interface IntroConfig {
  title: string
  subtitle: string
  rules: IntroRule[]
  durationText: string
}

const props = defineProps<{
  /** 游戏名（顶栏展示） */
  title: string
  /** 规则说明页配置 */
  intro: IntroConfig
  /** rule 标识 → 展示文案（如 LOOK_COLOR: '看颜色'） */
  ruleText: Record<string, string>
  /** 结果页是否展示冲突/切换正确率 */
  showSwitchStats?: boolean
  /** 通用挑战引擎实例（具体游戏引擎是其子类） */
  engine: ChallengeEngine<BaseQuestion> | ChallengeEngine<any>
  /** 本游戏 localStorage 存档 */
  store: ChallengeStore
  /** 键盘按键 → 答案值映射（PC 键盘操作；不传则无键盘支持） */
  keyMap?: Record<string, string>
}>()

const emit = defineEmits<{ (e: 'finished', result: ChallengeResult): void }>()

type ViewState = 'intro' | 'playing' | 'result'
const router = useRouter()

const view = ref<ViewState>('intro')
const remainingMs = ref(0)
const currentQuestion = ref<BaseQuestion | null>(null)
const ruleText = ref('')
const rulePulse = ref(false)
const score = ref(0)
const streak = ref(0)
const feedback = ref<{ correct: boolean; answer: string } | null>(null)
const locked = ref(false)
const result = ref<ChallengeResult | null>(null)
const highestScore = ref(0)

let timer: number | null = null
let feedbackTimer: number | null = null
let lastRuleText = ''

const remainingSeconds = computed(() => Math.max(0, Math.ceil(remainingMs.value / 1000)))

const conflictAccuracy = computed(() => {
  if (!result.value || result.value.conflictTotal <= 0) return 0
  return result.value.conflictCorrect / result.value.conflictTotal
})
const switchAccuracy = computed(() => {
  if (!result.value || result.value.switchTotal <= 0) return 0
  return result.value.switchCorrect / result.value.switchTotal
})

function pct(value: number): string {
  return `${Math.round(value * 100)}%`
}

function startGame(): void {
  props.engine.start()
  view.value = 'playing'
  const q = props.engine.getQuestion()
  currentQuestion.value = q
  lastRuleText = q ? props.ruleText[q.rule] ?? '' : ''
  ruleText.value = lastRuleText
  score.value = props.engine.getScore()
  streak.value = props.engine.getStreak()
  feedback.value = null
  locked.value = false
  startTimer()
}

function startTimer(): void {
  stopTimer()
  timer = window.setInterval(() => {
    if (!props.engine.isRunning()) return
    const remain = props.engine.remainingTimeMs()
    remainingMs.value = remain
    score.value = props.engine.getScore()
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

function handleAnswer(choice: string): void {
  if (locked.value || view.value !== 'playing' || !props.engine.isRunning()) return
  const res = props.engine.answer(choice)
  if (!res) return

  // 反馈 + 短暂锁定，防止快速双击重复计分
  locked.value = true
  feedback.value = { correct: res.correct, answer: choice }
  streak.value = props.engine.getStreak()
  score.value = props.engine.getScore()

  // 规则随下一题更新：切换粒度 = 每次作答后一次，标签始终与当前题一致
  currentQuestion.value = res.newQuestion
  updateRuleFrom(res.newQuestion)

  if (feedbackTimer !== null) clearTimeout(feedbackTimer)
  feedbackTimer = window.setTimeout(() => {
    feedback.value = null
    locked.value = false
  }, 180)
}

function updateRuleFrom(q: BaseQuestion): void {
  const text = q ? props.ruleText[q.rule] ?? '' : ''
  if (!text) return
  if (text !== lastRuleText) {
    lastRuleText = text
    rulePulse.value = true
    window.setTimeout(() => (rulePulse.value = false), 300)
  }
  ruleText.value = text
}

function finishGame(): void {
  if (!props.engine.isRunning()) return
  stopTimer()
  const r = props.engine.finish()
  result.value = r
  props.store.saveResult(r)
  highestScore.value = props.store.getHighestScore()
  view.value = 'result'
  emit('finished', r)
}

function backToHall(): void {
  router.push('/games')
}

/** 键盘操作（PC）：keyMap 命中 → 作答；忽略按住连发（e.repeat），每次按键一题 */
function onKeyDown(e: KeyboardEvent): void {
  if (!props.keyMap || view.value !== 'playing') return
  const value = props.keyMap[e.key]
  if (value && !e.repeat) {
    e.preventDefault()
    handleAnswer(value)
  }
}

onMounted(() => {
  highestScore.value = props.store.getHighestScore()
  if (props.keyMap) {
    window.addEventListener('keydown', onKeyDown)
  }
})

onUnmounted(() => {
  stopTimer()
  if (feedbackTimer !== null) clearTimeout(feedbackTimer)
  if (props.keyMap) {
    window.removeEventListener('keydown', onKeyDown)
  }
})
</script>

<template>
  <Header />
  <div class="challenge-page">
    <!-- 规则说明 -->
    <div v-if="view === 'intro'" class="challenge-card intro-card">
      <h1 class="intro-title">{{ intro.title }}</h1>
      <p class="intro-subtitle">{{ intro.subtitle }}</p>
      <div v-for="r in intro.rules" :key="r.label" class="rule-item">
        <b>{{ r.label }}</b><span>{{ r.desc }}</span>
      </div>
      <p class="intro-duration">{{ intro.durationText }}</p>
      <p v-if="highestScore > 0" class="intro-best">历史最高分：{{ highestScore }}</p>
      <button class="btn-start" @click="startGame">开始挑战</button>
    </div>

    <!-- 游戏中 -->
    <div v-else-if="view === 'playing'" class="play-area">
      <div class="play-topbar">
        <button class="top-btn" @click="backToHall">← 返回</button>
        <span class="game-name">{{ title }}</span>
        <span class="timer" :class="{ 'timer-hurry': remainingSeconds <= 5 && remainingSeconds > 0 }">
          {{ remainingSeconds }}s
        </span>
      </div>

      <div class="rule-tag" :class="{ 'rule-pulse': rulePulse }">{{ ruleText }}</div>

      <div class="question-slot">
        <slot name="question" :question="currentQuestion" />
      </div>

      <div class="answers-slot">
        <slot name="answers" :handle-answer="handleAnswer" :locked="locked" :feedback="feedback" />
      </div>

      <div class="bottom-info">
        <span>得分 {{ score }}</span>
        <span v-if="streak >= 2" class="streak-tip">连对 {{ streak }}</span>
      </div>
    </div>

    <!-- 结果 -->
    <div v-else-if="view === 'result' && result" class="challenge-card result-card">
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
        <template v-if="showSwitchStats">
          <div class="result-item">
            <span>冲突正确率</span><b>{{ pct(conflictAccuracy) }}</b>
          </div>
          <div class="result-item">
            <span>切换正确率</span><b>{{ pct(switchAccuracy) }}</b>
          </div>
        </template>
      </div>

      <slot name="record" :result="result" />

      <div class="result-actions">
        <slot name="resultExtra" />
        <button class="btn-secondary" @click="backToHall">返回小游戏</button>
        <button class="btn-primary" @click="startGame">再来一次</button>
      </div>
    </div>
  </div>
  <Footer />
</template>

<style scoped>
.challenge-page {
  max-width: var(--max-width);
  margin: 0 auto;
  padding: 24px 20px;
  min-height: calc(100vh - var(--header-height));
  display: flex;
  flex-direction: column;
}

/* ── 通用卡片 ── */
.challenge-card {
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

/* 题目/答案由子组件通过 slot 自定义布局，这里只保证容器存在 */
.question-slot,
.answers-slot {
  width: 100%;
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

.result-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
/* :deep 穿透：同时作用于壳内按钮与 resultExtra slot 传入的按钮
   （scoped 样式默认不匹配子组件渲染的 slot 内容） */
.result-actions :deep(.btn-primary),
.result-actions :deep(.btn-secondary) {
  flex: 1;
  padding: 10px 16px;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
}
.result-actions :deep(.btn-primary) {
  background: var(--color-primary);
  color: #fff;
}
.result-actions :deep(.btn-secondary) {
  background: var(--color-hover);
  color: var(--color-text);
}
</style>
