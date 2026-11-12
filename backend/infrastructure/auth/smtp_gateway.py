from email.message import EmailMessage
import smtplib
import ssl

from ...domain.app_auth import AuthUnavailable, normalize_email


class SmtpEmailGateway:
    def __init__(self, host: str, port: int, username: str, password: str,
                 sender: str | None = None, tls_mode: str = 'tls'):
        self._host = host.strip()
        self._port = int(port)
        self._username = username.strip()
        self._password = password
        self._sender = (sender or username).strip()
        self._tls_mode = tls_mode.strip().lower() or 'tls'

    def send_verification_code(self, email: str, code: str, expires_minutes: int) -> None:
        recipient = normalize_email(email)
        message = EmailMessage()
        message['From'] = self._sender
        message['To'] = recipient
        message['Subject'] = '【缠观】注册验证码'
        message.set_content(
            f'您的缠观注册验证码为：{code}，{expires_minutes} 分钟内有效。\n'
            '如非本人操作，请忽略本邮件。'
        )
        context = ssl.create_default_context()
        try:
            if self._tls_mode == 'starttls':
                with smtplib.SMTP(self._host, self._port, timeout=10) as client:
                    client.ehlo()
                    client.starttls(context=context)
                    client.ehlo()
                    client.login(self._username, self._password)
                    client.send_message(message)
            else:
                with smtplib.SMTP_SSL(self._host, self._port, timeout=10, context=context) as client:
                    client.login(self._username, self._password)
                    client.send_message(message)
        except Exception as error:
            raise AuthUnavailable('验证码邮件发送失败，请稍后重试') from error
