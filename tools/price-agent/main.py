import asyncio
import json
import os
import random
import re
import socket
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional

from playwright.async_api import async_playwright, Page, Response

ROOT = Path(__file__).resolve().parent
SKU_FILE = Path(os.getenv("SKU_FILE", ROOT / "skus.txt"))
RESULT_FILE = Path(os.getenv("RESULT_FILE", ROOT / "result.json"))
CDP_URL = os.getenv("CDP_URL", "http://127.0.0.1:9222")
WARE_BUSINESS_FUNCTION = "pc_detailpage_wareBusiness"


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def normalize_sku(line: str) -> Optional[str]:
    value = line.strip()
    if not value or value.startswith("#"):
        return None
    match = re.search(r"(\d{5,})", value)
    return match.group(1) if match else None


def read_skus() -> list[str]:
    text = SKU_FILE.read_text(encoding="utf-8")
    result, seen = [], set()
    for line in text.splitlines():
        sku = normalize_sku(line)
        if sku and sku not in seen:
            seen.add(sku)
            result.append(sku)
    return result


def to_price(value: Any) -> Optional[float]:
    if value is None:
        return None
    text = str(value).replace("¥", "").replace("￥", "").replace(",", "").strip()
    try:
        number = float(text)
    except ValueError:
        return None
    return number if 0 < number < 10_000_000 else None


def find_best_promotion(value: Any, depth: int = 0) -> Optional[dict[str, Any]]:
    if depth > 12:
        return None
    if isinstance(value, dict):
        best = value.get("bestPromotion")
        if isinstance(best, dict):
            price = to_price(best.get("purchasePrice"))
            if price is not None:
                return {"price": price, "bestPromotion": best}
        for child in value.values():
            found = find_best_promotion(child, depth + 1)
            if found:
                return found
    elif isinstance(value, list):
        for child in value:
            found = find_best_promotion(child, depth + 1)
            if found:
                return found
    return None


async def extract_dom_price(page: Page) -> Optional[dict[str, Any]]:
    for selector in [".summary-price .p-price .price", ".p-price .price", ".p-price"]:
        try:
            locator = page.locator(selector).first
            if await locator.count():
                raw = (await locator.inner_text(timeout=800)).strip()
                price = to_price(raw)
                if price is not None:
                    return {"price": price, "source": f"dom:{selector}", "raw": raw}
        except Exception:
            pass
    return None


async def collect_sku(page: Page, sku: str) -> dict[str, Any]:
    url = f"https://item.jd.com/{sku}.html"
    started_at = now_iso()
    ware_business_seen = False
    ware_business_hit: Optional[dict[str, Any]] = None

    async def on_response(response: Response) -> None:
        nonlocal ware_business_seen, ware_business_hit
        response_url = response.url
        if "api.m.jd.com/" not in response_url or f"functionId={WARE_BUSINESS_FUNCTION}" not in response_url:
            return
        ware_business_seen = True
        try:
            data = await response.json()
            found = find_best_promotion(data)
            if found and ware_business_hit is None:
                ware_business_hit = {
                    "price": found["price"],
                    "source": "network:pc_detailpage_wareBusiness.bestPromotion.purchasePrice",
                    "bestPromotion": found["bestPromotion"],
                    "responseUrl": response_url,
                }
        except Exception as exc:
            print(f"\n  wareBusiness 响应解析失败: {exc}")

    page.on("response", on_response)
    try:
        await page.goto(url, wait_until="domcontentloaded", timeout=30_000)
        for _ in range(24):
            if ware_business_hit is not None:
                break
            await page.wait_for_timeout(250)

        title = await page.title()
        dom_hit = None if ware_business_hit else await extract_dom_price(page)
        hit = ware_business_hit or dom_hit
        return {
            "sku": sku, "url": url, "title": title,
            "price": hit.get("price") if hit else None,
            "source": hit.get("source") if hit else None,
            "checkedAt": now_iso(), "startedAt": started_at,
            "wareBusinessSeen": ware_business_seen,
            "bestPromotion": ware_business_hit.get("bestPromotion") if ware_business_hit else None,
            "ok": bool(hit),
        }
    except Exception as exc:
        return {
            "sku": sku, "url": url, "title": "", "price": None, "source": None,
            "checkedAt": now_iso(), "startedAt": started_at,
            "wareBusinessSeen": ware_business_seen, "bestPromotion": None,
            "ok": False, "error": str(exc),
        }
    finally:
        page.remove_listener("response", on_response)


async def main() -> None:
    skus = read_skus()
    if not skus:
        raise RuntimeError("skus.txt 里没有可用 SKU")

    print(f"Price Agent Python v0.2 — 共 {len(skus)} 个 SKU")
    print(f"连接现有 Chrome: {CDP_URL}")

    async with async_playwright() as p:
        try:
            browser = await p.chromium.connect_over_cdp(CDP_URL)
        except Exception as exc:
            raise RuntimeError(
                f"无法连接 Chrome {CDP_URL}。请先用 --remote-debugging-port=9222 启动 Chrome。\n原始错误: {exc}"
            ) from exc

        if not browser.contexts:
            raise RuntimeError("已连接 Chrome，但没有可用浏览器上下文")
        context = browser.contexts[0]
        page = context.pages[0] if context.pages else await context.new_page()
        results: list[dict[str, Any]] = []

        for index, sku in enumerate(skus, start=1):
            print(f"[{index}/{len(skus)}] {sku} ... ", end="", flush=True)
            result = await collect_sku(page, sku)
            results.append(result)
            if result["ok"]:
                print(f"¥{result['price']} ({result['source']})")
            elif result["wareBusinessSeen"]:
                print("未识别到价格（已捕获 wareBusiness，但未找到 purchasePrice）")
            else:
                print("未识别到价格（未捕获 wareBusiness）")
            if index < len(skus):
                await asyncio.sleep(random.uniform(2.5, 5.0))

        output = {
            "generatedAt": now_iso(), "host": socket.gethostname(),
            "count": len(results), "success": sum(1 for item in results if item["ok"]),
            "results": results,
        }
        RESULT_FILE.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"\n完成：{output['success']}/{output['count']}；结果：{RESULT_FILE}")
        print("Chrome 保持打开，Agent 不会关闭你手动启动的浏览器。")


if __name__ == "__main__":
    asyncio.run(main())
