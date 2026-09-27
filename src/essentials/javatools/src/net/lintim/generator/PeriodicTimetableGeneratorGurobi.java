package net.lintim.generator;


import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBConstr;
import com.gurobi.gurobi.GRBEnv;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBModel;
import com.gurobi.gurobi.GRBVar;

import java.util.LinkedHashMap;
import java.util.Map;

import net.lintim.callback.DefaultCallbackGurobi;
import net.lintim.exception.DataInconsistentException;
import net.lintim.main.PeriodicTimetable;
import net.lintim.model.Activity;

/**
 * Solves the PESP with help of Gurobi, cyclic periodicity formulation. The
 * wrapper, i.e. {@link PeriodicTimetable} uses a class loader, since gurobi.jar
 * may be unavailble on the system. If there are compile errors in eclipse,
 * right click on PeriodicTimetableGeneratorGurobi.java in the package explorer
 * and select "Build Path -> Exclude".
 */
public class PeriodicTimetableGeneratorGurobi extends PeriodicTimetableGenerator{
    /**
     * Wrapper to solve the actual problem with Gurobi.
     */
    @Override
    public void solveInternal() throws DataInconsistentException{
        Boolean failed = true;

        if(solver == Solver.GUROBI && linearModel == LinearModel.CPF){
            try {
                solveGurobiCPF();
                failed = false;
            } catch (GRBException e) {
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

    protected void solveGurobiCPF() throws GRBException, DataInconsistentException{
        // TODO create a log directory resp. proper log file
        GRBEnv    env   = new GRBEnv("gurobi.log");
//        env.set(GRB.IntParam.Cuts, 3);
//        env.set(GRB.IntParam.VarBranch, 3);

        GRBModel  model = new GRBModel(env);

        DefaultCallbackGurobi callback = new DefaultCallbackGurobi();
        model.setCallback(callback);

        model.set(GRB.StringAttr.ModelName, "PeriodicEventSchedulingProblem");

        if(objectiveFunctionModel == ObjectiveFunctionModel.TRAVELING_TIME){
            model.set(GRB.DoubleAttr.ObjCon, 0.0);
        }
        else if(objectiveFunctionModel == ObjectiveFunctionModel.SLACK){
            model.set(GRB.DoubleAttr.ObjCon, -slackDifference);
        }
        else{
            throw new DataInconsistentException("objective function " +
                    "model not supported for cplex");
        }

        LinkedHashMap<Activity, GRBVar> x = new LinkedHashMap<Activity, GRBVar>();
        LinkedHashMap<Integer, GRBVar> z = new LinkedHashMap<Integer, GRBVar>();
        LinkedHashMap<Integer, GRBConstr> c = new LinkedHashMap<Integer, GRBConstr>();

        for(Activity a : activities){
            x.put(a, model.addVar(a.getLowerBound(), a.getUpperBound(),
                    a.getPassengers(), GRB.INTEGER, "activity_" + a.getIndex()));
        }

        for(Integer cycleIndex : cycles.keySet()){
            z.put(cycleIndex, model.addVar(a_C.get(cycleIndex),
                    b_C.get(cycleIndex), 0.0, GRB.INTEGER, "cycle_" + cycleIndex));
        }

        model.update();

        if(ean.timetableGiven() && useInitialTimetable){
            for(Map.Entry<Activity, GRBVar> e : x.entrySet()){

                Activity a = e.getKey();
                GRBVar var = e.getValue();

                var.set(GRB.DoubleAttr.Start, a.getDuration());
            }

            ean.clearDurations();

        }

        for(Map.Entry<Integer, LinkedHashMap<Activity, Boolean>> e1 :
            cycles.entrySet()){

            Integer cycleIndex = e1.getKey();
            LinkedHashMap<Activity, Boolean> cycle = e1.getValue();

            GRBLinExpr expr = new GRBLinExpr();

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

            c.put(cycleIndex, model.addConstr(expr, GRB.EQUAL, 0.0,
                    "constraint_" + cycleIndex));

        }

//		Debugging Stuff
//		========================================================================
//		model.update();
//		model.write("test.mps");
//
//		try {
//			FileWriter fw = new FileWriter(new File("test.sol"));
//
//			for(Activity a : activities){
//				GRBVar var = x.get(a);
//				fw.write(var.get(GRB.StringAttr.VarName) + "  "
//						+ Math.round(var.get(GRB.DoubleAttr.Start)) + "\n");
//			}
//
//			fw.close();
//
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//      ========================================================================

        model.optimize();

        for(Activity a : activities){
            a.setDuration((double)Math.round(x.get(a).get(GRB.DoubleAttr.X)));
        }

        objectiveFunction = (double)Math.round(model.get(GRB.DoubleAttr.ObjVal));
    }


}
