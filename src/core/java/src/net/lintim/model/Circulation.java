package net.lintim.model;

import net.lintim.util.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Class representing a circulation in a LinTim-vehicle schedule. For information on a circulation, see the LinTim
 * documentation and the documentation of the canal vs model.
 */
public class Circulation implements Iterable<VehicleTour> {
	private static Logger logger = new Logger(Circulation.class);
	private int circulationId;
	private HashMap<Integer, VehicleTour> vehicleTours;

    /**
     * Create a new circulation with the given id. The id should be unique in a Vehicle Schedule
     * @param circulationId the id of the new circulation
     */
	public Circulation(int circulationId){
		this.circulationId = circulationId;
		this.vehicleTours = new HashMap<>();
	}

    /**
     * Get the id of the circulation
     * @return the circulation id
     */
	public int getCirculationId() {
		return circulationId;
	}

    /**
     * get a list of the tours contained in the circulation
     * @return the vehicle tour list
     */
	public List<VehicleTour> getVehicleTourList() {
		return vehicleTours.entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).map(Map
				.Entry::getValue).collect(Collectors.toList());
	}

    /**
     * Add a vehicle tour to the circulation. A circulation cannot contain multiple tours having the same vehicle id.
     * @param vehicleTour the tour to add.
     */
	public void addVehicle(VehicleTour vehicleTour){
		if(vehicleTours.containsKey(vehicleTour.getVehicleId())){
			logger.warn("Replacing existing vehicle tour in circulation!");
		}
		vehicleTours.put(vehicleTour.getVehicleId(), vehicleTour);
	}

    /**
     * Get the vehicle tour with the given vehicle id
     * @param vehicleId the id to search for
     * @return the tour with the given id in the circulation, or null if there is none.
     */
	public VehicleTour getVehicleTour(int vehicleId){
		return vehicleTours.get(vehicleId);
	}

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Circulation ").append(circulationId).append(":\n");
        for(Map.Entry<Integer, VehicleTour> vehicleTourEntry : vehicleTours.entrySet()){
            builder.append(vehicleTourEntry.getKey()).append(":").append(vehicleTourEntry.getValue()).append("\n");
        }
        return builder.toString();
    }

    @Override
    public Iterator<VehicleTour> iterator() {
        return vehicleTours.values().iterator();
    }
}
