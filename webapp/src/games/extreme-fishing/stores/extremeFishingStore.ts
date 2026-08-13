/**
 * 极限捞鱼 — localStorage 存档
 *
 * 记录字段：bestScore / bestPerfectCount / bestCombo / gameCount / lastResult。
 * localStorage 不可用时游戏仍可玩，仅不保存（所有读写 try/catch 兜底）。
 */
import type { GameResult } from '../engine/types'

const KEY = 'fishingtime:extremeFishing'

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
    // 忽略
  }
}

export const extremeFishingStore = {
  best(): { score: number; perfectCount: number; maxCombo: number } {
    return {
      score: Number(safeGet(`${KEY}:bestScore`) || 0),
      perfectCount: Number(safeGet(`${KEY}:bestPerfectCount`) || 0),
      maxCombo: Number(safeGet(`${KEY}:bestCombo`) || 0),
    }
  },

  gameCount(): number {
    return Number(safeGet(`${KEY}:gameCount`) || 0)
  },

  lastResult(): GameResult | null {
    try {
      return JSON.parse(safeGet(`${KEY}:lastResult`) || 'null')
    } catch {
      return null
    }
  },

  saveResult(result: GameResult): void {
    try {
      safeSet(`${KEY}:lastResult`, JSON.stringify(result))
      safeSet(`${KEY}:gameCount`, String(this.gameCount() + 1))
      const b = this.best()
      if (result.score > b.score) {
        safeSet(`${KEY}:bestScore`, String(result.score))
        safeSet(`${KEY}:bestPerfectCount`, String(result.perfectCount))
        safeSet(`${KEY}:bestCombo`, String(result.maxCombo))
      }
    } catch {
      // 忽略
    }
  },
}
