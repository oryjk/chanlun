import re

# 与报名系统小程序及 /mini-links/chanlun 的回跳参数规则保持一致
RETURN_QUERY_PATTERN = re.compile(r"^[A-Za-z0-9._=&-]*$")
MAX_RETURN_QUERY_LENGTH = 512


def sanitize_return_query(value: str | None) -> str:
    """登录后回到缠观时附带的查询串；不合法时回到首页而不是报错。"""
    query = (value or "").strip().lstrip("?")
    if len(query) > MAX_RETURN_QUERY_LENGTH or not RETURN_QUERY_PATTERN.fullmatch(query):
        return ""
    return query
