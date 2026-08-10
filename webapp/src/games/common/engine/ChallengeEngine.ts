/**
 * 40 秒挑战系列通用引擎（基类）
 *
 * 处理两个系列小游戏（选颜色 / 方向陷阱）的共性逻辑：
 * - 固定 40 秒倒计时（结束时间戳计算，防 setInterval 漂移）
 * - 计分 = 正确×20 − 错误×10 + 连对里程碑（5/10/20 → +20/+50/+100，同局首次）
 * - 连对、冲突题、规则切换题统计
 * - finish 结算（正确率/平均反应/最高连对/得分/称号/冲突与切换正确率）
 *
 * 子类实现差异：
 * - {@link #generateQuestion()}：具体游戏出题（规则分段 + 题面生成 + 去重）
 * - {@link #judge(choice, question)}：按规则判定答案
 * - {@link #correctAnswerOf(question)}：返回正确答案标识
 */

export type ChallengeState = 'idle' | 'running' | 'finished'

export interface BaseQuestion {
  /** 当前规则标识（子类定义，如 LOOK_COLOR / LOOK_ARROW） */
  rule: string
  /** word/箭头 与 展示信息 是否冲突 */
  isConflict: boolean
  /** 本题出现时间（performance.now()），用于反应时间 */
  startTime: number
}

export interface AnswerOutcome<Q extends BaseQuestion> {
  correct: boolean
  /** 正确答案标识（供反馈展示） */
  correctAnswer: string
  /** 判定后立即生成的新题 */
  newQuestion: Q
}

export interface ChallengeResult {
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
  /** 规则切换题正确数/总数 */
  switchCorrect: number
  switchTotal: number
}

/** 单局时长 */
export const CHALLENGE_DURATION_MS = 40_000

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

export abstract class ChallengeEngine<Q extends BaseQuestion> {
  private state: ChallengeState = 'idle'
  private startTime = 0
  private endTime = 0

  private current: Q | null = null
  /** 上一题（用于规则切换统计 + 子类出题时避免连续相同） */
  private prevQuestion: Q | null = null

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
  private lastResult: ChallengeResult | null = null

  /** 开始新一局 */
  start(): void {
    this.state = 'running'
    this.startTime = performance.now()
    this.endTime = this.startTime + this.durationMs()

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
  getQuestion(): Q | null {
    return this.current
  }

  /** 实时得分 */
  getScore(): number {
    return Math.max(0, this.score)
  }

  /** 当前连对数 */
  getStreak(): number {
    return this.streak
  }

  /**
   * 作答。判定后立即进入下一题（当前题锁定）。
   * 由页面配合短暂反馈锁防止双击重复计分。
   */
  answer(choice: string): AnswerOutcome<Q> | null {
    const q = this.current
    if (!q || this.state !== 'running') return null

    const correctAnswer = this.correctAnswerOf(q)
    const correct = this.judge(choice, q)
    const reactionMs = performance.now() - q.startTime

    this.totalCount++
    this.reactionTimes.push(reactionMs)
    if (q.isConflict) {
      this.conflictTotal++
      if (correct) this.conflictCorrect++
    }
    // 规则切换题：本题 rule 与上一题不同（第 1 题不计入）
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

  /** 结束本局并结算（可重复调用，结果幂等） */
  finish(): ChallengeResult {
    if (this.state === 'finished' && this.lastResult) return this.lastResult
    this.state = 'finished'

    const total = this.totalCount
    const correct = this.correctCount
    const finalScore = Math.max(0, this.score)
    const result: ChallengeResult = {
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

  // ────────────── 供子类使用 ──────────────

  /** 单局时长（毫秒），子类可覆盖（默认 40 秒） */
  protected durationMs(): number {
    return CHALLENGE_DURATION_MS
  }

  /** 本局已流逝毫秒 */
  protected elapsedMs(): number {
    return performance.now() - this.startTime
  }

  /** 上一题（子类出题时用于规则切换统计 / 避免连续相同） */
  protected getPrevQuestion(): Q | null {
    return this.prevQuestion
  }

  // ────────────── 子类实现 ──────────────

  /** 生成下一题（含规则分段、题面随机、去重） */
  protected abstract generateQuestion(): Q

  /** 按当前规则判定玩家选项是否正确 */
  protected abstract judge(choice: string, question: Q): boolean

  /** 返回本题正确答案标识 */
  protected abstract correctAnswerOf(question: Q): string

  // ────────────── 内部 ──────────────

  private applyStreakBonus(): void {
    for (const { streak, bonus } of STREAK_BONUSES) {
      if (this.streak === streak && !this.awardedStreaks.has(streak)) {
        this.awardedStreaks.add(streak)
        this.score += bonus
      }
    }
  }
}
