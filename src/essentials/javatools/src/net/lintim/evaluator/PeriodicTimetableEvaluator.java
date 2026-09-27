package net.lintim.evaluator;

import net.lintim.model.Activity;
import net.lintim.model.Activity.ActivityType;
import net.lintim.model.EventActivityNetwork;
import net.lintim.util.MathHelper;

import java.util.Set;

/**
 * Evaluates different properties of an {@link EventActivityNetwork}. These
 * properties must concern the timetable; for non-timetable properties,
 * have a look at {@link EventActivityNetworkEvaluator}.
 */
public class PeriodicTimetableEvaluator {

    /**
     * Computes the sum of duration*passengers in a given {@link Set} of
     * {@link Activity}s, i.e. the average traveling time.
     * @param activities the given {@link Set} of {@link Activity}s.
     * @return sum of duration*passengers, i.e. the average traveling time.
     */
    public static Double averageTravelingTime(Set<Activity> activities){
        Double retval = 0.0;

        for(Activity a : activities){
            retval += a.getDuration()*a.getPassengers();
        }

        return retval;
    }

    /**
     * Computes the sum of duration*passengers over all {@link Activity}s of
     * a certain {@link ActivityType} in a given {@link Set} of
     * {@link Activity}s.
     * @param activities the given {@link Set} of {@link Activity}s.
     * @param type the {@link ActivityType} to sum over.
     * @return sum of duration*passengers over all drive {@link Activity}s, i.e.
     * the average drive time.
     */
    public static Double averageTypedTime(Set<Activity> activities,
             ActivityType type){

        Double retval = 0.0;

        for(Activity a : activities){
            if(a.getType() == type){
                retval += a.getDuration()*a.getPassengers();
            }
        }

        return retval;
    }

    /**
     * Redirects to {@link #averageTypedTime(Set, ActivityType)} with second
     * parameter {@link ActivityType#DRIVE}.
     * @param activities the given {@link Set} of {@link Activity}s.
     * @return sum of duration*passengers over all drive {@link Activity}s, i.e.
     * the average drive time.
     */
    public static Double averageDriveTime(Set<Activity> activities){
        return averageTypedTime(activities, ActivityType.DRIVE);
    }

    /**
     * Redirects to {@link #averageTypedTime(Set, ActivityType)} with second
     * parameter {@link ActivityType#WAIT}.
     * @param activities the given {@link Set} of {@link Activity}s.
     * @return sum of duration*passengers over all wait {@link Activity}s,
     * i.e. the average change wait.
     */
    public static Double averageWaitTime(Set<Activity> activities){
        return averageTypedTime(activities, ActivityType.WAIT);
    }

    /**
     * Redirects to {@link #averageTypedTime(Set, ActivityType)} with second
     * parameter {@link ActivityType#CHANGE}.
     * @param activities the given {@link Set} of {@link Activity}s.
     * @return sum of duration*passengers over all change {@link Activity}s,
     * i.e. the average change time.
     */
    public static Double averageChangeTime(Set<Activity> activities){
        return averageTypedTime(activities, ActivityType.CHANGE);
    }

    /**
     * Computes the sum of (duration-lowerbound)*passengers in a given
     * {@link Set} of {@link Activity}s, i.e. the average slack time.
     * @param activities the given {@link Set} of {@link Activity}s.
     * @return sum of (duration-lowerbound)*passengers, i.e. the average
     * slack time.
     */
    public static Double weightedSlackTime(Set<Activity> activities){
        Double retval = 0.0;

        for(Activity a : activities){
            retval += (a.getDuration()-a.getLowerBound())*a.getPassengers();
        }

        return retval;
    }

    /**
     * Redirects to {@link #averageTravelingTime(Set)} and sums over all
     * activities of an {@link EventActivityNetwork}.
     * @param ean the {@link EventActivityNetwork} to sum over.
     * @return sum the duration*passengers over all activities of
     * {@link EventActivityNetwork#getActivities()} for the ean parameter.
     */
    public static Double averageTravelingTime(EventActivityNetwork ean){
        return averageTravelingTime(ean.getActivities());
    }

    /**
     * Redirects to {@link #averageTravelingTime(Set)} and sums over all
     * {@link ActivityType#DRIVE} activities of an {@link EventActivityNetwork}.
     * @param ean the {@link EventActivityNetwork} to sum over.
     * @return sum the duration*passengers over all {@link ActivityType#DRIVE}
     * activities of {@link EventActivityNetwork#getActivities()} for the ean
     * parameter.
     */
    public static Double averageDriveTime(EventActivityNetwork ean){
        return averageTravelingTime(ean.getDriveActivities());
    }

    /**
     * Redirects to {@link #averageTravelingTime(Set)} and sums over all
     * {@link ActivityType#WAIT} activities of an {@link EventActivityNetwork}.
     * @param ean the {@link EventActivityNetwork} to sum over.
     * @return sum the duration*passengers over all {@link ActivityType#WAIT}
     * activities of {@link EventActivityNetwork#getActivities()} for the ean
     * parameter.
     */
    public static Double averageWaitTime(EventActivityNetwork ean){
        return averageTravelingTime(ean.getWaitActivities());
    }

    /**
     * Redirects to {@link #averageTravelingTime(Set)} and sums over all
     * {@link ActivityType#CHANGE} activities of an
     * {@link EventActivityNetwork}.
     * @param ean the {@link EventActivityNetwork} to sum over.
     * @return sum the duration*passengers over all {@link ActivityType#CHANGE}
     * activities of {@link EventActivityNetwork#getActivities()} for the ean
     * parameter.
     */
    public static Double averageChangeTime(EventActivityNetwork ean){
        return averageTravelingTime(ean.getChangeActivities());
    }

    /**
     * Redirects to {@link #weightedSlackTime(Set)} and sums over all
     * activities of an {@link EventActivityNetwork}.
     * @param ean the {@link EventActivityNetwork} to sum over.
     * @return sum the (duration-lowerBound)*passengers over all activities of
     * {@link EventActivityNetwork#getActivities()} for the ean parameter.
     */
    public static Double weightedSlackTime(EventActivityNetwork ean){
        return weightedSlackTime(ean.getActivities());
    }

    /**
     * Looks up the minimum over all change durations with more than zero (0.01)
     * passengers for a given {@link EventActivityNetwork}.
     * @param ean the given {@link EventActivityNetwork}.
     * @return the minimum over all change durations with more than zero (0.01)
     * passengers.
     */
    public static Double minimalUsedChangeDuration(EventActivityNetwork ean){
        Double retval = Double.MAX_VALUE;

        for(Activity a : ean.getChangeActivities()){
            if(Math.abs(a.getPassengers()) > MathHelper.epsilon){
                retval = Math.min(a.getDuration(), retval);
            }
        }

        return retval;

    }

    /**
     * Looks up the maximum over all change durations with more than zero (0.01)
     * passengers for a given {@link EventActivityNetwork}.
     * @param ean the given {@link EventActivityNetwork}.
     * @return the maximum over all change durations with more than zero (0.01)
     * passengers.
     */
    public static Double maximalUsedChangeDuration(EventActivityNetwork ean){
        Double retval = Double.MIN_VALUE;

        for(Activity a : ean.getChangeActivities()){
            if(Math.abs(a.getPassengers()) > MathHelper.epsilon){
                retval = Math.max(a.getDuration(), retval);
            }
        }

        return retval;

    }

}
