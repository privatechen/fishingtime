/**
 * 《细节》游戏 API 客户端 + 类型
 *
 * 服务端权威判定：start 只返回图片，draw 返回问题（不含答案），answer 服务端判题，
 * finish 汇总答对数 + 累计用时并落库。重复 answer/finish 幂等。
 */

export interface DetailRoundInfo {
  round: number
  imageKey: string
  imageUrl: string
}

export interface DetailStartResponse {
  sessionId: string
  observationMs: number
  rounds: DetailRoundInfo[]
}

export interface DetailDrawResponse {
  questionId: number
  questionText: string
  /** 乱序后的 4 个选项文本 */
  options: string[]
  /** 与 options 对应的选项键 A/B/C/D */
  optionKeys: string[]
}

export interface DetailAnswerResponse {
  correct: boolean
  correctOption: string
  correctAnswer: string
  elapsedMs: number
}

export interface DetailRoundResult {
  round: number
  /** 是否实际参与（未抽题的轮次 = false，不计入成绩） */
  played: boolean
  correct: boolean
  /** 是否超时 */
  timeout: boolean
  elapsedMs: number
}

export interface DetailFinishResponse {
  correctCount: number
  /** 实际作答（含超时）的轮次数 */
  answeredCount: number
  answerTimeMs: number
  saved: boolean
  bestCorrectCount: number | null
  bestAnswerTimeMs: number | null
  todayRank: number | null
  allRank: number | null
  /** 每轮明细 */
  rounds: DetailRoundResult[]
}

async function post<T>(url: string, body?: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const json = await res.json()
  if (json.code !== 200) {
    throw new Error(json.message || '请求失败')
  }
  return json.data as T
}

export const detailApi = {
  start: () => post<DetailStartResponse>('/api/games/detail/start'),
  draw: (sessionId: string, round: number, number: number) =>
    post<DetailDrawResponse>(`/api/games/detail/${sessionId}/round/${round}/draw`, { number }),
  answer: (sessionId: string, round: number, option: string | null) =>
    post<DetailAnswerResponse>(`/api/games/detail/${sessionId}/round/${round}/answer`, { option }),
  finish: (sessionId: string) => post<DetailFinishResponse>(`/api/games/detail/${sessionId}/finish`),
}
