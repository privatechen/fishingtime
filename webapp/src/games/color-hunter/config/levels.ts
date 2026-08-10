/**
 * 颜色猎手 — 关卡配置与颜色池
 *
 * 9 轮：Level 1（3×3，目标 3/4/5）→ Level 2（4×4，目标 5/6/7）→ Level 3（5×5，目标 7/8/9）。
 * 目标数量严格等于配置值；非目标格不得包含目标色。
 */
export type HunterColor = 'RED' | 'YELLOW' | 'BLUE' | 'GREEN'

export const HUNTER_COLORS: HunterColor[] = ['RED', 'YELLOW', 'BLUE', 'GREEN']

export const COLOR_NAME: Record<HunterColor, string> = {
  RED: '红色',
  YELLOW: '黄色',
  BLUE: '蓝色',
  GREEN: '绿色',
}

/** 色值（浅色/深色主题下均需可辨识） */
export const COLOR_CSS: Record<HunterColor, string> = {
  RED: '#e74c3c',
  YELLOW: '#f7b500',
  BLUE: '#2d8cf0',
  GREEN: '#27ae60',
}

export interface RoundConfig {
  /** 等级 1~3 */
  level: number
  /** 网格边长（3 / 4 / 5） */
  gridSize: number
  /** 目标颜色数量（严格精确） */
  targetCount: number
}

/** 9 轮配置（索引 0~8 对应 1-1 至 3-3） */
export const ROUND_CONFIGS: RoundConfig[] = [
  { level: 1, gridSize: 3, targetCount: 3 },
  { level: 1, gridSize: 3, targetCount: 4 },
  { level: 1, gridSize: 3, targetCount: 5 },
  { level: 2, gridSize: 4, targetCount: 5 },
  { level: 2, gridSize: 4, targetCount: 6 },
  { level: 2, gridSize: 4, targetCount: 7 },
  { level: 3, gridSize: 5, targetCount: 7 },
  { level: 3, gridSize: 5, targetCount: 8 },
  { level: 3, gridSize: 5, targetCount: 9 },
]

/** 关卡展示文案，如 1-1 / 2-2 */
export function roundLabel(roundIndex: number): string {
  const cfg = ROUND_CONFIGS[roundIndex]
  return `${cfg.level}-${(roundIndex % 3) + 1}`
}
