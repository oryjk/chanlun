from typing import Protocol

from ...domain.models import UserIdentity


class IdentityGateway(Protocol):
    """用报名系统签发的一次性 web-view code 换取用户身份；code 兑换后即失效。"""

    def exchange_webview_code(self, code: str) -> UserIdentity: ...


class SessionCodec(Protocol):
    max_age_seconds: int

    def issue(self, identity: UserIdentity) -> str: ...

    def parse(self, token: str) -> UserIdentity | None: ...
