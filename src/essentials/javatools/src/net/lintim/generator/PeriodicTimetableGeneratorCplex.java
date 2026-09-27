package net.lintim.generator;

import ilog.concert.IloConstraint;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearIntExpr;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;

import java.io.FileNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

import net.lintim.callback.DefaultCallbackCplex;
import net.lintim.exception.DataInconsistentException;
import net.lintim.main.PeriodicTimetable;
import net.lintim.model.Activity;
import net.lintim.model.Activity.ActivityType;
import net.lintim.model.Event;
import net.lintim.model.EventActivityNetwork.ModelChange;
import net.lintim.model.EventActivityNetwork.ModelHeadway;

import org.apache.commons.math.util.MathUtils;

/**
 * Solves the PESP resp. EPESP, depending on
 * {@link PeriodicTimetableGenerator#linearModel} with help of Cplex using the
 * cyclic periodicity formulation for former. The wrapper, i.e.
 * {@link PeriodicTimetable} uses a class loader, since cplex.jar may be
 * unavailble on the system. If there are compile errors in eclipse, right click
 * on PeriodicTimetableGeneratorCplex.java in the package explorer and select
 * "Build Path -> Exclude".
 */
public class PeriodicTimetableGeneratorCplex extends PeriodicTimetableGenerator{
    /**
     * Wrapper to solve the actual problem with Cplex.
     */
    @Override
    public void solveInternal() throws DataInconsistentException{
        Boolean failed = true;

        if(solver == Solver.CPLEX && linearModel == LinearModel.EPESP){
            try {
                solveCplexEpesp();
                failed = false;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IloException e) {
                e.printStackTrace();
            }
        }
        else if(solver == Solver.CPLEX && linearModel == LinearModel.CPF){
            try {
                solveCplexCPF();
                failed = false;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
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

    protected void solveCplexEpesp() throws DataInconsistentException,
    IloException, FileNotFoundException{
        IloCplex model = new IloCplex();

        model.setName("PeriodicEventSchedulingProblem");
        // model.setOut(new FileOutputStream("cplex.log"));
        model.use(new DefaultCallbackCplex());
//        model.use(new PresolveCallbackCplex());
        model.setOut(stream);

        if(!useInitialTimetable && !fixModuloFromInitialTimetable){
            throw new DataInconsistentException("for the epesp, an initial " +
                    "timetable must be given and either use requested or " +
                    "modulo parameters fixed");
        }

        if(modelChange != ModelChange.LCM_SIMPLIFICATION ||
                modelHeadway != ModelHeadway.LCM_REPRESENTATION){
            throw new DataInconsistentException("linear model epesp " +
                    "requires change activity model LCM_SIMPLIFICATION " +
                    "and headway model LCM_REPRESENTATION");
        }

        IloLinearNumExpr objective = model.linearNumExpr();

        if (objectiveFunctionModel == ObjectiveFunctionModel.SLACK) {
            objective.setConstant((int)Math.round(-slackDifference));
        }

        // times
        LinkedHashMap<Event, IloIntVar> pi =
            new LinkedHashMap<Event, IloIntVar>();

        for(Event e : events){
            IloIntVar pie = model.intVar(0, (int)Math.round(periodLength-1),
                    "event_" + e.getIndex());
            pi.put(e, pie);
            model.add(pie);
        }

        int intPeriodLength = (int)Math.round(periodLength);

        for(Activity a : activities){

            Event fromEvent = a.getFromEvent();
            Event toEvent = a.getToEvent();

            double lcm = (double)MathUtils.lcm(
                    fromEvent.getLine().getFrequency(),
                    toEvent.getLine().getFrequency());
            double tau = (double)(intPeriodLength/lcm);

            double ta, ua, za;

            double fromTime = fromEvent.getTime();
            double toTime = toEvent.getTime();
            double la = a.getLowerBound();

            double duration = toTime - fromTime;

            ta = periodLength;
            ua = Math.round(a.getUpperBound());

            if(a.getType() == ActivityType.DRIVE ||
                    a.getType() == ActivityType.WAIT){
                ta = intPeriodLength;
                ua = (int)Math.round(a.getUpperBound());

            }
            else if(a.getType() == ActivityType.CHANGE){
                if((int)Math.round(a.getUpperBound()-a.getLowerBound()+1)
                        < intPeriodLength){
                    throw new DataInconsistentException("change activities " +
                            "are expected to span a period, but activity " +
                            a.getIndex() + " does not");
                }
                ta = tau;
                ua = tau + la - 1;

            }
            else if(a.getType() == ActivityType.HEADWAY){
                ta = tau;
                ua = tau - la;

            } else {
                throw new DataInconsistentException("unsupported activity " +
                        "type " + a.getType());
            }
            duration -= Math.floor((duration - la)/ta)*ta;
            za = (duration - toTime + fromTime)/ta;
            IloLinearNumExpr expr = model.linearNumExpr(ta*za);
            expr.addTerm(1, pi.get(toEvent));
            expr.addTerm(-1, pi.get(fromEvent));
            model.addLe(la, expr, "la_" + a.getIndex());
            model.addLe(expr, ua, "ua_" + a.getIndex());
//            System.err.println(la + " <= pi_" + toEvent.getIndex() + " - " +
//                    "pi_" + fromEvent.getIndex() + " + " + za*ta + " <= " + ua);

            double passengers = a.getPassengers();

            IloLinearNumExpr objexpr = model.linearNumExpr(passengers*ta*za);
            objexpr.addTerm(passengers, pi.get(toEvent));
            objexpr.addTerm(-passengers, pi.get(fromEvent));
            objective.add(objexpr);

        }

        model.addMinimize(objective);

        if(model.solve()){
            ean.clearDurations();

            for (Event e : events) {
                e.setTime(model.getValue(pi.get(e)));
            }

            ean.computeDurationsFromTimetable();

            objectiveFunction = (double) Math.round(model.getObjValue());

        }
        else {
            throw new DataInconsistentException("no feasible timetable available");
        }

    }

    protected void solveCplexCPF() throws DataInconsistentException,
    IloException, FileNotFoundException{

        IloCplex model = new IloCplex();

        model.setName("PeriodicEventSchedulingProblem");
        // model.setOut(new FileOutputStream("cplex.log"));
        model.use(new DefaultCallbackCplex());
//        model.use(new PresolveCallbackCplex());
        model.setOut(stream);
//        model.setParam(IloCplex.IntParam.VarSel, 3);
//        model.setParam(IloCplex.IntParam.Cliques, 3);
//        model.setParam(IloCplex.IntParam.Covers, 3);
//        model.setParam(IloCplex.IntParam.DisjCuts, 3);
//        model.setParam(IloCplex.IntParam.FlowCovers, 2);
//        model.setParam(IloCplex.IntParam.FracCuts, 2);
//        model.setParam(IloCplex.IntParam.GUBCovers, 2);
//        // model.setParam(IloCplex.IntParam.ImplBd, 2);
//        model.setParam(IloCplex.IntParam.MCFCuts, 2);
//        model.setParam(IloCplex.IntParam.MIRCuts, 2);
//        model.setParam(IloCplex.IntParam.ZeroHalfCuts, 2);

        IloLinearNumExpr objective = model.linearNumExpr();

        if (objectiveFunctionModel == ObjectiveFunctionModel.SLACK) {
            objective.setConstant((int)Math.round(-slackDifference));
        }

        // plain initial values and constraints
        Vector<IloIntVar> plainVariables = new Vector<IloIntVar>();
        Vector<Double> initialValues = new Vector<Double>();
        Vector<IloConstraint> constraints = new Vector<IloConstraint>();

        // durations
        LinkedHashMap<Activity, IloLinearIntExpr> xpos =
            new LinkedHashMap<Activity, IloLinearIntExpr>();
        LinkedHashMap<Activity, IloLinearIntExpr> xneg =
            new LinkedHashMap<Activity, IloLinearIntExpr>();
        LinkedHashMap<Activity, IloIntVar> kx =
            new LinkedHashMap<Activity, IloIntVar>();
        LinkedHashMap<Activity, IloIntVar> hx =
            new LinkedHashMap<Activity, IloIntVar>();

        // circles, modulo parameters and their constraints
        LinkedHashMap<Integer, IloIntVar> z =
            new LinkedHashMap<Integer, IloIntVar>();
        LinkedHashMap<Integer, IloRange> zc =
            new LinkedHashMap<Integer, IloRange>();

        Integer periodLength = ((int)Math.round(this.periodLength));

        for (Activity a : activities) {

            ActivityType type = a.getType();
            Integer la = (int) Math.round(a.getLowerBound());
            Integer ua = (int) Math.round(a.getUpperBound());
            Double passengers = a.getPassengers();
            Integer index = a.getIndex();
            Integer duration = useInitialTimetable ?
                    (int) Math.round(a.getDuration()) : null;

            Boolean activityInSomeCycle = edgeInSomeCycle.contains(a);

            Integer lcm = MathUtils.lcm(
                    a.getFromEvent().getLine().getFrequency(),
                    a.getToEvent().getLine().getFrequency());

            if(type == ActivityType.DRIVE || type == ActivityType.WAIT ||
                    type == ActivityType.CHANGE){
                IloIntVar xa = model.intVar(la, ua, "activity_" + index);

                IloLinearIntExpr exprpos = model.linearIntExpr();
                exprpos.addTerm(1, xa);
                xpos.put(a, exprpos);

                IloLinearIntExpr exprneg = model.linearIntExpr();
                exprneg.addTerm(-1, xa);
                xneg.put(a, exprneg);

                if(activityInSomeCycle){
                    objective.addTerm(passengers, xa);
                }
                else{
                    objective.add(model.linearNumExpr(((double)la)*passengers));
                }

                if(useInitialTimetable){
                    if(activityInSomeCycle || forceEqualities){
                        plainVariables.add(xa);
                        initialValues.add((double) duration);
                    }

                    if(forceEqualities){
                        IloLinearIntExpr expr = model.linearIntExpr();
                        expr.addTerm(1, xa);
                        constraints.add(model.addEq(expr, duration));
                    }
                }
            }
            else if(type == ActivityType.HEADWAY){
                if(passengers > 0){
                    throw new DataInconsistentException("there should not be "
                            + " passengers on headway activity " + a.getIndex()
                            + ", but there are " + a.getPassengers());
                }

                if(modelHeadway == ModelHeadway.NO_HEADWAYS){
                    throw new DataInconsistentException("headway model " +
                            "NO_HEADWAYS selected, but found a headway " +
                            "activity in the event activity network");
                }

                if(modelHeadway == ModelHeadway.SIMPLE
                        || modelHeadway == ModelHeadway.PRODUCT_OF_FREQUENCIES
                        || modelHeadway == ModelHeadway.LCM_OF_FREQUENCIES
                        || lcm == 1){

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

                }
                else if(modelHeadway == ModelHeadway.LCM_REPRESENTATION){

                    Integer headwayLength = la;
                    Integer tau = periodLength/lcm;
                    Integer k = useInitialTimetable ? duration / tau : null;

                    IloIntVar headwayInstance = model.intVar(0, lcm-1,
                            "lcm_headway_k_" + index);
                    kx.put(a, headwayInstance);
                    if(useInitialTimetable){
                        plainVariables.add(headwayInstance);
                        initialValues.add((double)k);

                        if(forceEqualities){
                            IloLinearIntExpr expr = model.linearIntExpr();
                            expr.addTerm(1, headwayInstance);
                            constraints.add(model.addEq(k, expr));
                        }
                    }

                    IloIntVar headway = model.intVar(headwayLength, tau
                            -headwayLength, "lcm_headway_h_" + index);
                    hx.put(a, headway);

                    if(useInitialTimetable){
                        plainVariables.add(headway);
                        initialValues.add((double)(duration - k*tau));

                        if(forceEqualities){
                            IloLinearIntExpr expr = model.linearIntExpr();
                            expr.addTerm(1, headway);
                            constraints.add(model.addEq(duration - k*tau,
                                    expr));
                        }
                    }

                    IloLinearIntExpr exprpos = model.linearIntExpr();
                    exprpos.addTerm(tau, headwayInstance);
                    exprpos.addTerm(1, headway);
                    xpos.put(a, exprpos);

                    IloLinearIntExpr exprneg = model.linearIntExpr();
                    exprneg.addTerm(-tau, headwayInstance);
                    exprneg.addTerm(-1, headway);
                    xneg.put(a, exprneg);

                }
                else{
                    throw new UnsupportedOperationException("timetabling cannot "
                            + "process headway model " + modelHeadway.toString());
                }

            }
            else if(type == ActivityType.SYNC){
                if(useInitialTimetable){
                    if(duration - la != 0){
                        throw new DataInconsistentException("duration "
                                + duration + " from initial solution does "
                                + "not match the lower bound " + la
                                + " for activity " + a.getIndex());
                    }
                }

                if(passengers > 0){
                    throw new DataInconsistentException("there should not be "
                            + " passengers on sync/virtual activity "
                            + a.getIndex() + ", but there are "
                            + a.getPassengers());
                }

                if(la - ua != 0){
                    throw new DataInconsistentException("sync/virtual " +
                            "activity " + a.getIndex() + " with lower bound " +
                            la + " and upper bound " + ua +
                            "; they must be equal");
                }

                xpos.put(a, model.linearIntExpr(la));
                xneg.put(a, model.linearIntExpr(-la));

            }
            else{
                throw new UnsupportedOperationException("timetabling cannot "
                        + "process activity type " + type.toString());
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
                Double duration;
                if(modelChange == ModelChange.LCM_SIMPLIFICATION &&
                        activity.getType() == ActivityType.CHANGE){

                    Double la = activity.getLowerBound();

                    duration = activity.getToEvent().getTime()
                    - activity.getFromEvent().getTime();

                    duration -= Math.floor((duration - la)/periodLength)
                        *periodLength;
                }
                else {
                    duration = activity.getDuration();
                }
                if (e2.getValue()) {
                    cycleSum += timetableGiven ? duration : 0.0;
                    cycleExpr.add(xpos.get(activity));
                } else {
                    cycleSum -= timetableGiven ? duration : 0.0;
                    cycleExpr.add(xneg.get(activity));
                }
            }

            if(forceEqualities || fixModuloFromInitialTimetable){
                IloLinearIntExpr expr = model.linearIntExpr();
                expr.addTerm(1, zvar);
                constraints.add(model.addEq((double)Math.round(
                        cycleSum/periodLength), expr));
            }

            if(useInitialTimetable){
                plainVariables.add(zvar);
                initialValues.add((double)Math.round(cycleSum/periodLength));
            }

            cycleExpr.addTerm(-periodLength, zvar);

            IloRange cycleConstraint = model.eq(cycleExpr, 0.0,
                    "cycle_c_" + cycleIndex);
            zc.put(cycleIndex, cycleConstraint);

            model.add(cycleConstraint);


        }

        // If we do not check the number of rows, we will get a very obscure
        // error: "3003 Not a mixed-integer problem."
        if (useInitialTimetable && model.getNrows() > 0) {
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

            // model.writeMIPStart("model.mst");
        }

        model.addMinimize(objective);
        // model.setParam(IloCplex.IntParam.HeurFreq, 1);
        // model.exportModel("model.lp");

        if(model.solve()){
            ean.clearDurations();

            for (Activity a : activities) {
                a.setDuration(edgeInSomeCycle.contains(a) ?
                        (double) Math.round(model.getValue(xpos.get(a))) :
                            a.getLowerBound());
            }

            objectiveFunction = (double) Math.round(model.getObjValue());

        }
        else{
            throw new DataInconsistentException("no feasible timetable available");
        }

    }

}
