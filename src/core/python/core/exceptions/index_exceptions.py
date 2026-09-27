from core.exceptions.exceptions import LinTimException


class IndexOutOfBoundsException(LinTimException):
    """ Exception to throw if an index is out of bounds."""

    def __init__(self, exceptionKey: str, origin: int, matrixLength: int):
        super().__init__("Error: {} index {} is not in [1, {}] when\
                         accessing od matrix.".format(exceptionKey, origin,
                                                      matrixLength))
