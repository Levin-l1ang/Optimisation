package net.lintim.algorithm.vehiclescheduling;

import net.lintim.model.Graph;
import net.lintim.model.VehicleSchedule;
import net.lintim.model.vehiclescheduling.TripConnection;
import net.lintim.model.vehiclescheduling.TripNode;
import net.lintim.solver.*;
import net.lintim.model.Stop;
import net.lintim.model.Link;
import net.lintim.model.Depot;
import net.lintim.util.Logger;
import net.lintim.util.vehiclescheduling.Parameters;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class IPModelCore extends IPModelSolver{

    private static final Logger logger = new Logger(IPModelCore.class);

    @Override
    public VehicleSchedule solveVehicleSchedulingIPModel(Graph<TripNode, TripConnection> tripGraph, Parameters parameters, Optional<Depot> depot, Graph<Stop,Link> ptn) {
        Solver solver = Solver.createSolver(parameters.getSolverType());
        Model model = solver.createModel();
        parameters.setSolverParameters(model);

        logger.debug("Add variables");
        HashMap<Integer, Variable> edgeVariables = new HashMap<>();
        for (TripConnection connection: tripGraph.getEdges()) {
            edgeVariables.put(connection.getId(), model.addVariable(0, 1, Variable.VariableType.BINARY,
                connection.getCost(), "v_" + connection.getId()));
        }

        logger.debug("Add incoming connection constraints");
        LinearExpression incomingEdges;
        LinearExpression outgoingEdges;
        for (TripNode tripNode: tripGraph.getNodes()) {
            if (tripNode.isDepot()) {
                continue;
            }
            incomingEdges = model.createExpression();
            for (TripConnection incomingConnection: tripGraph.getIncomingEdges(tripNode)) {
                incomingEdges.add(edgeVariables.get(incomingConnection.getId()));
            }
            outgoingEdges = model.createExpression();
            for (TripConnection outgoingConnection: tripGraph.getOutgoingEdges(tripNode)) {
                outgoingEdges.add(edgeVariables.get(outgoingConnection.getId()));
            }
            model.addConstraint(incomingEdges, Constraint.ConstraintSense.EQUAL, 1, "c_inc_" + tripNode.getId());
            model.addConstraint(outgoingEdges, Constraint.ConstraintSense.EQUAL, 1, "c_out_" + tripNode.getId());
        }

        if (parameters.writeLpFile()) {
            logger.debug("Write lp file");
            model.write("vehicle-scheduling/vsModel.lp");
        }

        logger.debug("Start optimization");
        model.solve();
        logger.debug("End optimization");

        Model.Status status = model.getStatus();
        if (model.getIntAttribute(Model.IntAttribute.NUM_SOLUTIONS) > 0) {
            if (status == Model.Status.OPTIMAL) {
                logger.debug("Optimal solution found");
            }
            else {
                logger.debug("Feasible solution found");
            }
            // Create the vehicle schedule from the solution information
            List<TripConnection> usedTripConnections = new LinkedList<>();
            for (TripConnection connection : tripGraph.getEdges()) {
                if (Math.round(model.getValue(edgeVariables.get(connection.getId()))) > 0) {
                    usedTripConnections.add(connection);
                }
            }
            return computeSchedule(usedTripConnections, parameters, depot, ptn);
        }
        logger.warn("No feasible solution found!");
        if (status == Model.Status.INFEASIBLE) {
            logger.debug("Problem is infeasibe");
            model.computeIIS("vehicle-scheduling/vsModel.ilp");
        }
        return null;
    }
}
