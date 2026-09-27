package net.lintim.generator;

import java.util.LinkedHashMap;
import java.util.Map;

import net.lintim.exception.DataInconsistentException;
import net.lintim.main.PeriodicTimetable;
import net.lintim.model.Activity;

import com.dashoptimization.XPRB;
import com.dashoptimization.XPRBctr;
import com.dashoptimization.XPRBexpr;
import com.dashoptimization.XPRBprob;
import com.dashoptimization.XPRBvar;
import com.dashoptimization.XPRS;

/**
 * Solves the PESP with help of Xpress, cyclic periodicity formulation. The
 * wrapper, i.e. {@link PeriodicTimetable} uses a class loader, since xprb.jar,
 * xprm.jar resp. xprs.jar may be unavailble on the system. If there are compile
 * errors in eclipse, right click on PeriodicTimetableGeneratorXpress.java in
 * the package explorer and select "Build Path -> Exclude".
 */
public class PeriodicTimetableGeneratorXpress extends PeriodicTimetableGenerator{
    /**
     * Wrapper to solve the actual problem with Xpress.
     */
    @Override
    public void solveInternal() throws DataInconsistentException{
        Boolean failed = true;

        if(solver == Solver.XPRESS && linearModel == LinearModel.CPF){
            solveXpressCPF();
            failed = false;
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

    protected void solveXpressCPF() throws DataInconsistentException{
        XPRS.init();
        XPRB env = new XPRB();
        XPRBprob model = env.newProb("PeriodicEventSchedulingProblem");

//		GRBModel  model = new GRBModel(env);

//		GurobiCallback callback = new GurobiCallback();
//		model.setCallback(callback);
        XPRBexpr objective = new XPRBexpr();

        if(objectiveFunctionModel == ObjectiveFunctionModel.TRAVELING_TIME){
            // do nothing
        }
        else if(objectiveFunctionModel == ObjectiveFunctionModel.SLACK){
            objective.add(-slackDifference);
        }
        else{
            throw new DataInconsistentException("objective function " +
                    "model not supported for cplex");
        }

        LinkedHashMap<Activity, XPRBvar> x = new LinkedHashMap<Activity, XPRBvar>();

        LinkedHashMap<Integer, XPRBvar> z = new LinkedHashMap<Integer, XPRBvar>();
        LinkedHashMap<Integer, XPRBctr> c = new LinkedHashMap<Integer, XPRBctr>();

        for(Activity a : activities){
            XPRBvar var = model.newVar("activity_" + a.getIndex(), XPRB.UI,
                    a.getLowerBound(), a.getUpperBound());
            x.put(a, var);
            objective.add(new XPRBexpr(a.getPassengers(), var));
        }

        for(Integer cycleIndex : cycles.keySet()){
            z.put(cycleIndex, model.newVar("cycle_" + cycleIndex, XPRB.UI,
                    a_C.get(cycleIndex), b_C.get(cycleIndex)));
        }

        if(ean.timetableGiven() && useInitialTimetable){
            throw new UnsupportedOperationException("usage of initial " +
                    "timetable with Xpress not implemented yet");

//			for(Map.Entry<Activity, GRBVar> e : x.entrySet()){
//
//				Activity a = e.getKey();
//				GRBVar var = e.getValue();
//
//				var.set(GRB.DoubleAttr.Start, a.getDuration());
//			}
//
//			ean.clearDurations();

        }

        for(Map.Entry<Integer, LinkedHashMap<Activity, Boolean>> e1 :
            cycles.entrySet()){

            Integer cycleIndex = e1.getKey();
            LinkedHashMap<Activity, Boolean> cycle = e1.getValue();

            XPRBexpr expr = new XPRBexpr();

            for(Map.Entry<Activity, Boolean> e2 : cycle.entrySet()){
                Activity activity = e2.getKey();
                Boolean orientation = e2.getValue();

                if(orientation){
                    expr.addTerm(1.0, x.get(activity));

                }
                else{
                    expr.addTerm(-1.0, x.get(activity));

                }
            }

            expr.addTerm(-periodLength, z.get(cycleIndex));

            c.put(cycleIndex, model.newCtr("constraint_" + cycleIndex,
                    expr.eql(0.0)));
        }

        model.setObj(objective);
        model.minim("g");

        switch(model.getMIPStat()){
        case XPRB.MIP_NOT_LOADED:
            throw new DataInconsistentException("mip not loaded");
        case XPRB.MIP_LP_NOT_OPTIMAL:
        case XPRB.MIP_LP_OPTIMAL:
        case XPRB.MIP_NO_SOL_FOUND:
            throw new DataInconsistentException("mip no solution found");
        case XPRB.MIP_INFEAS:
            throw new DataInconsistentException("mip infeasible");
        case XPRB.MIP_OPTIMAL:
        case XPRB.MIP_SOLUTION:
        }

        for(Activity a : activities){
            // The Math.max is a workaround for a bug in Xpress mosel: variables
            // that are not part of the objective function or constraints are
            // set to zero.
            a.setDuration((double)Math.round(Math.max(a.getLowerBound(),
                    x.get(a).getSol())));
        }

        objectiveFunction = (double)Math.round(model.getObjVal());
    }

}
