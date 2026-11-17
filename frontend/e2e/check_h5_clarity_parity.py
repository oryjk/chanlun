"""Verify H5 Clarity UI parity against live backend & capture 390px / 1440px screenshots.

Run:
  uv run --with playwright python frontend/e2e/check_h5_clarity_parity.py \
    --browser-executable /home/betalpha/.cache/ms-playwright/chromium-1208/chrome-linux64/chrome
"""

import argparse
from pathlib import Path
from playwright.sync_api import sync_playwright

SCREENSHOT_DIR = Path(
    "/home/betalpha/.gemini/antigravity-cli/brain/7799fbc0-cbea-4af3-9103-b335d6b30d36/screenshots"
)


def check_nav_and_alignment(page, width: int, out_dir: Path) -> None:
    nav_labels = page.locator(".bottom-tabbar button span").all_inner_texts()
    assert nav_labels == ["行情", "跟踪", "榜单", "菜单"], f"Unexpected nav: {nav_labels}"

    # 等待行情列表加载完成
    page.locator(".market-asset-row").first.wait_for(timeout=20000)
    assert page.evaluate("document.documentElement.scrollWidth <= innerWidth"), f"Horizontal overflow on market list ({width}px)"

    # 检查表头（成交额 / 涨跌幅 / 最新价）与数据单元格右边缘误差 <= 1px
    deltas = page.evaluate("""() => {
      const cols = ['amount', 'change_percent', 'latest'];
      return cols.map(col => {
        const thText = document.querySelector(`.market-asset-table thead th.col-${col} button span`);
        const tdSpan = document.querySelector(`.market-asset-table tbody tr:first-child td.col-${col} span`);
        if (!thText || !tdSpan) return { col, missing: true };
        const thRight = thText.getBoundingClientRect().right;
        const tdRight = tdSpan.getBoundingClientRect().right;
        return {
          col,
          thRight: Number(thRight.toFixed(2)),
          tdRight: Number(tdRight.toFixed(2)),
          delta: Number(Math.abs(thRight - tdRight).toFixed(2)),
          value: tdSpan.textContent.trim(),
        };
      });
    }""")
    print(f"[{width}px] Market column right-edge alignment:", deltas, flush=True)
    for item in deltas:
        assert not item.get("missing"), f"Missing column {item}"
        assert item["delta"] <= 1.0, f"Column {item['col']} misaligned at {width}px: {item}"
        assert item["value"] != "", f"Column {item['col']} empty at {width}px"

    page.screenshot(path=str(out_dir / f"h5_market_stock_{width}_light.png"))

    # 切换 ETF
    page.get_by_role("tab", name="ETF", exact=True).click()
    page.locator(".market-asset-row .asset-meta-line", has_text="ETF").first.wait_for(timeout=15000)
    assert page.evaluate("document.documentElement.scrollWidth <= innerWidth"), f"Horizontal overflow on ETF ({width}px)"
    page.screenshot(path=str(out_dir / f"h5_market_etf_{width}_light.png"))

    # 切回股票并等待真实股票行就绪再点击进入详情
    page.get_by_role("tab", name="股票", exact=True).click()
    page.locator(".market-asset-row .asset-meta-line", has_text="A").first.wait_for(timeout=15000)
    page.locator(".market-asset-row").first.click()
    page.locator(".market-chart canvas").wait_for(timeout=20000)
    assert page.evaluate("document.documentElement.scrollWidth <= innerWidth"), f"Horizontal overflow on detail ({width}px)"
    page.screenshot(path=str(out_dir / f"h5_detail_{width}_light.png"))

    # 切到跟踪 Tab
    page.locator(".bottom-tabbar button", has_text="跟踪").click()
    page.locator(".tracking-page h1").wait_for()
    assert page.evaluate("document.documentElement.scrollWidth <= innerWidth"), f"Horizontal overflow on tracking ({width}px)"
    page.screenshot(path=str(out_dir / f"h5_tracking_{width}_light.png"))

    # 切到榜单 Tab，核查无冗余徽标且 4 字资产名不截断
    page.locator(".bottom-tabbar button", has_text="榜单").click()
    page.locator(".ranking-page h1").wait_for()
    # 等待榜单结果或空态呈现
    page.locator(".ranking-list-section, .empty-state").first.wait_for(timeout=15000)
    assert page.locator(".point-confirm-tag").count() == 0, "Redundant point-confirm-tag still rendered!"
    truncated = page.evaluate("""() => {
      return [...document.querySelectorAll('.ranking-name-line strong')]
        .slice(0, 10)
        .filter(el => el.scrollWidth > el.clientWidth + 1)
        .map(el => ({ text: el.textContent, scrollWidth: el.scrollWidth, clientWidth: el.clientWidth }));
    }""")
    assert not truncated, f"Truncated ranking names at {width}px: {truncated}"
    assert page.evaluate("document.documentElement.scrollWidth <= innerWidth"), f"Horizontal overflow on rankings ({width}px)"
    page.screenshot(path=str(out_dir / f"h5_rankings_{width}_light.png"))

    # 切到菜单 Tab，核查默认头像、登录弹窗触发及深色 OLED 模式
    page.locator(".bottom-tabbar button", has_text="菜单").click()
    page.locator(".menu-page .profile-default-avatar svg").wait_for()
    assert page.evaluate("document.documentElement.scrollWidth <= innerWidth"), f"Horizontal overflow on menu ({width}px)"
    page.screenshot(path=str(out_dir / f"h5_menu_{width}_light.png"))

    # 打开登录模态验证邮箱登录与注册表单可渲染
    page.locator(".menu-profile-row").get_by_role("button", name="登录", exact=True).click()
    page.locator(".email-auth-form").wait_for()
    if width == 390:
        page.screenshot(path=str(out_dir / f"h5_login_{width}_light.png"))
    page.get_by_role("button", name="关闭登录弹窗", exact=True).click()

    # 切换到深色模式并验证 body 背景为纯黑 OLED #000000
    page.get_by_role("button", name="深色", exact=True).click()
    page.wait_for_timeout(200)
    bg = page.evaluate("getComputedStyle(document.body).backgroundColor")
    assert bg == "rgb(0, 0, 0)", f"Expected OLED pure black body, got {bg}"
    page.screenshot(path=str(out_dir / f"h5_menu_{width}_dark.png"))

    # 深色模式切回行情截图检查
    page.locator(".bottom-tabbar button", has_text="行情").click()
    page.locator(".market-asset-row").first.wait_for(timeout=15000)
    page.screenshot(path=str(out_dir / f"h5_market_stock_{width}_dark.png"))


def check_authenticated_tracking_view(browser, base_url: str, out_dir: Path) -> None:
    """Verify signed-in Tracking view with ¥10,000 simulated principal, growth stages & summary."""
    from urllib.parse import urlparse

    for width, height in ((390, 844), (1440, 960)):
        context = browser.new_context(viewport={"width": width, "height": height})
        page = context.new_page()

        def handle_api(route):
            path = urlparse(route.request.url).path
            if path == "/api/auth/session":
                route.fulfill(json={"authenticated": True, "user": {"user_id": 42, "nickname": "缠观测试号", "avatar_url": None}})
            elif path == "/api/tracking":
                route.fulfill(json={
                    "as_of_date": "2026-11-17",
                    "items": [
                        {
                            "stock": {"secid": "300308.SZ", "symbol": "300308", "name": "中际旭创", "market": "深A"},
                            "start_date": "2026-09-10",
                            "start_price": 320.50,
                            "latest_price": 403.12,
                            "day_return_pct": -1.43,
                            "day_profit": -182.40,
                            "cumulative_return_pct": 25.78,
                            "cumulative_profit": 2578.00,
                            "current_value": 12578.00,
                            "sparkline": [340.0, 355.0, 370.0, 385.0, 408.97, 403.12],
                        },
                        {
                            "stock": {"secid": "510300.SH", "symbol": "510300", "name": "沪深300ETF", "market": "ETF"},
                            "start_date": "2026-11-12",
                            "start_price": 4.020,
                            "latest_price": 4.041,
                            "day_return_pct": 0.00,
                            "day_profit": 0.00,
                            "cumulative_return_pct": 0.52,
                            "cumulative_profit": 52.00,
                            "current_value": 10052.00,
                            "sparkline": [4.020, 4.035, 4.041, 4.041],
                        },
                    ],
                })
            elif path == "/api/rankings":
                route.fulfill(json={"generated_at": None, "timeframe": "1d", "bullish": [], "bearish": [], "state": "ready", "analysis_version": 4})
            else:
                route.continue_()

        page.route("**/api/**", handle_api)
        page.goto(base_url.rstrip("/") + "/?view=tracking", wait_until="domcontentloaded")
        page.locator(".tracking-row").first.wait_for()
        assert "¥22,630.00" in page.locator(".tracking-summary-strip").inner_text()
        assert "繁茂" in page.locator(".tracking-row").first.inner_text()
        assert "萌芽" in page.locator(".tracking-row").nth(1).inner_text()
        assert "0.00%" in page.locator(".tracking-row").nth(1).inner_text()
        assert "+0.00%" not in page.locator(".tracking-row").nth(1).inner_text()
        page.screenshot(path=str(out_dir / f"h5_tracking_signed_in_{width}_light.png"))
        context.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:5173")
    parser.add_argument("--browser-executable", required=True)
    parser.add_argument("--output-dir", default=str(SCREENSHOT_DIR))
    args = parser.parse_args()

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, executable_path=args.browser_executable)
        try:
            for width, height in ((390, 844), (1440, 960)):
                context = browser.new_context(viewport={"width": width, "height": height})
                page = context.new_page()
                errors = []
                page.on("pageerror", lambda err: errors.append(str(err)))
                page.goto(args.base_url.rstrip("/") + "/", wait_until="domcontentloaded")
                check_nav_and_alignment(page, width, out_dir)
                assert not errors, f"Page errors at {width}px: {errors}"
                context.close()
                print(f"[{width}px] H5 clarity parity verification PASSED", flush=True)
            check_authenticated_tracking_view(browser, args.base_url, out_dir)
            print("Authenticated tracking verification PASSED", flush=True)
        finally:
            browser.close()


if __name__ == "__main__":
    main()
