from enum import Enum


class TariffModelType(Enum):
    """
    Enumeration of all possible tariff types.
    """
    ZONE = "ZONE"
    BEELINE_DISTANCE = "BEELINE_DISTANCE"
    NETWORK_DISTANCE = "NETWORK_DISTANCE"
    FLAT = "FLAT"

class TariffWeightType(Enum):
    """
    Enumeration of all possible weight methods for the objective in tariff optimization.
    """
    OD = "OD"
    UNIT = "UNIT"
    REFERENCE_INVERSE = "REFERENCE_INVERSE"

class TariffObjectiveType(Enum):
    """
    Enumeration of all possible tariff optimization method types.
    """
    SUM_ABSOLUTE_DEVIATION = "SUM_ABSOLUTE_DEVIATION"
    MAX_ABSOLUTE_DEVIATION = "MAX_ABSOLUTE_DEVIATION"

class TariffZoneCountingType(Enum):
    """
    Enumeration of all possible counting modes in the zone tariff model.
    """
    SINGLE = "SINGLE"
    MULTIPLE = "MULTIPLE"

class TariffZoneSymmetryOption(Enum):
    """
    Enumeration of all possible symmetry breaking options in the zone tariff model.
    """
    A = "A"
    B = "B"
    NONE = "NONE"

class TariffRoutingType(Enum):
    """
    Enumeration of all possible routing generation types.
    """
    FASTEST_PATHS = "FASTEST-PATHS"
    READ_ALL = "READ-ALL"
    READ_PARTIAL_FILL = "READ-PARTIAL-FILL"