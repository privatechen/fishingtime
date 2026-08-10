/**
 * 颜色猎手 — 视觉搜索引擎（纯 TS，无 Vue 依赖）
 *
 * 9 轮递进：3×3→4×4→5×5，目标数 3→9 严格按配置。
 * - 目标色随机（避免连续两轮相同）；非目标格从另外 3 色轮询均匀分配（数量差 ≤1）
 * - 点击判定：目标色标记（selected），全部选中过本轮；错误点击只计数不改网格
 * - 计时：roundTime = 本轮完成 - 本轮开始；Level 过渡不计入（transition 状态等待页面过渡后 startNextRound）
 * - 实际用时 = Σ 各轮用时；最终成绩 = 实际用时 + 错误次数 × 1 秒
 */
import {
  HUNTER_COLORS,
  ROUND_CONFIGS,
  type HunterColor,
  type RoundConfig,
} from '../config/levels'

export type HunterState = 'idle' | 'running' | 'transition' | 'finished'

export interface HunterCell {
  color: HunterColor
  /** 已被正确点中 */
  selected: boolean
}

export interface HunterResult {
  /** 最终成绩（毫秒）= 实际用时 + 错误×1s */
  finalTime: number
  /** 实际用时（毫秒）= Σ 各轮用时（过渡不计入） */
  actualTime: number
  errorCount: number
  /** 最快一轮用时（毫秒） */
  fastestRound: number
  /** 各 Level 用时（毫秒） */
  levelTimes: [number, number, number]
  /** 9 轮各自用时（毫秒） */
  roundTimes: number[]
}

export interface CellClickResult {
  /** 是否点中目标色 */
  correct: boolean
  /** 本轮是否完成（最后一个目标点击） */
  done: boolean
}

/** 错误×1 秒惩罚 */
const ERROR_PENALTY_MS = 1000

export class ColorHunterEngine {
  private state: HunterState = 'idle'
  private currentRound = 0
  private gridSize = 3
  private targetColor: HunterColor = 'RED'
  private targetCount = 3
  private remainingTargets = 0
  private cells: HunterCell[] = []
  private roundStartTime = 0
  private roundTimes: number[] = []
  private errorCount = 0
  private lastTargetColor: HunterColor | null = null
  private lastResult: HunterResult | null = null

  start(): void {
    this.state = 'running'
    this.currentRound = 0
    this.roundTimes = []
    this.errorCount = 0
    this.lastTargetColor = null
    this.lastResult = null
    this.startRound(0)
  }

  getState(): HunterState {
    return this.state
  }

  isFinished(): boolean {
    return this.state === 'finished'
  }

  /** 进行中的轮次索引 0~8 */
  getRoundIndex(): number {
    return this.currentRound
  }

  getGridSize(): number {
    return this.gridSize
  }

  getTargetColor(): HunterColor {
    return this.targetColor
  }

  getTargetCount(): number {
    return this.targetCount
  }

  getRemaining(): number {
    return this.remainingTargets
  }

  getCells(): HunterCell[] {
    return this.cells
  }

  getErrorCount(): number {
    return this.errorCount
  }

  getRoundTimes(): number[] {
    return this.roundTimes
  }

  /**
   * 当前展示用时（毫秒）= Σ 已完成轮次 + 进行中本轮已用时（过渡期间不计入）。
   */
  getElapsedTime(): number {
    let total = this.roundTimes.reduce((a, b) => a + b, 0)
    if (this.state === 'running') {
      total += performance.now() - this.roundStartTime
    }
    return total
  }

  /** 点击格子。已选中/未运行返回 null。 */
  handleCellClick(index: number): CellClickResult | null {
    if (this.state !== 'running') return null
    const cell = this.cells[index]
    if (!cell || cell.selected) return null

    if (cell.color === this.targetColor) {
      cell.selected = true
      this.remainingTargets--
      if (this.remainingTargets <= 0) {
        this.completeRound()
        return { correct: true, done: true }
      }
      return { correct: true, done: false }
    }
    // 错误点击：计数，不改网格
    this.errorCount++
    return { correct: false, done: false }
  }

  /**
   * 过渡结束后开始下一轮（transition → running）。
   * Level 过渡时间不计入 roundTime（roundStartTime 在此刻才重置）。
   */
  startNextRound(): void {
    if (this.state !== 'transition') return
    this.state = 'running'
    this.startRound(this.currentRound + 1)
  }

  /** 结算结果（幂等） */
  finish(): HunterResult {
    if (this.lastResult) return this.lastResult
    this.state = 'finished'

    const actualTime = this.roundTimes.reduce((a, b) => a + b, 0)
    const finalTime = actualTime + this.errorCount * ERROR_PENALTY_MS
    const fastestRound = this.roundTimes.length > 0 ? Math.min(...this.roundTimes) : 0
    const levelTimes: [number, number, number] = [0, 0, 0]
    this.roundTimes.forEach((t, i) => {
      levelTimes[Math.floor(i / 3)] += t
    })

    this.lastResult = {
      finalTime,
      actualTime,
      errorCount: this.errorCount,
      fastestRound,
      levelTimes,
      roundTimes: [...this.roundTimes],
    }
    return this.lastResult
  }

  // ────────────── 内部 ──────────────

  private startRound(index: number): void {
    const config: RoundConfig = ROUND_CONFIGS[index]
    this.currentRound = index
    this.gridSize = config.gridSize
    this.targetCount = config.targetCount
    this.targetColor = this.pickTargetColor()
    this.lastTargetColor = this.targetColor
    this.cells = this.generateGrid()
    this.remainingTargets = this.targetCount
    this.roundStartTime = performance.now()
  }

  /** 目标色随机，原则上不与上一轮相同 */
  private pickTargetColor(): HunterColor {
    const others = HUNTER_COLORS.filter((c) => c !== this.lastTargetColor)
    return others[randIndex(others.length)]
  }

  /**
   * 生成网格：目标色位置随机打散；非目标格从另外 3 色轮询均匀分配（差 ≤1）；
   * 目标位置若形成整行/整列或恰好填满包围盒矩形，则重抽。
   */
  private generateGrid(): HunterCell[] {
    const total = this.gridSize * this.gridSize
    const targetCount = this.targetCount
    const gridSize = this.gridSize
    const nonTargetColors = HUNTER_COLORS.filter((c) => c !== this.targetColor)

    for (let attempt = 0; attempt < 5; attempt++) {
      const targetPositions = this.pickPositions(total, targetCount)
      if (attempt < 4 && this.isTooClustered(targetPositions, gridSize)) {
        continue
      }

      const cells: HunterCell[] = Array.from({ length: total }, (_, i) => ({
        color: targetPositions.has(i) ? this.targetColor : nonTargetColors[0],
        selected: false,
      }))
      // 非目标位置轮询分配另外 3 色（数量差 ≤1）
      let k = 0
      for (let i = 0; i < total; i++) {
        if (!targetPositions.has(i)) {
          cells[i].color = nonTargetColors[k % nonTargetColors.length]
          k++
        }
      }
      return cells
    }
    // 极端兜底：直接返回一个合法网格（不复检防集中）
    const targetPositions = this.pickPositions(total, targetCount)
    const cells: HunterCell[] = Array.from({ length: total }, (_, i) => ({
      color: targetPositions.has(i) ? this.targetColor : nonTargetColors[0],
      selected: false,
    }))
    let k = 0
    for (let i = 0; i < total; i++) {
      if (!targetPositions.has(i)) {
        cells[i].color = nonTargetColors[k % nonTargetColors.length]
        k++
      }
    }
    return cells
  }

  /** 从 total 中随机选 count 个位置（Fisher-Yates 取前 count） */
  private pickPositions(total: number, count: number): Set<number> {
    const arr = Array.from({ length: total }, (_, i) => i)
    for (let i = total - 1; i > 0; i--) {
      const j = randIndex(i + 1)
      ;[arr[i], arr[j]] = [arr[j], arr[i]]
    }
    return new Set(arr.slice(0, count))
  }

  /** 目标位置是否形成整行/整列或恰好填满一个包围盒矩形 */
  private isTooClustered(positions: Set<number>, gridSize: number): boolean {
    for (let r = 0; r < gridSize; r++) {
      let count = 0
      for (let c = 0; c < gridSize; c++) {
        if (positions.has(r * gridSize + c)) count++
      }
      if (count === gridSize) return true
    }
    for (let c = 0; c < gridSize; c++) {
      let count = 0
      for (let r = 0; r < gridSize; r++) {
        if (positions.has(r * gridSize + c)) count++
      }
      if (count === gridSize) return true
    }
    let minR = gridSize, maxR = -1, minC = gridSize, maxC = -1
    for (const p of positions) {
      const r = Math.floor(p / gridSize)
      const c = p % gridSize
      if (r < minR) minR = r
      if (r > maxR) maxR = r
      if (c < minC) minC = c
      if (c > maxC) maxC = c
    }
    const bboxArea = (maxR - minR + 1) * (maxC - minC + 1)
    return bboxArea === positions.size && bboxArea > 1
  }

  private completeRound(): void {
    this.roundTimes.push(performance.now() - this.roundStartTime)
    if (this.currentRound >= ROUND_CONFIGS.length - 1) {
      this.finish()
    } else {
      // 等待页面 Level 过渡后 startNextRound（过渡时间不计入）
      this.state = 'transition'
    }
  }
}

function randIndex(length: number): number {
  return Math.floor(Math.random() * length)
}
