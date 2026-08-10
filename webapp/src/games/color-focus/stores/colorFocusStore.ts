/**
 * 选颜色 — LocalStorage 成绩存档
 *
 * Key（前缀 fishingtime:colorFocus:）：
 * - highestScore: 历史最高分
 * - bestAccuracy: 最佳正确率（0~1）
 * - bestAvgReactionTime: 最佳平均反应时间（秒，越小越好；仅在有效答题数 ≥ 10 时更新）
 * - lastResult: 最近一次成绩
 *
 * localStorage 不可用时游戏仍可正常玩，仅不保存（所有读写 try/catch 兜底）。
 */
import type { GameResult } from '../engine/ColorFocusEngine'

const KEY_HIGHEST = 'fishingtime:colorFocus:highestScore'
const KEY_ACCURACY = 'fishingtime:colorFocus:bestAccuracy'
const KEY_REACTION = 'fishingtime:colorFocus:bestAvgReactionTime'
const KEY_LAST = 'fishingtime:colorFocus:lastResult'

/** 平均反应时间"最佳"的最低有效答题数，避免只答少量题导致记录失真 */
const MIN_VALID_COUNT = 10

export const colorFocusStore = {
  getHighestScore(): number {
    return Number(localStorage.getItem(KEY_HIGHEST) || 0)
  },

  getBestAccuracy(): number {
    return Number(localStorage.getItem(KEY_ACCURACY) || 0)
  },

  getBestAvgReactionTime(): number {
    return Number(localStorage.getItem(KEY_REACTION) || 0)
  },

  getLastResult(): GameResult | null {
    try {
      return JSON.parse(localStorage.getItem(KEY_LAST) || 'null')
    } catch {
      return null
    }
  },

  saveResult(result: GameResult): void {
    try {
      localStorage.setItem(KEY_LAST, JSON.stringify(result))
      if (result.score > this.getHighestScore()) {
        localStorage.setItem(KEY_HIGHEST, String(result.score))
      }
      if (result.totalCount >= MIN_VALID_COUNT) {
        if (result.accuracy > this.getBestAccuracy()) {
          localStorage.setItem(KEY_ACCURACY, String(result.accuracy))
        }
        const best = this.getBestAvgReactionTime()
        if (best === 0 || (result.avgReactionTime > 0 && result.avgReactionTime < best)) {
          localStorage.setItem(KEY_REACTION, String(result.avgReactionTime))
        }
      }
    } catch {
      // 忽略：localStorage 不可用时仅不保存，不影响游戏
    }
  },
}
