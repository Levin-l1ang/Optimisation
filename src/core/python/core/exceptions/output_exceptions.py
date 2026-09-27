from core.exceptions.exceptions import LinTimException


class OutputFileException(LinTimException):
    """
    Exception to throw if an output file cannot by written.
    """

    def __init__(self, file_name: str):
        """
        Exception to throw if an output file cannot by written.

        :param file_name: name of the output file
        :type file_name: str
        """
        super().__init__("Error O1: File {} cannot be written.".format(file_name))
