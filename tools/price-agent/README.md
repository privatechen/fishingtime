# FishingTime Price Agent v0.1

这是一个完全独立于 FishingTime 主业务的本地价格探针，用于验证：**能否利用本机 Chrome 的京东登录态，批量读取 SKU 当前显示价格。**

当前版本不会调用 FishingTime 后端，也不会上传 Cookie、账号密码或浏览器存储。它只在本机打开京东商品页，尝试从浏览器 Network 响应和页面 DOM 中提取价格，并把结果写到 `result.json`。

## 1. 环境

- Node.js 18+
- 本机已安装 Google Chrome

## 2. 安装

```bash
cd tools/price-agent
npm install
```

Playwright 会作为控制层使用，但实际启动的是你电脑上安装的 Chrome（`channel: chrome`）。

## 3. 填 SKU

编辑 `skus.txt`，一行一个：

```text
100012345678
100076543210
```

也可以直接写京东商品链接：

```text
https://item.jd.com/100012345678.html
```

程序会自动提取 SKU。

## 4. 运行

```bash
npm start
```

首次启动会创建本地专用浏览器档案：

```text
.chrome-profile/
```

如果打开京东后显示未登录，请在这个 Chrome 窗口里手动登录一次。之后再运行 Agent，会复用这份本地登录态。

> 这里故意不直接读取你日常 Chrome 的 Default Profile，避免 Chrome Profile 锁、数据损坏以及直接操作你的主浏览器档案。

## 5. 输出

执行结束会生成：

```text
result.json
```

示例：

```json
{
  "sku": "100012345678",
  "price": 2399,
  "source": "dom:.p-price .price",
  "ok": true
}
```

如果价格来自浏览器网络响应，`source` 会以 `network:` 开头。

如果拿不到价格，结果里会保留本次页面加载过程中命中的若干 `networkCandidates`，方便下一步定位京东真实的价格请求。

## 6. 当前提取策略

按优先级：

1. 监听浏览器 Network Response，筛选 URL 中包含 `price / sku / ware / item / goods` 的响应，再递归寻找价格字段。
2. Network 未拿到时，尝试京东常见价格 DOM 选择器。
3. 最后从页面正文中，在“京东价 / 秒杀价 / 到手价 / 售价 / 价格”附近寻找金额。

这只是 PoC，不保证适配京东当前所有页面。第一轮目的就是拿真实页面跑一遍，确定价格到底来自哪个响应或 DOM，再把提取逻辑收紧。

## 7. 调试

想让浏览器执行完后保持打开：

```bash
KEEP_OPEN=1 npm start
```

无头运行：

```bash
HEADLESS=1 npm start
```

如果某个 SKU 获取失败，把对应 `result.json` 中这一项（尤其是 `networkCandidates`）发出来，就可以继续针对当前京东页面调整解析逻辑。
