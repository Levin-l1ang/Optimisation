from core.exceptions.exceptions import LinTimException


class InputFileException(LinTimException):

    def __init__(self, file_name: str):
        """
        Exception to throw if an input file cannot be found.

        :param file_name: name of the file that could not be found
        :type file_name: str
        """
        super().__init__("Error I1: File {} cannot be found.".format(file_name))


class InputFormatException(LinTimException):

    def __init__(self, file_name: str, columns_given: int, columns_required: int):
        """
        Exception to throw if the input file is not formatted correctly, i.e., if the wrong number of columns is given.

        :param file_name: the read file
        :type file_name: str
        :param columns_given: the number of columns given
        :type columns_given: int
        :param columns_required: the required number of columns
        :type columns_required: int
        """
        super().__init__("Error I2: File {} is not formatted correctly: {} columns given, {} needed."
                         .format(file_name, columns_given, columns_required))


class InputTypeInconsistencyException(LinTimException):

    def __init__(self, file_name: str, column_index: int, line_number: int, expected_type: str, found: str):
        """
        Exception to throw if the input file has a type inconsistent.

        :param file_name: input file name
        :type file_name: str
        :param column_index: column in which exception occurs
        :type column_index: int
        :param line_number: number of line in which the exception occurs
        :type line_number: int
        :param expected_type: expected type
        :type expected_type: str
        :param found: entry of wrong type
        :type found: str
        """
        super().__init__("Error I3: Column {} of file {} should be of type {} but entry in line {} is {}."
                         .format(column_index, file_name, expected_type, line_number, found))


class InputUnsupportedModalityCategoryException(LinTimException):

    def __init__(self, unsupported_category: str):
        """
        Exception to throw if the modality category found is not supported for the called method.

        :param unsupported_category: the unsupported category
        :type unsupported_category: str
        """
        super().__init__(f"Error I5: Modality category {unsupported_category} is not supported for the called method!")
