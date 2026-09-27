import logging


class LinTimException(Exception):
    """Base class for exceptions in LinTim"""
    
    def __init__(self, message: str):
        """
        Initialise a new exception

        :param message: the message to raise
        :type message: str
        """
        super().__init__(message)
        logging.getLogger(__name__).error(message)
