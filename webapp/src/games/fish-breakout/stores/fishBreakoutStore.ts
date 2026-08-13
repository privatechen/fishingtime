/**
 * 鱼群突围 — localStorage 存档
 *
 * 记录字段：bestClearedPools / bestReleasedFish / bestMistakes / gameCount / lastResult。
 * 最佳比较顺序（PRD §14）：清空池数更多优先 → 放生数更多 → 失误更少。
 * localStorage 不可用（隐私模式/被禁用）时游戏仍可正常玩，仅不保存——
 * 所有读写必须 try/catch 兜底，避免访问 localStorage 抛异常导致页面空白。
 */
import type { PoolResult } from '../engine/types'

const KEY = 'fishingtime:fishBreakout'

interface BestRecord {
  clearedPools: number
  releasedFish: number
  mistakes: number
}

function safeGet(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function safeSet(key: string, value: string): void {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 忽略：localStorage 不可用时仅不保存
  }
}

export const fishBreakoutStore = {
  best(): BestRecord {
    return {
      clearedPools: Number(safeGet(`${KEY}:bestClearedPools`) || 0),
      releasedFish: Number(safeGet(`${KEY}:bestReleasedFish`) || 0),
      mistakes: Number(safeGet(`${KEY}:bestMistakes`) || 0),
    }
  },

  gameCount(): number {
    return Number(safeGet(`${KEY}:gameCount`) || 0)
  },

  lastResult(): PoolResult | null {
    try {
      return JSON.parse(safeGet(`${KEY}:lastResult`) || 'null')
    } catch {
      return null
    }
  },

  saveResult(result: PoolResult): void {
    try {
      safeSet(`${KEY}:lastResult`, JSON.stringify(result))
      safeSet(`${KEY}:gameCount`, String(this.gameCount() + 1))

      const b = this.best()
      const better =
        result.clearedPools > b.clearedPools ||
        (result.clearedPools === b.clearedPools && result.releasedFish > b.releasedFish) ||
        (result.clearedPools === b.clearedPools &&
          result.releasedFish === b.releasedFish &&
          result.mistakes < b.mistakes)
      if (better) {
        safeSet(`${KEY}:bestClearedPools`, String(result.clearedPools))
        safeSet(`${KEY}:bestReleasedFish`, String(result.releasedFish))
        safeSet(`${KEY}:bestMistakes`, String(result.mistakes))
      }
    } catch {
      // 忽略
    }
  },
}
