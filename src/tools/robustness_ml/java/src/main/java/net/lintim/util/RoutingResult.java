package net.lintim.util;

import net.lintim.model.Passenger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class RoutingResult {
    private final Passengers passengers;
    private final Map<Integer, Set<Integer>> passengersByEventId;
    private final Map<Integer, Set<Integer>> passengersByActivityId;

    public RoutingResult(Passengers passengers, Map<Integer, Set<Integer>> passengersByEventId, Map<Integer, Set<Integer>> passengersByActivityId) {
        this.passengers = passengers;
        this.passengersByEventId = passengersByEventId;
        this.passengersByActivityId = passengersByActivityId;
    }

    public Passengers getPassengers() {
        return passengers;
    }

    public Map<Integer, Set<Integer>> getPassengersByEventId() {
        return passengersByEventId;
    }

    public Map<Integer, Set<Integer>> getPassengersByActivityId() {
        return passengersByActivityId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingResult that = (RoutingResult) o;
        return Objects.equals(passengers, that.passengers) && Objects.equals(passengersByEventId, that.passengersByEventId) && Objects.equals(passengersByActivityId, that.passengersByActivityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(passengers, passengersByEventId, passengersByActivityId);
    }
}
