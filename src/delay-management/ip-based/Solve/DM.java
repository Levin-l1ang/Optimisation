import net.lintim.util.Logger;

import java.io.*;
import java.util.*;


public class DM {

    private static final Logger logger = new Logger(DM.class);

    private DM() {
    } // class only contains static methods

    /**
     * Computes the exact solution of the capacitated delay management problem.
     * Assumptions:
     * <ul>
     * <li>event IDs are continous (and starting with 1),
     * <li>activity IDs are continous (and starting with 1),
     * <li>each pair of two headway activities directly follows each other in {@code activityFileName},
     * <li>all lower bounds are respected.
     * </ul>
     * The changes applied to the input data are:
     * <ul>
     * <li>The value of {@code x} of each event is updated to represent the time of each
     *     event in the disposition timetable.
     * <li>The value of {@code z} of each changing activity is updated to represent the
     *     actual wait-depart decision done in the optimization step.
     * <li>The value of {@code g} of each headway activity is updated to represent the
     *     actual order of events determined by the optimization step.
     * </ul>
     * If DM_debug is set, additional tests to check the validity of the
     * event-activity network (the input) and to check the validity of the solution are done.
     * The tests for the input data include:
     * <ul>
     * <li>check whether all event IDs are continous (and starting with 1),
     * <li>check whether all activity IDs are continous (and starting with 1),
     * <li>check whether each pair of two headway activities directly follows each other,
     * <li>check whether all lower bounds are respected in the original timetable,
     * <li>check whether the source and the target of each activity is contained in the event list,
     * <li>output some details of the preprocessing step (if applicable).
     * </ul>
     * If DM_verbose is set, verbose output is enabled.
     *
     * @param Net           the event-activity network for which the capacitated delay management problem
     *                      should be solved
     * @param preprocessing indicates whether a preprocessing step (to reduce the network)
     *                      should be done
     * @return the objective value
     * NOTE THAT THIS JAVADOC PROBABLY ISN'T UPTODATE
     */
    public static double solveDM2(NonPeriodicEANetwork Net,
                                  boolean preprocessing, Parameters parameters) throws Exception {
        Net.resetDispositionDecisions();

        if (parameters.shouldCheckConsistency()) {
            Net.checkConsistency();
            Net.checkTimetable();
        }

        // Note that we use a copy of the event-activity network to make sure
        // that A_nice is according to our needs.
        // For the exact solution, set A_nice := A_drive + A_wait + A_circ;
        // all other sets do not change.
        LinkedHashSet<NonPeriodicEvent> events =
            Net.getEvents();
        LinkedHashSet<NonPeriodicActivity> activities =
            Net.getActivities();
        LinkedHashSet<NonPeriodicActivity> A_drive =
            Net.getDrivingActivities();
        LinkedHashSet<NonPeriodicActivity> A_wait =
            Net.getWaitingActivities();
        LinkedHashSet<NonPeriodicActivity> A_circ =
            Net.getCirculationActivities();
        LinkedHashSet<NonPeriodicChangingActivity> A_change =
            Net.getChangingActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head =
            Net.getHeadwayActivities();

        int count = A_drive.size() + A_wait.size() + A_circ.size();
        LinkedHashSet<NonPeriodicActivity> A_nice =
            new LinkedHashSet<>(count);
        A_nice.addAll(A_drive);
        A_nice.addAll(A_wait);
        A_nice.addAll(A_circ);

        NonPeriodicEANetwork Net2 =
            new NonPeriodicEANetwork(events, activities, A_drive, A_wait,
                A_circ, A_nice, A_change, A_head,
                Net.getPeriod(), parameters.shouldCheckConsistency());

        // Solve the IP; do a preprocessing step if requested by the
        // caller. Note that we use M from the MIP formulation as an
        // upper bound on the delay of each event, see PhD thesis
        // Schachtebeck, Theorem 3.1.
        if (preprocessing)
            solve(preprocessing(Net2, parameters.shouldCheckConsistency()), true, parameters);
        else
            solve(Net2, true, parameters);

        // If the preprocessing step has been done, the IP was solved for the
        // reduced network, so we have to explicitly set z and g values for
        // activities deleted during preprocessing!
        if (preprocessing) {
            Net.setZ();
            Net.setG();
        }

        if (parameters.shouldCheckConsistency())
            Net.checkDispositionTimetable();

        double objectiveValueManual = computeObjectiveValueDM2(Net);
        logger.debug("DM: exact: objective value according to "
            + "manual computation: " + objectiveValueManual);
        return objectiveValueManual;
    }


    /**
     * Computes a heuristic solution of the capacitated delay management problem using
     * the &quot;First Scheduled, First Served&quot; heuristics.
     * For the assumptions made, see {@link DM#solveDM1(NonPeriodicEANetwork, Parameters)}.
     *
     * @param Net the event-activity network for which the capacitated delay management problem
     *            should be solved
     * @return the objective value
     */
    public static double solveFSFS(NonPeriodicEANetwork Net, Parameters parameters) throws Exception {
        Net.resetDispositionDecisions();

        if (parameters.shouldCheckConsistency()) {
            logger.debug("Checking Consistency");
            Net.checkConsistency();
            Net.checkTimetable();
        }

        // Note that we use a copy of the event-activity network to make sure
        // that A_nice is according to our needs.
        // For this heuristic, we set A_nice := A_drive + A_wait + A_circ +
        // + fixed headways and use an empty headway set.
        LinkedHashSet<NonPeriodicEvent> events =
            Net.getEvents();
        LinkedHashSet<NonPeriodicActivity> activities =
            Net.getActivities();
        LinkedHashSet<NonPeriodicActivity> A_drive =
            Net.getDrivingActivities();
        LinkedHashSet<NonPeriodicActivity> A_wait =
            Net.getWaitingActivities();
        LinkedHashSet<NonPeriodicActivity> A_circ =
            Net.getCirculationActivities();
        LinkedHashSet<NonPeriodicChangingActivity> A_change =
            Net.getChangingActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head =
            Net.getHeadwayActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head_new =
            new LinkedHashSet<>();

        int count = A_drive.size() + A_wait.size() + A_circ.size()
            + A_head.size() / 2;
        LinkedHashSet<NonPeriodicActivity> A_nice =
            new LinkedHashSet<>(count);
        A_nice.addAll(A_drive);
        A_nice.addAll(A_wait);
        A_nice.addAll(A_circ);

        logger.debug("Fixing Headway activities");
        fixHeadwayActivitiesFSFS(A_head, A_nice);

        NonPeriodicEANetwork Net2 =
            new NonPeriodicEANetwork(events, activities, A_drive, A_wait,
                A_circ, A_nice, A_change, A_head_new,
                Net.getPeriod(), parameters.shouldCheckConsistency());

        // Solve the IP; do not use M from MIP formulation as upper
        // bound on maximal delay as this only works for the optimal
        // solution.
        logger.debug("Computing solution with " + parameters.getOptMethod());
        if (parameters.getOptMethod().equals("DM2")) {
            solve(Net2, false, parameters);
        } else if (parameters.getOptMethod().equals("DM1")) {
            LinkedList<Path> passenger_paths = IO.readPassengerPaths(Net2);
            Solve.solveDM1(Net2, passenger_paths, parameters);
        }


        if (parameters.shouldCheckConsistency()) {
            logger.debug("Checking consistency again...");
            Net.checkDispositionTimetable();
        }

        logger.debug("Computing objective value...");
        long objectiveValueManual = -1;
        if (parameters.getOptMethod().equals("DM2")) {
            objectiveValueManual = computeObjectiveValueDM2(Net);
        } else if (parameters.getOptMethod().equals("DM1")) {
            objectiveValueManual = computeObjectiveValueDM1(Net, parameters.getPathsFileName());
        }
        logger.debug("DM: FSFS: objective value according to "
            + "manual computation: " + objectiveValueManual);
        return objectiveValueManual;
    }


    public static double solveFRFS(NonPeriodicEANetwork Net, Parameters parameters) throws Exception {
        Net.resetDispositionDecisions();

        if (parameters.shouldCheckConsistency()) {
            Net.checkConsistency();
            Net.checkTimetable();
        }

        // Note that we use a copy of the event-activity network to make sure
        // that A_nice is according to our needs.
        // For the first step, we set A_nice := A_drive + A_wait + A_circ
        // and use an empty set A_head.
        LinkedHashSet<NonPeriodicEvent> events =
            Net.getEvents();
        LinkedHashSet<NonPeriodicActivity> activities =
            Net.getActivities();
        LinkedHashSet<NonPeriodicActivity> A_drive =
            Net.getDrivingActivities();
        LinkedHashSet<NonPeriodicActivity> A_wait =
            Net.getWaitingActivities();
        LinkedHashSet<NonPeriodicActivity> A_circ =
            Net.getCirculationActivities();
        LinkedHashSet<NonPeriodicChangingActivity> A_change =
            Net.getChangingActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head =
            Net.getHeadwayActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head_new =
            new LinkedHashSet<>();

        int count = A_drive.size() + A_wait.size() + A_circ.size()
            + A_head.size() / 2;
        LinkedHashSet<NonPeriodicActivity> A_nice =
            new LinkedHashSet<>(count);
        A_nice.addAll(A_drive);
        A_nice.addAll(A_wait);
        A_nice.addAll(A_circ);

        NonPeriodicEANetwork Net2 =
            new NonPeriodicEANetwork(events, activities, A_drive, A_wait,
                A_circ, A_nice, A_change, A_head_new,
                Net.getPeriod(), parameters.shouldCheckConsistency());

        // Solve the MIP; do not use M from MIP formulation as upper
        // bound on the maximal delay as this only works for the optimal
        // solution.
        if (parameters.getOptMethod().equals("DM2")) {
            solve(Net2, false, parameters);
        } else if (parameters.getOptMethod().equals("DM1")) {
            LinkedList<Path> passenger_paths = IO.readPassengerPaths(Net2);
            Solve.solveDM1(Net2, passenger_paths, parameters);
        }

        // Use the solution of the uncapacitated problem to fix the headway
        // activities. Note that due to the constructor of NonPeriodicEANetwork
        // used above, changes to A_nice will directly apply to Net2, too
        // (otherwise, we would have to construct a new NonPeriodicEANetwork
        // with the modified set A_nice after running fixHeadwayActivitiesFRFS
        // and use this new NonPeriodicEANetwork later on).
        fixHeadwayActivitiesFRFS(A_head, A_nice);

        // After the headway activities are fixed, we have to invalidate the
        // solution (to enable consistency checking after the next optimization
        // step). However, this requires to manually set the values of the g
        // variables after the optimization.
        Net.resetDispositionDecisions();

        // solve the MIP with the modified set A_nice (and with an still
        // empty set A_head)
        if (parameters.getOptMethod().equals("DM2")) {
            solve(Net2, false, parameters);
        } else if (parameters.getOptMethod().equals("DM1")) {
            LinkedList<Path> passenger_paths = IO.readPassengerPaths(Net2);
            Solve.solveDM1(Net2, passenger_paths, parameters);
        }

        // as we cleared all variables after the first step, we have
        // to set the g variables correctly
        Net.setG();

        if (parameters.shouldCheckConsistency())
            Net.checkDispositionTimetable();

        long objectiveValueManual = -1;
        if (parameters.getOptMethod().equals("DM2")) {
            objectiveValueManual = computeObjectiveValueDM2(Net);
        } else if (parameters.getOptMethod().equals("DM1")) {
            objectiveValueManual = computeObjectiveValueDM1(Net, parameters.getPathsFileName());
        }
        logger.debug("DM: FRFS: objective value according to "
            + "manual computation:   " + objectiveValueManual);
        return objectiveValueManual;
    }


    public static double solveEARLYFIX(NonPeriodicEANetwork Net, Parameters parameters) throws Exception {
        Net.resetDispositionDecisions();

        if (parameters.shouldCheckConsistency()) {
            Net.checkConsistency();
            Net.checkTimetable();
        }

        // Note that we use a copy of the event-activity network to make sure
        // that A_nice is according to our needs.
        // For the first step, we set A_nice := A_drive + A_wait + A_circ
        // and use an empty set A_head.
        LinkedHashSet<NonPeriodicEvent> events =
            new LinkedHashSet<>(Net.getEvents());
        LinkedHashSet<NonPeriodicActivity> activities =
            new LinkedHashSet<>(Net.getActivities());
        LinkedHashSet<NonPeriodicActivity> A_drive =
            new LinkedHashSet<>(Net.getDrivingActivities());
        LinkedHashSet<NonPeriodicActivity> A_wait =
            new LinkedHashSet<>(Net.getWaitingActivities());
        LinkedHashSet<NonPeriodicActivity> A_circ =
            new LinkedHashSet<>(Net.getCirculationActivities());
        LinkedHashSet<NonPeriodicChangingActivity> A_change =
            new LinkedHashSet<>(Net.getChangingActivities());
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head =
            Net.getHeadwayActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head_new =
            new LinkedHashSet<>();

        int count = A_drive.size() + A_wait.size() + A_circ.size()
            + A_head.size() / 2 + A_change.size();
        LinkedHashSet<NonPeriodicActivity> A_nice =
            new LinkedHashSet<>(count);
        A_nice.addAll(A_drive);
        A_nice.addAll(A_wait);
        A_nice.addAll(A_circ);

        NonPeriodicEANetwork Net2 =
            new NonPeriodicEANetwork(events, activities, A_drive, A_wait,
                A_circ, A_nice, A_change, A_head_new,
                Net.getPeriod(), parameters.shouldCheckConsistency());

        // Solve the MIP, do not use M from MIP formulation as upper
        // bound on the maximal delay as this only works for the optimal
        // solution.
        if (parameters.getOptMethod().equals("DM2")) {
            solve(Net2, false, parameters);
        } else if (parameters.getOptMethod().equals("DM1")) {
            LinkedList<Path> passenger_paths = IO.readPassengerPaths(Net2);
            Solve.solveDM1(Net2, passenger_paths, parameters);
        }

        // Use the solution of the uncapacitated problem to fix the headway
        // activities.
        fixHeadwayActivitiesFRFS(A_head, A_nice);

        // use the solution of the uncapacitated problem to fix the
        // connections
        for (NonPeriodicChangingActivity a : A_change)
            if (a.getZ() == 0)
                A_nice.add(a);

        // After the headway activities are fixed, we have to invalidate the
        // solution (to enable consistency checking after the next optimization
        // step). However, this requires to manually set the values of the z
        // and g variables after the optimization.
        Net.resetDispositionDecisions();

        // Note that due to the constructor of NonPeriodicEANetwork/ used
        // above, changes to A_nice and A_change will directly apply to Net2,
        // too (otherwise, we would have to construct a new NonPeriodicEANetwork
        // with the modified set A_nice instead of calling A_change.clear()).
        A_change.clear();

        // Solve the MIP with empty set A_change (and still empty set A_head) -
        // this basically is an application of the critical path method.
        // Do not use M from MIP formulation as upper bound on the maximal
        // delay as this only works for the optimal solution!
        if (parameters.getOptMethod().equals("DM2")) {
            solve(Net2, false, parameters);
        } else if (parameters.getOptMethod().equals("DM1")) {
            LinkedList<Path> passenger_paths = IO.readPassengerPaths(Net2);
            Solve.solveDM1(Net2, passenger_paths, parameters);
        }

        // As A_change was empty during the optimization process, we have to
        // manually set the values of the z variables according to the
        // disposition timetable. The same holds for the g variables.
        Net.setZ();
        Net.setG();

        if (parameters.shouldCheckConsistency())
            Net.checkDispositionTimetable();

        long objectiveValueManual = -1;
        if (parameters.getOptMethod().equals("DM2")) {
            objectiveValueManual = computeObjectiveValueDM2(Net);
        } else if (parameters.getOptMethod().equals("DM1")) {
            objectiveValueManual = computeObjectiveValueDM1(Net, parameters.getPathsFileName());
        }
        logger.debug("DM: EARLYFIX: objective value according to "
            + "manual computation:   " + objectiveValueManual);
        return objectiveValueManual;
    }


    public static double solvePRIORITY(NonPeriodicEANetwork Net, Parameters parameters) throws Exception {
        Net.resetDispositionDecisions();

        if (parameters.shouldCheckConsistency()) {
            Net.checkConsistency();
            Net.checkTimetable();
        }

        // Work with a copy of the event-activity network to make sure
        // that A_nice, A_head, and A_change is according to our needs.
        // For this heuristic, set A_nice := A_drive + A_wait + A_circ +
        // + fixed connections + fixed headways, use an empty connection
        // set and an empty headway set.
        LinkedHashSet<NonPeriodicEvent> events =
            Net.getEvents();
        LinkedHashSet<NonPeriodicActivity> activities =
            Net.getActivities();
        LinkedHashSet<NonPeriodicActivity> A_drive =
            Net.getDrivingActivities();
        LinkedHashSet<NonPeriodicActivity> A_wait =
            Net.getWaitingActivities();
        LinkedHashSet<NonPeriodicActivity> A_circ =
            Net.getCirculationActivities();
        LinkedHashSet<NonPeriodicChangingActivity> A_change =
            Net.getChangingActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head =
            Net.getHeadwayActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head_new =
            new LinkedHashSet<>();

        int count = A_drive.size() + A_wait.size() + A_circ.size()
            + A_head.size() / 2
            + Math.round(A_change.size() * parameters.getPercentage() / 100.0f);
        LinkedHashSet<NonPeriodicActivity> A_nice =
            new LinkedHashSet<>(count);
        A_nice.addAll(A_drive);
        A_nice.addAll(A_wait);
        A_nice.addAll(A_circ);

        ArrayList<NonPeriodicChangingActivity> A_change_sorted =
            new ArrayList<>(A_change);
        A_change_sorted.sort(new NonPeriodicChangingActivityComparator());
        for (int i = 1; i <= Math.round(A_change_sorted.size() * parameters.getPercentage() / 100.0f); i++) {
            // Collections.sort sorts in ascending order,
            // so we have to access the last elements
            A_nice.add(A_change_sorted.get(A_change.size() - i));
        }

        fixHeadwayActivitiesFSFS(A_head, A_nice);

        // Error corrected: A_change_new (empty) -> A_change (contains all change activities)
        NonPeriodicEANetwork Net2 =
            new NonPeriodicEANetwork(events, activities, A_drive, A_wait,
                A_circ, A_nice, A_change, A_head_new,
                Net.getPeriod(), parameters.shouldCheckConsistency());

        // solve the MIP, do not use M from MIP formulation as upper
        // bound on the maximal delay as this only works for the optimal
        // solution
        if (parameters.getOptMethod().equals("DM2")) {
            solve(Net2, false, parameters);
        } else if (parameters.getOptMethod().equals("DM1")) {
            LinkedList<Path> passenger_paths = IO.readPassengerPaths(Net2);
            Solve.solveDM1(Net2, passenger_paths, parameters);
        }

        // as A_change was empty during the optimization process, we have to
        // manually set the values of the z variables according to the
        // disposition timetable
        Net.setZ();

        if (parameters.shouldCheckConsistency())
            Net.checkDispositionTimetable();

        long objectiveValueManual = -1;
        if (parameters.getOptMethod().equals("DM2")) {
            objectiveValueManual = computeObjectiveValueDM2(Net);
        } else if (parameters.getOptMethod().equals("DM1")) {
            objectiveValueManual = computeObjectiveValueDM1(Net, parameters.getPathsFileName());
        }
        logger.debug("DM: PRIORITY: objective value according to "
            + "manual computation:   " + objectiveValueManual);

        return objectiveValueManual;
    }


    public static double solvePRIOREPAIR(NonPeriodicEANetwork Net, int priority, Parameters parameters) throws Exception {

        Net.resetDispositionDecisions();

        if (parameters.shouldCheckConsistency()) {
            Net.checkConsistency();
            Net.checkTimetable();
        }

        // Work with a copy of the event-activity network to make sure
        // that A_nice, A_head, and A_change is according to our needs.
        // In the first step, set A_nice := A_drive + A_wait + A_circ +
        // + fixed connections, use an empty connection set and an empty
        // headway set.
        LinkedHashSet<NonPeriodicEvent> events =
            Net.getEvents();
        LinkedHashSet<NonPeriodicActivity> activities =
            Net.getActivities();
        LinkedHashSet<NonPeriodicActivity> A_drive =
            Net.getDrivingActivities();
        LinkedHashSet<NonPeriodicActivity> A_wait =
            Net.getWaitingActivities();
        LinkedHashSet<NonPeriodicActivity> A_circ =
            Net.getCirculationActivities();
        LinkedHashSet<NonPeriodicChangingActivity> A_change =
            Net.getChangingActivities();
        LinkedHashSet<NonPeriodicChangingActivity> A_change_new =
            new LinkedHashSet<>();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head =
            Net.getHeadwayActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head_new =
            new LinkedHashSet<>();

        int count = A_drive.size() + A_wait.size() + A_circ.size()
            + Math.round(A_change.size() * priority / 100.0f);
        LinkedHashSet<NonPeriodicActivity> A_nice =
            new LinkedHashSet<>(count);
        A_nice.addAll(A_drive);
        A_nice.addAll(A_wait);
        A_nice.addAll(A_circ);

        ArrayList<NonPeriodicChangingActivity> A_change_sorted =
            new ArrayList<>(A_change);
        A_change_sorted.sort(new NonPeriodicChangingActivityComparator());
        for (int i = 1; i <= Math.round(A_change_sorted.size() * priority / 100.0f); i++) {
            // Collections.sort sorts in ascending order,
            // so we have to access the last elements
            A_nice.add(A_change_sorted.get(A_change.size() - i));
        }

        NonPeriodicEANetwork Net2 =
            new NonPeriodicEANetwork(events, activities, A_drive, A_wait,
                A_circ, A_nice, A_change_new, A_head_new,
                Net.getPeriod(), parameters.shouldCheckConsistency());

        // solve the MIP, do not use M from MIP formulation as upper
        // bound on the maximal delay as this only works for the optimal
        // solution
        if (parameters.getOptMethod().equals("DM2")) {
            solve(Net2, false, parameters);
        } else if (parameters.getOptMethod().equals("DM1")) {
            LinkedList<Path> passenger_paths = IO.readPassengerPaths(Net2);
            Solve.solveDM1(Net2, passenger_paths, parameters);
        }

        // Use the solution of the uncapacitated problem to fix the headway
        // activities. Note that due to the constructor of NonPeriodicEANetwork
        // used above, changes to A_nice will directly apply to Net2, too
        // (otherwise, we would have to construct a new NonPeriodicEANetwork
        // with the modified set A_nice afterwards).
        fixHeadwayActivitiesFRFS(A_head, A_nice);

        // After the headway activities are fixed, we have to invalidate the
        // solution (to enable consistency checking after the next optimization
        // step). However, this requires to manually set the values of the g
        // variables after the optimization.
        Net.resetDispositionDecisions();

        // solve the MIP with A_nice set according to the definition of
        // the heuristic (and with an still empty set A_head)
        if (parameters.getOptMethod().equals("DM2")) {
            solve(Net2, false, parameters);
        } else if (parameters.getOptMethod().equals("DM1")) {
            LinkedList<Path> passenger_paths = IO.readPassengerPaths(Net2);
            Solve.solveDM1(Net2, passenger_paths, parameters);
        }

        // as we cleared all variables after the first step, we have
        // to set the g and z variables correctly
        Net.setG();
        Net.setZ();

        if (parameters.shouldCheckConsistency())
            Net.checkDispositionTimetable();

        long objectiveValueManual = -1;
        if (parameters.getOptMethod().equals("DM2")) {
            objectiveValueManual = computeObjectiveValueDM2(Net);
        } else if (parameters.getOptMethod().equals("DM1")) {
            objectiveValueManual = computeObjectiveValueDM1(Net, parameters.getPathsFileName());
        }
        logger.debug("DM: PRIOREPAIR: objective value according to "
            + "manual computation:   " + objectiveValueManual);

        return objectiveValueManual;
    }

    /**
     * Computes an optimal solution for the DM1 program.
     *
     * @param Net The EANetwork for which the solution should be computed
     * @return The DM1 objective value
     * @throws Exception for reading the config file
     */
    public static double solveDM1(NonPeriodicEANetwork Net, Parameters parameters) throws Exception {
        Net.resetDispositionDecisions();

        if (parameters.shouldCheckConsistency()) {
            Net.checkConsistency();
            Net.checkTimetable();
        }

        // Note that we use a copy of the event-activity network to make sure
        // that A_nice is according to our needs.
        // For the exact solution, set A_nice := A_drive + A_wait + A_circ;
        // all other sets do not change.
        LinkedHashSet<NonPeriodicEvent> events =
            Net.getEvents();
        LinkedHashSet<NonPeriodicActivity> activities =
            Net.getActivities();
        LinkedHashSet<NonPeriodicActivity> A_drive =
            Net.getDrivingActivities();
        LinkedHashSet<NonPeriodicActivity> A_wait =
            Net.getWaitingActivities();
        LinkedHashSet<NonPeriodicActivity> A_circ =
            Net.getCirculationActivities();
        LinkedHashSet<NonPeriodicChangingActivity> A_change =
            Net.getChangingActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head =
            Net.getHeadwayActivities();

        int count = A_drive.size() + A_wait.size() + A_circ.size();
        LinkedHashSet<NonPeriodicActivity> A_nice =
            new LinkedHashSet<>(count);
        A_nice.addAll(A_drive);
        A_nice.addAll(A_wait);
        A_nice.addAll(A_circ);

        NonPeriodicEANetwork Net2 =
            new NonPeriodicEANetwork(events, activities, A_drive, A_wait,
                A_circ, A_nice, A_change, A_head,
                Net.getPeriod(), parameters.shouldCheckConsistency());

        //Read the passenger paths

        LinkedList<Path> passenger_paths = IO.readPassengerPaths(Net2);

        Solve.solveDM1(Net2, passenger_paths, parameters);

        if (parameters.shouldCheckConsistency()) {
            Net.checkDispositionTimetable();
        }

        double objectiveValueManual = computeObjectiveValueDM2(Net);
        logger.debug("DM1: exact: objective value according to "
            + "manual computation: " + objectiveValueManual);
        return objectiveValueManual;

    }

    /**
     * Solves the PASSENGERPRIOFIX method for the EAN. Fixes the headways of the first uncontradicted "percentage" paths sorted by weight.
     * After that uses the dm1 optimization for finding a solution to the reduced problem
     *
     * @param Net the NonPeriodicEAN
     * @return the dm1 objective value
     * @throws Exception for reading the config file
     */
    public static double solvePASSENGERPRIOFIX(NonPeriodicEANetwork Net, Parameters parameters) throws Exception {
        Net.resetDispositionDecisions();

        if (parameters.shouldCheckConsistency()) {
            Net.checkConsistency();
            Net.checkTimetable();
        }

        // Note that we use a copy of the event-activity network to make sure
        // that A_nice is according to our needs.
        // For the exact solution, set A_nice := A_drive + A_wait + A_circ;
        // all other sets do not change.
        LinkedHashSet<NonPeriodicEvent> events =
            Net.getEvents();
        LinkedHashSet<NonPeriodicActivity> activities =
            Net.getActivities();
        LinkedHashSet<NonPeriodicActivity> A_drive =
            Net.getDrivingActivities();
        LinkedHashSet<NonPeriodicActivity> A_wait =
            Net.getWaitingActivities();
        LinkedHashSet<NonPeriodicActivity> A_circ =
            Net.getCirculationActivities();
        LinkedHashSet<NonPeriodicChangingActivity> A_change =
            Net.getChangingActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head =
            Net.getHeadwayActivities();

        //Find the nice activities and add them to A_nice
        int count = A_drive.size() + A_wait.size() + A_circ.size();
        LinkedHashSet<NonPeriodicActivity> A_nice =
            new LinkedHashSet<>(count);
        A_nice.addAll(A_drive);
        A_nice.addAll(A_wait);
        A_nice.addAll(A_circ);

        //Read the passenger paths

        LinkedList<Path> passenger_paths = IO.readPassengerPaths(Net);

        fixHeadwayActivitiesPASSENGERPRIOFIX(Net, A_head, A_nice, passenger_paths, parameters.getPercentage());

        //Construct the new EAN to work with
        NonPeriodicEANetwork Net2 =
            new NonPeriodicEANetwork(events, activities, A_drive, A_wait,
                A_circ, A_nice, A_change, A_head,
                Net.getPeriod(), parameters.shouldCheckConsistency());

        Solve.solveDM1(Net2, passenger_paths, parameters);

        if (parameters.shouldCheckConsistency()) {
            Net.checkDispositionTimetable();
        }

        double objectiveValueManual = computeObjectiveValueDM1(Net, parameters.getPathsFileName());
        logger.debug("DM1: exact: objective value according to "
            + "manual computation: " + objectiveValueManual);

        return objectiveValueManual;
    }

    /**
     * Uses an IP to fix uncontradicted headways of paths with the most summed weight. After that uses the dm1 optimization for finding
     * a solution to the reduced problem
     *
     * @param Net the EAN to work with
     * @return the dm1 objective value of the found solution
     * @throws Exception for reading the config file
     */
    public static double solvePASSENGERFIX(NonPeriodicEANetwork Net, Parameters parameters) throws Exception {
        Net.resetDispositionDecisions();

        if (parameters.shouldCheckConsistency()) {
            Net.checkConsistency();
            Net.checkTimetable();
        }

        // Note that we use a copy of the event-activity network to make sure
        // that A_nice is according to our needs.
        // For the exact solution, set A_nice := A_drive + A_wait + A_circ;
        // all other sets do not change.
        LinkedHashSet<NonPeriodicEvent> events =
            Net.getEvents();
        LinkedHashSet<NonPeriodicActivity> activities =
            Net.getActivities();
        LinkedHashSet<NonPeriodicActivity> A_drive =
            Net.getDrivingActivities();
        LinkedHashSet<NonPeriodicActivity> A_wait =
            Net.getWaitingActivities();
        LinkedHashSet<NonPeriodicActivity> A_circ =
            Net.getCirculationActivities();
        LinkedHashSet<NonPeriodicChangingActivity> A_change =
            Net.getChangingActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head =
            Net.getHeadwayActivities();

        //Find the nice activities and add them to A_nice
        int count = A_drive.size() + A_wait.size() + A_circ.size();
        LinkedHashSet<NonPeriodicActivity> A_nice =
            new LinkedHashSet<>(count);
        A_nice.addAll(A_drive);
        A_nice.addAll(A_wait);
        A_nice.addAll(A_circ);

        //Read the passenger paths
        LinkedList<Path> passenger_paths = IO.readPassengerPaths(Net);

        Solve.fixPassengerHeadways(Net, A_nice, A_head, passenger_paths, parameters);

        //Construct a new EAN to work with
        NonPeriodicEANetwork Net2 =
            new NonPeriodicEANetwork(events, activities, A_drive, A_wait,
                A_circ, A_nice, A_change, A_head,
                Net.getPeriod(), parameters.shouldCheckConsistency());

        Solve.solveDM1(Net2, passenger_paths, parameters);

        if (parameters.shouldCheckConsistency()) {
            Net.checkDispositionTimetable();
        }
        //Manually calculate the objective of the found solution
        double objectiveValueManual = computeObjectiveValueDM1(Net, parameters.getPathsFileName());
        logger.debug("DM1: exact: objective value according to "
            + "manual computation: " + objectiveValueManual);
        return objectiveValueManual;
    }

    /**
     * Uses PASSENGERPRIOFIX to fix the maximal amount of headways on passengerpaths and fixes the remaining
     * headways with FRFS. After that, uses DM1 to find a solution to the reduced problem
     *
     * @param Net the EAN to work with
     * @return the dm1 objective value
     * @throws Exception for reading the config file
     */
    public static double solveFIXFRFS(NonPeriodicEANetwork Net, Parameters parameters) throws Exception {
        Net.resetDispositionDecisions();

        if (parameters.shouldCheckConsistency()) {
            Net.checkConsistency();
            Net.checkTimetable();
        }

        // Note that we use a copy of the event-activity network to make sure
        // that A_nice is according to our needs.
        // For the exact solution, set A_nice := A_drive + A_wait + A_circ;
        // all other sets do not change.
        LinkedHashSet<NonPeriodicEvent> events =
            Net.getEvents();
        LinkedHashSet<NonPeriodicActivity> activities =
            Net.getActivities();
        LinkedHashSet<NonPeriodicActivity> A_drive =
            Net.getDrivingActivities();
        LinkedHashSet<NonPeriodicActivity> A_wait =
            Net.getWaitingActivities();
        LinkedHashSet<NonPeriodicActivity> A_circ =
            Net.getCirculationActivities();
        LinkedHashSet<NonPeriodicChangingActivity> A_change =
            Net.getChangingActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head =
            Net.getHeadwayActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head_new =
            new LinkedHashSet<>();

        int count = A_drive.size() + A_wait.size() + A_circ.size();
        LinkedHashSet<NonPeriodicActivity> A_nice =
            new LinkedHashSet<>(count);
        A_nice.addAll(A_drive);
        A_nice.addAll(A_wait);
        A_nice.addAll(A_circ);

        //Read the passenger paths

        LinkedList<Path> passenger_paths = IO.readPassengerPaths(Net);
        fixHeadwayActivitiesPASSENGERPRIOFIX(Net, A_head, A_nice, passenger_paths, 100);
        // Use the solution of the uncapacitated problem to fix the remaining
        // headway activities.
        fixHeadwayActivitiesFRFS(A_head, A_nice);

        //Construct a new EAN to work with
        NonPeriodicEANetwork Net2 =
            new NonPeriodicEANetwork(events, activities, A_drive, A_wait,
                A_circ, A_nice, A_change, A_head_new,
                Net.getPeriod(), parameters.shouldCheckConsistency());

        Solve.solveDM1(Net2, passenger_paths, parameters);

        if (parameters.shouldCheckConsistency()) {
            Net.checkDispositionTimetable();
        }

        double objectiveValueManual = computeObjectiveValueDM1(Net, parameters.getPathsFileName());
        logger.debug("DM1: exact: objective value according to "
            + "manual computation: " + objectiveValueManual);
        return objectiveValueManual;
    }

    /**
     * Uses PASSENGERPRIOFIX to fix the maximal amount of headways on passengerpaths and fixes the remaining
     * headways with FSFS. After that, uses DM1 to find a solution to the reduced problem
     *
     * @param Net the EAN to work with
     * @return the dm1 objective value
     * @throws Exception for reading the config file
     */
    public static double solveFIXFSFS(NonPeriodicEANetwork Net, Parameters parameters) throws Exception {
        Net.resetDispositionDecisions();

        if (parameters.shouldCheckConsistency()) {
            Net.checkConsistency();
            Net.checkTimetable();
        }

        // Note that we use a copy of the event-activity network to make sure
        // that A_nice is according to our needs.
        // For the exact solution, set A_nice := A_drive + A_wait + A_circ;
        // all other sets do not change.
        LinkedHashSet<NonPeriodicEvent> events =
            Net.getEvents();
        LinkedHashSet<NonPeriodicActivity> activities =
            Net.getActivities();
        LinkedHashSet<NonPeriodicActivity> A_drive =
            Net.getDrivingActivities();
        LinkedHashSet<NonPeriodicActivity> A_wait =
            Net.getWaitingActivities();
        LinkedHashSet<NonPeriodicActivity> A_circ =
            Net.getCirculationActivities();
        LinkedHashSet<NonPeriodicChangingActivity> A_change =
            Net.getChangingActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head =
            Net.getHeadwayActivities();
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head_new =
            new LinkedHashSet<>();

        int count = A_drive.size() + A_wait.size() + A_circ.size();
        LinkedHashSet<NonPeriodicActivity> A_nice =
            new LinkedHashSet<>(count);
        A_nice.addAll(A_drive);
        A_nice.addAll(A_wait);
        A_nice.addAll(A_circ);

        //Read the passenger paths
        LinkedList<Path> passenger_paths = IO.readPassengerPaths(Net);
        fixHeadwayActivitiesPASSENGERPRIOFIX(Net, A_head, A_nice, passenger_paths, 100);
        fixHeadwayActivitiesFSFS(A_head, A_nice);

        //Construct a new EAN to work with
        NonPeriodicEANetwork Net2 =
            new NonPeriodicEANetwork(events, activities, A_drive, A_wait,
                A_circ, A_nice, A_change, A_head_new,
                Net.getPeriod(), parameters.shouldCheckConsistency());

        Solve.solveDM1(Net2, passenger_paths, parameters);

        if (parameters.shouldCheckConsistency()) {
            Net.checkDispositionTimetable();
        }

        double objectiveValueManual = computeObjectiveValueDM1(Net, parameters.getPathsFileName());
        logger.debug("DM1: exact: objective value according to "
            + "manual computation: " + objectiveValueManual);
        return objectiveValueManual;
    }


    private static void solve(NonPeriodicEANetwork Net,
                              boolean MAsUpperBound, Parameters parameters) throws Exception {
        /*************************************************
         *                   ATTENTION                   *
         *                  ===========                  *
         *                                               *
         * Set M heuristically. For the "correct" value, *
         * see PhD thesis Schachtebeck, Corollary 3.2.   *
         * Note that a smaller value can reduce the time *
         * needed to solve the MIP significantly!        *
         *************************************************/
        int M = 2 * (parameters.getLatestTime() - parameters.getEarliestTime());

        long start = new Date().getTime();
        Solve.solve(Net, M, MAsUpperBound, parameters);
        logger.debug("time for solving MIP: "
            + (new Date().getTime() - start) + "ms");
    }


    private static void fixHeadwayActivitiesFSFS(LinkedHashSet<NonPeriodicHeadwayActivity> A_head,
                                                 LinkedHashSet<NonPeriodicActivity> A_nice) {
        for (NonPeriodicHeadwayActivity a1 : A_head) {
            NonPeriodicHeadwayActivity a2 = a1.getCorrespodingHeadway();

            if (a1.getSource().getTime() < a1.getTarget().getTime()) {
                a1.setG(0);
                a2.setG(1);
                A_nice.add(a1);
            } else {
                a1.setG(1);
                a2.setG(0);
                A_nice.add(a2);
            }
        }
    }


    private static void fixHeadwayActivitiesFRFS(LinkedHashSet<NonPeriodicHeadwayActivity> A_head,
                                                 LinkedHashSet<NonPeriodicActivity> A_nice) {
        for (NonPeriodicHeadwayActivity a1 : A_head) {
            NonPeriodicHeadwayActivity a2 = a1.getCorrespodingHeadway();
            NonPeriodicEvent e1 = a1.getSource();
            NonPeriodicEvent e2 = a1.getTarget();
            int dispoTime1 = e1.getDispoTime();
            int dispoTime2 = e2.getDispoTime();

            if (dispoTime1 < dispoTime2
                || (dispoTime1 == dispoTime2 && e1.getTime() < e2.getTime())) {
                a1.setG(0);
                a2.setG(1);
                A_nice.add(a1);
            } else {
                a1.setG(1);
                a2.setG(0);
                A_nice.add(a2);
            }
        }
    }

    private static void fixHeadwayActivitiesPASSENGERPRIOFIX(NonPeriodicEANetwork Net, LinkedHashSet<NonPeriodicHeadwayActivity> A_head,
                                                             LinkedHashSet<NonPeriodicActivity> A_nice, LinkedList<Path> passenger_paths, int percentage) {
        //Iterate over the passenger paths and fix the headways on the first "percentage" percent of paths but only if they do not contradict each other
        ArrayList<Path> passenger_paths_sorted = new ArrayList<>(passenger_paths);
        passenger_paths_sorted.sort(new PathComparator());
        //The passenger paths are now in ascending order, iterate over them, begining at the heighest weight
        int number_of_paths = passenger_paths.size();
        int step = 0;
        Path path;
        LinkedList<NonPeriodicHeadwayActivity> headways;
        boolean path_can_be_fulfilled;
        NonPeriodicHeadwayActivity corresponding_headway;
        System.err.print("Fixing headways with PASSENGERPRIOFIX... ");
        for (int index = 1; index <= number_of_paths && step < (double) number_of_paths * percentage / 100; index++) {
            path = passenger_paths_sorted.get(number_of_paths - index);
            headways = Net.getHeadwaysOnTrip(path.getSource(), path.getTarget());
            path_can_be_fulfilled = true;
            for (NonPeriodicHeadwayActivity headway : headways) {
                if (headway.getG() == 1) {
                    //There is a headway on this path, which is already not fulfilled. Dismiss this path
                    path_can_be_fulfilled = false;
                    break;
                }
            }
            if (!path_can_be_fulfilled) {
                continue;
            } else {
                for (NonPeriodicHeadwayActivity headway : headways) {
                    corresponding_headway = headway.getCorrespodingHeadway();
                    headway.setG(0);
                    corresponding_headway.setG(1);
                    A_nice.add(headway);
                    A_head.remove(headway);
                    A_head.remove(corresponding_headway);
                }
                step++;
            }
        }
        System.err.println("Done!");
    }


    static long computeObjectiveValueDM2(NonPeriodicEANetwork Net) {
        long objectiveValue = 0;
        for (NonPeriodicEvent e : Net.getEvents())
            if (e.isArrivalEvent()) {
                objectiveValue += e.getWeight() * (e.getDispoTime() - e.getTime());
            }

        for (NonPeriodicChangingActivity a : Net.getChangingActivities())
            if (a.getTarget().getDispoTime() - a.getSource().getDispoTime() < a.getLowerBound())
                objectiveValue += (a.getWeight() * Net.getPeriod());

        return objectiveValue;
    }

    static long computeObjectiveValueDM1(NonPeriodicEANetwork Net, String pathsFileName) throws IOException {
        String line;
        String[] values;
        String[] changes;
        int position;
        boolean connection_missed;
        long realDelay = 0L;
        int weight;
        TreeMap<Integer, NonPeriodicEvent> events_by_id = new TreeMap<>();
        TreeMap<Integer, NonPeriodicChangingActivity> changing_activities_by_id = new TreeMap<>();
        for (NonPeriodicEvent e : Net.getEvents()) {
            events_by_id.put(e.getID(), e);
        }
        for (NonPeriodicChangingActivity a : Net.getChangingActivities()) {
            changing_activities_by_id.put(a.getID(), a);
        }
        BufferedReader reader = new BufferedReader(new FileReader(pathsFileName));
        while ((line = reader.readLine()) != null) {
            position = line.indexOf('#');
            if (position != -1)
                line = line.substring(0, position);
            line = line.trim();
            if (line.isEmpty())
                continue;
            values = line.split(";");
            weight = Integer.parseInt(values[0].trim());
            if (weight > 0) {
                if (values.length <= 5) {//There are no changes in the path
                    NonPeriodicEvent last_event_on_path = events_by_id.get(Integer.parseInt(values[2].trim()));
                    realDelay += (last_event_on_path.getDispoTime() - last_event_on_path.getTime()) * weight;
                } else {//Check if any connection is missed for the path. The delay is then the period length
                    changes = values[5].split(",");
                    connection_missed = false;
                    for (int i = 0; i < changes.length; i++) {
                        NonPeriodicChangingActivity act = changing_activities_by_id.get(Integer.parseInt(changes[i].trim()));
                        if (act.getZ() == 1) {//The connection is missed. The delay is considered to be the period length.
                            realDelay += Net.getPeriod() * weight;//frequencies.get(act);
                            connection_missed = true;
                            break;
                        }
                    }
                    if (!connection_missed) {//There were no missed connections
                        NonPeriodicEvent last_event_on_path = events_by_id.get(Integer.parseInt(values[2].trim()));
                        realDelay += (last_event_on_path.getDispoTime() - last_event_on_path.getTime()) * weight;
                    }
                }
            }
        }
        reader.close();
        return realDelay;
    }


    /***************************************************
     * This method reduces the event-activity network. *
     * @throws Exception
     ***************************************************/

    static NonPeriodicEANetwork preprocessing(NonPeriodicEANetwork originalNet, boolean checkConsistency) throws Exception {
        /*******************************************************
         * Create a copy of the EANetwork. Only this copy will *
         * be modified and returned after the preprocessing.   *
         *******************************************************/

        LinkedHashSet<NonPeriodicEvent> E =
            new LinkedHashSet<>(originalNet.getEvents());
        LinkedHashSet<NonPeriodicActivity> A =
            new LinkedHashSet<>(originalNet.getActivities());
        LinkedHashSet<NonPeriodicActivity> A_drive =
            new LinkedHashSet<>(originalNet.getDrivingActivities());
        LinkedHashSet<NonPeriodicActivity> A_wait =
            new LinkedHashSet<>(originalNet.getWaitingActivities());
        LinkedHashSet<NonPeriodicActivity> A_circ =
            new LinkedHashSet<>(originalNet.getCirculationActivities());
        LinkedHashSet<NonPeriodicChangingActivity> A_change =
            new LinkedHashSet<>(originalNet.getChangingActivities());
        LinkedHashSet<NonPeriodicHeadwayActivity> A_head =
            new LinkedHashSet<>(originalNet.getHeadwayActivities());
        LinkedHashSet<NonPeriodicActivity> A_nice =
            new LinkedHashSet<>(originalNet.getNiceActivities());
        NonPeriodicEANetwork Net =
            new NonPeriodicEANetwork(E, A, A_drive, A_wait, A_circ, A_nice, A_change, A_head, originalNet.getPeriod(),
                checkConsistency);

        logger.debug("DM: preprocessing: unreduced number of events: " + E.size());
        logger.debug("DM: preprocessing: unreduced number of activities: " + A.size());


        /*************************************************************
         * Mark all events directly affected by event source delays. *
         *************************************************************/

        LinkedList<NonPeriodicEvent> eventsToInspect = new LinkedList<>();
        boolean[] marked = new boolean[E.size()];
        for (int i = 0; i < marked.length; i++)
            marked[i] = false;

        for (NonPeriodicEvent e : E) {
            if (e.getSourceDelay() > 0) {
                marked[e.getID() - 1] = true;
                eventsToInspect.add(e);
            }
        }


        /****************************************************************
         * Mark all events directly affected by activity source delays. *
         ****************************************************************/

        for (NonPeriodicActivity a : A) {
            NonPeriodicEvent target = a.getTarget();
            int tID = target.getID();
            if (!marked[tID - 1] && a.getSourceDelay() > 0) {
                marked[tID - 1] = true;
                eventsToInspect.add(target);
            }
        }


        /*****************************************************************
         * Mark all events that might indirectly be affected by a delay. *
         *****************************************************************/

        while (!eventsToInspect.isEmpty()) {
            NonPeriodicEvent e = eventsToInspect.poll();
            int time = e.getTime();
            for (NonPeriodicActivity a : e.getOutgoingActivities()) {
                NonPeriodicEvent target = a.getTarget();
                int tID = target.getID();
                if (!marked[tID - 1] && time <= target.getTime()) {
                    marked[tID - 1] = true;
                    eventsToInspect.add(target);
                }
            }
        }


        /*************************************************************************
         * Delete all events that are not marked (as long as they do not have    *
         * a source-delayed outgoing activity) and re-number the remaining ones. *
         *************************************************************************/

        HashSet<NonPeriodicEvent> eventsToDelete =
            new HashSet<>(E.size());
        for (NonPeriodicEvent e : E) {
            boolean delete = false;
            if (!marked[e.getID() - 1]) {
                delete = true;
                for (NonPeriodicActivity a : e.getOutgoingActivities()) {
                    if (a.getSourceDelay() != 0) {
                        delete = false;
                        break;
                    }
                }
            }

            if (delete) {
                e.setDispoTime(e.getTime());
                eventsToDelete.add(e);
            }
        }
        E.removeAll(eventsToDelete);


        /********************************************************************
         * Delete an activities if at least one of its events has           *
         * been deleted (as long as it is not source-delayed).              *
         ********************************************************************/

        HashSet<NonPeriodicActivity> activitiesToDelete =
            new HashSet<>(A.size());
        for (NonPeriodicActivity a : A) {
            if (a.getSourceDelay() == 0
                && !(E.contains(a.getSource())
                && E.contains(a.getTarget()))) {
                activitiesToDelete.add(a);
            }
        }
        A.removeAll(activitiesToDelete);
        A_drive.removeAll(activitiesToDelete);
        A_wait.removeAll(activitiesToDelete);
        A_circ.removeAll(activitiesToDelete);
        A_change.removeAll(activitiesToDelete);
        A_head.removeAll(activitiesToDelete);
        A_nice.removeAll(activitiesToDelete);

        logger.debug("DM: preprocessing: reduced number of events: " + E.size());
        logger.debug("DM: preprocessing: reduced number of activities: " + A.size());

        return Net;
    }

}
