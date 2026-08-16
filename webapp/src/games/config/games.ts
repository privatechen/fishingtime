/**
 * 小游戏配置 — 驱动大厅渲染
 *
 * 未来新增游戏：在此数组增加一项 + 新增对应分包组件/引擎
 * 大厅组件零改动，自动渲染新卡片
 */
import colorFocusCover from '@/assets/png/games/small/颜色陷阱.png'
import directionTrapCover from '@/assets/png/games/small/方向陷阱.png'
import colorHunterCover from '@/assets/png/games/small/颜色捕手.png'
import cover2048 from '@/assets/png/games/small/2048.png'
import fishBreakoutCover from '@/assets/png/games/small/鱼群突围.png'
import extremeFishingCover from '@/assets/png/games/small/极限捞鱼.png'

export interface GameConfig {
  /** 游戏唯一标识，同时是路由路径 */
  id: string
  title: string
  desc: string
  /** 封面（可用 emoji 或图片路径） */
  cover: string
  /** 状态：available 可玩 / coming-soon 敬请期待 */
  status: 'available' | 'coming-soon'
  /** 可玩游戏的入口路径 */
  path?: string
}

export const games: GameConfig[] = [
  {
    id: '2048',
    title: '摸鱼2048',
    desc: '合并相同数字，挑战 2048',
    cover: cover2048,
    status: 'available',
    path: '/games/2048',
  },
  {
    id: 'fish-breakout',
    title: '鱼群突围',
    desc: '30 秒连续清空鱼池',
    cover: fishBreakoutCover,
    status: 'available',
    path: '/games/fish-breakout',
  },
  {
    id: 'extreme-fishing',
    title: '极限捞鱼',
    desc: '拖动撒网，30 秒冲分',
    cover: extremeFishingCover,
    status: 'available',
    path: '/games/extreme-fishing',
  },
  {
    id: 'color-focus',
    title: '选颜色',
    desc: '40 秒专注力挑战',
    cover: colorFocusCover,
    status: 'available',
    path: '/games/color-focus',
  },
  {
    id: 'direction-trap',
    title: '方向陷阱',
    desc: '40 秒反应挑战',
    cover: directionTrapCover,
    status: 'available',
    path: '/games/direction-trap',
  },
  {
    id: 'color-hunter',
    title: '颜色猎手',
    desc: '找出所有目标颜色',
    cover: colorHunterCover,
    status: 'available',
    path: '/games/color-hunter',
  },
  {
    id: 'detail',
    title: '细节',
    desc: '看图 10 秒，记住每一个细节',
    cover: '👀',
    status: 'available',
    path: '/games/detail',
  },
  {
    id: 'minesweeper',
    title: '扫雷',
    desc: '经典益智扫雷游戏',
    cover: '💣',
    status: 'coming-soon',
  },
  {
    id: 'gomoku',
    title: '五子棋',
    desc: '人机对战五子棋',
    cover: '⚫',
    status: 'coming-soon',
  },
  {
    id: 'tetris',
    title: '俄罗斯方块',
    desc: '经典方块下落游戏',
    cover: '🟦',
    status: 'coming-soon',
  },
]
