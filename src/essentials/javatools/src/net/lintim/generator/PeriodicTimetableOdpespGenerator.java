package net.lintim.generator;

import net.lintim.exception.DataInconsistentException;
import net.lintim.model.*;
import net.lintim.model.EventActivityNetwork.ModelHeadway;
import net.lintim.util.MathHelper;

import java.util.LinkedHashSet;

/**
 * Generates a periodic timetable by solving the ODPESP.
 */
public abstract class PeriodicTimetableOdpespGenerator extends
PeriodicTimetableGenerator {

    PublicTransportationNetwork ptn;
    OriginDestinationMatrix od;
    LinkedHashSet<Activity> passengerUsableActivities;
    Boolean useInitialPaths;

    Boolean useSuperactivities = false;

    /**
     * To be called after instantiation. Not a constructor due to the necessity
     * of a class loader for different solvers. WARNING: does not and should
     * never redirect to {@link #initialize(EventActivityNetwork, Solver,
     * LinearModel, CyclebaseModel, Boolean, Boolean, Double)}, since this
     * automatically eliminates unused change activities that could be part
     * of the ODPESP optimum.
     *
     * @param ean The event activity network.
     * @param od The origin destination matrix.
     * @param solver The solver.
     * @param linearModel The linear model.
     * @param modelHeadway The headway model.
     * @param cyclebaseModel The cyclebase model.
     * @param useInitialTimetable True if the timetable should be used for an
     * initial solution, false otherwise.
     * @param useInitialPaths True if initial paths should be used, false
     * otherwise.
     */
    public void initialize(EventActivityNetwork ean,
            OriginDestinationMatrix od,
            Solver solver, LinearModel linearModel,
            ModelHeadway modelHeadway, CyclebaseModel cyclebaseModel,
            Boolean useInitialTimetable, Boolean useInitialPaths) {

        // FIXME getter and setter
        this.ean = ean;
        this.ptn = ean.getPublicTransportationNetwork();
        this.od = od;
        this.events = ean.getEvents();
        this.activities = ean.getActivities();
        this.passengerUsableActivities = ean.getPassengerUsableActivities();
        this.periodLength = ean.getPeriodLength();
        this.objectiveFunctionModel = ObjectiveFunctionModel.TRAVELING_TIME;
        setSolver(solver);
        setLinearModel(linearModel);
        setModelHeadway(ean.getModelHeadway());
        setModelChange(ean.getModelChange());
        setModelFrequency(ean.getModelFrequency());
        setModelHeadway(modelHeadway);
        setCyclebaseModel(cyclebaseModel);
        setUseInitialTimetable(useInitialTimetable);
        setUseInitialPaths(useInitialPaths);

        makeCyclebase();

        initialized = true;

    }

    /**
     * Redirects to {@link #initialize(EventActivityNetwork,
     * OriginDestinationMatrix, Solver, LinearModel, ModelHeadway,
     * CyclebaseModel, Boolean, Boolean)}.
     *
     * @param ean The event activity network.
     * @param od The origin destination matrix.
     * @param config The configuration.
     * @throws DataInconsistentException
     */
    public void initialize(EventActivityNetwork ean,
            OriginDestinationMatrix od, Configuration config)
    throws DataInconsistentException{

        initialize(ean, od, PeriodicTimetableGenerator.Solver.
                valueOf(config.getStringValue("tim_odpesp_solver").trim().
                        toUpperCase()),
                    PeriodicTimetableGenerator.LinearModel.CPF,
                    ModelHeadway.valueOf(
                            config.getStringValue("ean_model_headway").
                            toUpperCase()),
                    CyclebaseModel.valueOf(config.getStringValue(
                            "tim_cyclebase_model").trim().toUpperCase()),
                            config.getBooleanValue("tim_odpesp_use_old_timetable"),
                            config.getBooleanValue("tim_odpesp_use_old_passenger_paths"));
    }

    /**
     * True if the od pair (<code>s1</code>, <code>s2</code>) should be
     * considered in the ODPESP, false otherwise. In the current implementation,
     * it just checks whether there are more than zero passengers that travel
     * from <code>s1</code> to <code>s2</code>.
     *
     * @param s1 From station.
     * @param s2 To station.
     * @param passengers Number of passengers.
     * @return
     */
    protected Boolean odConditionFullfilled(Station s1, Station s2,
            Double passengers) {
        if(Math.abs(passengers) < MathHelper.epsilon){
            return false;
        }
        return true;
    }

    /**
     * Solves the actual problem with {@link #solveInternal()} after checking
     * whether {@link #initialize(EventActivityNetwork, OriginDestinationMatrix,
     * Configuration)} has been run and verifies objective function integrity.
     *
     * @throws DataInconsistentException
     */
    public void solve() throws DataInconsistentException{
        if (useInitialTimetable || useInitialPaths) {
            if(useInitialTimetable && !ean.timetableGiven()){
                throw new DataInconsistentException("requested to use " +
                        "initial timetable, but no timetable given");
            }

            if(useInitialPaths && !ean.activityPathsGiven()){
                throw new DataInconsistentException("requested to use " +
                        "initial activity paths, but no paths given");
            }
        }

        super.solve();

    }

    // =========================================================================
    // === Setters =============================================================
    // =========================================================================
    public void setUseInitialPaths(Boolean useInitialPaths) {
        this.useInitialPaths = useInitialPaths;
    }

    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public Boolean getUseInitialPaths() {
        return useInitialPaths;
    }

}
