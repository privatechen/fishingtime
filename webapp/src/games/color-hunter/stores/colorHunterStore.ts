/**
 * 颜色猎手 — LocalStorage 存档
 *
 * Key（前缀 fishingtime:colorHunter:）：bestFinalTime / bestActualTime / lowestErrorCount / lastResult。
 * 最佳成绩：finalTime 最低优先；相同则错误次数更少；再相同则 actualTime 更低。
 * localStorage 不可用时游戏正常运行，仅不保存。
 */
import type { HunterResult } from '../engine/ColorHunterEngine'

const KEY_BEST_FINAL = 'fishingtime:colorHunter:bestFinalTime'
const KEY_BEST_ACTUAL = 'fishingtime:colorHunter:bestActualTime'
const KEY_LOWEST_ERROR = 'fishingtime:colorHunter:lowestErrorCount'
const KEY_LAST = 'fishingtime:colorHunter:lastResult'

export const colorHunterStore = {
  /** 最佳最终成绩（秒；0 表示暂无） */
  getBestFinalTime(): number {
    return Number(localStorage.getItem(KEY_BEST_FINAL) || 0)
  },

  getBestActualTime(): number {
    return Number(localStorage.getItem(KEY_BEST_ACTUAL) || 0)
  },

  getLowestErrorCount(): number {
    return Number(localStorage.getItem(KEY_LOWEST_ERROR) || 0)
  },

  getLastResult(): HunterResult | null {
    try {
      return JSON.parse(localStorage.getItem(KEY_LAST) || 'null')
    } catch {
      return null
    }
  },

  saveResult(result: HunterResult): void {
    try {
      localStorage.setItem(KEY_LAST, JSON.stringify(result))

      const bestFinal = this.getBestFinalTime()
      const better =
        bestFinal === 0 ||
        result.finalTime < bestFinal ||
        (result.finalTime === bestFinal && result.errorCount < this.getLowestErrorCount())

      if (better) {
        localStorage.setItem(KEY_BEST_FINAL, String(result.finalTime))
        localStorage.setItem(KEY_BEST_ACTUAL, String(result.actualTime))
        localStorage.setItem(KEY_LOWEST_ERROR, String(result.errorCount))
      }
    } catch {
      // 忽略：localStorage 不可用时仅不保存
    }
  },
}
