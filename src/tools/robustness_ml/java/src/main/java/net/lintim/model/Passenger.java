package net.lintim.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Passenger implements Comparable<Passenger> {

    private final int index;
    private final int weight;
    private List<RoutingNode> path;
    private final int startStopId;
    private final int endStopId;
    private final RoutingNode startNode;
    private int arrivalEndId;
    private int currentPathIndex;
    private int changes;
    private double travelTime;
    private int nextTime;

    public Passenger(int index, int weight, List<RoutingNode> path, int startStopId, int endStopId, int arrivalEndId) {
        this(index, weight, path, startStopId, endStopId, arrivalEndId, null);
    }

    public Passenger(int index, int weight, List<RoutingNode> path, int startStopId, int endStopId, RoutingNode startNode) {
        this(index, weight, path, startStopId, endStopId, -1, startNode);
    }

    public Passenger(int index, int weight, List<RoutingNode> path, int startStopId, int endStopId, int arrivalEndId, RoutingNode startNode) {
        this.index = index;
        this.weight = weight;
        this.path = path;
        this.startStopId = startStopId;
        this.endStopId = endStopId;
        this.arrivalEndId = arrivalEndId;
        this.startNode = startNode;
        this.currentPathIndex = -1;
        this.nextTime = -1;
        this.travelTime = 0;
        this.changes = 0;
        if (path != null) {
            resetCurrentPathStatus();
        }
    }

    private void resetCurrentPathStatus() {
        this.currentPathIndex = 0;
        this.nextTime = -1;
        for (int pathIndex = 0; pathIndex < this.path.size(); pathIndex++) {
            if (this.path.get(pathIndex).isStart()) {
                if (pathIndex >= 1) {
                    this.currentPathIndex = pathIndex - 1;
                }
                break;
            }
        }
        this.nextTime = this.path.get(this.currentPathIndex + 1).getEvent().getTime();
        this.arrivalEndId = this.path.get(this.path.size() - 2).getEvent().getId();
    }

    public void addChange() {
        this.changes += 1;
    }

    public void setChanges(int changes) {
        this.changes = changes;
    }

    public void addTravelTime(int travelTimeIncrease) {
        this.travelTime += travelTimeIncrease;
    }

    public void removeTravelTime(int travelTimeDecrease) {
        this.travelTime -= travelTimeDecrease;
    }

    public void setNewPath(List<RoutingNode> newPath) {
        this.path = newPath;
        this.resetCurrentPathStatus();
    }

    public int getIndex() {
        return index;
    }

    public int getWeight() {
        return weight;
    }

    public List<RoutingNode> getPath() {
        return path;
    }

    public int getStartStopId() {
        return startStopId;
    }

    public int getEndStopId() {
        return endStopId;
    }

    public int getArrivalEventId() {
        return arrivalEndId;
    }

    public int getCurrentPathIndex() {
        return currentPathIndex;
    }

    public int getChanges() {
        return changes;
    }

    public double getTravelTime() {
        return travelTime;
    }

    public void setTravelTime(double travelTime) {
        this.travelTime = travelTime;
    }


    public int getNextTime() {
        return nextTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Passenger passenger = (Passenger) o;
        return index == passenger.index &&
            Double.compare(passenger.weight, weight) == 0 &&
            startStopId == passenger.startStopId &&
            endStopId == passenger.endStopId &&
            arrivalEndId == passenger.arrivalEndId &&
            currentPathIndex == passenger.currentPathIndex &&
            changes == passenger.changes &&
            travelTime == passenger.travelTime &&
            nextTime == passenger.nextTime &&
            Objects.equals(path, passenger.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, weight, startStopId, endStopId);
    }

    @Override
    public String toString() {
        String pathRepr = "null";
        if (this.path != null) {
            pathRepr = this.path.stream().map(RoutingNode::toString).collect(Collectors.joining(", "));
        }
        return "Passenger{" +
            "index=" + index +
            ", weight=" + weight +
            ", path=" + pathRepr +
            ", startStopId=" + startStopId +
            ", endStopId=" + endStopId +
            ", arrivalEndId=" + arrivalEndId +
            ", currentPathIndex=" + currentPathIndex +
            ", changes=" + changes +
            ", travelTime=" + travelTime +
            ", nextTime=" + nextTime +
            '}';
    }

    @Override
    public int compareTo(Passenger o) {
        return (int) Math.signum(this.getNextTime() - o.getNextTime());
    }

    public RoutingNode getStartNode() {
        return startNode;
    }
}
