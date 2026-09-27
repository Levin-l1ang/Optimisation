from typing import Dict

from core.exceptions.statistic_exceptions import StatisticKeyNotFoundException, StatisticTypeMismatchException



class Statistic:
    """
    Implementation for a statistic class, handling all evaluation parameter
    interactions. Based on the 'old' LinTim implementation.
    """

    def __init__(self):
        self.data = {} # type: Dict[str, str]

    def setValue(self, key: str, value) -> None:
        self.data[key] = str(value)

    def getStringValue(self, key: str) -> str:
        try:
            return self.data[key]
        except KeyError:
            raise StatisticKeyNotFoundException(key)

    def getIntegerValue(self, key: str) -> int:
        value = self.getStringValue(key)
        try:
            return int(self.data[key])
        except ValueError:
            raise StatisticTypeMismatchException(key, "int", value)

    def getDoubleValue(self, key: str) -> float:
        value = self.getStringValue(key)
        try:
            return float(self.data[key])
        except ValueError:
            raise StatisticTypeMismatchException(key, "float", value)

    def getBooleanValue(self, key: str) -> bool:
        value = self.getStringValue(key).lower()
        if value == "true":
            return True
        if value == "false":
            return False
        raise StatisticTypeMismatchException(key, "boolean", value)

    @staticmethod
    def getStringValueStatic(key: str) -> str:
        return default_statistic.getStringValue(key)

    @staticmethod
    def getIntegerValueStatic(key: str) -> int:
        return default_statistic.getIntegerValue(key)

    @staticmethod
    def getDoubleValueStatic(key: str) -> float:
        return default_statistic.getDoubleValue(key)

    @staticmethod
    def getBooleanValueStatic(key: str) -> bool:
        return default_statistic.getBooleanValue(key)

    def getData(self) -> Dict:
        return self.data

    @staticmethod
    def getDefaultStatistic() -> "Statistic":
        return default_statistic


default_statistic=Statistic()