/**
 * 极限捞鱼 — 体验配置
 * 全部体验参数集中在此，试玩调整不改引擎。
 */
import type { ExtremeFishingConfig } from '../engine/types'

export const EXTREME_FISHING_CONFIG: ExtremeFishingConfig = {
  rows: 5,
  cols: 5,
  durationMs: 30_000,
  scorePerFish: 10,
  perfectBonus: 50,
  /** PERFECT 需选区内格子 ≥ 3 且鱼占比 ≥ 80%（6格要5、4格要4） */
  perfectMinCells: 3,
  perfectMinDensity: 0.8,
  pufferPenalty: 20,
  /** 河豚失误达 3 次本局提前结束 */
  pufferLimit: 3,
  comboMilestones: [
    { combo: 3, bonus: 30 },
    { combo: 5, bonus: 60 },
    { combo: 8, bonus: 120 },
  ],
  densityBonusTiers: [
    { minDensity: 0.9, factor: 10 },
    { minDensity: 0.6, factor: 5 },
    { minDensity: 0.4, factor: 2 },
  ],
  targetFishCount: 10,
  maxPufferCount: 3,
}
