/**
 * 选颜色 — Stroop 效应挑战引擎
 *
 * 继承 {@link ChallengeEngine} 通用 40 秒框架，仅实现差异：
 * - 4 色池（红/黄/绿/蓝），约 70% 冲突题（word ≠ fontColor）
 * - 规则分段：0-10s 看颜色 / 10-20s 看文字 / 20-40s 每题随机
 * - 判定：LOOK_COLOR 看 fontColor，LOOK_TEXT 看 word
 */
import {
  ChallengeEngine,
  CHALLENGE_DURATION_MS,
  type BaseQuestion,
} from '../../common/engine/ChallengeEngine'

export type ColorName = '红' | '黄' | '绿' | '蓝'
export type RuleType = 'LOOK_COLOR' | 'LOOK_TEXT'

export interface ColorQuestion extends BaseQuestion {
  /** 屏幕显示的文字语义，如"红" */
  word: ColorName
  /** 该文字实际使用的字体颜色 */
  fontColor: ColorName
  /** 当前规则：看颜色 / 看文字 */
  rule: RuleType
  /** word 与 fontColor 是否不同（冲突题） */
  isConflict: boolean
  /** 该题出现时间 */
  startTime: number
}

export const GAME_DURATION_MS = CHALLENGE_DURATION_MS
export const COLORS: ColorName[] = ['红', '黄', '绿', '蓝']

const CONFLICT_RATIO = 0.7
/** 0-10s 看颜色，10-20s 看文字 */
const RULE_COLOR_UNTIL_MS = 10_000
const RULE_TEXT_UNTIL_MS = 20_000

export class ColorFocusEngine extends ChallengeEngine<ColorQuestion> {
  protected generateQuestion(): ColorQuestion {
    const rule = this.ruleFor(this.elapsedMs())
    const prev = this.getPrevQuestion()
    // 避免连续多题完全相同：与上一题组合一致则重抽（循环直到不同，上限防御极端情况）
    let q = this.buildQuestion(rule)
    let tries = 0
    while (prev && isSameQuestion(q, prev) && tries < 10) {
      q = this.buildQuestion(rule)
      tries++
    }
    return q
  }

  protected judge(choice: string, question: ColorQuestion): boolean {
    return choice === this.correctAnswerOf(question)
  }

  protected correctAnswerOf(question: ColorQuestion): string {
    return question.rule === 'LOOK_COLOR' ? question.fontColor : question.word
  }

  // ────────────── 内部 ──────────────

  private ruleFor(elapsed: number): RuleType {
    if (elapsed < RULE_COLOR_UNTIL_MS) return 'LOOK_COLOR'
    if (elapsed < RULE_TEXT_UNTIL_MS) return 'LOOK_TEXT'
    return Math.random() < 0.5 ? 'LOOK_COLOR' : 'LOOK_TEXT'
  }

  private buildQuestion(rule: RuleType): ColorQuestion {
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
}

function randIndex(length: number): number {
  return Math.floor(Math.random() * length)
}

function isSameQuestion(a: ColorQuestion, b: ColorQuestion): boolean {
  return a.word === b.word && a.fontColor === b.fontColor && a.rule === b.rule
}
