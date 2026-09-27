from core.exceptions.exceptions import LinTimException

class DepotIdNegativeException(LinTimException):
    """
    Exception to throw if somebody tries to create a depot with negative index.
    """
    def __init__(self, depot_id: int):
        """
        Exception to throw if somebody tries to create a depot with negative index.
        :param depot_id: the depot id
        """
        super().__init__("Error DE1: Depot with id {} must have an positive ID.".format(depot_id))

class VehicleNumberNegativeException(LinTimException):
    """
    Exception to throw if somebody tries to create a depot containing negative amount of vehicles.
    """
    def __init__(self, vehicleNumber: int, depotId: int):
        """
        Exception to throw if somebody tries to create a depot containing negative amount of vehicles.
        :param vehicleNumber: the amount of vehicles
        :param depotId: the depot Id
        """
        super().__init__("Error DE2: Depot with id {} can not contain {} vehicles. The amount of vehicles must be positive".format(depotId,vehicleNumber))
        
class NoDepotExceptionInDepotFile(LinTimException):
    """
    Exception to throw if we read the depot file but it is empty.
    """
    def __init__(self, depot_file_name: str):
        """
        Exception to throw if we read the depot file but it is empty.
        :param depot_file_name: the name of the depot file
        """
        super().__init__("Error DE3: Depot File {} should contain depots but is empty".format(depot_file_name))
        
class OnlyOneDepotException(LinTimException):
    """
    Exception to throw if we use a model which only uses one depot but the depot file contains severally.
    """
    def __init__(self, depot_file_name: str):
        """
        Exception to throw if we use a model which only uses one depot but the depot file contains severally.
        :param depot_file_name: the name of the depot file
        """
        super().__init__("Error DE4: Depot File {} should contain only one depot but contains severally".format(depot_file_name))
        
class TooFewVehiclesException(LinTimException):
    """
    Exception to throw if not all trips can be served due to too few vehicles in our depots.
    """
    def __init__(self,depot_file_name: str, missed_out_trips_number: int):
        """
        Exception to throw if not all trips can be served due to too few vehicles in our depots.
        :param depot_file_name: the name of the depot file
        :param missed_out_trips_number: the number of missed out trips
        """
        super().__init__("Error DE5: Depot File {} contains too few vehicles. {} trips can not be served".format(depot_file_name, missed_out_trips_number))
