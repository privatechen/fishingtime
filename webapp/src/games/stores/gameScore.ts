/**
 * 游戏分数本地存储管理
 * 使用 LocalStorage 保存棋盘、分数、本地最高分
 */
const BOARD_KEY = 'game2048:board'
const SCORE_KEY = 'game2048:score'
const BEST_KEY = 'game2048:best'

export const gameScoreStore = {
  /** 保存当前棋盘 */
  saveBoard(board: number[][]): void {
    localStorage.setItem(BOARD_KEY, JSON.stringify(board))
  },

  /** 读取当前棋盘 */
  loadBoard(): number[][] | null {
    const raw = localStorage.getItem(BOARD_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw)
    } catch {
      return null
    }
  },

  /** 保存当前分数 */
  saveScore(score: number): void {
    localStorage.setItem(SCORE_KEY, String(score))
  },

  /** 读取当前分数 */
  loadScore(): number {
    return Number(localStorage.getItem(SCORE_KEY) || 0)
  },

  /** 保存本地最高分 */
  saveBest(score: number): void {
    const best = this.loadBest()
    if (score > best) {
      localStorage.setItem(BEST_KEY, String(score))
    }
  },

  /** 读取本地最高分 */
  loadBest(): number {
    return Number(localStorage.getItem(BEST_KEY) || 0)
  },

  /** 清空当前游戏状态（保留最高分） */
  clearCurrent(): void {
    localStorage.removeItem(BOARD_KEY)
    localStorage.removeItem(SCORE_KEY)
  },
}
