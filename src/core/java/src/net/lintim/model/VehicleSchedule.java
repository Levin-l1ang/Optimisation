package net.lintim.model;

import net.lintim.util.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Class representing a vehicle schedule
 */
public class VehicleSchedule implements Iterable<Circulation> {
	private static Logger logger = new Logger(VehicleSchedule.class);
	private HashMap<Integer, Circulation> circulations;

    /**
     * Generate a new, empty vehicle schedule.
     */
	public VehicleSchedule(){
		this.circulations = new HashMap<>();
	}

    /**
     * Get the circulation with the given id from the schedule
     * @param circulationId the id to search for
     * @return the collection with the given id
     */
	public Circulation getCirculation(int circulationId){
		return this.circulations.get(circulationId);
	}

    /**
     * Get a collection of the circulations in this vehicle schedule. Note that this will return not a copy of the
     * circulations, i.e., changes to the collection will change the vehicle schedule
     * @return the collections of this schedule.
     */
	public Collection<Circulation> getCirculations(){
		return this.circulations.values();
	}

    /**
     * Add the given circulation to the vehicle schedule. The schedule may not contain multiple circulations with the
     * same id, i.e., when inserting a second circulation with the same id, the old one will be replaced and a
     * warning will be logged
     * @param circulation the circulation to add
     */
	public void addCirculation(Circulation circulation){
		if(circulations.containsKey(circulation.getCirculationId())){
			logger.warn("Resetting circulation with id " + circulation.getCirculationId());
		}
		this.circulations.put(circulation.getCirculationId(), circulation);
	}

    /**
     * Get the mapping of the circulations, keyed by their id. Note that this will return not a copy of the
     * circulations, i.e., changes to the collection will change the vehicle schedule
     * @return the circulation map
     */
	public Map<Integer, Circulation> getCirculationMap(){
		return circulations;
	}

    /**
     * Return a LinTim compatible csv representation of the trip with the given ids for the vehicle scheduling file
     * @param circulationId the circulation id
     * @param vehicleId the vehicle id
     * @param tripNumber the trip number
     * @return a string representation of the corresponding trip
     */
	public String[] toCsvStrings(int circulationId, int vehicleId, int tripNumber){
	    Trip trip = circulations.get(circulationId).getVehicleTour(vehicleId).getTrip(tripNumber);
	    String[] tripOutput = trip.toCsvStrings();
	    String[] output = new String[tripOutput.length + 4];
	    output[0] = String.valueOf(circulationId);
	    output[1] = String.valueOf(vehicleId);
	    output[2] = String.valueOf(tripNumber);
	    output[3] = trip.getTripType().name();
	    System.arraycopy(tripOutput, 0, output, 4, tripOutput.length);
	    return output;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Vehicle Schedule:\n");
        for(Map.Entry<Integer, Circulation> circulationEntry : circulations.entrySet()){
            builder.append(circulationEntry.getKey()).append(":").append(circulationEntry.getValue());
        }
        return builder.toString();
    }

    @Override
    public Iterator<Circulation> iterator() {
        return circulations.values().iterator();
    }
}
