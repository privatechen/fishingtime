/**
 * 方向陷阱 — 四方向规则切换挑战引擎
 *
 * 继承 {@link ChallengeEngine} 通用框架，差异（v1.1 四方向 + 30 秒全程随机）：
 * - 单局 30 秒
 * - 规则全程逐题随机（看箭头 / 看文字），无固定引导阶段；同一规则最多连续 3 题，第 4 题强制切换
 * - 方向池 UP/DOWN/LEFT/RIGHT 等概率；约 70% 冲突题（textDirection 从排除 arrowDirection 的另外 3 个方向随机选）
 * - 判定：LOOK_ARROW 看 arrowDirection，LOOK_TEXT 看 textDirection
 */
import { ChallengeEngine, type BaseQuestion } from '../../common/engine/ChallengeEngine'
import { DIRECTIONS, type Direction, type DirectionRule } from '../config/directions'

export interface DirectionQuestion extends BaseQuestion {
  /** 箭头实际指向 */
  arrowDirection: Direction
  /** 文字表达的方向 */
  textDirection: Direction
  /** 当前规则：看箭头 / 看文字 */
  rule: DirectionRule
  /** 箭头与文字是否冲突 */
  isConflict: boolean
  /** 该题出现时间 */
  startTime: number
}

/** 单局时长 30 秒 */
const DIRECTION_TRAP_DURATION_MS = 30_000
const CONFLICT_RATIO = 0.7
/** 同一规则最多连续题数，第 4 题强制切换 */
const MAX_SAME_RULE = 3

export class DirectionTrapEngine extends ChallengeEngine<DirectionQuestion> {
  /** 当前规则连续出现的题数（用于"最多连续 3 题"限制） */
  private sameRuleCount = 0

  protected durationMs(): number {
    return DIRECTION_TRAP_DURATION_MS
  }

  protected generateQuestion(): DirectionQuestion {
    const rule = this.resolveRule()
    const prev = this.getPrevQuestion()
    // 更新连续计数
    if (prev && prev.rule === rule) {
      this.sameRuleCount++
    } else {
      this.sameRuleCount = 1
    }

    // 避免连续多题完全相同（rule+arrow+text 组合），循环重抽直到不同（上限防御极端情况）
    let q = this.buildQuestion(rule)
    let tries = 0
    while (prev && isSameQuestion(q, prev) && tries < 10) {
      q = this.buildQuestion(rule)
      tries++
    }
    return q
  }

  protected judge(choice: string, question: DirectionQuestion): boolean {
    return choice === this.correctAnswerOf(question)
  }

  protected correctAnswerOf(question: DirectionQuestion): string {
    return question.rule === 'LOOK_ARROW' ? question.arrowDirection : question.textDirection
  }

  // ────────────── 内部 ──────────────

  /**
   * 全程逐题随机决定规则；同一规则最多连续 3 题，第 4 题强制切换。
   */
  private resolveRule(): DirectionRule {
    const prev = this.getPrevQuestion()
    if (prev && this.sameRuleCount >= MAX_SAME_RULE) {
      return prev.rule === 'LOOK_ARROW' ? 'LOOK_TEXT' : 'LOOK_ARROW'
    }
    return Math.random() < 0.5 ? 'LOOK_ARROW' : 'LOOK_TEXT'
  }

  private buildQuestion(rule: DirectionRule): DirectionQuestion {
    const arrow = DIRECTIONS[randIndex(DIRECTIONS.length)].value
    let text: Direction
    if (Math.random() < CONFLICT_RATIO) {
      // 冲突题：从"除箭头方向外的另外 3 个方向"随机选一个（四方向非简单取反）
      const others = DIRECTIONS.map((d) => d.value).filter((d) => d !== arrow)
      text = others[randIndex(others.length)]
    } else {
      // 一致题：与箭头相同
      text = arrow
    }
    return {
      arrowDirection: arrow,
      textDirection: text,
      rule,
      isConflict: arrow !== text,
      startTime: performance.now(),
    }
  }
}

function randIndex(length: number): number {
  return Math.floor(Math.random() * length)
}

function isSameQuestion(a: DirectionQuestion, b: DirectionQuestion): boolean {
  return a.rule === b.rule && a.arrowDirection === b.arrowDirection && a.textDirection === b.textDirection
}
