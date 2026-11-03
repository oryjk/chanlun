from ...domain.services.return_query import sanitize_return_query
from ..ports.readers import MiniProgramLinkReader


class GetMiniProgramLinkQuery:
    def __init__(self, reader: MiniProgramLinkReader):
        self._reader = reader

    def execute(self, return_query: str | None) -> str:
        return self._reader.chanlun_link(sanitize_return_query(return_query))
