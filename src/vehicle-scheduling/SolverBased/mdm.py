import logging
import time

from core.model.vehicle_scheduling import VehicleSchedule, VehicleTour, Circulation, Trip, TripType
from core.solver.generic_solver_interface import Solver, VariableType, ConstraintSense, OptimizationSense, IntAttribute, Status
from core.solver.solver_parameters import SolverParameters
from core.exceptions.solver_exceptions import SolverFoundNoFeasibleSolutionException

logger = logging.getLogger(__name__)

def mdm1(
    trip_list: list[Trip],
    parameters: SolverParameters,
    turn_over_time: float) -> VehicleSchedule:
    """Implementation of the Mdm1 model described in the Paper by Bunte and Kliewer from 2009 (Chapter 2.1) and in the diplom thesis "Das Kanalmodell zur Effizienzsteigerung in der Fahrzeugumlaufplanung" by Anke Uffmann.
    We use the Generic-Solver-Interface provided by Lintim. If our IP is feasible, then the solution is returned as an VehicleSchedule.

    :param trip_list: List containing all trips the vehicles need to be assigned to
    :type trip_list: list[Trip]
    :param parameters: parameters which specify the solver used to solve the IP below
    :type parameters: SolverParameters
    :param turn_over_time: the time a vehicle needs after arrival to ready itself for the next trip
    :type turn_over_time: int

    ...
    :raises SolverFoundNoFeasibleSolutionException
    ...
    :return: vehicle schedule which contains the vehicle assignments
    :rtype: VehicleSchedule
    """
    def compatible(trip1: Trip, trip2: Trip) -> bool:
        """Short check whether trip2 can be reached by a vehicle after finishing trip1

        :param trip1: first trip
        :type trip1: Trip
        :param trip2: second trip
        :type trip2: Trip
        ...
        :return: True or False
        """
        return trip1.getEndStopId() == trip2.getStartStopId() and trip1.getEndTime() +turn_over_time <= trip2.getStartTime()

    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()
    logger.debug("Add parameters")
    parameters.setSolverParameters(model)

    large_negative_number = - 2*(len(trip_list)**2)
    # In the Paper of Bunte and Kliewer, there are -\infty coefficients in the objective functions.
    # We replace those by a large enough negative constant since not all IP solver can handle infinity.

    logger.debug("Add variables")
    #---------------------------------------create all variables----------------------------------------------
    x={}
    for i,trip_i in enumerate(trip_list):
        for j,trip_j in enumerate(trip_list):
            if compatible(trip_i,trip_j):
                x[(i,j)] = model.addVariable(
                    lower_bound=0,
                    upper_bound=1,
                    var_type=VariableType.BINARY,
                    objective=0,
                    name = "x"+str((i,j))
                )  # the trip j after trip i variables
            else:
                x[(i,j)] = model.addVariable(
                    lower_bound=0,
                    upper_bound=1,
                    var_type=VariableType.BINARY,
                    objective=large_negative_number,
                    name = "x"+str((i,j))
                )
    for i,trip_i in enumerate(trip_list):   # add the variables for driving from/into one depot
            x[i,len(trip_list)] = model.addVariable(
                lower_bound=0,
                upper_bound=1,
                var_type=VariableType.BINARY,
                objective=0,
                name="x"+str((i, len(trip_list)))
            )
            x[len(trip_list),i] = model.addVariable(
                lower_bound=0,
                upper_bound=1,
                var_type=VariableType.BINARY,
                objective=0,
                name="x"+str((len(trip_list), i))
            )
    x[len(trip_list), len(trip_list)] = model.addVariable(
        lower_bound=0,
        upper_bound=len(trip_list),
        var_type=VariableType.INTEGER,
        objective=1,
        name = "stay_in_depot_vehicle_number"
    )

    logger.debug("Add constraints")
    # ----------------------------------add constraints-----------------------------------------
    for i, trip_i in enumerate(trip_list):  # implement first sum constraints
        sum_constraint_1 = model.createExpression()
        sum_constraint_2 = model.createExpression()
        for j, trip_j in enumerate(trip_list):
                sum_constraint_1.add(x[(i,j)])
                sum_constraint_2.add(x[(j,i)])
        sum_constraint_1.add(x[(i,len(trip_list))])
        sum_constraint_2.add(x[(len(trip_list),i)])
        model.addConstraint(sum_constraint_1, ConstraintSense.EQUAL, 1,name = "sum_constraint_1"+str(i))
        model.addConstraint(sum_constraint_2, ConstraintSense.EQUAL, 1,name = "sum_constraint_2"+str(i))

    sum_constraint_3 = model.createExpression()  # create the "depot-input" constraint
    for j, trip_j in enumerate(trip_list):
        sum_constraint_3.add(x[(len(trip_list), j)])
    sum_constraint_3.add(x[len(trip_list), len(trip_list)])
    model.addConstraint(sum_constraint_3, ConstraintSense.EQUAL, len(trip_list), name="sum_constraint_3")
    # It is enough to have the constraint of exactly n=len(trips) vehicles entering the depot node. The additional constraint of exactly
    # n vehicles leaving the depot node is redundant in this case: Assume m!=n vehicles would leave the depot. Either they take the loop
    # back to the depot (then not n vehicles would enter the depot) or they take another arc to a trip node. All trip nodes have flow
    # constraints, ensuring exactly one vehicle entering and leaving it, so also in this case the m vehicles would have to go back to
    # the depot.
    #--------------------------------------------------solve Model---------------------------------
    model.setSense(OptimizationSense.MAXIMIZE)

    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("vehicle-scheduling/mdm1.lp")
        logger.debug("Finished writing lp file")

    logger.debug("Start optimization")
    start_time = time.time()
    model.solve()
    end_time = time.time()
    logger.info("Solver time: {:5.3f}s".format(end_time - start_time))
    logger.debug("Finished optimization")

    status = model.getStatus()
    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")

        # -------------------- create VehicleSchedule Object----------------------------------
        vehicle_tour_list = []
        vehicleId = 1
        for i,trip_i in enumerate(trip_list):
            if model.getValue(x[(len(trip_list),i)])==1:  # start one circulation/vehicle tour
                vehicletour = VehicleTour(vehicleId)
                vehicletour_tripId = 1

                # depot_trip = Trip(
                #     -1,
                #     -1,
                #     -1,
                #     trip_i.getStartTime()-1, # every vehicletour starts with the departure from the depot, but as the depot is not specified we just assume a starting time from the depot
                #     trip_i.getStartAperiodicEventId(),
                #     trip_i.getStartPeriodicEventId(),
                #     trip_i.getStartStopId(),
                #     trip_i.getStartTime(),
                #     -1,
                #     TripType.EMPTY
                # )  # empty trip starts at depot with aperiodic-start-id=-1, periodic-start-id=-1, the negative depotId as stopId and ends at beginning of i
                # vehicletour.addTrip(vehicletour_tripId, depot_trip)
                # vehicletour_tripId += 1

                same_vehicle = True
                while same_vehicle: # calculate next trips in the circulation/vehicle tour
                    for j,trip_j in enumerate(trip_list):
                        if model.getValue(x[(i,j)])==1:  # check if trip j successes trip i
                            vehicletour.addTrip(vehicletour_tripId, trip_i)
                            vehicletour_tripId += 1
                            empty_trip = Trip(
                                trip_i.getEndAperiodicEventId(),
                                trip_i.getEndPeriodicEventId(),
                                trip_i.getEndStopId(),
                                trip_i.getEndTime(),
                                trip_j.getStartAperiodicEventId(),
                                trip_j.getStartPeriodicEventId(),
                                trip_j.getStartStopId(),
                                trip_j.getStartTime(),
                                -1,
                                TripType.EMPTY
                            )  # empty trip starts at end of i and ends at beginning of j
                            vehicletour.addTrip(vehicletour_tripId, empty_trip)
                            vehicletour_tripId += 1
                            i,trip_i = j,trip_j
                            break
                    if model.getValue(x[i,len(trip_list)])==1: # end of circulation/vehicle tour
                        vehicletour.addTrip(vehicletour_tripId, trip_i)
                        vehicletour_tripId += 1
                        # depot_trip = Trip(
                        #     trip_i.getEndAperiodicEventId(),
                        #     trip_i.getEndPeriodicEventId(),
                        #     trip_i.getEndStopId(),
                        #     trip_i.getEndTime(), # every vehicletour ends with the arrival at the depot. As the depot is not specified we assume just assume one arrival time
                        #     -1,
                        #     -1,
                        #     -1,
                        #     trip_i.getEndTime()+1,
                        #     -1,
                        #     TripType.EMPTY) # empty trip ends at depot with aperiodic-end-id=-1, periodic-end-id=-1, the negative depotId -1 as stopId and starts at the end of i
                        # vehicletour.addTrip(vehicletour_tripId, depot_trip)
                        vehicle_tour_list.append(vehicletour)
                        vehicleId += 1
                        same_vehicle = False

        vehicle_schedule = VehicleSchedule()
        for vehicle_id, vehicle_tour in enumerate(vehicle_tour_list): # fill the vehicle_schedule
            circ = Circulation(vehicle_id + 1)  # our IDs start at 1 not 0
            circ.addVehicle(vehicle_tour)
            vehicle_schedule.addCirculation(circ)

        # ----------- return vehicle schedule and dispose model------------------
        model.dispose()
        solver.dispose()
        return(vehicle_schedule)

    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            logger.error("Model is infeasible, compute IIS")
            model.computeIIS("vehicle-scheduling/IIS_mdm1.ilp")
        model.dispose()
        solver.dispose()
        raise SolverFoundNoFeasibleSolutionException()


#-----------------------------------------MDM2-------------------------------------------------
def mdm2(
    trip_list: list[Trip],
    parameters: SolverParameters,
    turn_over_time: float) -> VehicleSchedule:
    """Implementation of the Mdm2 model described in the Paper by Bunte and Kliewer from 2009 (Chapter 2.2) and in the diplom thesis "Das Kanalmodell zur Effizienzsteigerung in der Fahrzeugumlaufplanung" by Anke Uffmann.
    We use the Generic-Solver-Interface provided by Lintim. If our IP is feasible, we return the solution as an VehicleSchedule.

    :param trip_list: List containing all trips the vehicles need to be assigned to
    :type trip_list: list[Trip]
    :param parameters: parameters specifying the solver used to solve the IP below
    :type parameters: SolverParameters
    :param turn_over_time: the time a vehicle needs after arrival to ready itself for the next trip
    :type turn_over_time: int

    ...
    :raises SolverFoundNoFeasibleSolutionException
    ...
    :return: vehicle schedule which contains the vehicle assignments
    :rtype: VehicleSchedule
    """

    def compatible(trip1: Trip, trip2: Trip) -> bool:
        """Short check whether trip2 can be reached by a vehicle after finishing trip1

        :param trip1: first trip
        :type trip1: Trip
        :param trip2: second trip
        :type trip2: Trip
        ...
        :return: True or False
        """
        return trip1.getEndStopId() == trip2.getStartStopId() and trip1.getEndTime()  +turn_over_time <= trip2.getStartTime()

    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()
    logger.debug("Add parameters")
    parameters.setSolverParameters(model)

    large_negative_number = - 2*(len(trip_list)**2)
    # In the Paper of Bunte and Kliewer, there are -\infty coefficients in the objective functions.
    # We replace those by a large enough negative constant since not all IP solver can handle infinity.

    logger.debug("Add variables")
    #---------------------------------------create all variables----------------------------------------------
    x={}
    for i,trip_i in enumerate(trip_list):
        for j,trip_j in enumerate(trip_list):
            if compatible(trip_i,trip_j):
                x[(i,j)] = model.addVariable(
                    lower_bound=0,
                    upper_bound=1,
                    var_type=VariableType.BINARY,
                    objective=1,
                    name = "x"+str((i,j))
                )  # the trip j after trip i variables
            else:
                x[(i,j)] = model.addVariable(
                    lower_bound=0,
                    upper_bound=1,
                    var_type=VariableType.BINARY,
                    objective=large_negative_number,
                    name = "x"+str((i,j))
                )

    logger.debug("Add constraints")
    # ----------------------------------add constraints-----------------------------------------
    for i, trip_i in enumerate(trip_list):  # implement first sum constraints
        sum_constraint_1 = model.createExpression()
        sum_constraint_2 = model.createExpression()
        for j, trip_j in enumerate(trip_list):
                sum_constraint_1.add(x[(i,j)])
                sum_constraint_2.add(x[(j,i)])
        model.addConstraint(
            sum_constraint_1,
            ConstraintSense.LESS_EQUAL,
            1,
            name = "sum_constraint_1"+str(i)
        )
        model.addConstraint(
            sum_constraint_2,
            ConstraintSense.LESS_EQUAL,
            1,
            name = "sum_constraint_2"+str(i)
        )

    #--------------------------------------------------solve Model---------------------------------
    model.setSense(OptimizationSense.MAXIMIZE)

    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("vehicle-scheduling/mdm2.lp")
        logger.debug("Finished writing lp file")

    logger.debug("Start optimization")
    start_time = time.time()
    model.solve()
    end_time = time.time()
    logger.info("Solver time: {:5.3f}s".format(end_time - start_time))
    logger.debug("Finished optimization")

    status = model.getStatus()
    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")
        # -------------------- create VehicleSchedule Object----------------------------------
        vehicle_tour_list = []
        vehicleId = 1
        for i,trip_i in enumerate(trip_list):
            if sum(model.getValue(x[(j,i)]) for j in range(len(trip_list)))==0:  # start one circulation/vehicle tour
                vehicletour = VehicleTour(vehicleId)
                vehicletour_tripId = 1

                # depot_trip = Trip(
                #     -1,
                #     -1,
                #     -1,
                #     trip_i.getStartTime()-1, # every vehicletour starts with the departure from the depot, but as the depot is not specified we just assume a starting time from the depot
                #     trip_i.getStartAperiodicEventId(),
                #     trip_i.getStartPeriodicEventId(),
                #     trip_i.getStartStopId(),
                #     trip_i.getStartTime(),
                #     -1,
                #     TripType.EMPTY
                # )  # empty trip starts at depot with aperiodic-start-id=-1, periodic-start-id=-1, the negative depotId as stopId and ends at beginning of i
                # vehicletour.addTrip(vehicletour_tripId, depot_trip)
                # vehicletour_tripId += 1

                same_vehicle = True
                while same_vehicle: # calculate next trips in the circulation/vehicle tour
                    for j,trip_j in enumerate(trip_list):
                        if model.getValue(x[(i,j)])==1:  # check if trip j successes trip i
                            vehicletour.addTrip(vehicletour_tripId, trip_i)
                            vehicletour_tripId += 1
                            empty_trip = Trip(
                                trip_i.getEndAperiodicEventId(),
                                trip_i.getEndPeriodicEventId(),
                                trip_i.getEndStopId(),
                                trip_i.getEndTime(),
                                trip_j.getStartAperiodicEventId(),
                                trip_j.getStartPeriodicEventId(),
                                trip_j.getStartStopId(),
                                trip_j.getStartTime(),
                                -1,
                                TripType.EMPTY
                            )  # empty trip starts at end of i and ends at beginning of j
                            vehicletour.addTrip(vehicletour_tripId, empty_trip)
                            vehicletour_tripId += 1
                            i,trip_i = j,trip_j
                            break
                    if sum(model.getValue(x[i,j]) for j in range(len(trip_list)))==0: # check if end of circulation/vehicle tour
                        vehicletour.addTrip(vehicletour_tripId, trip_i)
                        vehicletour_tripId += 1
                        # depot_trip = Trip(
                        #     trip_i.getEndAperiodicEventId(),
                        #     trip_i.getEndPeriodicEventId(),
                        #     trip_i.getEndStopId(),
                        #     trip_i.getEndTime(), # every vehicletour ends with the arrival at the depot. As the depot is not specified we assume just assume one arrival time
                        #     -1,
                        #     -1,
                        #     -1,
                        #     trip_i.getEndTime()+1,
                        #     -1,
                        #     TripType.EMPTY
                        # ) # empty trip ends at depot with aperiodic-end-id=-1, periodic-end-id=-1, the negative depotId -1 as stopId and starts at the end of i
                        # vehicletour.addTrip(vehicletour_tripId, depot_trip)
                        vehicle_tour_list.append(vehicletour)
                        vehicleId += 1
                        same_vehicle = False

        vehicle_schedule = VehicleSchedule()
        for vehicle_id, vehicle_tour in enumerate(vehicle_tour_list): # fill the vehicle_schedule
            circ = Circulation(vehicle_id + 1)  # our IDs start at 1 not 0
            circ.addVehicle(vehicle_tour)
            vehicle_schedule.addCirculation(circ)

        # ----------- return vehicle schedule and dispose model------------------
        model.dispose()
        solver.dispose()
        return(vehicle_schedule)

    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            logger.error("Model is infeasible, compute IIS")
            model.computeIIS("vehicle-scheduling/IIS_mdm2.ilp")
        model.dispose()
        solver.dispose()
        raise SolverFoundNoFeasibleSolutionException()
