from datetime import datetime

from common.util.constants import MINUTES_PER_HOUR, SECONDS_PER_MINUTE
from core.exceptions.exceptions import LinTimException


def transform_time_to_minutes(time_str: str) -> int:
    """
    Read the visum formatted time and return the number of minutes
    :param time_str: the visum formatted time string
    :return: the minutes
    """
    return datetime.strptime(time_str, "%H:%M:%S").minute

def transform_time_to_hour(time_str: str) -> int:
    """
    Read the visum formatted time and return the number of minutes
    :param time_str: the visum formatted time string
    :return: the minutes
    """
    return datetime.strptime(time_str, "%H:%M:%S").hour


def convert_time_to_time_units(time: str, time_units_per_minute: int) -> int:
    values = time.split(":")
    hours = int(values[0])
    minutes = int(values[1])
    seconds = int(values[2])
    time_units = (hours * MINUTES_PER_HOUR * SECONDS_PER_MINUTE + minutes * SECONDS_PER_MINUTE + seconds) / \
                 (SECONDS_PER_MINUTE / time_units_per_minute)
    if not time_units.is_integer():
        raise LinTimException(
            f"Try to convert {time} to time units, but it does not fit within the time_units_per_second")
    return time_units