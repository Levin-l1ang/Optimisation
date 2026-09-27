from core.exceptions.exceptions import LinTimException


class RoutingNoPathAvailableException(LinTimException):
    """
    Exception to throw if there is no path available from origin to destination.
    """

    def __init__(self, origin_id: int, destination_id: int):
        """
        Exception to throw if there is no path available from origin to destination.

        :param origin_id: Id of origin node
        :type origin_id: int
        :param destination_id: Id of destination node
        :type destination_id: int
        """
        super().__init__("Error R1: No path available fom {} to {}!".format(origin_id, destination_id))


class RoutingPathInconsistencyException(LinTimException):
    """
    Exception to throw when there is some inconsistency with a given path.
    """

    def __init__(self, origin_id: int, destination_id: int):
        """
        Exception to throw when there is some inconsistency with a given path.

        :param origin_id: id of the first node
        :type origin_id: int
        :param destination_id: id of the second node
        :type destination_id: int
        """
        super().__init__("Error R2: The given path from {} to {} is not consistent!".format(origin_id, destination_id))