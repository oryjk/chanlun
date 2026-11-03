class ChanlunError(Exception):
    pass


class InvalidSecurityId(ChanlunError):
    pass


class InvalidTimeframe(ChanlunError):
    pass


class EmptySearchQuery(ChanlunError):
    pass


class MarketDataUnavailable(ChanlunError):
    pass


class MarketDataNotFound(ChanlunError):
    pass


class AuthenticationFailed(ChanlunError):
    pass


class IdentityServiceUnavailable(ChanlunError):
    pass
