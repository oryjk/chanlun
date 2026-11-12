import json
from datetime import date, datetime, timezone
from pathlib import Path
from threading import Lock

from ...application.security_ids import legacy_security_id
from ...domain.errors import InvalidSecurityId
from ...domain.models import QuotePoint, QuoteSeries


class JsonQuoteStore:
    def __init__(self, root: str | Path):
        self._root = Path(root)
        self._lock = Lock()

    def read_series(self, secid: str) -> QuoteSeries:
        current_date = datetime.now().astimezone().date()
        with self._lock:
            path = self._path(secid, current_date)
            if not path.exists():
                legacy_path = self._legacy_path(secid, current_date)
                if legacy_path is not None and legacy_path.exists():
                    path = legacy_path
            return self._read_path(secid, path)

    def append(self, secid: str, time: datetime, price: float) -> QuoteSeries:
        current_date = datetime.now().astimezone().date()
        with self._lock:
            self._root.mkdir(parents=True, exist_ok=True)
            self._cleanup_expired(current_date)
            path = self._path(secid, current_date)
            legacy_path = self._legacy_path(secid, current_date)
            read_target = path if path.exists() or legacy_path is None else legacy_path
            current = self._read_path(secid, read_target)
            if time.tzinfo is None:
                time = time.replace(tzinfo=timezone.utc)
            timestamp = time.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
            prices = {
                point.time.astimezone(timezone.utc).isoformat().replace("+00:00", "Z"): point.price
                for point in current.points
            }
            prices[timestamp] = price
            points = sorted(
                (QuotePoint(time=datetime.fromisoformat(timestamp), price=price) for timestamp, price in prices.items()),
                key=lambda point: point.time,
            )
            series = QuoteSeries(secid=secid, points=tuple(points))
            self._write_path(path, series, current_date)
            if legacy_path is not None and legacy_path != path and legacy_path.exists():
                legacy_path.unlink()
            return series

    def _path(self, secid: str, current_date: date) -> Path:
        return self._root / f"{secid}-{current_date:%Y%m%d}.json"

    def _legacy_path(self, secid: str, current_date: date) -> Path | None:
        try:
            legacy_secid = legacy_security_id(secid)
        except InvalidSecurityId:
            return None
        return self._path(legacy_secid, current_date)

    def _read_path(self, secid: str, path: Path) -> QuoteSeries:
        if not path.exists():
            return QuoteSeries(secid=secid, points=())
        payload = json.loads(path.read_text(encoding="utf-8"))
        return QuoteSeries(
            secid=secid,
            points=tuple(
                QuotePoint(
                    time=datetime.fromisoformat(item["time"].replace("Z", "+00:00")),
                    price=float(item["price"]),
                )
                for item in payload.get("points", [])
            ),
        )

    def _write_path(self, path: Path, series: QuoteSeries, current_date: date) -> None:
        payload = {
            "secid": series.secid,
            "date": f"{current_date:%Y-%m-%d}",
            "points": [
                {
                    "time": point.time.astimezone(timezone.utc).isoformat().replace("+00:00", "Z"),
                    "price": point.price,
                }
                for point in series.points
            ],
        }
        temporary = path.with_suffix(".tmp")
        temporary.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        temporary.replace(path)

    def _cleanup_expired(self, current_date: date) -> None:
        current = f"{current_date:%Y%m%d}"
        for path in self._root.glob("*.json"):
            file_date = path.stem.rsplit("-", 1)[-1]
            if len(file_date) == 8 and file_date.isdigit() and file_date < current:
                path.unlink()
