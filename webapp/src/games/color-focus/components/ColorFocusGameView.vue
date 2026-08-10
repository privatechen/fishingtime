<script setup lang="ts">
import { ref } from 'vue'
import {
  ColorFocusEngine,
  COLORS,
  type ColorName,
  type ColorQuestion,
} from '../engine/ColorFocusEngine'
import { colorFocusStore } from '../../common/stores/challengeStore'
import ChallengeGameView, { type IntroConfig } from '../../common/components/ChallengeGameView.vue'
import BackendRecord from '../../common/components/BackendRecord.vue'
import ChallengeRanking from '../../common/components/ChallengeRanking.vue'

const engine = new ColorFocusEngine()

const intro: IntroConfig = {
  title: '选颜色',
  subtitle: '看清每一题的要求',
  rules: [
    { label: '看颜色', desc: '请选择字体实际显示的颜色' },
    { label: '看文字', desc: '请选择文字写的颜色' },
  ],
  durationText: '挑战时间：40 秒',
}

const ruleText: Record<string, string> = {
  LOOK_COLOR: '看颜色',
  LOOK_TEXT: '看文字',
}

const COLOR_CSS: Record<ColorName, string> = {
  红: '#e74c3c',
  黄: '#f7b500',
  绿: '#27ae60',
  蓝: '#2d8cf0',
}

const SCORE_URL = '/api/games/color-focus/score'
const MY_BEST_URL = '/api/games/color-focus/my-best'
const RANK_URL = '/api/games/color-focus/rank'

const showRanking = ref(false)
const rankingKey = ref(0)

function colorToCss(color: ColorName): string {
  return COLOR_CSS[color]
}

function feedbackClass(c: ColorName, fb: { correct: boolean; answer: string } | null): string {
  if (!fb || fb.answer !== c) return ''
  return fb.correct ? 'answer-ok' : 'answer-bad'
}
</script>

<template>
  <div>
    <ChallengeGameView
      :engine="engine"
      :store="colorFocusStore"
      :title="intro.title"
      :intro="intro"
      :rule-text="ruleText"
      :show-switch-stats="false"
    >
      <!-- 题目区：大字色字 -->
      <template #question="{ question }">
        <span
          v-if="question"
          class="question-word"
          :style="{ color: colorToCss((question as ColorQuestion).fontColor) }"
        >
          {{ (question as ColorQuestion).word }}
        </span>
      </template>

      <!-- 答案区：4 色按钮 -->
      <template #answers="{ handleAnswer, locked, feedback }">
        <div class="answers">
          <button
            v-for="c in COLORS"
            :key="c"
            class="answer-btn"
            :disabled="locked"
            :class="feedbackClass(c, feedback)"
            @click="handleAnswer(c)"
          >
            {{ c }}
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
/* 题目大字 */
.question-word {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 160px;
  background: var(--color-bg);
  border-radius: 16px;
  font-size: 96px;
  font-weight: 800;
  line-height: 1;
  user-select: none;
  margin-bottom: 32px;
}

/* 答案按钮：2×2 网格 */
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
</style>
