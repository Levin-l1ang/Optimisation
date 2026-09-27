import net.lintim.util.Logger;

import java.util.LinkedList;

public class Propagator {

    private static final Logger logger = new Logger(Propagator.class);

    public static double propagate(NonPeriodicEANetwork Net, int maxwait, boolean swapHeadways) throws Exception {
        // topological ordering
        LinkedList<NonPeriodicEvent> toDo = new LinkedList<NonPeriodicEvent>();
        // remove inactive headway activities from list of incoming activities of events
        // and set initial headway disposition decisions according to timetable
        logger.debug("removing inactive headways from events");
        for (NonPeriodicHeadwayActivity h : Net.getHeadwayActivities()) {
            if (h.getTarget().getTime() - h.getSource().getTime() >= h.getLowerBound())
                h.setG(0);
            else {
                h.setG(1);
                h.getTarget().removeIncomingActivity(h);
            }
        }
        // find events without incoming activities
        logger.debug("queueing first events");
        for (NonPeriodicEvent e : Net.getEvents())
            if (e.getIncomingActivities().isEmpty())
                toDo.addLast(e);
        logger.debug(toDo.size() + " events queued");
        while (!toDo.isEmpty()) {
            // access events in topological ordering
            NonPeriodicEvent e = toDo.removeFirst();

            // fix the disposition time. if the disposition time was affected by
            // incoming activities, this has already been set earlier.
            // only need to make sure the disposition time is not earlier
            // than the scheduled time + the source delay (if any)
            int minTime = e.getTime() + e.getSourceDelay();
            if (e.getDispoTime() < minTime)
                e.setDispoTime(minTime);

            // update events at outgoing activities, if they are delayed by this event
            // then remove the activities and, if that was the last incoming activity
            // of the other event, append it to the topological ordering
            for (NonPeriodicActivity a : e.getOutgoingActivities()) {
                boolean respectActivity = true;
                NonPeriodicEvent t = a.getTarget();
                minTime = e.getDispoTime() + a.getLowerBound() + a.getSourceDelay();
                // if changing activity: only wait if dispo time is not more than maxwait later than scheduled time
                if ((a instanceof NonPeriodicChangingActivity) && ((((NonPeriodicChangingActivity) a).getZ() == 1) || (minTime > t.getTime() + maxwait)))
                    respectActivity = false;
                // special treatment for headways
                if (a instanceof NonPeriodicHeadwayActivity) {
                    NonPeriodicHeadwayActivity h = (NonPeriodicHeadwayActivity) a;
                    NonPeriodicHeadwayActivity c = h.getCorrespodingHeadway(); // I'd be glad to see a refactoring from "Correspoding" to "Corresponding"
                    // don't respect inactive headways
                    if (h.getG() == 1) respectActivity = false;
                        // swap headway if second train can leave early enough (if this is not a swapped headway)
                    else if (t.getTime() - e.getTime() >= a.getLowerBound()) {
                        if (swapHeadways && ((t.getDispoTime() >= 0 ? t.getDispoTime() : t.getTime()) + c.getLowerBound() + c.getSourceDelay() <= e.getDispoTime())) {
                            h.setG(1);
                            c.setG(0);
                            respectActivity = false;
                        }
                        // swap headway back if second train would cause delay of first train on swapped headway
                    } else {
                        if (swapHeadways && (minTime > t.getDispoTime())) {
                            h.setG(1);
                            c.setG(0);
                            minTime = t.getDispoTime() + c.getLowerBound() + c.getSourceDelay();
                            // delay second train instead; if second train (this event) is delayed by this actually,
                            // restart processing of this element for this activity might not be the first activity processed,
                            // and the activities processed earlier must be re-processed as well then
                            if (minTime > e.getDispoTime()) {
                                e.setDispoTime(minTime);
                                toDo.addFirst(e); // sic!
                                break;
                            } else respectActivity = false;
                        }
                    }
                }
                // usual things to do:
                if (respectActivity && (t.getDispoTime() < minTime)) {
                    t.setDispoTime(minTime);
                }
                if (t.getIncomingActivities().remove(a) && t.getIncomingActivities().isEmpty())
                    toDo.addLast(t);
            }
        }
        Net.setZ();
        return DM.computeObjectiveValueDM2(Net);
    }

}
