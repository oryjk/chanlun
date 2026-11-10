class GetAppSession:
    def __init__(self, reader, now):
        self._reader, self._now = reader, now

    def execute(self, token):
        return self._reader.session(token, self._now()) if token else None
