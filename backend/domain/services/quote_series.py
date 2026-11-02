from datetime import timezone

from ..models import QuoteSeries


def merge_quote_series(*series_list: QuoteSeries) -> QuoteSeries:
    points = {}
    for series in series_list:
        for point in series.points:
            timestamp = point.time.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
            points[timestamp] = point
    return QuoteSeries(
        secid=series_list[-1].secid,
        points=tuple(sorted(points.values(), key=lambda point: point.time)),
    )
