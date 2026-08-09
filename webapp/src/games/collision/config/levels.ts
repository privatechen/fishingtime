/**
 * 鱼群碰撞 — 关卡配置 + 随机生成
 *
 * 门数值限定：+ 为 5/10/20，× 为 2/3/4
 * 门位置：随机出现在 3 条车道中的任意 2 条（第 3 条是安全空隙）
 * 每局 buildLevel() 时随机生成，每次游玩不同
 *
 * 障碍：礁石致命（必须躲）、水母 -8、渔网 -20%
 */
export type GateOperator = '+' | '*' | '/'
export type ObstacleKind = 'reef' | 'jellyfish' | 'net'
export type Side = 'left' | 'right'

export interface GateOption {
  op: GateOperator
  val: number
}
export interface GateConfig {
  laneA: number // -1 左 / 0 中 / 1 右
  gateA: GateOption
  laneB: number
  gateB: GateOption
}
export interface ObstacleConfig {
  kind: ObstacleKind
  side: Side
}
export interface EnemyConfig {
  count: number
}

export type SegmentConfig =
  | { type: 'gate'; gate: GateConfig }
  | { type: 'obstacle'; obstacle: ObstacleConfig }
  | { type: 'enemy'; enemy: EnemyConfig }
  | { type: 'finish' }

/** 关卡骨架（门不指定数值，buildLevel 时随机生成） */
type SkeletonSegment =
  | { type: 'gate' }
  | { type: 'obstacle'; obstacle: ObstacleConfig }
  | { type: 'enemy'; enemy: EnemyConfig }
  | { type: 'finish' }

export interface CollisionLevel {
  id: number
  name: string
  initialCount: number
  targetEnemy: number
  segments: SegmentConfig[]
}

const ADD_VALUES = [5, 10, 20]
const MUL_VALUES = [2, 3, 4]
const LANES = [-1, 0, 1]

/** 随机生成一扇门（数值 + 车道位置） */
function randomGate(): GateConfig {
  // 打乱车道顺序，取前两个为门所在车道，第三个是空隙
  const lanes = [...LANES]
  for (let i = lanes.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[lanes[i], lanes[j]] = [lanes[j], lanes[i]]
  }
  const [laneA, laneB] = lanes

  const pool: GateOption[] = [
    ...ADD_VALUES.map((v) => ({ op: '+' as GateOperator, val: v })),
    ...MUL_VALUES.map((v) => ({ op: '*' as GateOperator, val: v })),
  ]
  const a = pool[Math.floor(Math.random() * pool.length)]
  let b = pool[Math.floor(Math.random() * pool.length)]
  while (b === a) {
    b = pool[Math.floor(Math.random() * pool.length)]
  }

  return { laneA, gateA: a, laneB, gateB: b }
}

/** 根据关卡骨架构建可玩的关卡（随机门数值/位置 + 障碍车道） */
export function buildLevel(id: number): CollisionLevel {
  const skeleton = collisionLevels.find((l) => l.id === id) || collisionLevels[0]
  const segments: SegmentConfig[] = skeleton.segments.map((seg) => {
    if (seg.type === 'gate') {
      return { type: 'gate', gate: randomGate() }
    }
    if (seg.type === 'obstacle') {
      const side: Side = Math.random() < 0.5 ? 'left' : 'right'
      return { type: 'obstacle', obstacle: { kind: seg.obstacle.kind, side } }
    }
    return seg
  })
  return { ...skeleton, segments }
}

const G = (): SkeletonSegment => ({ type: 'gate' })
const O = (kind: ObstacleKind, side: Side = 'left'): SkeletonSegment =>
  ({ type: 'obstacle', obstacle: { kind, side } })
const E = (count: number): SkeletonSegment => ({ type: 'enemy', enemy: { count } })
const F = (): SkeletonSegment => ({ type: 'finish' })

export const collisionLevels: {
  id: number
  name: string
  initialCount: number
  targetEnemy: number
  segments: SkeletonSegment[]
}[] = [
  {
    id: 1,
    name: '初入海洋',
    initialCount: 5,
    targetEnemy: 8,
    segments: [G(), O('reef'), G(), G(), O('reef'), G(), E(8), G(), O('reef'), G(), G(), O('jellyfish'), G(), G(), F()],
  },
  {
    id: 2,
    name: '乘法初试',
    initialCount: 8,
    targetEnemy: 15,
    segments: [G(), O('reef'), G(), O('jellyfish'), G(), E(15), G(), O('reef'), G(), G(), O('net'), G(), G(), G(), F()],
  },
  {
    id: 3,
    name: '选择开始',
    initialCount: 10,
    targetEnemy: 22,
    segments: [G(), O('reef'), G(), O('net'), G(), E(22), G(), O('reef'), G(), G(), O('jellyfish'), G(), G(), G(), F()],
  },
  {
    id: 4,
    name: '水母来袭',
    initialCount: 12,
    targetEnemy: 30,
    segments: [G(), O('jellyfish'), G(), O('reef'), G(), E(30), G(), O('jellyfish'), G(), G(), O('net'), G(), G(), G(), F()],
  },
  {
    id: 5,
    name: '渔网危机',
    initialCount: 15,
    targetEnemy: 40,
    segments: [G(), O('net'), G(), O('jellyfish'), G(), E(40), G(), O('reef'), G(), G(), O('jellyfish'), G(), O('net'), G(), G(), F()],
  },
  {
    id: 6,
    name: '除法之道',
    initialCount: 18,
    targetEnemy: 55,
    segments: [G(), O('reef'), G(), O('net'), G(), E(55), G(), O('jellyfish'), G(), G(), O('reef'), G(), O('net'), G(), G(), F()],
  },
  {
    id: 7,
    name: '连续双门',
    initialCount: 20,
    targetEnemy: 70,
    segments: [G(), G(), O('jellyfish'), G(), O('net'), G(), E(70), G(), O('jellyfish'), G(), G(), O('net'), G(), G(), O('reef'), F()],
  },
  {
    id: 8,
    name: '密集障碍',
    initialCount: 25,
    targetEnemy: 85,
    segments: [G(), O('net'), O('reef'), G(), O('jellyfish'), G(), E(85), O('reef'), G(), O('jellyfish'), G(), O('net'), G(), G(), G(), O('reef'), F()],
  },
  {
    id: 9,
    name: '两次敌群',
    initialCount: 30,
    targetEnemy: 110,
    segments: [G(), O('reef'), E(40), G(), O('net'), G(), E(60), O('jellyfish'), G(), O('reef'), G(), O('net'), G(), G(), G(), O('jellyfish'), F()],
  },
  {
    id: 10,
    name: '章鱼终局',
    initialCount: 35,
    targetEnemy: 150,
    segments: [G(), O('jellyfish'), G(), O('net'), E(70), G(), O('reef'), G(), E(80), G(), O('jellyfish'), G(), O('net'), G(), G(), G(), F()],
  },
]
