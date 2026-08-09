/**
 * 鱼群碰撞 — Canvas 渲染 v3（纵向滚动）
 *
 * 坐标模型：鱼固定在下方，自下往上游；世界向下滚
 * 障碍/门/敌群从上方出现、向下流动经过鱼群；终点在最上方
 */
import { CollisionEngine, type EngineSnapshot, type RenderSegment } from '../engine/CollisionEngine'
import type { GateConfig } from '../config/levels'

export class CollisionGameCanvas {
  private canvas: HTMLCanvasElement
  private ctx: CanvasRenderingContext2D
  private engine: CollisionEngine
  private raf = 0
  private lastTime = 0
  private animTime = 0
  private onScoreUpdate?: (score: number) => void
  private destroying = false

  constructor(canvas: HTMLCanvasElement, engine: CollisionEngine) {
    this.canvas = canvas
    this.ctx = canvas.getContext('2d')!
    this.engine = engine
    this.resize()
    this.bindInput()
  }

  setScoreCallback(cb: (score: number) => void): void {
    this.onScoreUpdate = cb
  }

  resize(): void {
    const parent = this.canvas.parentElement
    if (!parent) return
    this.canvas.width = parent.clientWidth
    this.canvas.height = Math.min(540, parent.clientWidth * 0.5625)
  }

  start(): void {
    this.lastTime = performance.now()
    this.loop(this.lastTime)
  }

  destroy(): void {
    this.destroying = true
    cancelAnimationFrame(this.raf)
    window.removeEventListener('keydown', this.onKeyDown)
    window.removeEventListener('keyup', this.onKeyUp)
    this.canvas.removeEventListener('touchstart', this.onTouchStart)
    this.canvas.removeEventListener('touchmove', this.onTouchMove)
  }

  private loop(t: number): void {
    if (this.destroying) return
    const dt = Math.min(0.05, (t - this.lastTime) / 1000)
    this.lastTime = t
    this.animTime += dt

    this.engine.update(dt)
    const snap = this.engine.getSnapshot()

    this.draw(snap)
    if (this.onScoreUpdate) this.onScoreUpdate(snap.score)

    this.raf = requestAnimationFrame((tt) => this.loop(tt))
  }

  // ────────────── 坐标辅助 ──────────────

  private fishY(): number {
    return this.canvas.height * 0.7
  }

  /**
   * 轨道位置 → 屏幕 Y（视窗方案）
   * 只显示鱼群前方 WINDOW 距离内的障碍，从顶部边缘进入，滑向鱼群
   * 超出视窗（还没进入/已滑过）返回 -999（不渲染）
   */
  private trackToScreenY(trackPos: number, snap: EngineSnapshot): number {
    const topMargin = this.canvas.height * 0.12
    const usable = this.fishY() - topMargin
    const WINDOW = 400 // 视窗距离（SPEED 110 → 从顶部到鱼群约 3.6 秒）
    const rel = trackPos - snap.distance
    if (rel < -10 || rel > WINDOW) return -999
    return this.fishY() - (rel / WINDOW) * usable
  }

  private laneCenter(lane: number): number {
    return this.canvas.width / 2 + lane * (this.canvas.width / 3)
  }

  // ────────────── 绘制 ──────────────

  private draw(snap: EngineSnapshot): void {
    const { ctx, canvas } = this
    const W = canvas.width
    const H = canvas.height

    ctx.clearRect(0, 0, W, H)
    const grad = ctx.createLinearGradient(0, 0, 0, H)
    grad.addColorStop(0, '#b3e5fc')
    grad.addColorStop(1, '#e6f7ff')
    ctx.fillStyle = grad
    ctx.fillRect(0, 0, W, H)

    this.drawLanes()
    this.drawFlow()

    // 终点（顶部）
    this.drawFinish(snap)

    // 所有滚动物体（门 / 障碍 / 敌群）
    this.drawScrollingObjects(snap)

    // 鱼群（固定下方）
    this.drawFishSchool(snap)

    // 进度条
    this.drawProgress(snap)

    // 顶部信息
    this.drawTopInfo(snap.count, snap.score)
  }

  private drawLanes(): void {
    const { ctx, canvas } = this
    const laneW = canvas.width / 3
    ctx.strokeStyle = 'rgba(0,0,0,0.07)'
    ctx.lineWidth = 2
    for (let i = 1; i < 3; i++) {
      ctx.beginPath()
      ctx.moveTo(laneW * i, 0)
      ctx.lineTo(laneW * i, canvas.height)
      ctx.stroke()
    }
  }

  /** 水流：气泡向下流动 */
  private drawFlow(): void {
    const { ctx, canvas } = this
    for (let i = 0; i < 10; i++) {
      const x = (i * 137 + 40) % canvas.width
      const y = (this.animTime * 40 + i * 83) % canvas.height
      const r = 3 + (i % 3) * 3
      ctx.beginPath()
      ctx.arc(x, y, r, 0, Math.PI * 2)
      ctx.fillStyle = 'rgba(255,255,255,0.5)'
      ctx.fill()
    }
  }

  private drawFinish(snap: EngineSnapshot): void {
    const { ctx } = this
    const finishSeg = snap.segments.find((s) => s.type === 'finish')
    if (!finishSeg) return
    const y = this.trackToScreenY(finishSeg.trackPos, snap)
    const x = this.canvas.width / 2
    if (y < -30 || y > this.canvas.height + 30) return

    ctx.fillStyle = '#ff8a65'
    ctx.fillRect(x - 3, y - 60, 6, 60)
    ctx.beginPath()
    ctx.moveTo(x + 3, y - 60)
    ctx.lineTo(x + 55, y - 50)
    ctx.lineTo(x + 3, y - 40)
    ctx.closePath()
    ctx.fillStyle = '#ffcc80'
    ctx.fill()
    ctx.fillStyle = '#e65100'
    ctx.font = `bold 16px sans-serif`
    ctx.textAlign = 'center'
    ctx.fillText('终点', x, y - 66)
  }

  private drawScrollingObjects(snap: EngineSnapshot): void {
    for (const seg of snap.segments) {
      if (seg.processed) continue
      if (seg.type === 'finish') continue
      const y = this.trackToScreenY(seg.triggerPos, snap)
      if (y < -40 || y > this.canvas.height + 40) continue

      if (seg.type === 'gate' && seg.gate) {
        this.drawGates(snap, seg.gate, y)
      } else if (seg.type === 'obstacle') {
        this.drawObstacle(this.laneCenter(seg.lane), y, seg.label)
      } else if (seg.type === 'enemy') {
        this.drawEnemy(this.laneCenter(0), y, seg.label)
      }
    }
  }

  private drawObstacle(x: number, y: number, label: string): void {
    const { ctx } = this
    ctx.font = '28px serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(label === '礁石' ? '🪨' : label === '水母' ? '🪼' : '🥅', x, y)
    ctx.fillStyle = '#5d4037'
    ctx.font = 'bold 13px sans-serif'
    ctx.fillText(label, x, y + 28)
  }

  private drawEnemy(x: number, y: number, label: string): void {
    const { ctx } = this
    ctx.font = '32px serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText('🦈', x, y)
    ctx.fillStyle = '#b71c1c'
    ctx.font = 'bold 14px sans-serif'
    ctx.fillText(label, x, y + 32)
  }

  /** 两扇门按各自车道滚动，鱼所在车道对应的门高亮 */
  private drawGates(snap: EngineSnapshot, gate: GateConfig, y: number): void {
    const { canvas } = this
    // 门卡片高度很小
    const h = canvas.height * 0.052
    const w = canvas.width * 0.2
    const lane = this.snapLaneForHighlight(snap.laneX)

    this.drawGate(this.laneCenter(gate.laneA) - w / 2, y, w, h, gate.gateA.op, gate.gateA.val, lane === gate.laneA)
    this.drawGate(this.laneCenter(gate.laneB) - w / 2, y, w, h, gate.gateB.op, gate.gateB.val, lane === gate.laneB)
  }

  private snapLaneForHighlight(laneX: number): number {
    // 与引擎 snapLane 阈值保持一致（±0.3）
    if (laneX <= -0.3) return -1
    if (laneX >= 0.3) return 1
    return 0
  }

  private drawGate(x: number, y: number, w: number, h: number, op: string, val: number, highlighted: boolean): void {
    const { ctx } = this
    ctx.fillStyle = highlighted ? 'rgba(255,235,59,0.9)' : 'rgba(255,255,255,0.75)'
    ctx.strokeStyle = highlighted ? '#f9a825' : '#4db6ac'
    ctx.lineWidth = highlighted ? 3 : 2
    ctx.beginPath()
    ctx.roundRect(x, y, w, h, 6)
    ctx.fill()
    ctx.stroke()

    ctx.fillStyle = highlighted ? '#e65100' : '#00695c'
    ctx.font = `bold ${Math.max(10, h * 0.5)}px sans-serif`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(`${op}${val}`, x + w / 2, y + h / 2)
  }

  private drawFishSchool(snap: EngineSnapshot): void {
    const { ctx, canvas } = this
    const y = this.fishY()
    const x = this.laneCenter(snap.laneX)

    ctx.font = `${canvas.height * 0.16}px serif`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText('🐟', x, y)

    ctx.font = `${canvas.height * 0.07}px serif`
    ctx.fillText('🐟', x - 45, y - 12)
    ctx.fillText('🐟', x + 48, y - 5)
    ctx.fillText('🐟', x - 32, y + 18)
  }

  private drawProgress(snap: EngineSnapshot): void {
    const { ctx, canvas } = this
    const x = 10
    const y = canvas.height * 0.4
    const barH = canvas.height * 0.25
    const barW = 8

    ctx.fillStyle = 'rgba(0,0,0,0.15)'
    ctx.beginPath()
    ctx.roundRect(x, y, barW, barH, 4)
    ctx.fill()

    const fillH = barH * snap.progress
    ctx.fillStyle = '#4db6ac'
    ctx.beginPath()
    ctx.roundRect(x, y + barH - fillH, barW, fillH, 4)
    ctx.fill()

    // 终点标记在进度条顶端
    ctx.fillStyle = '#ff8a65'
    ctx.fillRect(x - 1, y - 4, barW + 2, 4)
  }

  private drawTopInfo(count: number, score: number): void {
    const { ctx, canvas } = this
    ctx.fillStyle = '#1d2129'
    ctx.font = `bold ${canvas.height * 0.055}px sans-serif`
    ctx.textBaseline = 'top'
    ctx.textAlign = 'left'
    ctx.fillText(`🐟 ${count}`, 10, canvas.height * 0.06)
    ctx.textAlign = 'right'
    ctx.fillText(`得分 ${score}`, canvas.width - 10, canvas.height * 0.06)
  }

  // ────────────── 输入 ──────────────

  private bindInput(): void {
    window.addEventListener('keydown', this.onKeyDown)
    window.addEventListener('keyup', this.onKeyUp)
    this.canvas.addEventListener('touchstart', this.onTouchStart, { passive: false })
    this.canvas.addEventListener('touchmove', this.onTouchMove, { passive: false })
  }

  private onKeyDown = (e: KeyboardEvent): void => {
    // 传 e.repeat（按住连发）给引擎，由引擎节流重复步进
    if (e.key === 'a' || e.key === 'ArrowLeft') {
      e.preventDefault()
      this.engine.startMoveLeft(e.repeat)
    } else if (e.key === 'd' || e.key === 'ArrowRight') {
      e.preventDefault()
      this.engine.startMoveRight(e.repeat)
    }
  }

  private onKeyUp = (e: KeyboardEvent): void => {
    if (e.key === 'a' || e.key === 'ArrowLeft' || e.key === 'd' || e.key === 'ArrowRight') {
      this.engine.stopMove()
    }
  }

  private touchStartX = 0

  private onTouchStart = (e: TouchEvent): void => {
    e.preventDefault()
    this.touchStartX = e.touches[0].clientX
  }

  private onTouchMove = (e: TouchEvent): void => {
    e.preventDefault()
    const dx = e.touches[0].clientX - this.touchStartX
    this.engine.setLane(dx / 120)
  }
}
