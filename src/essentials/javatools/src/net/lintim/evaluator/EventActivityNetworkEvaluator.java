package net.lintim.evaluator;

import net.lintim.exception.DataInconsistentException;
import net.lintim.generator.PeriodicTimetableGenerator;
import net.lintim.generator.PeriodicTimetableGenerator.CyclebaseModel;
import net.lintim.model.Activity;
import net.lintim.model.Event.EventType;
import net.lintim.model.EventActivityNetwork;
import net.lintim.util.MathHelper;

import java.util.Set;

/**
 * Evaluates different properties of an {@link EventActivityNetwork}. These
 * properties can concern the passenger distribution, but not the timetable;
 * therefore, see {@link PeriodicTimetableEvaluator}.
 */
public class EventActivityNetworkEvaluator {
    /**
     * Returns the logarithmic base 10 cyclebase length for a given
     * {@link EventActivityNetwork} obtained with the
     * {@link CyclebaseModel#MSF_FUNDAMENTAL_IMPROVEMENT} model and all
     * objective function relevant events included, i.e. all events with either
     * more than 0 passengers or spans less than the period length minus one.
     * @param ean the given {@link EventActivityNetwork}.
     * @return the logarithmic base 10 cyclebase length.
     */
    public static Double logarithmicBase10CyclebaseWidthObjective(
            EventActivityNetwork ean){
        // TODO make proper cyclebase computation methods
        PeriodicTimetableGenerator ptimgen = new PeriodicTimetableGenerator() {
            @Override
            protected void solveInternal() throws DataInconsistentException {
                // do nothing
            }
        };

        ptimgen.initialize(ean, null, null,
                CyclebaseModel.MSF_FUNDAMENTAL_IMPROVEMENT, false, false,
                1.0);

        return ptimgen.logarithmicBase10CyclebasisWidth();
    }

    /**
     * Returns the logarithmic base 10 cyclebase length for a given
     * {@link EventActivityNetwork} obtained with the
     * {@link CyclebaseModel#MSF_FUNDAMENTAL_IMPROVEMENT} model and all
     * feasibility events included, i.e. all events that span less than the
     * period length minus one.
     * @param ean the given {@link EventActivityNetwork}.
     * @return the logarithmic base 10 cyclebase length.
     */
    public static Double logarithmicBase10CyclebaseWidthFeasibility(
            EventActivityNetwork ean){
        // TODO make proper cyclebase computation methods
        PeriodicTimetableGenerator ptimgen = new PeriodicTimetableGenerator() {
            @Override
            protected void solveInternal() throws DataInconsistentException {
                // do nothing
            }
        };

        ptimgen.initialize(ean, null, null,
                CyclebaseModel.MSF_FUNDAMENTAL_IMPROVEMENT, false, false,
                0.0);

        return ptimgen.logarithmicBase10CyclebasisWidth();
    }

    /**
     * Sums up the passengers over a {@link Set} of {@link Activity}.
     * @param activities the activities to sum over.
     * @return the sum over the passengers using the given activities.
     */
    public static Double overallPassengerSum(Set<Activity> activities){
        Double retval = 0.0;

        for(Activity a : activities){
            retval += a.getPassengers();
        }

        return retval;
    }

    /**
     * Redirects to {@link #overallPassengerSum(Set)} and sums over all
     * activities of an {@link EventActivityNetwork}.
     * @param ean the {@link EventActivityNetwork} to sum over.
     * @return sum the passengers over all activities of
     * {@link EventActivityNetwork#getActivities()} for the ean parameter.
     */
    public static Double overallPassengerSum(EventActivityNetwork ean){
        return overallPassengerSum(ean.getActivities());
    }

    /**
     * Calculates a sum of initialDurationAssumption*passengers over all
     * actvities of an {@link EventActivityNetwork}. This is a "virtual average
     * traveling time" and can be used to estimate the average traveling time
     * if no timetable is available.
     * @param ean the {@link EventActivityNetwork} to sum over.
     * @return sum of initialDurationAssumption*passengers over all activities
     * of {@link EventActivityNetwork#getActivities()} for the ean parameter.
     */
    public static Double initialAverageTravelingTime(EventActivityNetwork ean) {
        Double retval = 0.0;
        Double passengers = 0.0;

        for(Activity a : ean.getActivities()){
            if(a.getInitialDurationAssumption() == null){
                continue;
            }
            passengers += a.getPassengers();
            retval += a.getInitialDurationAssumption()*a.getPassengers();
        }

        return retval/passengers;
    }

    /**
     * Calculates a sum of (initialDurationAssumption-lowerBound)*passengers
     * over all actvities of an {@link EventActivityNetwork}. This is a
     * "virtual average slack time" and can be used to estimate the average
     * slack time if no timetable is available.
     * @param ean the {@link EventActivityNetwork} to sum over.
     * @return sum of (initialDurationAssumption-lowerBound)*passengers over
     * all activities of {@link EventActivityNetwork#getActivities()} for the
     * ean parameter.
     */
    public static Double initialWeightedSlackTime(EventActivityNetwork ean) {
        Double retval = 0.0;

        for(Activity a : ean.getActivities()){
            if(a.getInitialDurationAssumption() == null){
                continue;
            }
            retval += (a.getInitialDurationAssumption()
                    -a.getLowerBound())*a.getPassengers();
        }

        return retval;
    }

    /**
     * Calculates a sum of initialDurationAssumption*passengers over all change
     * actvities of an {@link EventActivityNetwork}. This is a "virtual average
     * change time" and can be used to estimate the average change time
     * if no timetable is available.
     * @param ean the {@link EventActivityNetwork} to sum over.
     * @return sum of initialDurationAssumption*passengers over all change
     * activities of {@link EventActivityNetwork#getActivities()} for the ean
     * parameter.
     */
    public static Double initialAverageChangeTime(EventActivityNetwork ean) {
        Double retval = 0.0;

        for(Activity a : ean.getChangeActivities()){
            retval += a.getInitialDurationAssumption()*a.getPassengers();
        }

        return retval;

    }

    /**
     * Looks up the minimal duration over all activities with more than zero
     * (0.01) passengers for a given {@link EventActivityNetwork}.
     * @param ean the given {@link EventActivityNetwork}.
     * @return the minimal duration over all activities with more than zero
     * (0.01) passengers. Value of -1 means no change activities are used by passengers.
     */
    public static Double initialMinimalUsedChangeDuration(EventActivityNetwork ean) {
        Double retval = Double.MAX_VALUE;

        for(Activity a : ean.getChangeActivities()){
            if(Math.abs(a.getPassengers()) > MathHelper.epsilon){
                System.out.println(a.getInitialDurationAssumption());
				if(a.getInitialDurationAssumption() != null)
					retval = Math.min(a.getInitialDurationAssumption(), retval);
				else
					retval = Math.min(a.getLowerBound(),retval);
            }
        }
        if(retval == Double.MAX_VALUE){
            retval = -1.0;
        }

        return retval;

    }

    /**
     * Looks up the maximal duration over all activities with more than zero
     * (0.01) passengers for a given {@link EventActivityNetwork}.
     * @param ean the given {@link EventActivityNetwork}.
     * @return the maximal duration over all activities with more than zero
     * (0.01) passengers. Value of -1 means no change activities are used by passengers.
     */
    public static Double initialMaximalUsedChangeDuration(EventActivityNetwork ean) {
        Double retval = Double.MIN_VALUE;

        for(Activity a : ean.getChangeActivities()){
            if(Math.abs(a.getPassengers()) > MathHelper.epsilon){
                if(a.getInitialDurationAssumption() != null)
					retval = Math.max(a.getInitialDurationAssumption(), retval);
				else
					retval = Math.max(a.getLowerBound(),retval);
            }
        }
        if(retval == Double.MIN_VALUE){
            retval = -1.0;
        }

        return retval;

    }

    /**
     * Computes the number of all activities with more than zero (0.01)
     * passengers for a given {@link EventActivityNetwork}.
     * @param ean the given {@link EventActivityNetwork}.
     * @return the number of all activities with more than zero (0.01)
     * passengers.
     */
    public static Integer numberOfUsedActivites(EventActivityNetwork ean) {
        Integer count = 0;

        for(Activity a : ean.getActivities()){
            if(Math.abs(a.getPassengers()) > MathHelper.epsilon){
                count++;
            }
        }

        return count;

    }

    /**
     * Computes the number of all activities with either more than zero (0.01)
     * passengers or spans less than the period length for a given
     * {@link EventActivityNetwork}.
     * @param ean the given {@link EventActivityNetwork}.
     * @return the number of all activities with either more than zero (0.01)
     * passengers or span less than the period length minus one.
     * @throws DataInconsistentException if event activity network is not
     * periodic
     */
    public static Integer numberOfObjectiveActivities(EventActivityNetwork ean)
    throws DataInconsistentException {
        Integer count = 0;

        if(!ean.isPeriodic()){
            throw new DataInconsistentException("event activity network " +
                    "needs to be periodic to compute the number of " +
                    "model necessary activities");
        }

        double periodLength = Math.round(ean.getPeriodLength());

        for(Activity a : ean.getActivities()){
            if(Math.abs(a.getPassengers()) > MathHelper.epsilon ||
                    Math.round(a.getUpperBound()-a.getLowerBound()+1) <
                    periodLength){
                count++;
            }
        }

        return count;

    }

    /**
     * Computes the number of all activities that span less than the period
     * length minus one for a given {@link EventActivityNetwork}.
     * @param ean the given {@link EventActivityNetwork}.
     * @return the number of all activities that span less than the period
     * length minus one.
     * @throws DataInconsistentException if event activity network is not
     * periodic
     */
    public static Integer numberOfFeasibilityActivities(EventActivityNetwork ean)
    throws DataInconsistentException {
        Integer count = 0;

        if(!ean.isPeriodic()){
            throw new DataInconsistentException("event activity network " +
                    "needs to be periodic to compute the number of " +
                    "model necessary activities");
        }

        double periodLength = Math.round(ean.getPeriodLength());

        for(Activity a : ean.getActivities()){
            if(Math.round(a.getUpperBound()-a.getLowerBound()+1) < periodLength){
                count++;
            }
        }

        return count;

    }

    /**
     * Computes the number of all change activities with more than zero (0.01)
     * passengers for a given {@link EventActivityNetwork}.
     * @param ean the given {@link EventActivityNetwork}.
     * @return the number of all change activities with more than zero (0.01)
     * passengers.
     */
    public static Integer numberOfUsedChangeActivites(EventActivityNetwork ean) {
        Integer count = 0;

        for(Activity a : ean.getChangeActivities()){
            if(Math.abs(a.getPassengers()) > MathHelper.epsilon){
                count++;
            }
        }

        return count;

    }
    
        /**
     * Computes the number of all drive activities with more than zero (0.01)
     * passengers for a given {@link EventActivityNetwork}.
     * @param ean the given {@link EventActivityNetwork}.
     * @return the number of all drive activities with more than zero (0.01)
     * passengers.
     */
    public static Integer numberOfUsedDriveActivites(EventActivityNetwork ean) {
        Integer count = 0;

        for(Activity a : ean.getDriveActivities()){
            if(Math.abs(a.getPassengers()) > MathHelper.epsilon){
                count++;
            }
        }

        return count;

    }
    
        /**
     * Computes the number of all wait activities with more than zero (0.01)
     * passengers for a given {@link EventActivityNetwork}.
     * @param ean the given {@link EventActivityNetwork}.
     * @return the number of all wait activities with more than zero (0.01)
     * passengers.
     */
    public static Integer numberOfUsedWaitActivites(EventActivityNetwork ean) {
        Integer count = 0;

        for(Activity a : ean.getWaitActivities()){
            if(Math.abs(a.getPassengers()) > MathHelper.epsilon){
                count++;
            }
        }

        return count;

    }

    /**
     * Evaluates whether headways are only between departure events for a given
     * {@link EventActivityNetwork}
     * @param ean the given {@link EventActivityNetwork}.
     * @return true if headways are only between departure events and false
     * otherwise.
     */
    public static Boolean headwaysBetweenDeparturesOnly(EventActivityNetwork ean){
        for(Activity a : ean.getHeadwayActivities()){
            if(a.getFromEvent().getType() != EventType.DEPARTURE ||
                    a.getToEvent().getType() != EventType.DEPARTURE){
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluates whether headways are limited to one station or span over
     * several stations for a given {@link EventActivityNetwork}.
     * @param ean the given {@link EventActivityNetwork}.
     * @return true if there are headways that span over several stations and
     * false otherwise.
     */
    public static Boolean interstationHeadwaysExist(EventActivityNetwork ean){
        for(Activity a : ean.getHeadwayActivities()){
            if(a.getFromEvent().getStation() != a.getToEvent().getStation()){
                return true;
            }
        }
        return false;
    }

    /**
     * Computes a lower bound for a periodic event scheduling problem with
     * average traveling time as objective for a given
     * {@link EventActivityNetwork}, i.e. sum passengers times lower
     * bounds over all activities.
     * @param ean the given {@link EventActivityNetwork}.
     * @return the maximum over all change durations with more than zero (0.01)
     * passengers.
     */
    public static Double pespLowerBound(EventActivityNetwork ean){
        Double retval = 0.0;

        for(Activity a : ean.getActivities()){
            retval += a.getPassengers()*a.getLowerBound();
        }

        return retval;

    }
}
