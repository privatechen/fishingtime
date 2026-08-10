/**
 * 40 秒挑战系列通用 localStorage 存档工厂
 *
 * 每个游戏用不同前缀实例化（如 fishingtime:colorFocus / fishingtime:directionTrap）。
 * 记录字段：highestScore / bestAccuracy / bestAvgReactionTime / bestSwitchAccuracy / lastResult。
 * 最佳正确率/均时/切换正确率仅在有效答题数 ≥ 10 时更新，避免少量题失真。
 * localStorage 不可用时游戏仍可正常玩，仅不保存（所有读写 try/catch 兜底）。
 */
import type { ChallengeResult } from '../engine/ChallengeEngine'

/** 平均反应/正确率"最佳"的最低有效答题数 */
const MIN_VALID_COUNT = 10

export interface ChallengeStore {
  getHighestScore(): number
  getBestAccuracy(): number
  getBestAvgReactionTime(): number
  getBestSwitchAccuracy(): number
  getLastResult(): ChallengeResult | null
  saveResult(result: ChallengeResult): void
}

export function createChallengeStore(prefix: string): ChallengeStore {
  const key = (name: string) => `${prefix}:${name}`

  return {
    getHighestScore(): number {
      return Number(localStorage.getItem(key('highestScore')) || 0)
    },

    getBestAccuracy(): number {
      return Number(localStorage.getItem(key('bestAccuracy')) || 0)
    },

    getBestAvgReactionTime(): number {
      return Number(localStorage.getItem(key('bestAvgReactionTime')) || 0)
    },

    getBestSwitchAccuracy(): number {
      return Number(localStorage.getItem(key('bestSwitchAccuracy')) || 0)
    },

    getLastResult(): ChallengeResult | null {
      try {
        return JSON.parse(localStorage.getItem(key('lastResult')) || 'null')
      } catch {
        return null
      }
    },

    saveResult(result: ChallengeResult): void {
      try {
        localStorage.setItem(key('lastResult'), JSON.stringify(result))
        if (result.score > this.getHighestScore()) {
          localStorage.setItem(key('highestScore'), String(result.score))
        }
        if (result.totalCount >= MIN_VALID_COUNT) {
          if (result.accuracy > this.getBestAccuracy()) {
            localStorage.setItem(key('bestAccuracy'), String(result.accuracy))
          }
          const bestReaction = this.getBestAvgReactionTime()
          if (bestReaction === 0 || (result.avgReactionTime > 0 && result.avgReactionTime < bestReaction)) {
            localStorage.setItem(key('bestAvgReactionTime'), String(result.avgReactionTime))
          }
          const switchAccuracy = result.switchTotal > 0 ? result.switchCorrect / result.switchTotal : 0
          if (result.switchTotal > 0 && switchAccuracy > this.getBestSwitchAccuracy()) {
            localStorage.setItem(key('bestSwitchAccuracy'), String(switchAccuracy))
          }
        }
      } catch {
        // 忽略：localStorage 不可用时仅不保存
      }
    },
  }
}

/** 选颜色存档 */
export const colorFocusStore = createChallengeStore('fishingtime:colorFocus')

/** 方向陷阱存档 */
export const directionTrapStore = createChallengeStore('fishingtime:directionTrap')
