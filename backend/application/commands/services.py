from dataclasses import dataclass

from .login_with_webview_code import LoginWithWebviewCodeCommand
from .record_quote_sample import RecordQuoteSampleCommand


@dataclass(frozen=True)
class CommandServices:
    record_quote_sample: RecordQuoteSampleCommand
    login_with_webview_code: LoginWithWebviewCodeCommand
