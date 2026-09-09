# FishingTime Price Agent（Python）

这是一个独立于 FishingTime 主业务的本地价格探针，用于验证：**能否利用本机 Chrome 的京东登录态，批量读取 SKU 当前价格。**

当前版本不会调用 FishingTime 后端，也不会上传 Cookie、账号密码或浏览器存储。它只在本机打开京东商品页，监听浏览器网络响应，并优先读取：

```text
https://api.m.jd.com/?functionId=pc_detailpage_wareBusiness
```

响应 JSON 中的：

```text
bestPromotion.purchasePrice
```

如果该接口没有拿到价格，会再尝试页面 DOM 作为兜底。

## 1. 环境

- Python 3.10+
- 本机已安装 Google Chrome

## 2. 安装

```bash
cd tools/price-agent
python -m pip install -r requirements.txt
python -m playwright install
```

实际运行时指定 `channel="chrome"`，因此启动的是你电脑本机安装的 Chrome。

## 3. 填 SKU

编辑 `skus.txt`，一行一个：

```text
5634161
100012345678
```

也可以直接写京东商品链接：

```text
https://item.jd.com/5634161.html
```

程序会自动提取 SKU。

## 4. 运行

```bash
python main.py
```

首次启动会创建本地专用浏览器档案：

```text
.chrome-profile/
```

如果打开京东后显示未登录，请在这个 Chrome 窗口里手动登录一次。之后再次运行 Agent，会继续复用这份登录态。

这里故意不直接读取你日常 Chrome 的 Default Profile，避免浏览器 Profile 锁以及误操作主浏览器数据。

## 5. 输出

执行结束会生成：

```text
result.json
```

成功时类似：

```json
{
  "sku": "5634161",
  "price": 39.9,
  "source": "network:pc_detailpage_wareBusiness.bestPromotion.purchasePrice",
  "wareBusinessSeen": true,
  "ok": true
}
```

如果显示：

```text
已捕获 wareBusiness，但未找到 purchasePrice
```

说明接口已经监听到了，只是返回结构和当前解析规则不一致。

如果显示：

```text
未捕获 wareBusiness
```

说明当前商品页加载过程中没有监听到目标接口，需要继续看浏览器实际请求。

## 6. 调试

让浏览器执行完后保持打开：

macOS / Linux：

```bash
KEEP_OPEN=1 python main.py
```

Windows PowerShell：

```powershell
$env:KEEP_OPEN="1"
python main.py
```

无头运行：

macOS / Linux：

```bash
HEADLESS=1 python main.py
```

Windows PowerShell：

```powershell
$env:HEADLESS="1"
python main.py
```

当前仍然是 PoC。第一步建议只在 `skus.txt` 放 `5634161`，先确认本机登录状态下可以稳定捕获 `pc_detailpage_wareBusiness` 并读取 `bestPromotion.purchasePrice`。
