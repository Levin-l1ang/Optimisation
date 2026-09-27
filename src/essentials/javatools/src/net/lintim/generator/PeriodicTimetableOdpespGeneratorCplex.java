package net.lintim.generator;

import ilog.concert.IloConstraint;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearIntExpr;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import net.lintim.callback.DefaultCallbackCplex;
import net.lintim.exception.DataInconsistentException;
import net.lintim.main.PeriodicTimetableOdpesp;
import net.lintim.model.Activity;
import net.lintim.model.Activity.ActivityType;
import net.lintim.model.Event;
import net.lintim.model.Event.EventType;
import net.lintim.model.EventActivityNetwork.ModelHeadway;
import net.lintim.model.Station;
import net.lintim.util.BiLinkedHashMap;
import net.lintim.util.MathHelper;
import net.lintim.util.TriLinkedHashMap;

import org.apache.commons.math.util.MathUtils;

/**
 * Solves the ODPESP with help of Cplex using the cyclic periodicity formulation
 * The wrapper, i.e. {@link PeriodicTimetableOdpesp} uses a class loader, since
 * cplex.jar may be unavailble on the system. If there are compile errors in
 * eclipse, right click on PeriodicTimetableGeneratorOdpespCplex.java in the
 * package explorer and select "Build Path -> Exclude".
 */
public class PeriodicTimetableOdpespGeneratorCplex extends
PeriodicTimetableOdpespGenerator {
    /**
     * Wrapper to solve the actual problem with Cplex.
     */
    @Override
    public void solveInternal() throws DataInconsistentException{
        Boolean failed = true;

        if(solver == Solver.CPLEX && linearModel == LinearModel.CPF){
            try {
                solveCplexCpf();
                failed = false;
            } catch (IloException e) {
                e.printStackTrace();
            }
        }
        else{
            throw new UnsupportedOperationException("no support for " +
                    "solver, linearModel combination " + solver.name() + ", "
                    + linearModel.name());
        }

        if(failed){
            throw new DataInconsistentException("timetable computation failed");
        }

    }

    protected void solveCplexCpf() throws DataInconsistentException,
    IloException{

        Long time = System.currentTimeMillis();

        IloCplex model = new IloCplex();

        model.setName("ODPESP");
        // model.setOut(new FileOutputStream("cplex.log"));
        model.setOut(System.err);

        // model.setParam(IloCplex.IntParam.HeurFreq, -1);

        model.use(new DefaultCallbackCplex());

        IloLinearNumExpr objective = model.linearNumExpr(0);

        // plain initial values and constraints
        Vector<IloIntVar> plainVariables = new Vector<IloIntVar>();
        Vector<Double> initialValues = new Vector<Double>();
        Vector<IloConstraint> constraints = new Vector<IloConstraint>();

        // durations, helper variables, shortest path decisions + constraints
        LinkedHashMap<Activity, IloLinearIntExpr> xpos =
            new LinkedHashMap<Activity, IloLinearIntExpr>();
        LinkedHashMap<Activity, IloLinearIntExpr> xneg =
            new LinkedHashMap<Activity, IloLinearIntExpr>();

//        TriLinkedHashMap<Activity, Station, Station, IloIntVar> d =
//            new TriLinkedHashMap<Activity, Station, Station, IloIntVar>();
        TriLinkedHashMap<Activity, Station, Station, IloIntVar> p =
            new TriLinkedHashMap<Activity, Station, Station, IloIntVar>();
//        TriLinkedHashMap<Activity, Station, Station, IloConstraint> dc1 =
//            new TriLinkedHashMap<Activity, Station, Station, IloConstraint>();
//        TriLinkedHashMap<Activity, Station, Station, IloConstraint> dc2 =
//            new TriLinkedHashMap<Activity, Station, Station, IloConstraint>();
//        TriLinkedHashMap<Activity, Station, Station, IloConstraint> dc3 =
//            new TriLinkedHashMap<Activity, Station, Station, IloConstraint>();

//        TriLinkedHashMap<Station, Station, Event, IloConstraint> pc1 =
//            new TriLinkedHashMap<Station, Station, Event, IloConstraint>();
//        BiLinkedHashMap<Station, Station, IloConstraint> pc2 =
//            new BiLinkedHashMap<Station, Station, IloConstraint>();
//        BiLinkedHashMap<Station, Station, IloConstraint> pc3 =
//            new BiLinkedHashMap<Station, Station, IloConstraint>();

        // headways + constraints
        LinkedHashMap<Activity, IloIntVar> kx =
            new LinkedHashMap<Activity, IloIntVar>();
        LinkedHashMap<Activity, IloIntVar> hx =
            new LinkedHashMap<Activity, IloIntVar>();

        // circles, modulo parameters and their constraints
        LinkedHashMap<Integer, IloIntVar> z =
            new LinkedHashMap<Integer, IloIntVar>();
        LinkedHashMap<Integer, IloRange> zc =
            new LinkedHashMap<Integer, IloRange>();

        BiLinkedHashMap<Station, Station, LinkedHashSet<Activity>> odPaths =
            useInitialPaths ? ean.getOriginDestinationPathMap() : null;

        Integer periodLength = ((int)Math.round(this.periodLength));

//        eanTop.computeFeasibleArea();
//
//        BiLinkedHashMap<Station, Station, LinkedHashSet<Activity>>
//        feasibleArea = eanTop.getFeasibleArea();

        // definition and constraints:
        // - timespans x_a
        // - full objective function, helper variables d_{as_1s_2} + constraints
        // - part of the p_{as_1s_2} variables
        for (Activity a : activities) {

            ActivityType type = a.getType();
            Integer la = (int) Math.round(a.getLowerBound());
            Integer ua = (int) Math.round(a.getUpperBound());
            Double passengers = a.getPassengers();
            Integer index = a.getIndex();
            Integer duration = useInitialTimetable ? (int) Math.round(a
                    .getDuration()) : null;


            if(type == ActivityType.DRIVE || type == ActivityType.WAIT
                    || type == ActivityType.CHANGE){

                IloIntVar xa = model.intVar(la, ua, "activity_" + index);

                IloLinearIntExpr exprpos = model.linearIntExpr();
                exprpos.addTerm(1, xa);
                xpos.put(a, exprpos);

                IloLinearIntExpr exprneg = model.linearIntExpr();
                exprneg.addTerm(-1, xa);
                xneg.put(a, exprneg);

                if(useInitialTimetable){
                    plainVariables.add(xa);
                    initialValues.add((double) duration);

                    if(forceEqualities){
                        IloLinearIntExpr expr = model.linearIntExpr();
                        expr.addTerm(1, xa);
                        constraints.add(model.addEq(expr, duration));
                    }
                }

            } else if (type == ActivityType.HEADWAY) {
                if (passengers > 0) {
                    throw new DataInconsistentException("there should not be "
                            + " passengers on headway activity " + a.getIndex()
                            + ", but there are " + a.getPassengers());
                }

                Integer lcm = MathUtils.lcm(a.getFromEvent().getLine()
                        .getFrequency(), a.getToEvent().getLine()
                        .getFrequency());

                if (modelHeadway == ModelHeadway.SIMPLE
                        || modelHeadway == ModelHeadway.PRODUCT_OF_FREQUENCIES
                        || modelHeadway == ModelHeadway.LCM_OF_FREQUENCIES
                        || lcm == 1) {

                    IloIntVar xa = model.intVar(la, ua, "activity_" + index);

                    IloLinearIntExpr exprpos = model.linearIntExpr();
                    exprpos.addTerm(1, xa);
                    xpos.put(a, exprpos);

                    IloLinearIntExpr exprneg = model.linearIntExpr();
                    exprneg.addTerm(-1, xa);
                    xneg.put(a, exprneg);

                    if (useInitialTimetable) {
                        plainVariables.add(xa);
                        initialValues.add((double) duration);

                        if (forceEqualities) {
                            IloLinearIntExpr expr = model.linearIntExpr();
                            expr.addTerm(1, xa);
                            constraints.add(model.addEq(expr, duration));
                        }
                    }

                } else if (modelHeadway == ModelHeadway.LCM_REPRESENTATION) {

                    Integer headwayLength = la;
                    Integer tdivlcm = periodLength / lcm;
                    Integer k = useInitialTimetable ? duration / tdivlcm : null;

                    IloIntVar headwayInstance = model.intVar(0, lcm - 1,
                            "lcm_headway_" + index);
                    kx.put(a, headwayInstance);
                    if (useInitialTimetable) {
                        plainVariables.add(headwayInstance);
                        initialValues.add((double)k);

                        if (forceEqualities) {
                            IloLinearIntExpr expr = model.linearIntExpr();
                            expr.addTerm(1, headwayInstance);
                            constraints.add(model.addEq(k, expr));
                        }
                    }

                    IloIntVar headway = model.intVar(headwayLength, tdivlcm
                            - headwayLength, "lcm_headway_" + index);
                    hx.put(a, headway);

                    if (useInitialTimetable) {
                        plainVariables.add(headway);
                        initialValues.add((double)(duration - k * tdivlcm));

                        if (forceEqualities) {
                            IloLinearIntExpr expr = model.linearIntExpr();
                            expr.addTerm(1, headway);
                            constraints.add(model.addEq(duration - k * tdivlcm,
                                    expr));
                        }
                    }

                    IloLinearIntExpr exprpos = model.linearIntExpr();
                    exprpos.addTerm(tdivlcm, headwayInstance);
                    exprpos.addTerm(1, headway);
                    xpos.put(a, exprpos);

                    IloLinearIntExpr exprneg = model.linearIntExpr();
                    exprneg.addTerm(-tdivlcm, headwayInstance);
                    exprneg.addTerm(-1, headway);
                    xneg.put(a, exprneg);

                }
                else{
                    throw new UnsupportedOperationException("timetabling cannot "
                            + "process headway type " + modelHeadway.toString());
                }

            } else if (type == ActivityType.SYNC) {
                if (useInitialTimetable) {
                    if (duration != la) {
                        throw new DataInconsistentException("duration "
                                + duration + " from initial solution does "
                                + "not match the lower bound " + la
                                + " for activity " + a.getIndex());
                    }
                }

                if (passengers > 0) {
                    throw new DataInconsistentException("there should not be "
                            + "passengers on sync activity " + a.getIndex()
                            + ", but there are " + a.getPassengers());
                }

                if (la - ua != 0) {
                    throw new DataInconsistentException("sync activity with "
                            + "lower bound " + la + " and upper bound " + ua
                            + "; they must be equal");
                }

                xpos.put(a, model.linearIntExpr(la));
                xneg.put(a, model.linearIntExpr(-la));

            } else {
                throw new UnsupportedOperationException("timetabling cannot "
                        + "process activity type " + type.toString());
            }

            if(a.isPassengerUsable()){

                for (Map.Entry<Station, LinkedHashMap<Station, Double>> e1 :
                    od.getMatrix().entrySet()){

                    Station s1 = e1.getKey();

                    for(Map.Entry<Station, Double> e2 :
                        e1.getValue().entrySet()){

                        Station s2 = e2.getKey();

                        Double odpassengers = e2.getValue();

                        if(odConditionFullfilled(s1, s2, odpassengers)){
                            Boolean isContained = useInitialPaths ?
                                odPaths.get(s1, s2).contains(a) : null;

                            String indexString = "_" + a.getIndex() +
                            "_" + s1.getIndex() + "_" + s2.getIndex();

                            IloIntVar pas1s2 = model.boolVar(
                                    "shortest_path_decision" + indexString);

                            if(useInitialPaths){
                                plainVariables.add(pas1s2);
                                initialValues.add(
                                    isContained ? 1.0 : 0.0);

                                if(forceEqualities){
                                    IloLinearIntExpr expr =
                                    model.linearIntExpr();
                                    expr.addTerm(1, pas1s2);
                                    constraints.add(model.addEq(
                                        (isContained ? 1.0 : 0.0),
                                        expr));
                                }
                            }

                            p.put(a, s1, s2, pas1s2);

                            IloLinearIntExpr xa = xpos.get(a);

                            if(xa.linearIterator().hasNext()){
                                IloIntVar das1s2 = model.intVar(0, ua,
                                        "objective_helper" + indexString);

                                if(useInitialPaths && useInitialTimetable){
                                    plainVariables.add(das1s2);
                                    initialValues.add(isContained ?
                                            duration : 0.0);

                                    if(forceEqualities){
                                        IloLinearIntExpr expr =
                                            model.linearIntExpr();
                                        expr.addTerm(1, das1s2);
                                        constraints.add(model.addEq(
                                            (isContained ? duration : 0.0),
                                            expr));
                                    }
                                }

//                                d.put(a, s1, s2, das1s2);
                                objective.addTerm(odpassengers, das1s2);

                                IloLinearIntExpr dc1as1s2 =
                                    model.linearIntExpr();
                                dc1as1s2.addTerm(1, das1s2);
                                dc1as1s2.addTerm(-ua, pas1s2);
//                                IloConstraint dc1ac =
                                    model.addLe(dc1as1s2,
                                    0.0, "objective_helper_c1" +
                                    indexString);
//                                dc1.put(a, s1, s2, dc1ac);

                                IloLinearIntExpr dc2as1s2 =
                                    model.linearIntExpr();
                                dc2as1s2.addTerm(-1, das1s2);
                                dc2as1s2.add(xpos.get(a));
                                dc2as1s2.addTerm(ua, pas1s2);
//                                IloConstraint dc2ac =
                                    model.addLe(dc2as1s2,
                                    ua,	"objective_helper_c2" +
                                    indexString);
//                                dc2.put(a, s1, s2, dc2ac);

                                IloLinearIntExpr dc3as1s2 =
                                    model.linearIntExpr();
                                dc3as1s2.addTerm(1, das1s2);
                                dc3as1s2.add(xneg.get(a));
//                                IloConstraint dc3ac =
                                    model.addLe(dc3as1s2,
                                    0.0, "objective_helper_c3" +
                                    indexString);
//                                dc3.put(a, s1, s2, dc3ac);
                            }
                            else{
                                objective.addTerm(odpassengers *
                                        (double) la, pas1s2);
                            }
                        }
                    }
                }
            }

        }

        // definition & constraints: modulo parameters
        for (Map.Entry<Integer, LinkedHashMap<Activity, Boolean>> e1 : cycles
                .entrySet()) {

            Integer cycleIndex = e1.getKey();
            LinkedHashMap<Activity, Boolean> cycle = e1.getValue();

            IloIntVar zvar = model.intVar((int) Math
                    .round(a_C.get(cycleIndex)), (int) Math.round(b_C
                            .get(cycleIndex)), "cycle_" + cycleIndex);
            z.put(cycleIndex, zvar);

            IloLinearIntExpr cycleExpr = model.linearIntExpr();
            Double cycleSum = 0.0;

            for (Map.Entry<Activity, Boolean> e2 : cycle.entrySet()) {
                Activity activity = e2.getKey();
                Double duration = activity.getDuration();
                if (e2.getValue()) {
                    cycleSum += useInitialTimetable ? duration : 0.0;
                    cycleExpr.add(xpos.get(activity));
                } else {
                    cycleSum -= useInitialTimetable ? duration : 0.0;
                    cycleExpr.add(xneg.get(activity));
                }
            }

            if(useInitialTimetable){
                plainVariables.add(zvar);
                initialValues.add((double)Math.round(cycleSum/periodLength));

                if(forceEqualities || fixModuloFromInitialTimetable){
                    IloLinearIntExpr expr = model.linearIntExpr();
                    expr.addTerm(1, zvar);
                    constraints.add(model.addEq((double)Math.round(cycleSum/
                            periodLength), expr));
                }
            }

            cycleExpr.addTerm(-periodLength, zvar);

            IloRange cycleConstraint = model.addEq(cycleExpr, 0.0,
                    "cycle_c_" + cycleIndex);
            zc.put(cycleIndex, cycleConstraint);

        }

        // passengers take some drive activity to get from s1 to somewhere and
        // some drive activity to get from somewhere else to s2
        for (Map.Entry<Station, LinkedHashMap<Station, Double>> e1 :
            od.getMatrix().entrySet()){

            Station s1 = e1.getKey();

            for(Map.Entry<Station, Double> e2 : e1.getValue().entrySet()){

                Station s2 = e2.getKey();
                Double passengers = e2.getValue();

                if(odConditionFullfilled(s1, s2, passengers)){

                    String indexString = "_" + s1.getIndex() + "_" +
                    s2.getIndex();

                    for(Event e : events){
                        if(e.getType() == EventType.DEPARTURE &&
                                e.getStation() == s1 ||
                                e.getType() == EventType.ARRIVAL &&
                                e.getStation() == s2){

                            continue;
                        }

                        IloLinearIntExpr flowExpr = model.linearIntExpr();

                        for(Activity a : e.getIncomingActivities()){
                            if(a.isPassengerUsable()){
                                flowExpr.addTerm(1, p.get(a).get(s1, s2));
                            }
                        }

                        for(Activity a : e.getOutgoingActivities()){
                            if(a.isPassengerUsable()){
                                flowExpr.addTerm(-1, p.get(a).get(s1, s2));
                            }
                        }

//                        IloConstraint flowConstraint =
                            model.addEq(flowExpr,
                                0.0, "shortest_path_decision_flow" +
                                indexString + "_" + e.getIndex());

//                        pc1.put(s1, s2, e, flowConstraint);

                    }

                    IloLinearIntExpr enterExpr = model.linearIntExpr();

                    for(Event e : ean.getDepartureEventsByStation(s1)){
                        Activity drive = e.getAssociatedDriveActivity();
                        enterExpr.addTerm(1, p.get(drive).get(s1, s2));
                    }

//                    IloConstraint pc2c =
                        model.addEq(enterExpr, 1.0,
                            "shortest_path_decision_enter" + indexString);
//                    pc2.put(s1, s2, pc2c);

                    IloLinearIntExpr leaveExpr = model.linearIntExpr();

                    for(Event e : ean.getArrivalEventsByStation(s2)){
                        Activity drive = e.getAssociatedDriveActivity();
                        leaveExpr.addTerm(1, p.get(drive).get(s1, s2));
                    }

//                    IloConstraint pc3c =
                        model.addEq(leaveExpr, 1.0,
                            "shortest_path_decision_leave" + indexString);
//                    pc3.put(s1, s2, pc3c);
                }

            }

        }

        System.err.println("all loops: " + (System.currentTimeMillis() - time));

        model.addMinimize(objective);

        if (useInitialTimetable || useInitialPaths) {
            IloIntVar[] vars = new IloIntVar[plainVariables.size()];
            double[] initialVals = new double[vars.length];

            for(Integer i=0; i < vars.length; i++){
                vars[i] = plainVariables.get(i);
                initialVals[i] = initialValues.get(i);
            }

            // model.setParam(IloCplex.IntParam.AdvInd, 2);
            model.addMIPStart(vars, initialVals, 0, vars.length,
                    IloCplex.MIPStartEffort.CheckFeas, "m1");

            ean.clearDurations();

        }

        System.err.println("formulation time: " +
                (System.currentTimeMillis() - time));

        if(model.solve()){
            ean.resetPassengers();

            for (Entry<Activity, BiLinkedHashMap<Station, Station, IloIntVar>>
            e1 : p.entrySet()) {

                Double passengerSum = 0.0;

                for (Entry<Station, LinkedHashMap<Station, IloIntVar>> e2 :
                    e1.getValue().entrySet()){

                    Station s1 = e2.getKey();

                    for(Entry<Station, IloIntVar> e3 : e2.getValue().entrySet()){

                        Station s2 = e3.getKey();
                        Double passengers = od.getMatrix().get(s1, s2);
                        IloIntVar var = e3.getValue();

                        if(Math.abs(model.getValue(var) - 1) < MathHelper.epsilon){
                            passengerSum += passengers;
                        }

                    }
                }
                e1.getKey().setPassengers(passengerSum);
            }

            for (Activity a : forestEdges) {
                a.setDuration((double) Math.round(model.getValue(xpos.get(a))));

            }

            objectiveFunction = (double) Math.round(model.getObjValue());
        }
        else{
            throw new DataInconsistentException("no feasible timetable available");
        }

    }

}
