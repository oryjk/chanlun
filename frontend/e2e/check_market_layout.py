"""Check chart positions while market responses are pending and after they arrive.

Run with the Vite dev server running:
  uv run --with playwright python frontend/e2e/check_market_layout.py
Use --browser-executable to reuse an already installed Chromium binary.
"""

import argparse
from urllib.parse import parse_qs, urlparse

from playwright.sync_api import sync_playwright


def snapshot(timeframe="1d"):
    # Complete API DTO with a long source label that used to squeeze the title.
    candles = [
        {"time": f"2026-10-{day:02d}T15:00:00", "open": 1430.0, "close": 1480.0,
         "high": 1500.0, "low": 1400.0, "volume": 120000}
        for day in range(1, 21)
    ]
    return {
        "instrument": {"secid": "1.600519", "symbol": "600519", "name": "贵州茅台",
                       "decimal": 2, "data_source": "新浪财经 · 不复权（备用）", "currency": "CNY", "exchange": "SH"},
        "timeframe": timeframe, "timeframe_label": {"1d": "日线", "30m": "30 分钟"}[timeframe],
        "candles": candles,
        "analysis": {"fractals": [], "pens": [], "segments": [], "zhongshu": [], "merged_count": 20, "trend": "neutral"},
        "summary": {"latest": 1480.0, "change": 40.9, "change_percent": 2.84, "high": 1500.0, "low": 1400.0, "volume": 2400000},
        "guidance": {"bias": "neutral", "reasons": ["等待结构确认"], "invalidation_price": None, "strength": 0},
        "macd": [{"dif": 1.0, "dea": 0.5, "histogram": 1.0} for _ in candles],
        "signals": {"trend_type": "consolidation", "divergences": [], "points": []},
    }


def positions(page):
    return page.evaluate("""() => Object.fromEntries(
        ['.stock-tools', '.timeframe-tabs', '.chart-card', '.chart-wrap'].map(selector => {
            const {x, y, width, height} = document.querySelector(selector).getBoundingClientRect();
            return [selector, {x, y: y + scrollY, width, height}];
        }))""")


def assert_stable(before, after, phase):
    for selector, dimensions in before.items():
        for axis, value in dimensions.items():
            difference = abs(value - after[selector][axis])
            assert difference <= 0.5, f"{phase}: {selector} {axis} shifted {difference:.2f}px ({value} -> {after[selector][axis]})"


def check_width(browser, base_url, width):
    page = browser.new_page(viewport={"width": width, "height": 844})
    pending = []
    pending_search = []
    hold_search = True
    stocks = {"items": [{"secid": "1.600519", "symbol": "600519", "name": "贵州茅台", "market": "沪A"}]}
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))

    def api(route):
        path = urlparse(route.request.url).path
        if path == "/api/chan":
            pending.append(route)
        elif path == "/api/chan/confluence":
            route.fulfill(json={"secid": "1.600519", "levels": [], "conclusion": "暂无同向印证"})
        elif path == "/api/auth/session":
            route.fulfill(json={"authenticated": False, "user": None})
        elif path == "/api/stocks/search":
            if hold_search:
                pending_search.append(route)
            else:
                route.fulfill(json=stocks)
        else:
            route.continue_()

    page.route("**/api/**", api)
    try:
        page.goto(base_url + "/?secid=1.600519&timeframe=1d", wait_until="domcontentloaded")
        page.locator(".chart-state").wait_for()
        assert pending, "initial snapshot request missing"
        initial = positions(page)
        pending.pop().fulfill(json=snapshot())
        page.locator(".chart-state").wait_for(state="detached")
        page.locator(".market-chart canvas").wait_for()
        loaded = positions(page)
        assert_stable(initial, loaded, "initial load")

        hold_search = False
        while pending_search:
            pending_search.pop().fulfill(json=stocks)
        page.get_by_role("heading", name="贵州茅台", exact=True).wait_for()
        assert_stable(loaded, positions(page), "stock name resolution")

        for _ in range(2):
            page.get_by_role("button", name="刷新行情", exact=True).click()
            page.locator(".chart-state").wait_for()
            assert_stable(loaded, positions(page), "refresh pending")
            pending.pop().fulfill(json=snapshot())
            page.locator(".chart-state").wait_for(state="detached")
            assert_stable(loaded, positions(page), "refresh complete")

        page.get_by_role("tab", name="30 分钟", exact=True).click()
        page.locator(".chart-state").wait_for()
        assert_stable(loaded, positions(page), "timeframe pending")
        route = pending.pop()
        assert parse_qs(urlparse(route.request.url).query)["timeframe"] == ["30m"]
        route.fulfill(json=snapshot("30m"))
        page.locator(".chart-state").wait_for(state="detached")
        assert_stable(loaded, positions(page), "timeframe complete")
        assert page.evaluate("document.documentElement.scrollWidth <= innerWidth"), "horizontal overflow"
        assert not errors, errors
        print(f"{width}px: initial load, repeated refresh and timeframe switch keep chart geometry stable", flush=True)
    finally:
        while pending:
            pending.pop().abort()
        while pending_search:
            pending_search.pop().abort()
        page.unroute_all(behavior="wait")
        page.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:5173")
    parser.add_argument("--browser-executable")
    args = parser.parse_args()
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True, executable_path=args.browser_executable)
        try:
            for width in (320, 375, 390, 600, 768, 1440):
                check_width(browser, args.base_url.rstrip("/"), width)
        finally:
            browser.close()


if __name__ == "__main__":
    main()
