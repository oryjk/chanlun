"""Verify signal grouping/date semantics against controlled complete API responses.

uv run --with playwright python frontend/e2e/check_signal_groups.py
"""
import argparse
from copy import deepcopy
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import sync_playwright
from check_market_layout import snapshot


def board_fixture():
    def entry(index, confirmed=True):
        point = {"kind": 3, "side": "buy", "pen_id": 2, "candle_index": 24,
                 "time": "2026-03-02T00:00:00+08:00" if index == 1 else "2026-11-06T00:00:00+08:00", "price": 10, "confirmed": confirmed,
                 "reason": "回踩未进入中枢", "invalidation_price": 9, "source_zone_id": 1}
        quality = {"state": "confirmed" if confirmed else "candidate", "invalidation_price": 9,
                   "momentum_status": "unvalidated", "volume_status": "unvalidated",
                   "macd_histogram": .4, "macd_area_ratio": None, "pullback_macd_ratio": .5,
                   "breakout_volume_ratio": 1.3, "pullback_volume_ratio": .6,
                   "history_status": "checked_from_pivot", "first_detected_at": None, "confirmed_at": None,
                   "first_reference_crossed_at": None, "first_reference_crossed_close": None,
                   "history_checked_through": "2026-11-09T00:00:00+08:00",
                   "reasons": [], "bars_since_pivot": 171 if index == 1 else 1}
        return {"rank": index, "stock": {"secid": f"1.600{index:03d}", "symbol": f"600{index:03d}",
                "name": f"{'确认测试' if confirmed else '观察测试'}{index}", "market": "沪A"},
                "bias": "bullish", "strength": 4, "latest": 10.3, "change_percent": 2,
                "invalidation_price": 9, "latest_point": point, "buy_point": point, "sell_point": None,
                "trend": [{"time": "2026-11-06T00:00:00+08:00", "price": 10},
                          {"time": "2026-11-09T00:00:00+08:00", "price": 10.3}],
                "signal_group": "confirmed" if confirmed else "observation", "signal_quality": quality}
    entries = [entry(i) for i in range(1, 13)] + [entry(13, False)]
    invalidated = entry(14)
    invalidated['latest_point']['time'] = '2026-09-01T00:00:00+08:00'
    invalidated['signal_group'] = 'observation'
    invalidated['signal_quality'].update(state='invalidated', history_status='invalidated',
        first_reference_crossed_at='2026-09-15T00:00:00+08:00', first_reference_crossed_close=8.8,
        bars_since_pivot=45)
    entries.append(invalidated)
    return {"generated_at": "2026-11-09T07:49:00Z", "trade_date": "2026-11-09", "timeframe": "1d",
            "bullish": entries, "bearish": [], "total_count": 14, "analyzed_count": 14, "failed_count": 0,
            "state": "ready", "progress_total": 14, "progress_processed": 14, "update_error": None,
            "analysis_version": 4}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:5173')
    parser.add_argument('--browser-executable')
    parser.add_argument('--output-dir', default='/tmp/chanlun-signal-screenshots')
    args = parser.parse_args()
    out = Path(args.output_dir)
    out.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, executable_path=args.browser_executable)
        try:
            for width in (390, 1440):
                context = browser.new_context(viewport={"width": width, "height": 1000 if width == 1440 else 844}, timezone_id='UTC')
                page = context.new_page()
                board = board_fixture()
                market = snapshot()
                confirmed_point = deepcopy(board['bullish'][0]['latest_point'])
                candidate_entry = next(item for item in board['bullish'] if not item['latest_point']['confirmed'])
                candidate_point = deepcopy(candidate_entry['latest_point'])
                for point, index in ((confirmed_point, 10), (candidate_point, 18)):
                    point.update(time=market['candles'][index]['time'], candle_index=index,
                                 price=1450, invalidation_price=1400, pen_id=index)
                market['signals']['points'] = [confirmed_point, candidate_point]
                market['signal_quality'] = deepcopy(candidate_entry['signal_quality'])
                market['signal_quality']['invalidation_price'] = 1400
                market['signal_quality']['history_checked_through'] = market['candles'][-1]['time']
                errors = []
                page.on('pageerror', lambda error: errors.append(str(error)))

                def api(route):
                    path = urlparse(route.request.url).path
                    if path == '/api/rankings':
                        route.fulfill(json=board)
                    elif path == '/api/auth/session':
                        route.fulfill(json={"authenticated": False, "user": None})
                    elif path == '/api/stocks/search':
                        route.fulfill(json={"items": []})
                    elif path == '/api/chan':
                        route.fulfill(json=market)
                    elif path == '/api/chan/confluence':
                        route.fulfill(json={'secid': '1.600519', 'levels': [], 'conclusion': '暂无已确认同向印证'})
                    else:
                        route.continue_()

                page.route('**/api/**', api)
                page.goto(args.base_url + '/?view=rankings', wait_until='domcontentloaded')
                confirmed = page.locator('.ranking-card.bullish [data-group="confirmed"]')
                observation = page.locator('.ranking-card.bullish [data-group="observation"]')
                confirmed.locator('.ranking-row').first.wait_for()
                assert confirmed.locator('.ranking-row').count() == 10
                assert confirmed.locator('.point-confirm-tag.pending').count() == 0
                assert '距结构端点 171 根已收盘 K 线（日线）' in confirmed.locator('.ranking-row').first.inner_text()
                assert observation.locator('.point-confirm-tag.pending').count() == 1
                assert '买点结构已失效' in observation.inner_text()
                assert '首次越界 2026-09-15，收盘价 8.80' in observation.inner_text()
                assert '价格恢复不重用该信号' in observation.inner_text()
                assert '买点结构端点' in observation.inner_text()
                assert '可关注买入' not in page.locator('main').inner_text()
                assert page.locator('.ranking-update-time time').inner_text() == '2026-11-09 15:49'
                assert '首次识别及确认时间尚未追溯' in page.locator('.ranking-status').inner_text()
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
                page.screenshot(path=str(out / f'signals-{width}.png'))
                confirmed.get_by_role('button', name='展开更多', exact=False).click()
                assert confirmed.locator('.ranking-row').count() == 12
                page.get_by_role('searchbox', name='搜索榜单证券').fill('观察测试')
                assert confirmed.locator('.ranking-row').count() == 0
                assert observation.locator('.ranking-row').count() == 1
                assert '未匹配' in confirmed.inner_text()
                page.get_by_role('button', name='清空', exact=True).click()
                assert confirmed.locator('.ranking-row').count() == 10

                board = deepcopy(board_fixture())
                board['analysis_version'] = 3
                for item in board['bullish']:
                    item['signal_group'] = 'observation'
                    item['signal_quality']['history_status'] = 'unknown'
                    item['signal_quality']['first_reference_crossed_at'] = None
                page.get_by_role('button', name='刷新榜单', exact=True).click()
                page.get_by_text('当前快照尚未核对端点起的历史收盘越界，暂列观察组，等待后台更新。', exact=True).wait_for()
                assert confirmed.locator('.ranking-row').count() == 0

                board = deepcopy(board_fixture())
                for item in board['bullish']:
                    item['signal_quality'].pop('bars_since_pivot')
                page.get_by_role('button', name='刷新榜单', exact=True).click()
                confirmed.get_by_text('距结构端点的 K 线数尚未计算', exact=True).first.wait_for()
                assert confirmed.locator('.ranking-row').count() == 10

                board = deepcopy(board_fixture())
                board.pop('analysis_version')
                for item in board['bullish']:
                    item.pop('signal_group')
                    item.pop('signal_quality')
                page.get_by_role('button', name='刷新榜单', exact=True).click()
                page.get_by_text('当前为旧版快照，辅助证据尚未计算，暂列观察组，等待后台更新。', exact=True).wait_for()
                assert confirmed.locator('.ranking-row').count() == 0
                assert observation.locator('.ranking-row').count() == 10
                assert '候选不会补入此组' in confirmed.inner_text()
                page.goto(args.base_url + '/?view=market&timeframe=1d', wait_until='domcontentloaded')
                panel = page.locator('.signal-detail')
                panel.get_by_role('heading', name='候选，等待确认', exact=True).wait_for()
                assert panel.get_by_role('heading', name='已确认结构', exact=True).count() == 1
                assert '结构端点 2026-10-19' in panel.inner_text()
                assert '首次识别：未追溯' in panel.locator('.signal-evidence').inner_text()
                assert '首次确认：尚未确认' in panel.locator('.signal-evidence').inner_text()
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
                assert not errors, errors
                print(f'{width}px: ranking/market grouping, truthful dates, Beijing time, pagination, search and legacy snapshots OK', flush=True)
                context.close()
        finally:
            browser.close()


if __name__ == '__main__':
    main()
