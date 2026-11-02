from ..models import Candle, MacdPoint


def compute_macd(
    candles: tuple[Candle, ...],
    fast: int = 12,
    slow: int = 26,
    signal: int = 9,
) -> tuple[MacdPoint, ...]:
    if not candles:
        return ()

    fast_alpha = 2 / (fast + 1)
    slow_alpha = 2 / (slow + 1)
    signal_alpha = 2 / (signal + 1)
    fast_ema = slow_ema = candles[0].close
    dea = 0.0
    points = []
    for candle in candles:
        fast_ema += fast_alpha * (candle.close - fast_ema)
        slow_ema += slow_alpha * (candle.close - slow_ema)
        dif = fast_ema - slow_ema
        dea += signal_alpha * (dif - dea)
        # 国内行情软件惯例：MACD 柱 = 2 × (DIF − DEA)
        points.append(MacdPoint(dif=round(dif, 6), dea=round(dea, 6), histogram=round(2 * (dif - dea), 6)))
    return tuple(points)
