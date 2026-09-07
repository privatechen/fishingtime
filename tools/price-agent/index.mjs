import fs from 'node:fs/promises'
import path from 'node:path'
import os from 'node:os'
import { chromium } from 'playwright'

const root = path.dirname(new URL(import.meta.url).pathname)
const skuFile = process.env.SKU_FILE || path.join(root, 'skus.txt')
const resultFile = process.env.RESULT_FILE || path.join(root, 'result.json')
const profileDir = process.env.CHROME_PROFILE_DIR || path.join(root, '.chrome-profile')
const headless = process.env.HEADLESS === '1'

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function normalizeSku(line) {
  const v = line.trim()
  if (!v || v.startsWith('#')) return null
  const match = v.match(/(\d{5,})/)
  return match?.[1] || null
}

async function readSkus() {
  const text = await fs.readFile(skuFile, 'utf8')
  return [...new Set(text.split(/\r?\n/).map(normalizeSku).filter(Boolean))]
}

function toPrice(value) {
  if (value == null) return null
  const text = String(value).replace(/,/g, '').trim()
  const match = text.match(/(?:¥|￥)?\s*(\d+(?:\.\d{1,2})?)/)
  if (!match) return null
  const n = Number(match[1])
  return Number.isFinite(n) && n > 0 && n < 10000000 ? n : null
}

function searchJsonForPrice(value, sku, depth = 0) {
  if (depth > 7 || value == null) return null
  if (Array.isArray(value)) {
    for (const item of value) {
      const p = searchJsonForPrice(item, sku, depth + 1)
      if (p != null) return p
    }
    return null
  }
  if (typeof value !== 'object') return null

  const entries = Object.entries(value)
  const skuLike = entries.find(([k]) => /sku(id)?|ware(id)?|item(id)?/i.test(k))
  const objectMatchesSku = !skuLike || String(skuLike[1]).includes(String(sku))

  if (objectMatchesSku) {
    const preferredKeys = ['price', 'p', 'jdPrice', 'salePrice', 'currentPrice', 'finalPrice', 'realPrice']
    for (const key of preferredKeys) {
      if (key in value) {
        const p = toPrice(value[key])
        if (p != null) return p
      }
    }
  }

  for (const [, child] of entries) {
    const p = searchJsonForPrice(child, sku, depth + 1)
    if (p != null) return p
  }
  return null
}

async function extractDomPrice(page) {
  const selectors = [
    '.summary-price .p-price .price',
    '.summary-price .p-price',
    '.p-price .price',
    '.p-price',
    '[class*="price"] [class*="price"]',
    '[class*="Price"]',
    '[class*="price"]'
  ]

  for (const selector of selectors) {
    const loc = page.locator(selector).filter({ visible: true }).first()
    try {
      if (await loc.count()) {
        const text = (await loc.innerText({ timeout: 800 })).trim()
        const p = toPrice(text)
        if (p != null) return { price: p, source: `dom:${selector}`, raw: text }
      }
    } catch {}
  }

  try {
    const bodyText = await page.locator('body').innerText({ timeout: 1500 })
    const lines = bodyText.split(/\r?\n/).map(s => s.trim()).filter(Boolean)
    const labels = ['京东价', '秒杀价', '到手价', '售价', '价格']
    for (let i = 0; i < lines.length; i++) {
      if (labels.some(label => lines[i].includes(label))) {
        for (const candidate of lines.slice(i, i + 4)) {
          const p = toPrice(candidate)
          if (p != null) return { price: p, source: 'dom:text-near-price-label', raw: candidate }
        }
      }
    }
  } catch {}

  return null
}

async function collectSku(page, sku) {
  const url = `https://item.jd.com/${sku}.html`
  let networkHit = null
  const networkCandidates = []

  const onResponse = async response => {
    const responseUrl = response.url()
    if (!/price|pprice|sku|ware|item|goods/i.test(responseUrl)) return
    if (networkCandidates.length < 20) networkCandidates.push(responseUrl)
    try {
      const type = response.headers()['content-type'] || ''
      if (!type.includes('json') && !type.includes('javascript') && !type.includes('text')) return
      const text = await response.text()
      if (!text || text.length > 2_000_000) return
      let data = null
      try {
        data = JSON.parse(text)
      } catch {
        const jsonMatch = text.match(/^[^(]*\((\{.*\}|\[.*\])\)\s*;?$/s)
        if (jsonMatch) {
          try { data = JSON.parse(jsonMatch[1]) } catch {}
        }
      }
      if (data != null) {
        const price = searchJsonForPrice(data, sku)
        if (price != null && !networkHit) {
          networkHit = { price, source: `network:${responseUrl}` }
        }
      }
    } catch {}
  }

  page.on('response', onResponse)
  const startedAt = new Date().toISOString()
  let title = ''
  let error = null

  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 })
    await page.waitForTimeout(3500)
    title = await page.title().catch(() => '')
    const dom = await extractDomPrice(page)
    const hit = networkHit || dom
    return {
      sku,
      url,
      title,
      price: hit?.price ?? null,
      source: hit?.source ?? null,
      raw: dom?.raw ?? null,
      checkedAt: new Date().toISOString(),
      startedAt,
      networkCandidates,
      ok: !!hit
    }
  } catch (e) {
    error = e instanceof Error ? e.message : String(e)
    return {
      sku,
      url,
      title,
      price: null,
      source: null,
      raw: null,
      checkedAt: new Date().toISOString(),
      startedAt,
      networkCandidates,
      ok: false,
      error
    }
  } finally {
    page.off('response', onResponse)
  }
}

async function main() {
  const skus = await readSkus()
  if (!skus.length) {
    console.log('skus.txt 里没有可用 SKU。')
    process.exit(1)
  }

  console.log(`Price Agent v0.1 — 共 ${skus.length} 个 SKU`)
  console.log(`本地浏览器档案: ${profileDir}`)
  console.log('首次运行如果京东未登录，请在打开的 Chrome 里手动登录一次。登录态会保存在本地档案中。')

  const context = await chromium.launchPersistentContext(profileDir, {
    channel: 'chrome',
    headless,
    viewport: { width: 1440, height: 1000 },
    locale: 'zh-CN',
    args: ['--start-maximized']
  })

  const page = context.pages()[0] || await context.newPage()
  const results = []

  for (let i = 0; i < skus.length; i++) {
    const sku = skus[i]
    process.stdout.write(`[${i + 1}/${skus.length}] ${sku} ... `)
    const result = await collectSku(page, sku)
    results.push(result)
    if (result.ok) console.log(`¥${result.price} (${result.source})`)
    else console.log('未识别到价格')

    if (i < skus.length - 1) {
      const wait = 2500 + Math.floor(Math.random() * 2500)
      await sleep(wait)
    }
  }

  const output = {
    generatedAt: new Date().toISOString(),
    host: os.hostname(),
    count: results.length,
    success: results.filter(x => x.ok).length,
    results
  }
  await fs.writeFile(resultFile, JSON.stringify(output, null, 2), 'utf8')
  console.log(`\n完成：${output.success}/${output.count} 个 SKU 获取到价格`)
  console.log(`结果文件：${resultFile}`)

  if (process.env.KEEP_OPEN === '1') {
    console.log('KEEP_OPEN=1，浏览器保持打开；Ctrl+C 结束。')
    await new Promise(() => {})
  } else {
    await context.close()
  }
}

main().catch(err => {
  console.error(err)
  process.exit(1)
})
