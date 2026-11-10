from ..models import BuySellPoint, ChanSignals, LevelSignal, SignalConfluence, TimeFrame

KIND_LABELS = {1: "一", 2: "二", 3: "三"}
SIDE_LABELS = {"buy": "买", "sell": "卖"}


def describe_point(point: BuySellPoint) -> str:
    return f"{KIND_LABELS[point.kind]}类{SIDE_LABELS[point.side]}点"


def evaluate_confluence(secid: str, levels: list[tuple[TimeFrame, ChanSignals]]) -> SignalConfluence:
    """levels 按级别从大到小排列；次级别最近的买卖点与大级别最近买卖点同向且在其之后，视为区间套印证。"""
    results = []
    for position, (timeframe, signals) in enumerate(levels):
        latest = signals.points[-1] if signals.points else None
        confirmation = None
        if latest is not None and position + 1 < len(levels) and levels[position + 1][1].points:
            lower_latest = levels[position + 1][1].points[-1]
            if (latest.confirmed and lower_latest.confirmed
                    and lower_latest.side == latest.side and lower_latest.time >= latest.time):
                confirmation = lower_latest
        results.append(
            LevelSignal(
                timeframe=timeframe,
                timeframe_label=timeframe.label,
                trend_type=signals.trend_type,
                latest_point=latest,
                lower_confirmation=confirmation,
            )
        )
    return SignalConfluence(secid=secid, levels=tuple(results), conclusion=_conclude(results))


def _conclude(levels: list[LevelSignal]) -> str:
    for index, level in enumerate(levels):
        if level.latest_point is None or level.lower_confirmation is None:
            continue
        lower = levels[index + 1]
        return (
            f"{level.timeframe_label}{describe_point(level.latest_point)}"
            f"得到{lower.timeframe_label}{describe_point(level.lower_confirmation)}印证"
        )
    if any(level.latest_point is not None for level in levels):
        return "各级别买卖点暂未形成同向印证，宜等待次级别确认"
    return "各级别均未出现买卖点"
