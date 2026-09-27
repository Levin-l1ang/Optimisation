from core.exceptions.exceptions import LinTimException
from typing import List, Union

class ConfigNoFileNameGivenException(LinTimException):

    def __init__(self):
        """
        Exception to throw if no config file name to read is given to a program but one is needed
        """
        super().__init__("Error C4: No config file name given.")


class ConfigKeyNotFoundException(LinTimException):

    def __init__(self, config_key: str):
        """
        Exception to throw if a config key cannot be found

        :param config_key: the key that could not be found
        :type config_key: str
        """
        super().__init__("Error C2: Config parameter {} does not exists".format(config_key))


class ConfigTypeMismatchException(LinTimException):

    def __init__(self, config_key: str, expected_type: str, config_parameter: str):
        """
        Exception to throw if the type of the config parameter does not match

        :param config_key: the key with the false type
        :type config_key: str
        :param expected_type: name of the expected type
        :type expected_type: str
        :param config_parameter: the actual parameter found
        :type config_parameter: str
        """
        super().__init__(
            "Error C3: Config parameter {} should be of type {} but is {}.".format(config_key, expected_type,
                                                                                   config_parameter))

class ConfigInvalidValueException(LinTimException):

    def __init__(self, parameter_names: Union[str, List[str]]):
        """
        Exception to throw if a given config value is not valid.

        :param parameter_names: List of the parameter names for which invalid or incompatible values were given in the config file
        :type parameter_names: Union[str, List[str]]
        """
        super().__init__(
            f"Error C5: Value(s) of config parameter(s) {parameter_names} are invalid or incompatible in this context.")