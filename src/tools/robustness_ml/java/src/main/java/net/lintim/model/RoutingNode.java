package net.lintim.model;

public class RoutingNode {

    private final int stopId;
    private final AperiodicEvent event;
    private final boolean start;

    public RoutingNode(int stopId, AperiodicEvent event, boolean isStart) {
        this.stopId = stopId;
        this.event = event;
        this.start = isStart;
    }

    public int getStopId() {
        return stopId;
    }

    public AperiodicEvent getEvent() {
        return event;
    }

    public boolean isStart() {
        return start;
    }

    @Override
    public String toString() {
        return "RoutingNode{" +
            "stopId=" + stopId +
            ", event=" + event +
            ", start=" + start +
            '}';
    }
}
