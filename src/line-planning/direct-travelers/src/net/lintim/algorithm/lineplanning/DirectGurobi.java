package net.lintim.algorithm.lineplanning;

import com.gurobi.gurobi.*;
import net.lintim.exception.SolverGurobiException;
import net.lintim.solver.impl.GurobiHelper;
import net.lintim.util.lineplanning.DirectHelper;
import net.lintim.util.lineplanning.DirectParameters;
import net.lintim.util.lineplanning.DirectSolutionDescriptor;
import net.lintim.model.*;
import net.lintim.util.*;

import java.util.*;

import static net.lintim.util.lineplanning.DirectHelper.computeAcceptableLineIds;

/**
 *
 */
public class DirectGurobi extends LinePlanningDirectSolver {

    private static final Logger logger = new Logger(DirectGurobi.class.getCanonicalName());

    /**
     * Solve the direct line planning problem without a given set of preferable paths, i.e., the shortest paths for each
     * passenger will be computed beforehand and set as preferable.
     *
     * @param ptn        the infrastructure network
     * @param od         the demand
     * @param linePool   the line pool
     * @param parameters the parameters of the model
     * @return a descriptor of the found solution
     */
    public DirectSolutionDescriptor solveLinePlanningDirect(Graph<Stop, Link> ptn, OD od, LinePool linePool,
                                                                   DirectParameters parameters) {
        return solveLinePlanningDirect(ptn, od, linePool, DirectHelper.computeShortestPaths(ptn, od, parameters),
            parameters);
    }

    /**
     * Solve the direct line planning problem for a given set of preferable paths.
     *
     * @param ptn             the infrastructure network
     * @param od              the demand
     * @param linePool        the line pool
     * @param preferablePaths the preferable paths of the passengers. A passenger is counted as a direct traveller if
     *                        it can travel directly on a preferable path.
     * @param parameters      the parameters of the model
     * @return a descriptor of the found solution
     */
    public DirectSolutionDescriptor solveLinePlanningDirect(Graph<Stop, Link> ptn, OD od, LinePool linePool,
                                                                   Map<Pair<Integer, Integer>, Collection<Path<Stop, Link>>>
                                                                       preferablePaths, DirectParameters parameters) {
        try {
            GRBEnv env = new GRBEnv();
            GRBModel directModel = new GRBModel(env);
            GurobiHelper.setGurobiSolverParameters(directModel, parameters);
            logger.debug("Precomputation, see which lines can be used directly by which passengers");
            Map<Pair<Integer, Integer>, Map<Integer, Path<Stop, Link>>> acceptableLineIds = computeAcceptableLineIds(linePool,
                preferablePaths, ptn);
            logger.debug("Add variables");
            //Notation is used from the public transportation script of Prof. Schöbel. When you need explanation on
            // the meaning of the variables/constraints, please see the script. We will try to use the same names as
            // mentioned there.
            //d[i][j][l] = number of passenger directly travelling from i to j using line with id l
            HashMap<Integer, HashMap<Integer, HashMap<Integer, GRBVar>>> d = new HashMap<>();
            double directFactor = parameters.getDirectFactor();
            double costFactor = parameters.getCostFactor();
            logger.debug("Using directFactor " + directFactor + " and costFactor " + costFactor);
            for (Stop origin : ptn.getNodes()) {
                d.put(origin.getId(), new HashMap<>());
                for (Stop destination : ptn.getNodes()) {
                    d.get(origin.getId()).put(destination.getId(), new HashMap<>());
                    GRBLinExpr sumOfAllVariablesPerODPair = new GRBLinExpr();
                    if (origin.equals(destination) || od.getValue(origin.getId(), destination.getId()) == 0) {
                        continue;
                    }
                    for (int lineId : acceptableLineIds.get(new Pair<>(origin.getId(), destination.getId())).keySet()) {
                        d.get(origin.getId()).get(destination.getId()).put(lineId, directModel.addVar(0, od.getValue
                            (origin.getId(), destination.getId()), directFactor, GRB.INTEGER, "d_" + origin.getId() + "_" +
                            destination.getId() + "_" + lineId));
                        sumOfAllVariablesPerODPair.addTerm(1, d.get(origin.getId()).get(destination.getId())
                            .get(lineId));
                    }
                    //Constraint 3.6 -> Ensure that only the number of passengers on an od pair can travel directly
                    directModel.addConstr(sumOfAllVariablesPerODPair, GRB.LESS_EQUAL, od.getValue(origin.getId(),
                        destination.getId()), "od_constraint_" + origin.getId() + "_" + destination.getId());
                }
            }
            //f[l] = frequency of line l
            HashMap<Integer, GRBVar> f = new HashMap<>();
            for (Line line : linePool.getLines()) {
                GRBVar frequency = directModel.addVar(0, GRB.INFINITY, -1 * costFactor * line.getCost(), GRB.INTEGER, "f_" + line.getId());
                f.put(line.getId(), frequency);
                GRBVar systemFrequencyDivisor = directModel.addVar(0, GRB.INFINITY, 0, GRB.INTEGER, "g_" +
                    line.getId());
                GRBLinExpr rhs = new GRBLinExpr();
                rhs.addTerm(parameters.getCommonFrequencyDivisor(), systemFrequencyDivisor);
                directModel.addConstr(frequency, GRB.EQUAL, rhs, "systemFrequency_" + line.getId());
            }
            logger.debug("Add capacity constraints");
            //Constraint 3.7 -> Ensure that the capacity of each line is not exceeded
            int capacity = parameters.getCapacity();
            for (Link link : ptn.getEdges()) {
                HashMap<Integer, GRBLinExpr> directTravellersOnLineAndEdge = new HashMap<>();
                for (Line line : linePool.getLines()) {
                    if (!line.getLinePath().contains(link)) {
                        continue;
                    }
                    for (Stop origin : ptn.getNodes()) {
                        for (Stop destination : ptn.getNodes()) {
                            if (origin.equals(destination) || od.getValue(origin.getId(), destination.getId()) == 0) {
                                continue;
                            }
                            Pair<Integer, Integer> odPair = new Pair<>(origin.getId(), destination.getId());
                            if (!acceptableLineIds.get(odPair).containsKey(line.getId())) {
                                continue;
                            }
                            if (!acceptableLineIds.get(odPair).get(line.getId()).contains(link)) {
                                continue;
                            }
                            directTravellersOnLineAndEdge.computeIfAbsent(line.getId(), k -> new GRBLinExpr());
                            directTravellersOnLineAndEdge.get(line.getId()).addTerm(1, d.get(origin.getId()).get
                                (destination.getId()).get(line.getId()));
                        }
                    }
                    GRBLinExpr capacityOfLine = new GRBLinExpr();
                    capacityOfLine.addTerm(capacity, f.get(line.getId()));
                    //There may be a line that is not acceptable for anybody
                    GRBLinExpr directTravellersOnLine = directTravellersOnLineAndEdge.get(line.getId());
                    if (directTravellersOnLine != null) {
                        directModel.addConstr(directTravellersOnLine, GRB.LESS_EQUAL, capacityOfLine,
                            "capacity_constraint_" + link.getId() + "_" + line.getId());
                    }
                }
            }
            //Constraint 3.8 -> Ensure the upper and lower frequency bounds on the links
            logger.debug("Add upper and lower frequency bounds");
            for (Link link : ptn.getEdges()) {
                GRBLinExpr sumOfFrequencies = new GRBLinExpr();
                for (Line line : linePool.getLines()) {
                    if (line.getLinePath().contains(link)) {
                        sumOfFrequencies.addTerm(1, f.get(line.getId()));
                    }
                }
                directModel.addConstr(sumOfFrequencies, GRB.LESS_EQUAL, link.getUpperFrequencyBound(), "f_" + link
                    .getId() + "_max");
                directModel.addConstr(sumOfFrequencies, GRB.GREATER_EQUAL, link.getLowerFrequencyBound(), "f_" +
                    link.getId() + "_min");
            }

            //Budget constraints -> Restrict the costs of the line concept
            logger.debug("Add budget constraint");
            double budget = parameters.getBudget();
            GRBLinExpr costOfLineConcept = new GRBLinExpr();
            for (Line line : linePool.getLines()) {
                costOfLineConcept.addTerm(line.getCost(), f.get(line.getId()));
            }
            directModel.addConstr(costOfLineConcept, GRB.LESS_EQUAL, budget, "budget");
            directModel.set(GRB.IntAttr.ModelSense, GRB.MAXIMIZE);
            if (parameters.writeLpFile()) {
                directModel.write("line-planning/direct-model.lp");
            }
            logger.debug("Start optimizing");
            directModel.optimize();
            logger.debug("End optimization");
            int status = directModel.get(GRB.IntAttr.Status);
            if (directModel.get(GRB.IntAttr.SolCount) > 0) {
                if (status == GRB.OPTIMAL) {
                    logger.debug("Optimal solution found");
                } else {
                    logger.debug("Feasible solution found");
                }
                logger.debug("Read back solution");
                //Read the frequencies and set the lines accordingly
                for (Line line : linePool.getLines()) {
                    line.setFrequency((int) Math.round(f.get(line.getId()).get(GRB.DoubleAttr.X)));
                }
                return new DirectSolutionDescriptor(true, directModel.get(GRB.DoubleAttr.ObjVal));
            }
            logger.debug("No feasible solution found");
            if (status == GRB.INFEASIBLE && parameters.getCommonFrequencyDivisor() == 1) {
                logger.debug("The problem is infeasible!");
                directModel.computeIIS();
                directModel.write("line-planning/direct-model.ilp");
            }
            return new DirectSolutionDescriptor(false, Double.NEGATIVE_INFINITY);

        } catch (GRBException e) {
            throw new SolverGurobiException(e.toString());
        }
    }


    public boolean preprocessPassengersForFixedLines(OD od, Map<Pair<Integer, Integer>,
        Collection<Path<Stop, Link>>> preferablePaths, Graph<Stop, Link> ptn, LinePool fixedLines, Map<Line, Integer> fixedLineCapacities,
                                                            DirectParameters parameters) {
        Map<Pair<Integer, Integer>, Map<Integer, Path<Stop, Link>>> acceptableLineIds = computeAcceptableLineIds(fixedLines,
            preferablePaths, ptn);
        try {
            GRBEnv env = new GRBEnv();
            GRBModel directModel = new GRBModel(env);
            GurobiHelper.setGurobiSolverParameters(directModel, parameters);
            directModel.set(GRB.IntAttr.ModelSense, GRB.MAXIMIZE);
            HashMap<Integer, HashMap<Integer, HashMap<Integer, GRBVar>>> d = new HashMap<>();
            for (Stop origin : ptn.getNodes()) {
                d.put(origin.getId(), new HashMap<>());
                for (Stop destination : ptn.getNodes()) {
                    d.get(origin.getId()).put(destination.getId(), new HashMap<>());
                    GRBLinExpr sumOfAllVariablesPerODPair = new GRBLinExpr();
                    if (origin.equals(destination) || od.getValue(origin.getId(), destination.getId()) == 0) {
                        continue;
                    }
                    for (int lineId : acceptableLineIds.get(new Pair<>(origin.getId(), destination.getId())).keySet()) {
                        d.get(origin.getId()).get(destination.getId()).put(lineId, directModel.addVar(0, od.getValue
                            (origin.getId(), destination.getId()), 1, GRB.INTEGER, "d_" + origin.getId() + "_" +
                            destination.getId() + "_" + lineId));
                        sumOfAllVariablesPerODPair.addTerm(1, d.get(origin.getId()).get(destination.getId())
                            .get(lineId));
                    }
                    //Constraint 3.6 -> Ensure that only the number of passengers on an od pair can travel directly
                    directModel.addConstr(sumOfAllVariablesPerODPair, GRB.LESS_EQUAL, od.getValue(origin.getId(),
                        destination.getId()), "od_constraint_" + origin.getId() + "_" + destination.getId());
                }
            }
            //Constraint 3.7 -> Ensure that the capacity of each line is not exceeded
            for (Link link : ptn.getEdges()) {
                HashMap<Integer, GRBLinExpr> directTravellersOnLineAndEdge = new HashMap<>();
                for (Line line : fixedLines.getLines()) {
                    if (!line.getLinePath().contains(link)) {
                        continue;
                    }
                    for (Stop origin : ptn.getNodes()) {
                        for (Stop destination : ptn.getNodes()) {
                            if (origin.equals(destination) || od.getValue(origin.getId(), destination.getId()) == 0) {
                                continue;
                            }
                            if (!acceptableLineIds.get(new Pair<>(origin.getId(), destination.getId())).containsKey(line.getId())) {
                                continue;
                            }
                            if (!acceptableLineIds.get(new Pair<>(origin.getId(), destination.getId())).get(line.getId()).contains(link)) {
                                continue;
                            }
                            directTravellersOnLineAndEdge.computeIfAbsent(line.getId(), k -> new GRBLinExpr());
                            directTravellersOnLineAndEdge.get(line.getId()).addTerm(1, d.get(origin.getId()).get
                                (destination.getId()).get(line.getId()));
                        }
                    }
                    GRBLinExpr capacityOfLine = new GRBLinExpr();
                    capacityOfLine.addConstant(fixedLineCapacities.get(line) * line.getFrequency());
                    //There may be a line that is not acceptable for anybody
                    GRBLinExpr directTravellersOnLine = directTravellersOnLineAndEdge.get(line.getId());
                    if (directTravellersOnLine != null) {
                        directModel.addConstr(directTravellersOnLine, GRB.LESS_EQUAL, capacityOfLine,
                            "capacity_constraint_" + link.getId() + "_" + line.getId());
                    }
                }
            }
            if (parameters.writeLpFile()) {
                directModel.write("line-planning/direct-model-preprocess.lp");
            }
            logger.debug("Start optimizing");
            directModel.optimize();
            logger.debug("End optimization");
            int status = directModel.get(GRB.IntAttr.Status);
            if (directModel.get(GRB.IntAttr.SolCount) > 0) {
                logger.debug("Read back solution");
                for (Stop origin : ptn.getNodes()) {
                    for (Stop destination : ptn.getNodes()) {
                        if (origin.equals(destination) || od.getValue(origin.getId(), destination.getId()) == 0) {
                            continue;
                        }
                        for (int lineId : acceptableLineIds.get(new Pair<>(origin.getId(), destination.getId())).keySet()) {
                            double passengersUsingLine = d.get(origin.getId()).get(destination.getId()).get(lineId).get(GRB.DoubleAttr.X);
                            if (passengersUsingLine > 0) {
                                double oldOdValue = od.getValue(origin.getId(), destination.getId());
                                double newValue = oldOdValue - passengersUsingLine;
                                od.setValue(origin.getId(), destination.getId(), newValue);
                            }
                        }
                    }
                }
                return true;
            }
            logger.debug("No feasible solution found");
            if (status == GRB.INFEASIBLE) {
                logger.debug("The problem is infeasible!");
                directModel.computeIIS();
                directModel.write("line-planning/direct-model-preprocess.ilp");
            }
            return false;

        } catch (GRBException e) {
            throw new SolverGurobiException(e.toString());
        }
    }

}
