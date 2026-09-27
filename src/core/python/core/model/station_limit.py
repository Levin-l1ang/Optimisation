class StationLimit:
    """
    Class representing a station limit, i.e., limits on change and wait time for one specific station. For details on the
    different parameters, see the LinTim-documentation.
    """
    def __init__(self, stop_id: int, min_wait_time: int, max_wait_time: int, min_change_time: int, max_change_time: int):
        """
        Create a new station limit.
        :param stop_id: the id of the stop for which the limits should hold
        :param min_wait_time: the minimal wait time
        :param max_wait_time: the maximal wait time
        :param min_change_time: the minimal change time
        :param max_change_time: the maximal change time
        """
        self._stop_id = stop_id
        self._min_wait_time = min_wait_time
        self._max_wait_time = max_wait_time
        self._min_change_time = min_change_time
        self._max_change_time = max_change_time

    def getStopId(self) -> int:
        return self._stop_id

    def getMinChangeTime(self) -> int:
        return self._min_change_time

    def getMaxChangeTime(self) -> int:
        return self._max_change_time

    def getMinWaitTime(self) -> int:
        return self._min_wait_time

    def getMaxWaitTime(self) -> int:
        return self._max_wait_time

    def __eq__(self, o: object) -> bool:
        if not isinstance(o, StationLimit):
            return False
        return (o.getStopId() == self._stop_id and
                o.getMinChangeTime() == self._min_change_time and
                o.getMaxChangeTime() == self._max_change_time and
                o.getMinWaitTime() == self._min_wait_time and
                o.getMaxWaitTime() == self._max_wait_time)

    def __ne__(self, o: object) -> bool:
        return not self.__eq__(o)

    def __hash__(self) -> int:
        result = self._stop_id
        result = 31 * result + self._min_change_time
        result = 31 * result + self._max_change_time
        result = 31 * result + self._min_wait_time
        result = 31 * result + self._max_wait_time
        return result



