from typing import Protocol, runtime_checkable

from ...domain.tracking import TrackedAssetRecord


@runtime_checkable
class AppTrackingReader(Protocol):
    def list_by_user(self, user_id: str) -> tuple[TrackedAssetRecord, ...]: ...


@runtime_checkable
class AppTrackingWriter(Protocol):
    def upsert_tracking(self, record: TrackedAssetRecord) -> TrackedAssetRecord: ...
    def remove_trackings(self, user_id: str, secids: tuple[str, ...]) -> int: ...
    def clear_user_trackings(self, user_id: str) -> None: ...
