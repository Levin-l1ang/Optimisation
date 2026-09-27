package net.lintim.algorithm.vehiclescheduling;

import com.gurobi.gurobi.*;
import net.lintim.exception.LinTimException;
import net.lintim.exception.SolverGurobiException;
import net.lintim.model.Graph;
import net.lintim.model.Stop;
import net.lintim.model.Link;
import net.lintim.model.Depot;
import net.lintim.model.VehicleSchedule;
import net.lintim.model.vehiclescheduling.TripConnection;
import net.lintim.model.vehiclescheduling.TripNode;
import net.lintim.solver.impl.GurobiHelper;
import net.lintim.util.Logger;
import net.lintim.util.vehiclescheduling.Parameters;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * @author Alexander Schiewe
 * Date: 12.09.17
 */
public class IPModelGurobi extends IPModelSolver {

    private static final Logger logger = new Logger(IPModelGurobi.class.getCanonicalName());

    @Override
    public VehicleSchedule solveVehicleSchedulingIPModel(Graph<TripNode, TripConnection> tripGraph,
                                                         Parameters parameters, Optional<Depot> depot, Graph<Stop,Link> ptn) {
        try {
            GRBEnv env = new GRBEnv();
            GRBModel vsModel = new GRBModel(env);
            vsModel.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);
            GurobiHelper.setGurobiSolverParameters(vsModel, parameters);

            // Add variables
            logger.debug("Add variables");
            HashMap<Integer, GRBVar> edgeVariables = new HashMap<>();
            for (TripConnection connection : tripGraph.getEdges()) {
                edgeVariables.put(connection.getId(), vsModel.addVar(0, 1, connection.getCost(), GRB.INTEGER, "v_"
                    + connection.getId()));
            }

            // Add constraints
            logger.debug("Add incoming connection constraints");
            GRBLinExpr incomingEdges;
            GRBLinExpr outgoingEdges;
            for (TripNode tripNode : tripGraph.getNodes()) {
                if (tripNode.isDepot()) {
                    continue;
                }
                incomingEdges = new GRBLinExpr();
                for (TripConnection incomingConnection : tripGraph.getIncomingEdges(tripNode)) {
                    incomingEdges.addTerm(1, edgeVariables.get(incomingConnection.getId()));
                }
                outgoingEdges = new GRBLinExpr();
                for (TripConnection outgoingConnection : tripGraph.getOutgoingEdges(tripNode)) {
                    outgoingEdges.addTerm(1, edgeVariables.get(outgoingConnection.getId()));
                }
                vsModel.addConstr(incomingEdges, GRB.EQUAL, 1, "c_inc_" + tripNode.getId());
                vsModel.addConstr(outgoingEdges, GRB.EQUAL, 1, "c_out_" + tripNode.getId());
            }

            logger.debug("Start optimization");
            if (parameters.writeLpFile()) {
                logger.debug("Writing lp file");
                vsModel.write("vehicle-scheduling/vsModel.lp");
            }
            vsModel.optimize();
            logger.debug("End optimization");

            int status = vsModel.get(GRB.IntAttr.Status);
            if (vsModel.get(GRB.IntAttr.SolCount) > 0) {
                if (status == GRB.OPTIMAL) {
                    logger.debug("Optimal solution found");
                } else {
                    logger.debug("Feasible solution found");
                }
                // Create the vehicle schedule from the solution information
                List<TripConnection> usedTripConnections = new LinkedList<>();
                for (TripConnection connection : tripGraph.getEdges()) {
                    // We are using a nested try-catch-exception here. We had the problem that
                    // the get method would sometimes fail with a "variable not in model"-error,
                    // altough the varible was contained. Adding an additional check solved
                    // this problem. To avoid doing this in every step, we will only do it if
                    // the first try fails.
                    try {
                        if (Math.round(edgeVariables.get(connection.getId()).get(GRB.DoubleAttr.X)) > 0) {
                            usedTripConnections.add(connection);
                        }
                    } catch (GRBException e) {
                        // Try again to find the variable. First check, if the variable is
                        // contained in the model (and fail otherwise) and then query it again.
                        GRBVar[] variables = vsModel.getVars();
                        GRBVar variable = edgeVariables.get(connection.getId());
                        if (Arrays.asList(variables).contains(variable)) {
                            if (Math.round(edgeVariables.get(connection.getId()).get(GRB.DoubleAttr.X)) > 0) {
                                usedTripConnections.add(connection);
                            }
                        } else {
                            throw new LinTimException("Variable " + connection.getId() + " is not contained!");
                        }
                        throw new SolverGurobiException(e.getErrorCode() + ": " + e);
                    }
                }
                return computeSchedule(usedTripConnections, parameters, depot, ptn);
            }
            logger.warn("No feasible solution found");
            if (status == GRB.INFEASIBLE) {
                logger.debug("The problem is infeasible");
                vsModel.computeIIS();
                vsModel.write("vehicle-scheduling/vsModel.ilp");
            }
            return null;
        } catch (GRBException e) {
            throw new SolverGurobiException(e.toString());
        }
    }
}
