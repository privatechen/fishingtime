/**
 * 方向陷阱 — 四方向配置
 * 枚举：UP / DOWN / LEFT / RIGHT；箭头符号 ↑↓←→；文字 向上/向下/向左/向右。
 */
export type Direction = 'UP' | 'DOWN' | 'LEFT' | 'RIGHT'
export type DirectionRule = 'LOOK_ARROW' | 'LOOK_TEXT'

export interface DirectionOption {
  value: Direction
  arrow: string
  text: string
}

export const DIRECTIONS: DirectionOption[] = [
  { value: 'UP', arrow: '↑', text: '向上' },
  { value: 'DOWN', arrow: '↓', text: '向下' },
  { value: 'LEFT', arrow: '←', text: '向左' },
  { value: 'RIGHT', arrow: '→', text: '向右' },
]

export function arrowOf(direction: Direction): string {
  return DIRECTIONS.find((d) => d.value === direction)?.arrow ?? ''
}

export function textOf(direction: Direction): string {
  return DIRECTIONS.find((d) => d.value === direction)?.text ?? ''
}
