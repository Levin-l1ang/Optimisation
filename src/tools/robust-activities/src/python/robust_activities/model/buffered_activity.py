from core.model.periodic_ean import PeriodicActivity


class BufferedPeriodicActivity(PeriodicActivity):

    def __init__(self, activity: PeriodicActivity):
        """
        Create a new buffered activity from the given activity
        :param activity: the base activity
        """
        super().__init__(activity.getId(), activity.getType(), activity.getLeftNode(), activity.getRightNode(),
                         activity.getLowerBound(), activity.getUpperBound(), activity.getNumberOfPassengers())
        self.buffer: int = 0
        self.buffer_weight: float = 0

    def set_buffer(self, buffer: int) -> None:
        """
        Set the buffer
        :param buffer: the new buffer
        """
        if self.getLowerBound() + buffer > self.getUpperBound():
            raise ValueError("Cannot buffer activity {} with {} (l:{},u:{})"
                             .format(self.getId(), buffer, self.getLowerBound(), self.getUpperBound()))
        self.buffer = buffer

    def get_buffer(self) -> int:
        """
        Get the buffer from this activity
        :return: the buffer
        """
        return self.buffer

    def set_buffer_weight(self, buffer_weight: float) -> None:
        """
        Set the buffer weight
        :param buffer_weight: the new buffer weight
        """
        self.buffer_weight = buffer_weight

    def get_buffer_weight(self) -> float:
        """
        Get the buffer weight for this activity
        :return: the buffer weight
        """
        return self.buffer_weight

    def getLowerBound(self) -> int:
        return self.lowerBound + self.buffer

    def getUpperBound(self):
        return self.upperBound

    def to_buffered_weights_csv(self) -> [str]:
        """
        Transform the activity to the buffer weight csv string
        :return: buffer weight csv representation of this activity
        """
        return [str(self.getId()), str(self.get_buffer_weight())]
