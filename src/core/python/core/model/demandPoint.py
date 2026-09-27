import math


class DemandPoint:
    """
    A class to represen demand point, i.e., the location and the number of
    people in a city that serves as demand.
    """

    def __init__(self, demandPointId: int, shortName: str, longName: str,
                 xCoordinate: float, yCoordinate: float, demand: int):
        """
        Constructur of a demand point.

        :param demandPointId    id of the demand point
        :param shortName        short name of the demand point
        :param longName         long name of the demand point
        :param xCoordinate      x coordniate of the demand point
        :param yCoordinate      y coordinate of the demand point
        :param demand           demand at the demand point
        """

        self.demandPointId = demandPointId
        self.shortName = shortName
        self.longName = longName
        self.xCoordinate = xCoordinate
        self.yCoordinate = yCoordinate
        self.demand = demand


    def getId(self) -> int:
        """
        Get the id of the demand point.
        :return     id of the demand point.
        """
        return self.demandPointId


    def getShortName(self) -> str:
        """
        Get the short name of the demand point
        :return  the short name of the demand point
        """
        return self.shortName


    def getLongName(self) -> str:
        """
        Get the long name of the demand point
        :return  the long name of the demand point
        """
        return self.longName


    def getxCoordinate(self) -> float:
        """
        Get the x coordinate of the demand point.
        :return  the x coordinate of the demand point
        """
        return self.xCoordinate


    def getyCoordinate(self) -> float:
        """
        Get the y coordinate of the demand point.
        :return  the y coordinate of the demand point
        """
        return self.yCoordinate


    def getDemand(self) -> int:
        """
        Get the demand at the demand point.
        :return     the demand
        """
        return self.demand

    def __str__(self) -> str:
        return "Demand (" + ", ".join(self.toCsvStrings()) + ")"

    def __eq__(self, other) -> bool:
        if not isinstance(other, DemandPoint):
            return False
        return (other.getId() == self.getId()
                and math.isclose(other.getxCoordinate(), self.getxCoordinate())
                and math.isclose(other.getyCoordinate(), self.getyCoordinate())
                and other.getDemand() == self.getDemand()
                and other.getShortName() == self.getShortName()
                and other.getLongName() == self.getLongName())

    def __ne__(self, other) -> bool:
        return not self == other

    def __hash__(self) -> int:
        return hash((self.demandPointId, self.shortName, self.longName, self.xCoordinate, self.yCoordinate, self.demand))

    def toCsvStrings(self) -> [str]:
        """
        Create a csv array containing the values needed for a demnad file in LinTim
        format.
        :return     a representation of this demandin LinTim format.
        """
        return [str(self.getId()),
                self.shortName,
                self.longName,
                str(self.getxCoordinate()),
                str(self.getyCoordinate()),
                str(self.getDemand())]
