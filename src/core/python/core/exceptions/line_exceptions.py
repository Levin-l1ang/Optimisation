from core.exceptions.exceptions import LinTimException


class LineLinkNotAddableException(LinTimException):
    """
    Exception to throw if a link cannot be added to a line.
    """

    def __init__(self, link_id: int, line_id: int):
        """
        Exception to throw if a link cannot be added to a line.

        :param link_id: link id
        :type link_id: int
        :param line_id: line id
        :type line_id: int
        """
        super().__init__("Error L1: Link {} cannot be added to line {}.".format(link_id, line_id))


class LineNoPathException(LinTimException):
    """
    Exception to throw if a line is no path.
    """

    def __init__(self, line_id: int):
        """
        Exception to throw if a line is no path.

        :param line_id: line id
        :type line_id: int
        """
        super().__init__("Error L3: Line {} is no path.".format(line_id))
