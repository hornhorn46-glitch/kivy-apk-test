import time
from datetime import datetime


def now_ts():
    return int(time.time())


def clamp(value, low, high):
    return max(low, min(high, value))


def fmt_ms(value):
    return "-" if value is None else f"{int(round(value))} ms"


def session_time(session):
    return datetime.fromtimestamp(session.get("timestamp", now_ts()))
