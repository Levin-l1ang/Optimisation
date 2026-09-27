package net.lintim.algorithms.vehiclescheduling;

import net.lintim.exception.LinTimException;
import net.lintim.model.*;

import java.util.Collection;
import java.util.Comparator;

/**
 * Class for computing a new simple vehicle scheduling.
 */
public class SimpleVehicleSchedule {

	/**
	 * Create a new simple vehicle schedule. A vehicle will cover the next other direction of the same line after
	 * finishing a trip.
	 * @param trips the trips to cover
	 * @return the resulting vehicle schedule.
	 */
	public static VehicleSchedule createSimpleVehicleSchedule(Collection<Trip> trips, int turnOverTime){
		VehicleSchedule vehicleSchedule = new VehicleSchedule();
		Circulation circulation = new Circulation(1);
		vehicleSchedule.addCirculation(circulation);
		int nextVehicleId = 1;
		while (!trips.isEmpty()){
			VehicleTour nextTour = new VehicleTour(nextVehicleId);
			circulation.addVehicle(nextTour);
			nextVehicleId += 1;
			int nextTripId = 1;
			//First, find the earliest trip
			Trip nextTrip = trips.stream().min(Comparator.comparingInt(Trip::getStartTime)).orElse(null);
			if(nextTrip == null){
				throw new LinTimException("Did not found first trip, but list of trips is not empty!");
			}
			nextTour.addTrip(nextTripId, nextTrip);
			nextTripId += 1;
			trips.remove(nextTrip);
			int currentLineId = nextTrip.getLineId();
			while (true){
				Trip lastTrip = nextTrip;
				int currentStopId = lastTrip.getEndStopId();
				int currentTime = lastTrip.getEndTime() + turnOverTime;
				//Fill the tour with corresponding trips, until it is full
				nextTrip = trips.stream()
						.filter(trip -> trip.getLineId() == currentLineId) //Only append the same line
						.filter(trip -> trip.getStartStopId() == currentStopId) //We need to have the correct direction
						.filter(trip -> trip.getStartTime() >= currentTime) //We need to start later as the current time
						.min(Comparator.comparingInt(Trip::getStartTime)) //Choose the earliest time
						.orElse(null); //Null is the debug output, if there was none. This is our stopping criterion
				if(nextTrip == null){
					//There was no next trip, create the next tour
					break;
				}
				//Create the respective empty trip
				Trip emptyTrip = new Trip(
					lastTrip.getEndAperiodicEventId(), 
					lastTrip.getEndPeriodicEventId(),
					lastTrip.getEndStopId(), 
					lastTrip.getEndTime(), 
					nextTrip.getStartAperiodicEventId(), 
					nextTrip.getStartPeriodicEventId(), 
					nextTrip.getStartStopId(), 
					nextTrip.getStartTime(),
					-1, 
					TripType.EMPTY);
				nextTour.addTrip(nextTripId, emptyTrip);
				nextTripId += 1;
				nextTour.addTrip(nextTripId, nextTrip);
				nextTripId += 1;
				trips.remove(nextTrip);
			}
		}
		return vehicleSchedule;
	}
}
