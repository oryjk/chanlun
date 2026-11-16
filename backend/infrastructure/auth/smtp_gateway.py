from email.message import EmailMessage
from email.utils import formataddr
import logging
import smtplib
import ssl

from ...domain.app_auth import AuthUnavailable, normalize_email


class SmtpEmailGateway:
    def __init__(
        self,
        host: str,
        port: int,
        username: str,
        password: str,
        sender: str | None = None,
        tls_mode: str = 'tls',
        from_name: str = '缠观',
    ):
        self._host = host.strip()
        self._port = int(port)
        self._username = username.strip()
        self._password = password
        self._sender = (sender or username).strip()
        self._from_name = (from_name or '').strip()
        self._tls_mode = tls_mode.strip().lower() or 'tls'

    def send_verification_code(self, email: str, code: str, expires_minutes: int) -> None:
        recipient = normalize_email(email)
        message = EmailMessage()
        message['From'] = (
            formataddr((self._from_name, self._sender))
            if self._from_name
            else self._sender
        )
        message['To'] = recipient
        message['Subject'] = '【缠观】邮箱注册验证码'
        message.set_content(
            f'您的缠观邮箱注册验证码为：{code}\n'
            f'验证码 {expires_minutes} 分钟内有效，如非本人操作请忽略本邮件。'
        )
        context = ssl.create_default_context()
        try:
            if self._tls_mode in ('starttls', 'start_tls'):
                with smtplib.SMTP(self._host, self._port, timeout=10) as client:
                    client.ehlo()
                    client.starttls(context=context)
                    client.ehlo()
                    if self._username:
                        client.login(self._username, self._password)
                    client.send_message(message)
            elif self._tls_mode == 'none':
                with smtplib.SMTP(self._host, self._port, timeout=10) as client:
                    if self._username:
                        client.login(self._username, self._password)
                    client.send_message(message)
            else:
                with smtplib.SMTP_SSL(self._host, self._port, timeout=10, context=context) as client:
                    if self._username:
                        client.login(self._username, self._password)
                    client.send_message(message)
        except Exception as error:
            logging.getLogger(__name__).warning(
                '验证码邮件发送失败 (%s)',
                type(error).__name__,
            )
            raise AuthUnavailable('验证码邮件发送失败，请稍后重试') from error
