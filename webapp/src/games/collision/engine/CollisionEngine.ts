/**
 * 鱼群碰撞 — 引擎 v4（统一滚动物体）
 *
 * 所有事件（门/障碍/敌群/终点）都是从上往下滚动的物体：
 * - 到达鱼群高度（段中点）时触发
 * - 门：两扇并排滚下，鱼所在车道决定穿哪扇
 * - 礁石：碰到直接失败；水母 -8；渔网 -20%
 * - 敌群：1:1 抵消，我方 ≤ 敌方则失败
 * - 终点：结算，分数 = 剩余鱼数量
 * - 无暂停状态，持续上行
 */
import type { CollisionLevel, GateConfig, GateOption, ObstacleKind } from '../config/levels'

export type EngineState = 'running' | 'paused' | 'complete' | 'failed'

export interface RenderSegment {
  index: number
  type: string
  label: string
  lane: number
  trackPos: number
  triggerPos: number
  processed: boolean
  gate?: GateConfig
}

export interface EngineSnapshot {
  count: number
  laneX: number
  lane: number
  distance: number
  totalDistance: number
  progress: number
  state: EngineState
  score: number
  elapsed: number
  segments: RenderSegment[]
}

export type EngineEvent =
  | { type: 'count-change'; count: number; delta: number; reason: string }
  | { type: 'collision'; count: number; kind: string; delta: number; dodged: boolean }
  | { type: 'enemy-battle'; enemyCount: number; won: boolean; count: number }
  | { type: 'level-complete'; score: number; count: number }
  | { type: 'level-failed'; reason: string; count: number }

const SPEED = 150          // 上行速度（轨道单位/秒）— 已加快
const SEGMENT_LENGTH = 220 // 每段长度（轨道单位）
const MAX_COUNT = 500      // 数量上限提高，避免 ×门堆满后 +门失效
const LANE_EASE = 4        // 横向缓动系数：鱼朝目标平滑靠近（调低起步更柔，更丝滑）
const NUDGE = 0.2          // 每次按键的小幅挪动距离（可多次累积）
const REPEAT_INTERVAL = 0.18 // 按住方向键时重复步进的节流间隔（秒；elapsed 以秒计，浏览器 key repeat 约 30ms，需节流）

export class CollisionEngine {
  private level: CollisionLevel
  private count = 0
  private laneX = 0
  private targetX = 0 // 目标横向位置（缓动朝它移动）
  private distance = 0
  private totalDistance = 0
  private segStart: number[] = []
  private segLength: number[] = []
  private processed = new Set<number>()
  private state: EngineState = 'running'
  private score = 0
  private elapsed = 0
  private lastNudgeAt = 0 // 上次按键步进的时间（用于按住节流）
  private listeners: Array<(e: EngineEvent) => void> = []
  private finished = false

  constructor(level: CollisionLevel) {
    this.level = level
    this.count = level.initialCount
    let acc = 0
    for (const seg of level.segments) {
      this.segStart.push(acc)
      this.segLength.push(SEGMENT_LENGTH)
      acc += SEGMENT_LENGTH
    }
    this.totalDistance = acc
  }

  onEvent(cb: (e: EngineEvent) => void): void {
    this.listeners.push(cb)
  }

  getSnapshot(): EngineSnapshot {
    return {
      count: this.count,
      laneX: this.laneX,
      lane: this.snapLane(),
      distance: this.distance,
      totalDistance: this.totalDistance,
      progress: this.totalDistance > 0 ? this.distance / this.totalDistance : 0,
      state: this.state,
      score: this.score,
      elapsed: this.elapsed,
      segments: this.buildRenderSegments(),
    }
  }

  update(dt: number): void {
    if (this.state !== 'running') return
    this.elapsed += dt
    this.distance += dt * SPEED
    // 目标缓动：鱼平滑朝 targetX 移动，接近时减速（丝滑）
    this.laneX += (this.targetX - this.laneX) * Math.min(1, dt * LANE_EASE)
    this.processSegments()
  }

  /**
   * 向左挪动一格（方案A：每次按键 targetX 小幅移动 NUDGE，可累积）
   * repeat=true 表示浏览器 key repeat（按住），需节流，避免冲到底
   */
  startMoveLeft(repeat = false): void {
    if (repeat && this.elapsed - this.lastNudgeAt < REPEAT_INTERVAL) return
    this.targetX = Math.max(-1, this.targetX - NUDGE)
    this.lastNudgeAt = this.elapsed
  }

  /** 向右挪动一格（方案A：每次按键 targetX 小幅移动 NUDGE，可累积） */
  startMoveRight(repeat = false): void {
    if (repeat && this.elapsed - this.lastNudgeAt < REPEAT_INTERVAL) return
    this.targetX = Math.min(1, this.targetX + NUDGE)
    this.lastNudgeAt = this.elapsed
  }

  /** 停止移动（松手）— 方案A：不冻结，鱼滑到当前目标位自然停止 */
  stopMove(): void {
    // no-op
  }

  /** 触控拖动：直接设置横向位置 */
  setLane(x: number): void {
    this.laneX = Math.max(-1, Math.min(1, x))
    this.targetX = this.laneX
  }

  pause(): void {
    if (this.state === 'running') this.state = 'paused'
  }

  resume(): void {
    if (this.state === 'paused') this.state = 'running'
  }

  isPaused(): boolean {
    return this.state === 'paused'
  }

  // ────────────── 内部 ──────────────

  private snapLane(): number {
    // 车道阈值与 NUDGE 联动：2 次按键（±0.4）即进入侧车道
    if (this.laneX <= -0.3) return -1
    if (this.laneX >= 0.3) return 1
    return 0
  }

  private processSegments(): void {
    for (let i = 0; i < this.level.segments.length; i++) {
      if (this.processed.has(i)) continue
      const seg = this.level.segments[i]
      const triggerPos = this.segStart[i] + this.segLength[i] / 2

      if (this.distance >= triggerPos) {
        switch (seg.type) {
          case 'gate':
            this.resolveGate(seg.gate)
            break
          case 'obstacle':
            this.resolveObstacle(seg.obstacle.kind, seg.obstacle.side)
            break
          case 'enemy':
            this.resolveEnemy(seg.enemy.count)
            break
          case 'finish':
            this.resolveFinish()
            break
        }
        this.processed.add(i)
      }
    }
  }

  private resolveGate(gate: GateConfig): void {
    // 门在 laneA/laneB 两条车道；鱼不在任一门车道则平安穿过（无变化、无提示）
    const lane = this.snapLane()
    let chosen: GateOption | null = null
    if (lane === gate.laneA) chosen = gate.gateA
    else if (lane === gate.laneB) chosen = gate.gateB

    if (chosen === null) return

    const old = this.count
    const newCount = this.applyGateOp(old, chosen.op, chosen.val)
    const delta = newCount - old
    this.count = newCount
    // 过门不弹提示文字，数量直接变化（顶部数字更新）
    this.emit({ type: 'count-change', count: this.count, delta, reason: `门 ${chosen.op}${chosen.val}` })
  }

  private resolveObstacle(kind: ObstacleKind, side: string): void {
    const obstacleLane = side === 'left' ? -1 : 1
    const dodged = this.snapLane() !== obstacleLane

    if (dodged) {
      // 躲避成功：无飘字，仅在失败/通关时弹结算
      this.emit({ type: 'collision', count: this.count, kind, delta: 0, dodged: true })
      return
    }

    // 礁石致命
    if (kind === 'reef') {
      this.fail('撞上礁石！')
      return
    }

    let loss = 0
    if (kind === 'jellyfish') loss = 8
    else if (kind === 'net') loss = Math.ceil(this.count * 0.2)

    const newCount = Math.max(1, this.count - loss)
    this.count = newCount
    const kindName = kind === 'jellyfish' ? '水母' : '渔网'
    this.emit({ type: 'collision', count: newCount, kind, delta: -loss, dodged: false })
  }

  private resolveEnemy(enemyCount: number): void {
    if (this.count <= enemyCount) {
      this.emit({ type: 'enemy-battle', enemyCount, won: false, count: this.count })
      this.fail(`敌方鱼群(${enemyCount})比我们多`)
      return
    }
    const newCount = this.count - enemyCount
    this.count = newCount
    this.emit({ type: 'enemy-battle', enemyCount, won: true, count: newCount })
    this.emit({ type: 'count-change', count: newCount, delta: -enemyCount, reason: '战胜敌方' })
  }

  private resolveFinish(): void {
    if (this.finished) return
    this.finished = true
    this.state = 'complete'
    // 分数 = 剩余鱼的数量
    this.score = this.count
    this.emit({ type: 'level-complete', score: this.score, count: this.count })
  }

  private applyGateOp(count: number, op: string, val: number): number {
    let result = count
    switch (op) {
      case '+': result = count + val; break
      case '*': result = count * val; break
    }
    return Math.max(1, Math.min(MAX_COUNT, result))
  }

  private buildRenderSegments(): RenderSegment[] {
    return this.level.segments.map((seg, i) => {
      let label = ''
      let lane = 0
      switch (seg.type) {
        case 'gate':
          label = `${seg.gate.gateA.op}${seg.gate.gateA.val} | ${seg.gate.gateB.op}${seg.gate.gateB.val}`
          break
        case 'obstacle':
          label = seg.obstacle.kind === 'reef' ? '礁石' : seg.obstacle.kind === 'jellyfish' ? '水母' : '渔网'
          lane = seg.obstacle.side === 'left' ? -1 : 1
          break
        case 'enemy':
          label = `敌方 ${seg.enemy.count}`
          break
        case 'finish':
          label = '终点'
          break
      }
      return {
        index: i,
        type: seg.type,
        label,
        lane,
        trackPos: this.segStart[i],
        triggerPos: this.segStart[i] + this.segLength[i] / 2,
        processed: this.processed.has(i),
        gate: seg.type === 'gate' ? seg.gate : undefined,
      }
    })
  }

  private fail(reason: string): void {
    this.state = 'failed'
    this.emit({ type: 'level-failed', reason, count: this.count })
  }

  private emit(e: EngineEvent): void {
    this.listeners.forEach((cb) => cb(e))
  }

  destroy(): void {
    this.listeners = []
  }
}
