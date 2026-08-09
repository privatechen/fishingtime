/**
 * 鱼群碰撞 — LocalStorage 存档
 * Key:
 * - unlockedLevel: 已解锁最高关卡
 * - bestScores: 各关最高分 { "1": 2300 }
 * - lastLevel: 最近游玩关卡
 */
const KEY_UNLOCKED = 'fishingtime:collision:unlockedLevel'
const KEY_BEST = 'fishingtime:collision:bestScores'
const KEY_LAST = 'fishingtime:collision:lastLevel'

export const collisionStore = {
  getUnlockedLevel(): number {
    return Number(localStorage.getItem(KEY_UNLOCKED) || 1)
  },

  setUnlockedLevel(level: number): void {
    const current = this.getUnlockedLevel()
    if (level > current) {
      localStorage.setItem(KEY_UNLOCKED, String(level))
    }
  },

  getBestScore(levelId: number): number {
    try {
      const map = JSON.parse(localStorage.getItem(KEY_BEST) || '{}')
      return map[String(levelId)] || 0
    } catch {
      return 0
    }
  },

  setBestScore(levelId: number, score: number): void {
    try {
      const map = JSON.parse(localStorage.getItem(KEY_BEST) || '{}')
      if (score > (map[String(levelId)] || 0)) {
        map[String(levelId)] = score
        localStorage.setItem(KEY_BEST, JSON.stringify(map))
      }
    } catch {
      // 忽略
    }
  },

  getLastLevel(): number {
    return Number(localStorage.getItem(KEY_LAST) || 1)
  },

  setLastLevel(level: number): void {
    localStorage.setItem(KEY_LAST, String(level))
  },
}
