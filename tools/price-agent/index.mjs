import fs from 'node:fs/promises'
import path from 'node:path'
import os from 'node:os'
import { chromium } from 'playwright'

const root = path.dirname(new URL(import.meta.url).pathname)
const skuFile = process.env.SKU_FILE || path.join(root, 'skus.txt')
const resultFile = process.env.RESULT_FILE || path.join(root, 'result.json')
const profileDir = process.env.CHROME_PROFILE_DIR || path.join(root, '.chrome-profile')
const headless = process.env.HEADLESS === '1'

function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)) }

function normalizeSku(line) {
  const v = line.trim()
  if (!v || v.startsWith('#')) return null
  return v.match(/(\d{5,})/)?.[1] || null
}

async function readSkus() {
  const text = await fs.readFile(skuFile, 'utf8')
  return [...new Set(text.split(/\r?\n/).map(normalizeSku).filter(Boolean))]
}

function toPrice(value) {
  if (value == null) return null
  const n = Number(String(value).replace(/[¥￥,\s]/g, ''))
  return Number.isFinite(n) && n > 0 && n < 10000000 ? n : null
}

function findBestPromotion(value, depth = 0) {
  if (value == null || typeof value !== 'object' || depth > 12) return null
  if (value.bestPromotion && typeof value.bestPromotion === 'object') {
    const price = toPrice(value.bestPromotion.purchasePrice)
    if (price != null) return { price, bestPromotion: value.bestPromotion }
  }
  for (const child of Object.values(value)) {
    const found = findBestPromotion(child, depth + 1)
    if (found) return found
  }
  return null
}

async function extractDomPrice(page) {
  for (const selector of ['.summary-price .p-price .price', '.p-price .price', '.p-price']) {
    try {
      const loc = page.locator(selector).first()
      if (await loc.count()) {
        const raw = (await loc.innerText({ timeout: 800 })).trim()
        const price = toPrice(raw)
        if (price != null) return { price, source: `dom:${selector}`, raw }
      }
    } catch {}
  }
  return null
}

async function collectSku(page, sku) {
  const url = `https://item.jd.com/${sku}.html`
  let wareBusinessHit = null
  let wareBusinessSeen = false

  const onResponse = async response => {
    const responseUrl = response.url()
    if (!responseUrl.includes('api.m.jd.com/')) return
    if (!responseUrl.includes('functionId=pc_detailpage_wareBusiness')) return
    wareBusinessSeen = true
    try {
      const data = await response.json()
      const found = findBestPromotion(data)
      if (found && !wareBusinessHit) {
        wareBusinessHit = {
          price: found.price,
          source: 'network:pc_detailpage_wareBusiness.bestPromotion.purchasePrice',
          bestPromotion: found.bestPromotion,
          responseUrl
        }
      }
    } catch (e) {
      console.warn(`\n  wareBusiness 响应解析失败: ${e instanceof Error ? e.message : String(e)}`)
    }
  }

  page.on('response', onResponse)
  const startedAt = new Date().toISOString()
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 })
    // 给 pc_detailpage_wareBusiness 留出返回时间。
    for (let i = 0; i < 20 && !wareBusinessHit; i++) await page.waitForTimeout(250)

    const title = await page.title().catch(() => '')
    const dom = wareBusinessHit ? null : await extractDomPrice(page)
    const hit = wareBusinessHit || dom
    return {
      sku, url, title,
      price: hit?.price ?? null,
      source: hit?.source ?? null,
      checkedAt: new Date().toISOString(), startedAt,
      wareBusinessSeen,
      bestPromotion: wareBusinessHit?.bestPromotion ?? null,
      ok: !!hit
    }
  } catch (e) {
    return {
      sku, url, title: '', price: null, source: null,
      checkedAt: new Date().toISOString(), startedAt,
      wareBusinessSeen, bestPromotion: null, ok: false,
      error: e instanceof Error ? e.message : String(e)
    }
  } finally {
    page.off('response', onResponse)
  }
}

async function main() {
  const skus = await readSkus()
  if (!skus.length) throw new Error('skus.txt 里没有可用 SKU。')

  console.log(`Price Agent v0.2 — 共 ${skus.length} 个 SKU`)
  console.log(`Chrome 登录档案: ${profileDir}`)
  console.log('首次运行请在 Agent 打开的 Chrome 中登录京东；以后会复用这个登录态。')

  const context = await chromium.launchPersistentContext(profileDir, {
    channel: 'chrome', headless,
    viewport: { width: 1440, height: 1000 }, locale: 'zh-CN',
    args: ['--start-maximized']
  })
  const page = context.pages()[0] || await context.newPage()
  const results = []

  for (let i = 0; i < skus.length; i++) {
    const sku = skus[i]
    process.stdout.write(`[${i + 1}/${skus.length}] ${sku} ... `)
    const result = await collectSku(page, sku)
    results.push(result)
    console.log(result.ok ? `¥${result.price} (${result.source})` : `未识别到价格${result.wareBusinessSeen ? '（已捕获 wareBusiness，但未找到 purchasePrice）' : '（未捕获 wareBusiness）'}`)
    if (i < skus.length - 1) await sleep(2500 + Math.floor(Math.random() * 2500))
  }

  const output = {
    generatedAt: new Date().toISOString(), host: os.hostname(),
    count: results.length, success: results.filter(x => x.ok).length, results
  }
  await fs.writeFile(resultFile, JSON.stringify(output, null, 2), 'utf8')
  console.log(`\n完成：${output.success}/${output.count}；结果：${resultFile}`)
  if (process.env.KEEP_OPEN === '1') await new Promise(() => {})
  await context.close()
}

main().catch(err => { console.error(err); process.exit(1) })
