package net.lintim.graphviz;

import net.lintim.csv.Formatter;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Activity;
import net.lintim.model.Activity.ActivityType;
import net.lintim.model.Event;
import net.lintim.model.EventActivityNetwork;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashSet;

/**
 * Methods to plot an event activity network, optinally with timetable.
 *
 */
public class EventActivityNetworkGraphviz {

    private static String shortEventType(Event.EventType type) {
        switch (type) {
        case ARRIVAL:
            return "arr";
        case DEPARTURE:
            return "dep";
        default:
            return null;
        }
    }

    private static String shortActivityType(Activity.ActivityType type) {
        switch (type) {
        case CHANGE:
            return "change";
        case DRIVE:
            return "drive";
        case HEADWAY:
            return "headway";
        case SYNC:
            return "sync";
        case TURNAROUND:
            return "turnaround";
        case WAIT:
            return "wait";
        default:
            return null;
        }
    }

    /**
     * Writes a GraphViz file.
     *
     * @param ean The event activity network (optinally with timetable) to
     * read from.
     * @param graphvizFile The GraphViz file to write to.
     * @throws IOException
     * @throws DataInconsistentException
     */
    public static void toFile(EventActivityNetwork ean, File graphvizFile)
            throws IOException, DataInconsistentException {

        Boolean timetableGiven = ean.timetableGiven();
        LinkedHashSet<Event> events = ean.getEvents();
        LinkedHashSet<Activity> activities = ean.getActivities();

        graphvizFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(graphvizFile);

        fw.write("digraph EventActivityNetwork {\n" + "rankdir=\"LR\"\n"
                + "node [shape=record,style=rounded];");

        for (Event e : events) {

            fw.write("node [label=\""
                    + e.getIndex()
                    + ", "
                    + shortEventType(e.getType())
                    + (timetableGiven ? " |{S " + e.getStation().getIndex()
                            + " |L " + e.getLine().getIndex() + "  }|  @ "
                            + Formatter.format(e.getTime()) : "") + "\"] "
                    + e.getIndex() + "\n");

        }

        for (Activity a : activities) {

            if (a.getType() == ActivityType.CHANGE && a.getPassengers() == 0) {
                continue;
            }

            fw.write(a.getFromEvent().getIndex()
                    + " -> "
                    + a.getToEvent().getIndex()
                    + " [label=<<table border=\"0\"><tr><td>"
                    + a.getIndex()
                    + ", "
                    + shortActivityType(a.getType())
                    + "</td></tr><tr><td>"
                    + (timetableGiven ? Formatter.format(a.getDuration())
                            + " &isin; " : "") + "["
                            + Formatter.format(a.getLowerBound()) + "; "
                            + Formatter.format(a.getUpperBound()) + "]"
                            + "</td></tr><tr><td>"
                            + Formatter.format(a.getPassengers())
                            + "</td></tr></table>>]\n");


        }

        fw.write("}");

        fw.close();

    }

}
