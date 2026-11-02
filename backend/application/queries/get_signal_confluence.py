from concurrent.futures import ThreadPoolExecutor

from ...domain.errors import ChanlunError
from ...domain.models import ChanSignals, SignalConfluence, TimeFrame, validate_security_id
from ...domain.services.signal_confluence import evaluate_confluence
from .get_market_snapshot import GetMarketSnapshotQuery

LEVELS = (TimeFrame.ONE_DAY, TimeFrame.ONE_HOUR, TimeFrame.THIRTY_MINUTES, TimeFrame.FIVE_MINUTES)


class GetSignalConfluenceQuery:
    def __init__(self, snapshot_query: GetMarketSnapshotQuery):
        self._snapshot_query = snapshot_query

    def execute(self, secid: str) -> SignalConfluence:
        normalized_secid = validate_security_id(secid)
        with ThreadPoolExecutor(max_workers=len(LEVELS)) as executor:
            results = list(executor.map(lambda timeframe: self._read(normalized_secid, timeframe), LEVELS))
        levels = [(timeframe, item) for timeframe, item in zip(LEVELS, results) if isinstance(item, ChanSignals)]
        if not levels:
            raise results[0]
        return evaluate_confluence(normalized_secid, levels)

    def _read(self, secid: str, timeframe: TimeFrame) -> ChanSignals | ChanlunError:
        # 单个级别失败不影响其他级别；全部失败时抛出首个错误
        try:
            return self._snapshot_query.execute(secid, timeframe.value).signals
        except ChanlunError as error:
            return error
