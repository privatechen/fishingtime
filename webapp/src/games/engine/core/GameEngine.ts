/**
 * 通用游戏引擎接口
 * 后续游戏（扫雷/五子棋等）实现此接口，保证 UI 层与引擎解耦
 */
export interface GameEngine {
  /** 初始化/重置游戏 */
  init(): void
  /** 是否游戏结束 */
  isGameOver(): boolean
}
