package net.lintim.model;

/**
 * Enumeration of all possible event types.
 * Arrival and departure are the classical event types, fix is for fixed events, i.e., when parts of a timetable should
 * be fixed for the optimization.
 */
public enum EventType {
    ARRIVAL("\"arrival\""),
    DEPARTURE("\"departure\""),
    ORIGIN("\"origin\""),
    DESTINATION("\"destination\""),
    FIX("\"fix\""),
    VIRTUAL("\"virtual\"");

    private String representation;

    EventType(String representation){
        this.representation = representation;
    }

    public String toString(){
        return representation;
    }
}
