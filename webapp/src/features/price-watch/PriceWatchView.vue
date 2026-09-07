<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Header from '@/components/Header.vue'
import Footer from '@/components/Footer.vue'
import { useAdminAuth } from '@/stores/adminAuth'

type Platform = '京东' | '淘宝' | '天猫' | '拼多多' | ''

interface WatchItem {
  id: number
  title: string
  platform: Exclude<Platform, ''>
  purchasePrice: number
  currentPrice: number | null
  days: number
  createdAt: string
}

const STORAGE_KEY = 'fishingtime:price-watch:prototype'
const { isAdmin, checkAdmin } = useAdminAuth()

const productUrl = ref('')
const purchasePrice = ref('')
const watchDays = ref(15)
const activeTab = ref<'watching' | 'history'>('watching')
const notice = ref('')
const items = ref<WatchItem[]>([])

const platform = computed<Platform>(() => {
  const url = productUrl.value.toLowerCase()
  if (url.includes('jd.com') || url.includes('3.cn')) return '京东'
  if (url.includes('tmall.com')) return '天猫'
  if (url.includes('taobao.com') || url.includes('tb.cn')) return '淘宝'
  if (url.includes('pinduoduo.com') || url.includes('yangkeduo.com')) return '拼多多'
  return ''
})

const validUrl = computed(() => /^https?:\/\//i.test(productUrl.value.trim()) && !!platform.value)

function loadPrototypeData() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    items.value = raw ? JSON.parse(raw) : []
  } catch {
    items.value = []
  }
}

function savePrototypeData() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(items.value))
}

function recognize() {
  notice.value = ''
  if (!productUrl.value.trim()) {
    notice.value = '请先粘贴商品链接'
    return
  }
  if (!validUrl.value) {
    notice.value = '当前原型仅识别京东、淘宝、天猫和拼多多链接'
    return
  }
  notice.value = `已识别为${platform.value}商品。当前版本先验证页面流程，商品标题、图片和实时价格接口将在下一步接入。`
}

function addWatch() {
  notice.value = ''
  if (!validUrl.value) {
    notice.value = '请先粘贴并识别支持的商品链接'
    return
  }
  const price = Number(purchasePrice.value)
  if (!Number.isFinite(price) || price <= 0) {
    notice.value = '请输入正确的购买价格'
    return
  }
  items.value.unshift({
    id: Date.now(),
    title: `${platform.value}商品（价格接口待接入）`,
    platform: platform.value as Exclude<Platform, ''>,
    purchasePrice: price,
    currentPrice: null,
    days: watchDays.value,
    createdAt: new Date().toLocaleDateString('zh-CN'),
  })
  savePrototypeData()
  productUrl.value = ''
  purchasePrice.value = ''
  watchDays.value = 15
  notice.value = '已加入本地监控原型。后续接入价格接口后会自动刷新商品信息和价格。'
}

function removeItem(id: number) {
  items.value = items.value.filter(item => item.id !== id)
  savePrototypeData()
}

function scrollToTop() {
  globalThis.window?.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(async () => {
  await checkAdmin()
  if (isAdmin.value) loadPrototypeData()
})
</script>

<template>
  <Header />
  <main class="price-watch-page">
    <div v-if="!isAdmin" class="access-card">
      <h1>无访问权限</h1>
      <p>比价功能目前处于内部测试阶段，仅 admin 用户可访问。</p>
      <router-link to="/" class="ghost-link">返回首页</router-link>
    </div>

    <template v-else>
      <section class="hero">
        <div>
          <div class="title-row">
            <h1>比价</h1>
            <span class="beta">Beta</span>
          </div>
          <p>买完别急，我帮你盯价格</p>
        </div>
        <div class="hero-visual" aria-hidden="true">
          <div class="speech">价格降了？<br><b>我来提醒你！</b></div>
          <div class="fish">🐟</div>
        </div>
        <div class="feature-list">
          <span>◷　自动监控商品价格</span>
          <span>♧　价格下降及时提醒</span>
          <span>◇　支持京东 / 淘宝 / 天猫 / 拼多多</span>
        </div>
      </section>

      <section class="panel add-panel">
        <div class="panel-title"><span class="link-icon">⌁</span> 添加监控商品</div>
        <div class="url-row">
          <div class="url-input-wrap">
            <input v-model="productUrl" placeholder="请粘贴商品链接（支持京东、淘宝、天猫、拼多多）" @keyup.enter="recognize" />
            <small>例如：https://item.jd.com/100012345678.html</small>
          </div>
          <button class="primary-btn recognize-btn" @click="recognize">识别商品</button>
        </div>

        <div class="recognition-box" :class="{ recognized: validUrl }">
          <template v-if="validUrl">
            <div class="product-placeholder">{{ platform.slice(0, 1) }}</div>
            <div class="recognized-copy">
              <strong>已识别 {{ platform }} 商品链接</strong>
              <span>第一版先验证完整交互；商品名称、图片、当前公开价格待价格接口接入。</span>
            </div>
            <span class="platform-chip">{{ platform }}</span>
          </template>
          <template v-else>
            <div class="empty-link">⌁</div>
            <span>粘贴商品链接，系统将自动识别商品信息</span>
          </template>
        </div>

        <div class="settings-row">
          <label>
            <span>你的购买价</span>
            <div class="money-input"><b>¥</b><input v-model="purchasePrice" inputmode="decimal" placeholder="1299.00" /></div>
          </label>
          <div class="days-field">
            <span>监控天数</span>
            <div class="days-options">
              <button v-for="d in [7, 15, 30]" :key="d" :class="{ active: watchDays === d }" @click="watchDays = d">{{ d }}天</button>
            </div>
          </div>
          <button class="primary-btn start-btn" @click="addWatch">开始盯价</button>
        </div>
        <p v-if="notice" class="notice">{{ notice }}</p>
        <p class="fine-print">ⓘ 当前为内部原型：购买价由你填写，实时商品价格接口暂未接入，不会对外发送提醒。</p>
      </section>

      <div class="content-grid">
        <section class="panel monitor-panel">
          <div class="tabs">
            <button :class="{ active: activeTab === 'watching' }" @click="activeTab = 'watching'">正在监控 ({{ items.length }})</button>
            <button :class="{ active: activeTab === 'history' }" @click="activeTab = 'history'">历史提醒 (0)</button>
          </div>

          <div v-if="activeTab === 'history'" class="empty-state">
            <div class="box-icon">▱</div>
            <h3>还没有降价提醒</h3>
            <p>接入价格接口后，发现价格下降会记录在这里。</p>
          </div>

          <div v-else-if="items.length === 0" class="empty-state">
            <div class="box-icon">▱</div>
            <h3>还没有监控商品</h3>
            <p>快去添加一个商品链接吧，我帮你盯价格！</p>
            <button class="primary-btn small-btn" @click="scrollToTop">＋ 添加商品</button>
          </div>

          <div v-else class="watch-list">
            <article v-for="item in items" :key="item.id" class="watch-item">
              <div class="item-thumb">{{ item.platform.slice(0, 1) }}</div>
              <div class="item-main">
                <strong>{{ item.title }}</strong>
                <span>{{ item.platform }} · 开始时间：{{ item.createdAt }}</span>
              </div>
              <div class="metric"><span>购买价</span><b>¥{{ item.purchasePrice.toFixed(2) }}</b></div>
              <div class="metric"><span>当前价</span><b class="muted">待接入</b></div>
              <div class="metric"><span>监控周期</span><b>{{ item.days }} 天</b></div>
              <button class="remove-btn" @click="removeItem(item.id)">移除</button>
            </article>
          </div>
        </section>

        <aside class="panel guide-panel">
          <div class="guide-title">❔　使用说明</div>
          <ol>
            <li><b>粘贴商品链接</b><span>支持京东、淘宝、天猫、拼多多</span></li>
            <li><b>确认商品信息</b><span>后续由系统自动识别商品名称、图片和当前价格</span></li>
            <li><b>设置购买价和监控天数</b><span>选择 7 天 / 15 天 / 30 天</span></li>
            <li><b>开始监控</b><span>发现价格下降后再接入站内/微信提醒</span></li>
          </ol>
          <div class="tip">
            <b>💡 温馨提示</b>
            <span>1. 当前仅对 admin 用户开放使用。</span>
            <span>2. 价格数据来源仍在验证，本页面暂为功能原型。</span>
            <span>3. 实际保价结果最终以电商平台规则为准。</span>
          </div>
        </aside>
      </div>
    </template>
  </main>
  <Footer />
</template>

<style scoped>
.price-watch-page{min-height:calc(100vh - 64px);padding:92px max(24px,calc((100vw - 1320px)/2)) 48px;background:#f5f8fb;color:#1f2937}.access-card{max-width:680px;margin:90px auto;background:#fff;border-radius:18px;padding:50px;text-align:center;box-shadow:0 8px 30px rgba(31,41,55,.06)}.access-card h1{margin:0 0 12px;font-size:28px}.access-card p{color:#7b8794;margin-bottom:24px}.ghost-link{display:inline-block;padding:10px 22px;border:1px solid #dce4ec;border-radius:12px;color:#4d6577;text-decoration:none}.hero{display:grid;grid-template-columns:1.2fr .9fr 1fr;align-items:center;gap:24px;margin-bottom:18px;padding:4px 16px 10px}.title-row{display:flex;align-items:center;gap:10px}.title-row h1{font-size:40px;line-height:1;margin:0;color:#151b23}.beta{font-size:12px;color:#ff6a1a;background:#fff0e6;border-radius:8px;padding:4px 8px}.hero p{font-size:20px;color:#6f7f91;margin:14px 0 0}.hero-visual{display:flex;align-items:center;justify-content:center;gap:10px}.speech{background:#fff1e5;border-radius:24px;padding:14px 18px;transform:rotate(-4deg);font-size:14px;color:#303846}.fish{font-size:66px;filter:saturate(.9)}.feature-list{background:linear-gradient(135deg,#fff8f1,#fff);border-radius:16px;padding:16px 20px;display:flex;flex-direction:column;gap:10px;color:#596a7a;font-size:13px}.panel{background:#fff;border:1px solid #e9eef3;border-radius:16px;box-shadow:0 6px 24px rgba(38,57,77,.05)}.add-panel{padding:22px;margin-bottom:18px}.panel-title{font-size:18px;font-weight:700;margin-bottom:16px}.link-icon{font-size:23px;margin-right:7px}.url-row{display:flex;gap:12px}.url-input-wrap{flex:1;border:1px solid #dbe3eb;border-radius:12px;padding:9px 14px}.url-input-wrap input{width:100%;border:0;outline:0;font-size:14px;color:#2d3c4a}.url-input-wrap small{display:block;color:#a4afba;margin-top:7px}.primary-btn{border:0;background:linear-gradient(135deg,#ff7a24,#ff5b16);color:#fff;border-radius:12px;font-weight:700;cursor:pointer;box-shadow:0 6px 14px rgba(255,97,24,.15)}.recognize-btn{width:126px}.recognition-box{min-height:92px;border:1px dashed #dce5ed;border-radius:12px;margin-top:12px;display:flex;align-items:center;justify-content:center;gap:12px;color:#8090a0;background:#fbfdff}.recognition-box.recognized{justify-content:flex-start;padding:0 18px;border-style:solid;background:#fffdfa}.empty-link{width:42px;height:42px;border-radius:50%;background:#edf2f7;display:grid;place-items:center;font-size:24px}.product-placeholder,.item-thumb{width:52px;height:52px;border-radius:12px;background:#fff0e7;color:#ff681c;display:grid;place-items:center;font-weight:800;font-size:22px}.recognized-copy{display:flex;flex-direction:column;gap:6px;flex:1}.recognized-copy span{font-size:12px;color:#8795a2}.platform-chip{font-size:12px;color:#ff681c;background:#fff0e7;border-radius:8px;padding:6px 10px}.settings-row{display:grid;grid-template-columns:1fr 1.5fr 150px;gap:18px;align-items:end;margin-top:16px}.settings-row label>span,.days-field>span{display:block;font-size:13px;font-weight:600;margin-bottom:7px}.money-input{height:44px;border:1px solid #dde5ec;border-radius:10px;display:flex;align-items:center;padding:0 12px;gap:8px}.money-input input{border:0;outline:0;width:100%;font-size:15px}.days-options{display:flex;gap:8px}.days-options button{height:44px;min-width:76px;border:1px solid #dce4eb;background:#fff;border-radius:10px;cursor:pointer}.days-options button.active{border-color:#ff681c;color:#ff681c;background:#fff8f3}.start-btn{height:46px}.notice{margin:12px 0 0;color:#d65b18;font-size:13px}.fine-print{margin:12px 0 0;color:#8a98a7;font-size:12px}.content-grid{display:grid;grid-template-columns:minmax(0,1fr) 350px;gap:18px}.monitor-panel{min-height:390px;overflow:hidden}.tabs{height:58px;border-bottom:1px solid #edf1f5;display:flex;align-items:end;padding-left:18px;gap:18px}.tabs button{height:58px;border:0;background:none;padding:0 12px;color:#526476;font-weight:700;cursor:pointer;border-bottom:3px solid transparent}.tabs button.active{color:#ff681c;border-color:#ff681c}.empty-state{height:330px;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#7f8e9e}.empty-state h3{margin:15px 0 5px;color:#26384a}.empty-state p{margin:0 0 18px;font-size:13px}.box-icon{font-size:52px;color:#d3dce5}.small-btn{padding:12px 22px}.watch-list{padding:8px 20px}.watch-item{display:grid;grid-template-columns:54px minmax(180px,1fr) 110px 100px 100px 66px;gap:14px;align-items:center;padding:15px 0;border-bottom:1px solid #edf1f5}.item-main{display:flex;flex-direction:column;gap:6px}.item-main span,.metric span{font-size:11px;color:#8998a7}.metric{display:flex;flex-direction:column;gap:5px}.metric b{font-size:13px}.metric .muted{color:#97a4af}.remove-btn{border:1px solid #e1e8ee;background:#fff;color:#768899;border-radius:9px;padding:8px;cursor:pointer}.guide-panel{padding:20px}.guide-title{font-size:17px;font-weight:800;margin-bottom:18px}.guide-panel ol{list-style:none;padding:0;margin:0}.guide-panel li{position:relative;padding:0 0 18px 42px;display:flex;flex-direction:column;gap:5px}.guide-panel li::before{content:counter(list-item);position:absolute;left:0;top:0;width:28px;height:28px;border-radius:50%;background:#fff0e7;color:#ff681c;display:grid;place-items:center;font-weight:800}.guide-panel li span{font-size:12px;color:#8494a4;line-height:1.55}.tip{border-top:1px solid #edf1f5;padding-top:16px;display:flex;flex-direction:column;gap:7px;font-size:12px;color:#7c8b9a}.tip b{color:#344556;margin-bottom:3px}@media(max-width:980px){.hero{grid-template-columns:1fr}.hero-visual,.feature-list{display:none}.settings-row{grid-template-columns:1fr}.content-grid{grid-template-columns:1fr}.watch-item{grid-template-columns:54px 1fr 90px}.watch-item .metric:nth-of-type(n+2){display:none}}@media(max-width:680px){.price-watch-page{padding:78px 12px 30px}.url-row{flex-direction:column}.recognize-btn{width:100%;height:44px}.settings-row{gap:12px}.hero{padding:0 4px}.title-row h1{font-size:32px}.watch-item{grid-template-columns:44px 1fr 56px}.item-thumb{width:44px;height:44px}.guide-panel{display:none}}
</style>
