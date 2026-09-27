from core.exceptions.exceptions import LinTimException


class StatisticKeyNotFoundException(LinTimException):

    def __init__(self, key: str):
        """Exception to throw if a statistic key was not found.

        :param key: the statistic key
        :type key: str
        """
        super().__init__("Error ST2: Statistic parameter {} does not exist.".format(key))


class StatisticTypeMismatchException(LinTimException):

    def __init__(self, key: str, expected_type: str, value: str):
        """Exception to throw if a statistic key should be set with the wrong type.

        :param key: the statistic key
        :type key: str
        :param expected_type: the expected type of the key
        :type expected_type: str
        :param value: given value of the statistic key
        :type value: str
        """
        super().__init__("Error ST1: Statistic key {} should have type {} but has value {}.".format(key, expected_type, value))