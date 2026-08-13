/**
 * 鱼群突围 — 体验配置
 * 棋盘尺寸 / 时长 / 难度档集中在此，调整参数不改引擎核心逻辑。
 */
import type { DifficultySpec } from '../engine/types'

export const BOARD_ROWS = 5
export const BOARD_COLS = 5
export const GAME_DURATION_MS = 30_000
/** 失误达到该次数本局提前结束 */
export const MISTAKES_LIMIT = 3
/** 鱼池切换过渡时长（ms） */
export const POOL_TRANSITION_MS = 350
/** 成功鱼离场动画时长（ms） */
export const EXIT_ANIMATION_MS = 220

/**
 * 难度递进：按已清空池数切换（5×5 = 25 格，鱼数约翻倍，密度 40%→72%）。
 * maxInitialFreeRatio 为开局可直接离开鱼的最大占比（软约束，越低越难）：
 * 生成器优先返回满足该占比的棋盘，有界重试内通常都能命中；命中率偏低时返回重试中最优可解棋盘兜底。
 */
export const DIFFICULTY: DifficultySpec[] = [
  { minPools: 0, fishCount: [10, 12], maxInitialFreeRatio: 0.6 },
  { minPools: 2, fishCount: [12, 14], maxInitialFreeRatio: 0.55 },
  { minPools: 4, fishCount: [14, 16], maxInitialFreeRatio: 0.5 },
  { minPools: 6, fishCount: [16, 18], maxInitialFreeRatio: 0.45 },
]
