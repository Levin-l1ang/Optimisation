package net.lintim.util;

import net.lintim.exception.LinTimException;
import net.lintim.model.Passenger;

import java.util.*;

public class Passengers {
    private final SortedMap<Integer, Passenger> passengerMap;

    public Passengers() {
        this.passengerMap = new TreeMap<>();
    }

    public void addPassenger(Passenger passenger) {
        Passenger previous = this.passengerMap.put(passenger.getIndex(), passenger);
        if (previous != null) {
            throw new LinTimException("There were multiple passengers with the same index!");
        }
    }

    public Passenger getPassenger(int index) {
        return passengerMap.get(index);
    }

    public Collection<Passenger> getSortedPassengersById() {
        return new ArrayList<>(passengerMap.values());
    }

    public Collection<Passenger> getSortedPassengersByTime() {
        List<Passenger> sortedPassengers = new ArrayList<>(passengerMap.values());
        sortedPassengers.sort(Comparator.comparingInt(Passenger::getNextTime));
        return sortedPassengers;
    }

    public int size() {
        return passengerMap.size();
    }

    public void clear() {
        passengerMap.clear();
    }
}
