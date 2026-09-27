import logging
from enum import Enum
from operator import itemgetter  # if vehicle tour list should be sorted
from typing import Dict, List, ValuesView, Tuple
from core.model.ptn import Stop, Link
from core.model.graph import Node, Graph
from core.exceptions.depot_exceptions import DepotIdNegativeException,VehicleNumberNegativeException
import numpy as np


class TripType(Enum):
    """
    Enum reprensenting possible trip types.
    """
    TRIP = "\"trip\""
    EMPTY = "\"empty\""


class Trip:
    """
    Class representing a trip in a the vehicle schedule. A trip contains all
    necessary information to reconstrcuct the trip of a line served by a
    vehicle.
    """
    logger = logging.getLogger(__name__)

    def __init__(self, startAperiodicEventId: int, startPeriodicEventId: int,
                 startStopId: int, startTime: int, endAperiodicEventId: int,
                 endPeriodicEventId: int,
                 endStopId: int, endTime: int, lineId: int,
                 tripType: TripType):
        """
        Create a new trip with the given information.
        :param startAperiodicEventId    the id of the aperiodic start event
        :paran startPeriodicEventId     the id of the periodic start event
        :param startStopId  the id of the start stop
        :param startTime    the start time of the trip, in seconds
        :param endAperiodicEventId  the id of the aperiodic end event
        :param endPeriodicEventId  the id of the periodic end event
        :param endStopId    the id of the end stop
        :param endTime  the end time of the trip, in seconds
        :param lineId   the id of the corresponding line id
        :param tripType     the type of the trip
        """

        self.startAperiodicEventId = startAperiodicEventId
        self.startPeriodicEventId = startPeriodicEventId
        self.startStopId = startStopId
        self.startTime = startTime
        self.endAperiodicEventId = endAperiodicEventId
        self.endPeriodicEventId = endPeriodicEventId
        self.endStopId = endStopId
        self.endTime = endTime
        self.lineId = lineId
        self.tripType = tripType

        if((self.lineId == -1 and self.tripType == TripType.TRIP) or
           (self.lineId != -1 and self.tripType == TripType.EMPTY)):
            Trip.logger.warning('Unfitting line id {} and trip type {}'.format(
                                self.lineId, self.tripType.name))

    def getStartAperiodicEventId(self) -> int:
        """
        Get the id of the aperiodic start event.
        :return     the id of the aperiodic start event
        """
        return self.startAperiodicEventId

    def setStartAperiodicEventId(self, startAperiodicEventId: int) -> None:
        """
        Set the id of the aperiodic start event id.
        :param startAperiodicEventId    the new aperiodic start event id.
        """
        self.startAperiodicEventId = startAperiodicEventId

    def getStartPeriodicEventId(self) -> int:
        """
        Get the id of the periodic start event.
        :return     the id of the periodic start event.
        """
        return self.startPeriodicEventId

    def setStartPeriodicEventId(self, startPeriodicEventId) -> None:
        """
        Set the id of the periodic start event id.
        :param startPeriodicEventId     the new periodic start event id.
        """
        self.startPeriodicEventId = startPeriodicEventId

    def getStartStopId(self) -> int:
        """
        Get the id of the start stop.
        :return     the id of the start stop.
        """
        return self.startStopId

    def setStartStopId(self, startStopId) -> None:
        """
        Set the id of the start stop.
        :return     the id of the start stop.
        """
        self.startStopId = startStopId

    def getStartTime(self) -> int:
        """
        Get the start time of the trip.
        :return     the start time.
        """
        return self.startTime

    def setStartTime(self, startTime: int) -> None:
        """
        Set the start time of the trip.
        :param startTime    the new start time
        """
        self.startTime = startTime

    def getEndAperiodicEventId(self) -> int:
        """
        Get the id of the aperiodic end event.
        :return     the id of the aperiodic end event.
        """
        return self.endAperiodicEventId

    def setEndAperiodicEventId(self, endAperiodicEventId: int) -> None:
        """
        Set the id of the aperiodic end event.
        :param endAperiodicEventId  the new aperiodic end event id
        """
        self.endAperiodicEventId = endAperiodicEventId

    def getEndPeriodicEventId(self) -> int:
        """
        Get the id of the periodic end event.
        :return     the id of the periodic end event.
        """
        return self.endPeriodicEventId

    def setEndPeriodicEventId(self, endPeriodicEventId: int) -> None:
        """
        Set the id of the periodic end event.
        :param endPeriodicEventId   the new periodic end event id.
        """
        self.endPeriodicEventId = endPeriodicEventId

    def getEndStopId(self) -> int:
        """
        Get the id of the end stop.
        :return     the id of the end stop.
        """
        return self.endStopId

    def setEndStopId(self, endStopId: int) -> None:
        """
        Set the id of hte end stop.
        :param endStopId    the new id of the end stop.
        """
        self.endStopId = endStopId

    def getEndTime(self) -> int:
        """
        Get the end time of the trip.
        :return     the end time.
        """
        return self.endTime

    def setEndTime(self, endTime: int) -> None:
        """
        Set the end time of the trip.
        :param endTime  the new end time.
        """
        self.endTime = endTime

    def getLineId(self) -> int:
        """
        Get the id of the corresponding line of the trip. -1 is used for empty
        trips.
        :return     the id of the corresponding line of the trip.
        """
        return self.lineId

    def setLineId(self, lineId: int) -> None:
        """
        Set a new line id for this trip.
        :param lineId   the new line id.
        """
        self.lineId = lineId

    def getTripType(self) -> TripType:
        """
        Get the type of the trip, see TripType.
        :return     the trip type.
        """
        return self.tripType

    def setTripType(self, tripType: TripType) -> None:
        """
        Set a new trip type of this trip.
        :param tripType     the new type of the trip.
        """
        self.tripType = tripType

    def toCsvStrings(self) -> List[str]:
        """
        Get a csv representation of this trip.
        :return the representation of this trip.
        """
        return [str(self.getStartAperiodicEventId()),
                str(self.getStartPeriodicEventId()),
                str(self.getStartStopId()),
                str(self.getStartTime()),
                str(self.getEndAperiodicEventId()),
                str(self.getEndPeriodicEventId()),
                str(self.getEndStopId()),
                str(self.getEndTime()),
                str(self.getLineId())]

    def __str__(self) -> str:
        return "Trip (" + ", ".join(self.toCsvStrings()) + ")"

    def __eq__(self, other) -> bool:
        if not isinstance(other, Trip):
            return False
        return (other.getStartAperiodicEventId() !=
                self.getStartAperiodicEventId() and
                other.getStartPeriodicEventId() !=
                self.getStartPeriodicEventId() and
                other.getStartStopId() != self.getStartStopId() and
                other.getStartTime() != self.getStartTime() and
                other.getEndAperiodicEventId() !=
                self.getEndAperiodicEventId() and
                other.getEndPeriodicEventId() !=
                self.getEndPeriodicEventId() and
                other.getEndStopId() != self.getEndStopId() and
                other.getEndTime() != self.getEndTime() and
                other.getLineId() != self.getLineId() and
                other.getTripType() != self.getTripType())

    def __ne__(self, other) -> bool:
        return not self == other

    def __hash__(self) -> int:
        result = self.startAperiodicEventId
        result = 31 * result + self.startPeriodicEventId
        result = 31 * result + self.startStopId
        result = 31 * result + self.startTime
        result = 31 * result + self.endAperiodicEventId
        result = 31 * result + self.endPeriodicEventId
        result = 31 * result + self.endStopId
        result = 31 * result + self.endTime
        result = 31 * result + self.lineId
        result = 31 * result + hash(self.tripType)
        return result


class VehicleTour:
    """
    Class representing a vehicle tour, i.e., a sequence of trips served by one
    vehicle.
    """
    logger = logging.getLogger(__name__)

    def __init__(self, vehicleId: int):
        """
        Create a new empty vehicle tour for a vehicle with the given id.
        :param vehicleId    the id of the vehicle to serve the trip. There may
        not be multiple vehicle tours served by the same vehicle in one
        ciculation.
        """
        self.vehicleId = vehicleId
        self.trips = {}  # type: Dict[int, Trip]

    def getVehicleId(self) -> int:
        """
        Get the vehicle id of this tour.
        :return     the vehicle tour id.
        """
        return self.vehicleId

    def getTripList(self) -> List[Trip]:
        """
        Get a list of the trips served in this vehicle tour. This will return a
        copy of the list, i.e., changes of the list will not be represented in
        the vehicle tour.
        :return     the trips served in this vehicle tour.
        """
        return [trip for _, trip in sorted(self.trips.items(), key=itemgetter(0))]

    def getTrip(self, tripNumber: int) -> Trip:
        """
        Get the trip with the given trip number.
        :param tripNumber   the number to look for
        :return     the trip for the given number.
        """
        return self.trips.get(tripNumber)

    def getTripsWithIds(self) -> List[Tuple[int, Trip]]:
        """
        Get the trips, associated with their respective trip number.
        :return     a collection of trips and their trip numbers
        """
        return list(self.trips.items())

    def addTrip(self, tripId: int, trip: Trip):
        """
        Add a trip with the given trip number. Note that a vehicle tour connot
        contacontain multiple trips with the same trip number. Therefore when
        adding a second trip with the same number, the old one will be
        replaced.
        :param tripId   the trip id
        :param trip     the trip to add
        """
        Trip.trip = trip

        tripBefore = self.trips.get(tripId - 1)
        tripAfter = self.trips.get(tripId + 1)

        if tripBefore is not None:
           if(tripBefore.getEndAperiodicEventId() !=
              Trip.trip.getStartAperiodicEventId()):
               VehicleTour.logger.warning("Fitting a nonmatiching trip into a\
                                   vehicle tour")
        if tripAfter is not None:
           if(tripAfter.getStartAperiodicEventId() !=
              Trip.trip.getEndAperiodicEventId()):
               VehicleTour.logger.warning("Fitting a nonmatuching trip into a\
                                   vehicle tour")
        if tripId in self.trips:
           VehicleTour.logger.warning("Replacing ecisting trip in vehicle tour!")
        self.trips[tripId] = Trip.trip

    def __str__(self):
        return "Vehicle Tour:\n " + "\n".join(['{}:{}'.format(key, val)
                for key, val in sorted(self.trips.items())])


class Circulation:
    """
    Class representing a circulation in a LinTim-vehicle schedule.
    For more information on a circulation, see the LinTim documentation and the
    documentation of the canal vs model
    """

    logger = logging.getLogger(__name__)

    def __init__(self, circulationId: int):
        """
        Create a new circulation with the given id. The id should be unique in
        a vehicle shedule.
        :param circulationId the id of the new circulation
        """
        self.circulationId = circulationId
        self.vehicleTours = {}  # type: Dict[int, VehicleTour]

    def getCirculationId(self) -> int:
        """
        Get the id of the circulation
        :return the circulation id
        """
        return self.circulationId

    def getVehicleTourList(self) -> List[VehicleTour]:
        """
        Get a list of the tours contained in the circulation.
        :return the vehicle tour list
        """
        return [tour for _, tour in sorted(self.vehicleTours.items(), key=itemgetter(0))]

    def addVehicle(self, vehicleTour: VehicleTour) -> None:
        """
        Add a vehicle tour to the circulation. A circulation cannot contain
        multiple tours having the same vehicle id.
        :param vehicleTour  the tour to add
        """
        if vehicleTour in self.vehicleTours.values():
            Circulation.logger.warning("Replacing existing vehicle tour in circulation!")
        self.vehicleTours[vehicleTour.getVehicleId()] = vehicleTour

    def getVehicleTour(self, vehicleId: int) -> VehicleTour:
        """
        Get the vehicle tour with the given vehicle id.
        :param vehicleId    the id to search for.
        :return     the tour with the given id in the circulation, or null if
        there is none.
        """
        return self.vehicleTours.get(vehicleId)

    def __str__(self):
        return "Circulation {}:\n".format(self.getCirculationId()) + \
               "\n".join('{}:{}'.format(key, val) for key, val in sorted(self.vehicleTours.items()))



class VehicleSchedule:
    """
    Class representing avehicle schedule
    """
    logger = logging.getLogger(__name__)

    def __init__(self):
        """
        Gernerate a new, empty vehicle schedule
        """
        self.circulations = {}  # type: Dict[int, Circulation]

    def getCirculation(self, circulationId: int) -> Circulation:
        """
        Get the circulation with the given id from the schedule.
        :param circulationId    the id to search for
        :return the collectionwith the given id
        """
        return self.circulations.get(circulationId)

    def getCirculations(self) -> ValuesView[Circulation]:
        """
        Get a collection of the circulations in this vehicle schedule. Note
        that this will return not a copy of the circulationsbut a view of the
        underlying collection, i.e., changes to the VehicleSchedule will be
        reflected in the returned view.
        :return     a view of the circulations of this schedule.
        """
        return self.circulations.values()

    def addCirculation(self, circulation: Circulation) -> None:
        """
        Add the given circulation to the vehicle schedule. The schedule may not
        contain multiple circulations with the same id, i.e., when inserting a
        second circualtion with the same id, the odl one will be replaced and a
        warning will be logged.
        :param circulation  the circulation to add
        """
        if circulation in self.circulations:
            VehicleSchedule.logger.warning('Resetting circulation with id %d',
                                circulation.getCirculationId())
        self.circulations[circulation.getCirculationId()] = circulation

    def getCirculationMap(self) -> Dict[int, Circulation]:
        """
        Get the mapping of the circulations, keyed by their id. Note that this
        will not return a copy of the circulations, i.e., changes to the
        collection will change the vehicle schedule.
        :return     the circulation map
        """
        return self.circulations

    def toCsvStrings(self, circulationId: int, vehicleId: int, tripNumber:
                     int) -> List[str]:
        """
        Return a LinTim compatible scv representation of the trip with the
        given ids for the vehicle scheduling file.
        :param circulationId    the circulation id
        :param vehicleId    the vehicle id
        :param tripNumber   the trip number
        :return     a string representation of the correspnding trip
        """
        trip = self.circulations.get(circulationId).getVehicleTour(vehicleId).getTrip(tripNumber)
        tripOutput = trip.toCsvStrings()
        return [str(circulationId), str(vehicleId), str(tripNumber), str(trip.getTripType().name)] + tripOutput

    def __str__(self) -> str:
        return ("Vehicle Schedule:\n " + "\n".join('{}:{}'.format(key, val)
                for key, val in sorted(self.circulations.items())))

class Depot(Node):
    """
    Class representing a depot from which vehicles start and at which they terminate.
    Class must contain coordinates of the depot and the amount of vehicles stored in the depot.
    """
    logger = logging.getLogger(__name__)

    def __init__(self, depotId: int, x_coordinate: float, y_coordinate: float, number_of_vehicles: int):
        """
        Create a new depot with the given information.
        :param depotId: the id of the depot
        :param x_coordinate: x_coordinate of the depot
        :param y_coordinate: y_coordinate of the depot
        :param number_of_vehicles: number of vehicles stored in the depot which can be scheduled
        """
        self.depotId = depotId
        self.x_coordinate = x_coordinate
        self.y_coordinate = y_coordinate
        self.number_of_vehicles = number_of_vehicles

        if self.depotId < 0:
           raise(DepotIdNegativeException(self.depotId))
        if self.number_of_vehicles < 0:
           raise(VehicleNumberNegativeException(self.number_of_vehicles,self.depotId))

    def getId(self) -> int:
        """
        Get the id of the Depot.
        :return     the id of the Depot
        """
        return self.depotId

    def getXCoordinate(self) -> float:
        """
        Get the x coordinate of the Depot.
        :return     the x coordinate of the Depot
        """
        return self.x_coordinate

    def getYCoordinate(self) -> float:
        """
        Get the y coordinate of the Depot.
        :return:    the y coordinate of the Depot
        """
        return self.y_coordinate

    def getNearestStop(self, ptn: Graph[Stop, Link]):
        """
        Get the nearest stop to the depot from the given ptn.
        :param ptn: the ptn we locate our depot in
        :return: the nearest stop to the depot
        """
        distance_list = []
        for stop in ptn.getNodes():
            depot_distance = (stop.getXCoordinate() - self.x_coordinate)**2 + (stop.getYCoordinate() - self.y_coordinate)**2
            distance_list.append((depot_distance, stop))
        distance_list.sort(key=lambda tup: tup[0])
        return distance_list[0][1]



    def getNumberOfVehicles(self) -> int:
        """
        Get the number of vehicles stored in the Depot.
        :return:    the number of vehicles stored in the Depot
        """
        return self.number_of_vehicles

    def setId(self, new_id: int) -> None:
        """
        Set a new id of the Depot.
        :param depotId: the new DepotId
        :return: None
        """
        self.depotId = new_id

    def setXCoordinate(self, x_coordinate: float) -> None:
        """
        Set a new x coordinate of the Depot.
        :param x_coordinate: the new x coordinate of the Depot
        :return: None
        """
        self.x_coordinate = x_coordinate

    def setYCoordinate(self, y_coordinate: float) -> None:
        """
        Set a new y coordinate of the Depot.
        :param y_coordinate: the new y coordinate of the Depot
        :return: None
        """
        self.y_coordinate = y_coordinate

    def setNumberOfVehicles(self, numberOfVehicles) -> None:
        """
        Set a new number of vehicles stored in the Depot.
        :param numberOfVehciles: the new number of vehicles stored in the Depot
        :return: None
        """
        self.number_of_vehicles = numberOfVehicles

    def __eq__(self, other) -> bool:
        return self.depotId == other.depotId and self.x_coordinate == other.x_coordinate and self.y_coordinate == other.y_coordinate and self.number_of_vehicles == other.number_of_vehicles

    def __hash__(self) -> int:
        return hash((self.depotId, self.x_coordinate, self.y_coordinate))

    def getDistance(self, stop: Stop) -> float:
        return np.sqrt((self.x_coordinate - stop.getXCoordinate())**2 + (self.y_coordinate - stop.getYCoordinate())**2)

    def toCsvStrings(self) -> List[str]:
        return [str(self.depotId), str(self.x_coordinate), str(self.y_coordinate), str(self.number_of_vehicles)]