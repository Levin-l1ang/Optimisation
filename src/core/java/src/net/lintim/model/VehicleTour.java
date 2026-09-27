package net.lintim.model;

import net.lintim.util.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Class representing a vehicle tour, i.e., a sequence of trips served by one vehicle
 */
public class VehicleTour implements Iterable<Map.Entry<Integer, Trip>> {
	private static Logger logger = new Logger(VehicleTour.class);
	private int vehicleId;
	private HashMap<Integer, Trip> trips;

    /**
     * Create a new empty vehicle tour for a vehicle with the given id
     * @param vehicleId the id of the vehicle to serve the trip. There may not be multiple vehicle tours served by
     *                  the same vehicle in one circulation
     */
	public VehicleTour(int vehicleId){
		this.vehicleId = vehicleId;
		this.trips = new HashMap<>();
	}

    /**
     * Get the vehicle id of this tour
     * @return the vehicle tour
     */
	public int getVehicleId() {
		return vehicleId;
	}

    /**
     * Get a list of the trips served in this vehicle tour. This will return a copy of the list, i.e., changes of the
     * list will not be represented in the vehicle tour
     * @return the trips served in this vehicle tour.
     */
	public List<Trip> getTripList() {
		return trips.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).map(Map.Entry::getValue)
				.collect(Collectors.toList());
	}

    /**
     * Get the trip with the given trip number
     * @param tripNumber the number to look for
     * @return the trip for the given number
     */
	public Trip getTrip(int tripNumber){
	    return trips.get(tripNumber);
    }

    /**
     * Get the trips, associated with their respective trip number.
     * @return a collection of trips and their trip numbers
     */
    public Collection<Map.Entry<Integer, Trip>> getTripsWithIds(){
	    return trips.entrySet();
    }

    /**
     * Add a trip with the given trip number, note that a vehicle tour cannot contain multiple trips with the same
     * trip number. Therefore, when adding a second trip with the same number, the old one will be replaced
     * @param tripId the trip id
     * @param trip the trip to add
     */
	public void addTrip(int tripId, Trip trip){
		Trip tripBefore = trips.get(tripId-1);
		Trip tripAfter = trips.get(tripId+1);
		if(tripBefore != null){
			if(tripBefore.getEndAperiodicEventId() != trip.getStartAperiodicEventId()){
				logger.warn("Fitting a nonmatching trip into a vehicle tour");
			}
		}
		if(tripAfter != null){
			if(tripAfter.getStartAperiodicEventId() != trip.getEndAperiodicEventId()){
				logger.warn("Fitting a nonmatching trip into a vehicle tour");
			}
		}
		if(trips.containsKey(tripId)){
			logger.warn("Replacing existing trip in vehicle tour!");
		}
		trips.put(tripId, trip);
	}

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Vehicle Tour:\n");
        for(Map.Entry<Integer, Trip> tripEntry : trips.entrySet()){
            builder.append(tripEntry.getKey()).append(":").append(tripEntry.getValue());
        }
        return builder.toString();
    }

    @Override
    public Iterator<Map.Entry<Integer, Trip>> iterator() {
        return trips.entrySet().iterator();
    }
}
