<script setup lang="ts">
import { ref } from 'vue'
import { DirectionTrapEngine, type DirectionQuestion } from '../engine/DirectionTrapEngine'
import { arrowOf, textOf } from '../config/directions'
import { directionTrapStore } from '../../common/stores/challengeStore'
import ChallengeGameView, { type IntroConfig } from '../../common/components/ChallengeGameView.vue'
import BackendRecord from '../../common/components/BackendRecord.vue'
import ChallengeRanking from '../../common/components/ChallengeRanking.vue'

const engine = new DirectionTrapEngine()

const intro: IntroConfig = {
  title: '方向陷阱',
  subtitle: '别被文字带偏了',
  rules: [
    { label: '看箭头', desc: '只判断箭头指向，忽略文字' },
    { label: '看文字', desc: '只判断文字内容，忽略箭头' },
  ],
  durationText: '挑战时间：30 秒',
}

const ruleText: Record<string, string> = {
  LOOK_ARROW: '看箭头',
  LOOK_TEXT: '看文字',
}

const SCORE_URL = '/api/games/direction-trap/score'
const MY_BEST_URL = '/api/games/direction-trap/my-best'
const RANK_URL = '/api/games/direction-trap/rank'

const showRanking = ref(false)
const rankingKey = ref(0)

function feedbackClass(value: string, fb: { correct: boolean; answer: string } | null): string {
  if (!fb || fb.answer !== value) return ''
  return fb.correct ? 'answer-ok' : 'answer-bad'
}
</script>

<template>
  <div>
    <ChallengeGameView
      :engine="engine"
      :store="directionTrapStore"
      :title="intro.title"
      :intro="intro"
      :rule-text="ruleText"
      :show-switch-stats="true"
      :key-map="{ ArrowUp: 'UP', ArrowDown: 'DOWN', ArrowLeft: 'LEFT', ArrowRight: 'RIGHT' }"
    >
      <!-- 题目区：大箭头 + 方向文字 -->
      <template #question="{ question }">
        <div v-if="question" class="direction-question">
          <span class="big-arrow">{{ arrowOf((question as DirectionQuestion).arrowDirection) }}</span>
          <span class="direction-text">{{ textOf((question as DirectionQuestion).textDirection) }}</span>
        </div>
      </template>

      <!-- 答案区：十字方向键（位置整局固定） -->
      <template #answers="{ handleAnswer, locked, feedback }">
        <div class="dpad">
          <button
            class="dpad-btn"
            :disabled="locked"
            :class="feedbackClass('UP', feedback)"
            @click="handleAnswer('UP')"
          >
            ↑ 上
          </button>
          <div class="dpad-mid">
            <button
              class="dpad-btn"
              :disabled="locked"
              :class="feedbackClass('LEFT', feedback)"
              @click="handleAnswer('LEFT')"
            >
              ← 左
            </button>
            <button
              class="dpad-btn"
              :disabled="locked"
              :class="feedbackClass('RIGHT', feedback)"
              @click="handleAnswer('RIGHT')"
            >
              右 →
            </button>
          </div>
          <button
            class="dpad-btn"
            :disabled="locked"
            :class="feedbackClass('DOWN', feedback)"
            @click="handleAnswer('DOWN')"
          >
            ↓ 下
          </button>
        </div>
      </template>

      <!-- 记录区：已登录自动保存 / 未登录注册引导 -->
      <template #record="{ result }">
        <BackendRecord
          :score-url="SCORE_URL"
          :my-best-url="MY_BEST_URL"
          :result="result"
          @saved="rankingKey++"
        />
      </template>

      <!-- 额外按钮：查看排行榜 -->
      <template #resultExtra>
        <button class="btn-secondary" @click="showRanking = true">查看排行榜</button>
      </template>
    </ChallengeGameView>

    <ChallengeRanking
      :visible="showRanking"
      :refresh-key="rankingKey"
      :rank-url="RANK_URL"
      @close="showRanking = false"
    />
  </div>
</template>

<style scoped>
.direction-question {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 160px;
  background: var(--color-bg);
  border-radius: 16px;
  margin-bottom: 32px;
  gap: 4px;
}
.big-arrow {
  font-size: 80px;
  line-height: 1;
  user-select: none;
}
.direction-text {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
}

/* 十字方向键布局：上/下居中，左/右中间 */
.dpad {
  display: grid;
  grid-template-rows: auto auto auto;
  gap: 12px;
  justify-items: center;
}
.dpad-mid {
  display: flex;
  gap: 12px;
  justify-content: center;
}
.dpad-btn {
  min-width: 96px;
  padding: 16px 20px;
  border: 2px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-card);
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, transform 0.1s;
}
.dpad-btn:hover {
  border-color: var(--color-primary);
}
.dpad-btn:active {
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
</style>
