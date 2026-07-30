import type { HotItem } from '@/types'

const weibo: HotItem[] = [
  { platform: 'weibo', rank: 1, title: '全国多地高温预警持续发布', hot: '328万', tag: '社会' },
  { platform: 'weibo', rank: 2, title: '暑期档电影票房突破百亿', hot: '256万', tag: '娱乐' },
  { platform: 'weibo', rank: 3, title: '新一代智能手表发布', hot: '198万', tag: '科技' },
  { platform: 'weibo', rank: 4, title: '国际泳联锦标赛中国队再夺金', hot: '167万', tag: '体育' },
  { platform: 'weibo', rank: 5, title: '城市马拉松赛本周日开跑', hot: '145万', tag: '体育' },
  { platform: 'weibo', rank: 6, title: '新型电池技术突破续航瓶颈', hot: '132万', tag: '科技' },
  { platform: 'weibo', rank: 7, title: '热门综艺新一季阵容官宣', hot: '118万', tag: '娱乐' },
  { platform: 'weibo', rank: 8, title: '高温天气如何正确防暑', hot: '105万', tag: '健康' },
  { platform: 'weibo', rank: 9, title: '新能源汽车销量再创新高', hot: '92万', tag: '汽车' },
  { platform: 'weibo', rank: 10, title: '气象部门发布台风预警', hot: '87万', tag: '社会' },
  { platform: 'weibo', rank: 11, title: '智能家居市场快速增长', hot: '76万', tag: '科技' },
  { platform: 'weibo', rank: 12, title: '暑期旅游热门目的地推荐', hot: '65万', tag: '旅游' },
]

const baidu: HotItem[] = [
  { platform: 'baidu', rank: 1, title: '全国高温天气范围扩大', hot: '412万', tag: '社会' },
  { platform: 'baidu', rank: 2, title: '教育部发布暑期安全提示', hot: '356万', tag: '教育' },
  { platform: 'baidu', rank: 3, title: '粮食产量再创新高', hot: '298万', tag: '三农' },
  { platform: 'baidu', rank: 4, title: '国产大飞机完成新航线验证', hot: '234万', tag: '科技' },
  { platform: 'baidu', rank: 5, title: '新能源发电量占比持续提升', hot: '189万', tag: '财经' },
  { platform: 'baidu', rank: 6, title: '多个城市放宽落户政策', hot: '167万', tag: '社会' },
  { platform: 'baidu', rank: 7, title: '人工智能赋能制造业转型', hot: '143万', tag: '科技' },
  { platform: 'baidu', rank: 8, title: '暑期档电影票房创新高', hot: '128万', tag: '娱乐' },
  { platform: 'baidu', rank: 9, title: '碳交易市场成交额突破记录', hot: '112万', tag: '财经' },
  { platform: 'baidu', rank: 10, title: '全民健身日活动即将启动', hot: '98万', tag: '体育' },
  { platform: 'baidu', rank: 11, title: '数字人民币试点扩大范围', hot: '85万', tag: '财经' },
  { platform: 'baidu', rank: 12, title: '跨境电商进出口额增长', hot: '72万', tag: '财经' },
]

const zhihu: HotItem[] = [
  { platform: 'zhihu', rank: 1, title: '如何看待近期全国范围高温现象？', hot: '876万', tag: '社会' },
  { platform: 'zhihu', rank: 2, title: '2026年最值得入手的汽车推荐', hot: '654万', tag: '汽车' },
  { platform: 'zhihu', rank: 3, title: '程序员如何应对AI时代的挑战？', hot: '543万', tag: '科技' },
  { platform: 'zhihu', rank: 4, title: '有哪些适合夏天的小众旅游地？', hot: '432万', tag: '旅游' },
  { platform: 'zhihu', rank: 5, title: '如何培养良好的理财习惯？', hot: '387万', tag: '财经' },
  { platform: 'zhihu', rank: 6, title: '《三体》影视化改编前景如何', hot: '345万', tag: '娱乐' },
  { platform: 'zhihu', rank: 7, title: '长期坚持运动有哪些变化？', hot: '298万', tag: '健康' },
  { platform: 'zhihu', rank: 8, title: '2026年互联网行业就业趋势', hot: '267万', tag: '职场' },
  { platform: 'zhihu', rank: 9, title: '租房时需要注意哪些细节？', hot: '234万', tag: '生活' },
  { platform: 'zhihu', rank: 10, title: '有哪些令人惊艳的国产独立游戏？', hot: '198万', tag: '游戏' },
  { platform: 'zhihu', rank: 11, title: '如何科学制定学习计划？', hot: '176万', tag: '教育' },
  { platform: 'zhihu', rank: 12, title: '智能家居设备选购指南', hot: '154万', tag: '科技' },
]

export const hotData: Record<string, HotItem[]> = {
  weibo,
  baidu,
  zhihu,
}
