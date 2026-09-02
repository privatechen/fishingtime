<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

interface DailyStat {
  date: string
  uv: number
  pv: number
  newUsers: number
}
interface AnalyticsData {
  todayUv: number
  todayPv: number
  todayNewUsers: number
  yesterdayUv: number
  avgVisits: number
  daily: DailyStat[]
}

const data = ref<AnalyticsData | null>(null)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await fetch('/api/analytics/admin/overview?days=7', { credentials: 'same-origin' })
    const json = await res.json()
    if (json.code === 200 && json.data) data.value = json.data
    else error.value = json.message || '加载失败'
  } catch {
    error.value = '网络异常，加载失败'
  } finally {
    loading.value = false
  }
}

const maxUv = computed(() => Math.max(1, ...(data.value?.daily || []).map(i => i.uv)))
const chartPoints = computed(() => {
  const items = data.value?.daily || []
  if (items.length === 0) return ''
  return items.map((item, index) => {
    const x = items.length === 1 ? 50 : 8 + (84 * index) / (items.length - 1)
    const y = 88 - (72 * item.uv) / maxUv.value
    return `${x},${y}`
  }).join(' ')
})
const delta = computed(() => (data.value?.todayUv || 0) - (data.value?.yesterdayUv || 0))

function shortDate(value: string) {
  const parts = value.split('-')
  return parts.length === 3 ? `${Number(parts[1])}/${Number(parts[2])}` : value
}

onMounted(load)
</script>

<template>
  <div class="analytics-wrap">
    <div class="analytics-head">
      <div>
        <h1>小程序数据</h1>
        <p>统计已识别用户的进入情况 · 同一用户当天 UV 只计 1 次</p>
      </div>
      <button class="refresh-btn" :disabled="loading" @click="load">{{ loading ? '刷新中...' : '刷新' }}</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <div v-if="loading && !data" class="card empty">正在加载...</div>

    <template v-else-if="data">
      <div class="kpi-grid">
        <div class="card kpi">
          <span>今日用户</span><strong>{{ data.todayUv }}</strong>
          <small :class="{ up: delta > 0, down: delta < 0 }">较昨日 {{ delta >= 0 ? '+' : '' }}{{ delta }}</small>
        </div>
        <div class="card kpi">
          <span>今日访问</span><strong>{{ data.todayPv }}</strong>
          <small>进入 / 回到前台的有效访问</small>
        </div>
        <div class="card kpi">
          <span>新增用户</span><strong>{{ data.todayNewUsers }}</strong>
          <small>今天首次被系统识别</small>
        </div>
        <div class="card kpi">
          <span>人均访问</span><strong>{{ data.avgVisits.toFixed(2) }}</strong>
          <small>PV / UV</small>
        </div>
      </div>

      <div class="card trend-card">
        <div class="section-head">
          <div><h2>近 7 天用户趋势</h2><p>每日独立访问用户（UV）</p></div>
          <div class="legend"><i></i> 用户数</div>
        </div>
        <div v-if="data.daily.length === 0" class="empty">暂无访问数据</div>
        <div v-else class="chart">
          <svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-label="近7天用户趋势">
            <line v-for="y in [16, 40, 64, 88]" :key="y" x1="8" :y1="y" x2="92" :y2="y" class="grid-line" />
            <polyline :points="chartPoints" class="trend-line" />
            <circle v-for="(item, index) in data.daily" :key="item.date"
              :cx="data.daily.length === 1 ? 50 : 8 + (84 * index) / (data.daily.length - 1)"
              :cy="88 - (72 * item.uv) / maxUv" r="0.55" class="dot" />
          </svg>
          <div class="x-axis">
            <span v-for="item in data.daily" :key="item.date">{{ shortDate(item.date) }}</span>
          </div>
        </div>
      </div>

      <div class="card detail-card">
        <div class="section-head"><div><h2>每日明细</h2><p>最近 7 天访问统计</p></div></div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>日期</th><th>用户数</th><th>访问次数</th><th>新增用户</th><th>人均访问</th></tr></thead>
            <tbody>
              <tr v-for="item in [...data.daily].reverse()" :key="item.date">
                <td>{{ item.date }}</td><td>{{ item.uv }}</td><td>{{ item.pv }}</td><td>{{ item.newUsers }}</td>
                <td>{{ item.uv ? (item.pv / item.uv).toFixed(2) : '0.00' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.analytics-wrap{display:flex;flex-direction:column;gap:18px}.analytics-head{display:flex;align-items:center;justify-content:space-between;gap:20px}.analytics-head h1{font-size:26px;margin:0 0 7px}.analytics-head p,.section-head p{margin:0;color:var(--color-text-secondary);font-size:13px}.refresh-btn{border:1px solid var(--color-border);background:var(--color-card);border-radius:18px;padding:9px 18px;cursor:pointer;color:var(--color-text-secondary)}.card{background:var(--color-card);border-radius:16px;box-shadow:var(--shadow)}.kpi-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}.kpi{padding:22px}.kpi span{display:block;color:var(--color-text-secondary);font-size:13px}.kpi strong{display:block;font-size:30px;margin:8px 0 5px;color:var(--color-text)}.kpi:first-child strong,.kpi:nth-child(3) strong{color:var(--color-primary)}.kpi small{font-size:11px;color:var(--color-text-muted)}.kpi small.up{color:#38a169}.kpi small.down{color:var(--color-danger)}.trend-card,.detail-card{padding:24px}.section-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.section-head h2{font-size:18px;margin:0 0 5px}.legend{font-size:12px;color:var(--color-text-secondary);display:flex;align-items:center;gap:6px}.legend i{width:7px;height:7px;border-radius:50%;background:var(--color-primary)}.chart{height:230px}.chart svg{width:100%;height:190px;overflow:visible}.grid-line{stroke:#edf0f3;stroke-width:.5;vector-effect:non-scaling-stroke}.trend-line{fill:none;stroke:var(--color-primary);stroke-width:2;vector-effect:non-scaling-stroke;stroke-linecap:round;stroke-linejoin:round}.dot{fill:var(--color-primary);stroke:#fff;stroke-width:.45;vector-effect:non-scaling-stroke;opacity:.95}.x-axis{display:flex;justify-content:space-between;padding:0 4%;font-size:11px;color:var(--color-text-muted)}.table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse;font-size:13px}th,td{text-align:left;padding:12px 10px;border-bottom:1px solid var(--color-border)}th{color:var(--color-text-secondary);font-weight:500}td{color:var(--color-text)}.empty{text-align:center;padding:48px;color:var(--color-text-muted)}.error{color:var(--color-danger);font-size:13px}.refresh-btn:disabled{opacity:.5}@media(max-width:720px){.kpi-grid{grid-template-columns:repeat(2,1fr)}.analytics-head{align-items:flex-start}.trend-card,.detail-card{padding:18px}}@media(max-width:430px){.kpi-grid{grid-template-columns:1fr 1fr;gap:10px}.kpi{padding:16px}.kpi strong{font-size:25px}.analytics-head h1{font-size:22px}}
</style>
