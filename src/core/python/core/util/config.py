import logging

from core.exceptions.config_exceptions import ConfigKeyNotFoundException, ConfigTypeMismatchException
from core.solver.generic_solver_interface import SolverType


class Config:
    """
    Implementation of a config class, handling all the config interaction. Based on the "old" LinTim implementation. Has
    static and non-static methods, where the static methods operate on a "default" config object
    """

    logger_ = logging.getLogger(__name__)

    def __init__(self):
        """
        Initialize an empty config.
        """
        self.data = {}
        self.logged_data = {}

    def getStringValue(self, key: str, modality: str = "") -> str:
        """Get the string value of the given config key.

        :param key: the key to look for in the config
        :type key: str
        :raises ConfigKeyNotFoundException: if the config key was not found in the config.
        :param modality:
        :type modality: str, optional
        :return: the value to the given key
        :rtype: str
        """
        if key == "default_activities_periodic_file" and "use_buffered_activities" in self.data and \
                self.getBooleanValue("use_buffered_activities"):
            key = "default_activity_buffer_file"
        elif key == "default_activities_periodic_unbuffered_file":
            if "use_buffered_activities" in self.data and self.getBooleanValue("use_buffered_activities"):
                key = "default_activity_relax_file"
            else:
                key = "default_activities_periodic_file"
        if modality != "":
            key = f"{modality}.{key}"
        if key not in self.data:
            if "." in key:
                if key.split('.')[1] not in self.logged_data:
                    self.logger_.debug(f"Did not find key {key}, search for {key.split('.')[1]} instead")
                return self.getStringValue(key.split(".")[1])
            else:
                raise ConfigKeyNotFoundException(key)
        else:
            value = self.data[key]
            if key not in self.logged_data or self.logged_data[key] != value:
                self.logger_.debug("Read key {0} with value {1} from config".format(key, value))
                self.logged_data[key] = value
            return value

    def getStringListValue(self, key: str, modality = "") -> list[str]:
        """
        Get a list of stripped strings corresponding to a given config key.
        :param key: The key to look for in the config.
        :return: The list of strings corresponding to the config key.
        """
        if modality != "":
            key = f"{modality}.{key}"
        if key not in self.data:
            if "." in key:
                self.logger_.debug(f"Did not find key {key}, search for {key.split('.')[1]} instead")
                return self.getStringListValue(key.split(".")[1])
            else:
                raise ConfigKeyNotFoundException(key)
        dict_value: str = self.data[key]
        dict_value = dict_value.strip()
        if dict_value[0] == '[' and dict_value[-1] == ']':
            dict_value = dict_value[1:-1]
        else:
            self.logger_.error(f"Key {self.data[key]} invalid for ListValue, should be formatted as [<comma-separated values>]")
        if dict_value.strip() == "":
            values = []
        else:
            values = [value.strip() for value in dict_value.split(',')]
        self.logger_.debug(f'Read key {key} with value {values} from config.')
        return values

    def getBooleanValue(self, key: str, modality: str = ""):
        """
        Get the boolean value of the given config key

        :param key: the key to look for
        :type key: str
        :raises ConfigTypeMismatchException: if the vale of the config key was not convertible to boolean.
        :return: the boolean value to the given key
        :rtype: bool
        """
        value = self.getStringValue(key, modality).lower()
        if value == "true":
            return True
        elif value == "false":
            return False
        else:
            raise ConfigTypeMismatchException(key, "boolean", value)

    def getDoubleValue(self, key: str, modality: str = "") -> float:
        """
        Get the double value of the given config key

        :param key: the key to look for
        :type key: str
        :raises ConfigTypeMismatchException: if the value of the config key was not convertible to float.
        :return: the double value to the given key
        :rtype: float
        """
        value = self.getStringValue(key, modality).lower()
        try:
            return float(value)
        except ValueError:
            raise ConfigTypeMismatchException(key, "double", value)

    def getIntegerValue(self, key: str, modality: str = "") -> int:
        """
        Get the integer value of the given config key

        :param key: the key to look for
        :type key: str
        :raises ConfigTypeMismatchException: if the value of the config key was not convertible to int.
        :return: the integer value to the given key
        :rtype: int
        """
        value = self.getStringValue(key, modality).lower()
        try:
            return int(value)
        except ValueError:
            raise ConfigTypeMismatchException(key, "integer", value)

    def getIntegerListValue(self, key: str, modality: str = "") -> list[int]:
        """
        Get a list of integers corresponding to a given config key.
        :param key: The key to look for in the config.
        :return: The list of integers corresponding to the config key.
        """
        if modality != "":
            key = f"{modality}.{key}"
        if key not in self.data:
            if "." in key:
                self.logger_.debug(f"Did not find key {key}, search for {key.split('.')[1]} instead")
                return self.getIntegerListValue(key.split(".")[1])
            else:
                raise ConfigKeyNotFoundException(key)
        dict_value: str = self.data[key]
        dict_value = dict_value.strip()
        if dict_value[0] == '[' and dict_value[-1] == ']':
            dict_value = dict_value[1:-1]
        else:
            self.logger_.error(f"Value {self.data[key]} invalid for ListValue, should be formatted as [<comma-separated values>]")
        if dict_value.strip() == "":
            values = []
        else:
            try:
                values = [int(value.strip()) for value in dict_value.split(',')]
            except ValueError:
                raise ConfigTypeMismatchException(key, "list[int]", self.data[key])
        self.logger_.debug(f'Read key {key} with value {values} from config.')
        return values

    def getSolverType(self, key: str) -> SolverType:
        """
        Get the solver type value of the given config key. Does not support a modality-prefix argument.

        :param key: the key to look for
        :type key: str
        :raises ConfigTypeMismatchException: if the value of the config key was not convertible to a SolverType.
        :return: the solver type of the given key
        :rtype: SolverTpe
        """
        value = self.getStringValue(key).upper()
        if value == "XPRESS":
            return SolverType.XPRESS
        elif value == "GUROBI":
            return SolverType.GUROBI
        elif value == "CPLEX":
            return SolverType.CPLEX
        elif value == "GLPK":
            return SolverType.GLPK
        elif value == "SCIP":
            return SolverType.SCIP
        elif value == "CBC":
            return SolverType.CBC
        elif value == "HIGHS":
            return SolverType.HIGHS
        elif value == "MOSEK":
            return SolverType.MOSEK
        elif value == "COPT":
            return SolverType.COPT
        else:
            raise ConfigTypeMismatchException(key, "XPRESS/GUROBI/CPLEX/GLPK/SCIP/CBC/HIGHS/MOSEK/COPT", value)

    def getLogLevel(self, key: str):
        """
        Get the log level value of the given config key. Does not support a modality-prefix argument.

        :param key: the key to look for
        :type key: str
        :raises ConfigTypeMismatchException: if the value of the config key was not convertible to a logging level.
        :return: the log level of the given key
        """
        value = self.getStringValue(key).upper()
        if value == "FATAL":
            return logging.CRITICAL
        elif value == "ERROR":
            return logging.ERROR
        elif value == "WARN":
            return logging.WARNING
        elif value == "INFO":
            return logging.INFO
        elif value == "DEBUG":
            return logging.DEBUG
        else:
            raise ConfigTypeMismatchException(key, "FATAL/ERROR/WARN/INFO/DEBUG", value)

    def put(self, key: str, value, modality: str = ""):
        """
        Put the specified data into the config collection. Content with the same key will be overwritten.

        :param key: the key to add
        :type key: str
        :param value: the value to add
        :type value: anything convertible to str
        """
        if modality != "":
            key = f"{modality}.{key}"
        self.data[key] = str(value)

    def __str__(self):
        return '\n'.join([('{0}; {1}'.format(key, value)) for (key, value) in self.data.items()])

    def __eq__(self, other):
        if isinstance(other, self.__class__):
            return self.data == other.data
        return NotImplemented

    def __ne__(self, other):
        if isinstance(other, self.__class__):
            return not self.__eq__(other)
        return NotImplemented

    def __hash__(self):
        return hash(self.data)

    @staticmethod
    def putStatic(key, value, modality: str = ""):
        """
        Put the specified data into the config collection of the default config. Content with the same key will be
        overwritten.

        :param key: the key to add
        :type key: str
        :param value: the value to add
        :type value: anything convertible to str
        """
        default_config.put(key, value, modality)

    @staticmethod
    def getStringValueStatic(key: str, modality: str = "") -> str:
        """
        Get the string value of the given config key from the default config.

        :param key: the key to look for in the config
        :type key: str
        :return: the value to the given key
        :rtype: str
        """
        return default_config.getStringValue(key, modality)

    @staticmethod
    def getDoubleValueStatic(key: str, modality: str = "") -> float:
        """
        Get the double value of the given config key from the default config

        :param key: the key to look for
        :type key: str
        :return: the double value to the given key
        :rtype: float
        """
        return default_config.getDoubleValue(key, modality)

    @staticmethod
    def getIntegerValueStatic(key: str, modality: str = "") -> int:
        """
        Get the integer value of the given config key from the default config.

        :param key: the key to look for
        :type key: str
        :return: the integer value to the given key
        :rtype: int
        """
        return default_config.getIntegerValue(key, modality)

    @staticmethod
    def getBooleanValueStatic(key: str, modality: str = "") -> bool:
        """
        Get the boolean value of the given config key from the default config

        :param key: the key to look for
        :type key: str
        :return: the boolean value to the given key
        :rtype: bool
        """
        return default_config.getBooleanValue(key, modality)

    @staticmethod
    def getSolverTypeStatic(key: str) -> SolverType:
        """
        Get the solver type value of the given config key from the default config. Does not support a modality-prefix argument.

        :param key: the key to look for
        :type key: str
        :return: the solver type of the given key
        :rtype: SolverType
        """
        return default_config.getSolverType(key)

    @staticmethod
    def getLogLevelStatic(key: str):
        """
        Get the log level value of the given config key from the default config. Does not support a modality-prefix argument.

        :param key: the key to look for
        :type key: str
        :return: the log level of the given key
        """
        return default_config.getLogLevel(key)

    @staticmethod
    def getDefaultConfig() -> "Config":
        """Get the default config.

        :return: the default config object.
        :rtype: Config
        """
        return default_config


# The default config object. Will be used by all non-class methods in this file
default_config = Config()
