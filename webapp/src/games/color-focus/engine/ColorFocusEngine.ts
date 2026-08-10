/**
 * 选颜色 — Stroop 效应挑战引擎（纯 TS，无 Vue 依赖）
 *
 * 规则（对齐《选颜色prd.docx》）：
 * - 单局固定 40 秒，用"结束时间戳"计算剩余时间，不依赖 setInterval 计数，防累计漂移
 * - 0-10 秒固定"看颜色"；10-20 秒固定"看文字"；20-40 秒每题随机
 * - 4 色池（红/黄/绿/蓝），约 70% 冲突题（word ≠ fontColor）
 * - 计分 = 正确×20 − 错误×10 + 连对里程碑（5/10/20 题 → +20/+50/+100，同局首次达到发放，最低 0 分）
 * - 答完一题立即进入下一题（当前题锁定，防重复计分由组件侧反馈锁配合）
 */

export type ColorName = '红' | '黄' | '绿' | '蓝'
export type RuleType = 'LOOK_COLOR' | 'LOOK_TEXT'

export interface Question {
  /** 屏幕显示的文字语义，如"红" */
  word: ColorName
  /** 该文字实际使用的字体颜色 */
  fontColor: ColorName
  /** 当前规则：看颜色 / 看文字 */
  rule: RuleType
  /** word 与 fontColor 是否不同（冲突题） */
  isConflict: boolean
  /** 该题出现时间（performance.now()），用于计算反应时长 */
  startTime: number
}

export interface AnswerResult {
  correct: boolean
  /** 本题正确答案（供反馈展示） */
  correctAnswer: ColorName
  /** 判定后立即生成的新题 */
  newQuestion: Question
}

export interface GameResult {
  totalCount: number
  correctCount: number
  wrongCount: number
  /** 0~1 */
  accuracy: number
  /** 平均反应时间（秒） */
  avgReactionTime: number
  maxStreak: number
  score: number
  title: string
  conflictCorrect: number
  conflictTotal: number
  /** 规则切换后的题正确数/总数 */
  switchCorrect: number
  switchTotal: number
}

export const GAME_DURATION_MS = 40_000
export const COLORS: ColorName[] = ['红', '黄', '绿', '蓝']

const CONFLICT_RATIO = 0.7
/** 0-10s 看颜色，10-20s 看文字 */
const RULE_COLOR_UNTIL_MS = 10_000
const RULE_TEXT_UNTIL_MS = 20_000

const STREAK_BONUSES: { streak: number; bonus: number }[] = [
  { streak: 5, bonus: 20 },
  { streak: 10, bonus: 50 },
  { streak: 20, bonus: 100 },
]

/** 得分 → 娱乐化称号（区间为自定义初版） */
export function titleForScore(score: number): string {
  if (score >= 800) return '王者辨色'
  if (score >= 500) return '钻石反应'
  if (score >= 300) return '铂金专注'
  if (score >= 200) return '黄金分色'
  if (score >= 100) return '白银观察'
  return '青铜摸鱼'
}

export class ColorFocusEngine {
  private state: 'idle' | 'running' | 'finished' = 'idle'
  private startTime = 0
  private endTime = 0

  private current: Question | null = null
  /** 上一题（用于规则切换统计 + 避免连续出相同题） */
  private prevQuestion: Question | null = null

  private totalCount = 0
  private correctCount = 0
  private wrongCount = 0
  private streak = 0
  private maxStreak = 0
  private reactionTimes: number[] = []
  private score = 0
  private conflictCorrect = 0
  private conflictTotal = 0
  private switchCorrect = 0
  private switchTotal = 0
  private awardedStreaks = new Set<number>()
  private lastResult: GameResult | null = null

  /** 开始新一局 */
  start(): void {
    this.state = 'running'
    this.startTime = performance.now()
    this.endTime = this.startTime + GAME_DURATION_MS

    this.current = null
    this.prevQuestion = null
    this.totalCount = 0
    this.correctCount = 0
    this.wrongCount = 0
    this.streak = 0
    this.maxStreak = 0
    this.reactionTimes = []
    this.score = 0
    this.conflictCorrect = 0
    this.conflictTotal = 0
    this.switchCorrect = 0
    this.switchTotal = 0
    this.awardedStreaks.clear()
    this.lastResult = null

    this.current = this.generateQuestion()
  }

  isRunning(): boolean {
    return this.state === 'running'
  }

  isFinished(): boolean {
    return this.state === 'finished'
  }

  /** 剩余毫秒；非运行中返回 0 */
  remainingTimeMs(): number {
    if (this.state !== 'running') return 0
    return this.endTime - performance.now()
  }

  /** 当前题（无题或未开始返回 null） */
  getQuestion(): Question | null {
    return this.current
  }

  /** 当前应展示的规则（随时间分段变化），用于顶栏常驻展示 */
  currentRule(): RuleType {
    return this.ruleFor(this.elapsedMs())
  }

  /** 实时得分（结果页前展示） */
  getScore(): number {
    return Math.max(0, this.score)
  }

  /** 当前连对数（用于"连对 X"展示） */
  getStreak(): number {
    return this.streak
  }

  /**
   * 作答。判定后立即进入下一题（当前题锁定）。
   * 由组件侧配合 150ms 反馈锁防止双击重复计分。
   */
  answer(color: ColorName): AnswerResult | null {
    const q = this.current
    if (!q || this.state !== 'running') return null

    const correctAnswer = q.rule === 'LOOK_COLOR' ? q.fontColor : q.word
    const correct = color === correctAnswer
    const reactionMs = performance.now() - q.startTime

    this.totalCount++
    this.reactionTimes.push(reactionMs)
    if (q.isConflict) {
      this.conflictTotal++
      if (correct) this.conflictCorrect++
    }
    if (this.prevQuestion && this.prevQuestion.rule !== q.rule) {
      this.switchTotal++
      if (correct) this.switchCorrect++
    }

    if (correct) {
      this.correctCount++
      this.streak++
      if (this.streak > this.maxStreak) this.maxStreak = this.streak
      this.score += 20
      this.applyStreakBonus()
    } else {
      this.wrongCount++
      this.streak = 0
      this.score = Math.max(0, this.score - 10)
    }

    this.prevQuestion = q
    const newQuestion = this.generateQuestion()
    this.current = newQuestion
    return { correct, correctAnswer, newQuestion }
  }

  /** 结束本局并结算（超时或主动结束时调用；可重复调用，结果幂等） */
  finish(): GameResult {
    if (this.state === 'finished' && this.lastResult) return this.lastResult
    this.state = 'finished'

    const total = this.totalCount
    const correct = this.correctCount
    const finalScore = Math.max(0, this.score)
    const result: GameResult = {
      totalCount: total,
      correctCount: correct,
      wrongCount: this.wrongCount,
      accuracy: total > 0 ? correct / total : 0,
      avgReactionTime:
        this.reactionTimes.length > 0
          ? this.reactionTimes.reduce((a, b) => a + b, 0) / this.reactionTimes.length / 1000
          : 0,
      maxStreak: this.maxStreak,
      score: finalScore,
      title: titleForScore(finalScore),
      conflictCorrect: this.conflictCorrect,
      conflictTotal: this.conflictTotal,
      switchCorrect: this.switchCorrect,
      switchTotal: this.switchTotal,
    }
    this.lastResult = result
    return result
  }

  // ────────────── 内部 ──────────────

  private elapsedMs(): number {
    return performance.now() - this.startTime
  }

  private ruleFor(elapsed: number): RuleType {
    if (elapsed < RULE_COLOR_UNTIL_MS) return 'LOOK_COLOR'
    if (elapsed < RULE_TEXT_UNTIL_MS) return 'LOOK_TEXT'
    return Math.random() < 0.5 ? 'LOOK_COLOR' : 'LOOK_TEXT'
  }

  private generateQuestion(): Question {
    const rule = this.ruleFor(this.elapsedMs())
    const q = this.buildQuestion(rule)
    // 避免连续多题完全相同：与上一题组合一致则重抽一次（16 种组合，重抽概率低）
    if (this.prevQuestion && isSameQuestion(q, this.prevQuestion)) {
      return this.buildQuestion(rule)
    }
    return q
  }

  private buildQuestion(rule: RuleType): Question {
    let word: ColorName
    let fontColor: ColorName
    if (Math.random() < CONFLICT_RATIO) {
      word = COLORS[randIndex(COLORS.length)]
      do {
        fontColor = COLORS[randIndex(COLORS.length)]
      } while (fontColor === word)
    } else {
      word = COLORS[randIndex(COLORS.length)]
      fontColor = word
    }
    return {
      word,
      fontColor,
      rule,
      isConflict: word !== fontColor,
      startTime: performance.now(),
    }
  }

  private applyStreakBonus(): void {
    for (const { streak, bonus } of STREAK_BONUSES) {
      if (this.streak === streak && !this.awardedStreaks.has(streak)) {
        this.awardedStreaks.add(streak)
        this.score += bonus
      }
    }
  }
}

function randIndex(length: number): number {
  return Math.floor(Math.random() * length)
}

function isSameQuestion(a: Question, b: Question): boolean {
  return a.word === b.word && a.fontColor === b.fontColor && a.rule === b.rule
}
