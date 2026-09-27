package net.lintim.algorithm.lineplanning;

import net.lintim.model.*;
import net.lintim.solver.*;
import net.lintim.util.Logger;
import net.lintim.util.Pair;
import net.lintim.util.lineplanning.DirectHelper;
import net.lintim.util.lineplanning.DirectParameters;
import net.lintim.util.lineplanning.DirectSolutionDescriptor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static net.lintim.util.lineplanning.DirectHelper.computeAcceptableLineIds;

public class DirectSolverAgnostic extends LinePlanningDirectSolver{

    private static final Logger logger = new Logger(DirectSolverAgnostic.class);

    @Override
    public DirectSolutionDescriptor solveLinePlanningDirect(Graph<Stop, Link> ptn, OD od, LinePool linePool, DirectParameters parameters) {
        return solveLinePlanningDirect(ptn, od, linePool, DirectHelper.computeShortestPaths(ptn, od, parameters), parameters);
    }

    @Override
    public DirectSolutionDescriptor solveLinePlanningDirect(Graph<Stop, Link> ptn, OD od, LinePool linePool, Map<Pair<Integer, Integer>, Collection<Path<Stop, Link>>> preferablePaths, DirectParameters parameters) {
        Solver solver = Solver.createSolver(parameters.getSolverType());
        Model model = solver.createModel();
        parameters.setSolverParameters(model);
        logger.debug("Precomputation, see which lines can be used directly by which passengers");
        Map<Pair<Integer, Integer>, Map<Integer, Path<Stop, Link>>> acceptableLineIds = computeAcceptableLineIds(linePool,
            preferablePaths, ptn);
        logger.debug("Add variables");
        //Notation is used from the public transportation script of Prof. Schöbel. When you need explanation on
        // the meaning of the variables/constraints, please see the script. We will try to use the same names as
        // mentioned there.
        //d[i][j][l] = number of passenger directly travelling from i to j using line with id l
        HashMap<Integer, HashMap<Integer, HashMap<Integer, Variable>>> d = new HashMap<>();
        double directFactor = parameters.getDirectFactor();
        double costFactor = parameters.getCostFactor();
        logger.debug("Using directFactor " + directFactor + " and costFactor " + costFactor);
        for (Stop origin : ptn.getNodes()) {
            d.put(origin.getId(), new HashMap<>());
            for (Stop destination : ptn.getNodes()) {
                d.get(origin.getId()).put(destination.getId(), new HashMap<>());
                LinearExpression sumOfAllVariablesPerODPair = model.createExpression();
                if (origin.equals(destination) || od.getValue(origin.getId(), destination.getId()) == 0) {
                    continue;
                }
                for (int lineId : acceptableLineIds.get(new Pair<>(origin.getId(), destination.getId())).keySet()) {
                    d.get(origin.getId()).get(destination.getId()).put(lineId, model.addVariable(0, od.getValue
                        (origin.getId(), destination.getId()), Variable.VariableType.INTEGER, directFactor, "d_" + origin.getId() + "_" +
                        destination.getId() + "_" + lineId));
                    sumOfAllVariablesPerODPair.multiAdd(1, d.get(origin.getId()).get(destination.getId())
                        .get(lineId));
                }
                //Constraint 3.6 -> Ensure that only the number of passengers on an od pair can travel directly
                model.addConstraint(sumOfAllVariablesPerODPair, Constraint.ConstraintSense.LESS_EQUAL, od.getValue(origin.getId(),
                    destination.getId()), "od_constraint_" + origin.getId() + "_" + destination.getId());

            }
        }
        //f[l] = frequency of line l
        HashMap<Integer, Variable> f = new HashMap<>();
        for (Line line : linePool.getLines()) {
            Variable frequency = model.addVariable(0, Double.POSITIVE_INFINITY, Variable.VariableType.INTEGER, -1 * costFactor * line.getCost(), "f_" + line.getId());
            f.put(line.getId(), frequency);
            Variable systemFrequencyDivisor = model.addVariable(0, Double.POSITIVE_INFINITY, Variable.VariableType.INTEGER, 0, "g_" +
                line.getId());
            LinearExpression expr = model.createExpression();
            expr.multiAdd(parameters.getCommonFrequencyDivisor(), systemFrequencyDivisor);
            expr.multiAdd(-1, frequency);
            model.addConstraint(expr, Constraint.ConstraintSense.EQUAL, 0, "systemFrequency_" + line.getId());
        }
        logger.debug("Add capacity constraints");
        //Constraint 3.7 -> Ensure that the capacity of each line is not exceeded
        int capacity = parameters.getCapacity();
        for (Link link : ptn.getEdges()) {
            HashMap<Integer, LinearExpression> directTravellersOnLineAndEdge = new HashMap<>();
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
                        directTravellersOnLineAndEdge.computeIfAbsent(line.getId(), k -> model.createExpression());
                        directTravellersOnLineAndEdge.get(line.getId()).multiAdd(1, d.get(origin.getId()).get
                            (destination.getId()).get(line.getId()));
                    }
                }
                LinearExpression capacityOfLine = model.createExpression();
                capacityOfLine.multiAdd(capacity, f.get(line.getId()));
                //There may be a line that is not acceptable for anybody
                LinearExpression directTravellersOnLine = directTravellersOnLineAndEdge.get(line.getId());
                if (directTravellersOnLine != null) {
                    capacityOfLine.multiAdd(-1, directTravellersOnLine);
                    model.addConstraint(capacityOfLine, Constraint.ConstraintSense.GREATER_EQUAL, 0,
                        "capacity_constraint_" + link.getId() + "_" + line.getId());
                }
            }
        }
        //Constraint 3.8 -> Ensure the upper and lower frequency bounds on the links
        logger.debug("Add upper and lower frequency bounds");
        for (Link link : ptn.getEdges()) {
            LinearExpression sumOfFrequencies = model.createExpression();
            for (Line line : linePool.getLines()) {
                if (line.getLinePath().contains(link)) {
                    sumOfFrequencies.multiAdd(1, f.get(line.getId()));
                }
            }
            model.addConstraint(sumOfFrequencies, Constraint.ConstraintSense.LESS_EQUAL, link.getUpperFrequencyBound(), "f_" + link
                .getId() + "_max");
            model.addConstraint(sumOfFrequencies, Constraint.ConstraintSense.GREATER_EQUAL, link.getLowerFrequencyBound(), "f_" +
                link.getId() + "_min");
        }
        //Budget constraints -> Restrict the costs of the line concept
        logger.debug("Add budget constraint");
        double budget = parameters.getBudget();
        LinearExpression costOfLineConcept = model.createExpression();
        for (Line line : linePool.getLines()) {
            costOfLineConcept.multiAdd(line.getCost(), f.get(line.getId()));
        }
        model.addConstraint(costOfLineConcept, Constraint.ConstraintSense.LESS_EQUAL, budget, "budget");
        model.setSense(Model.OptimizationSense.MAXIMIZE);
        if (parameters.writeLpFile()) {
            model.write("line-planning/direct-model.lp");
        }
        logger.debug("Start optimizing");
        model.solve();
        logger.debug("End optimization");
        Model.Status status = model.getStatus();
        if (model.getIntAttribute(Model.IntAttribute.NUM_SOLUTIONS) > 0) {
            if (status == Model.Status.OPTIMAL) {
                logger.debug("Optimal solution found");
            } else {
                logger.debug("Feasible solution found");
            }
            logger.debug("Read back solution");
            //Read the frequencies and set the lines accordingly
            for (Line line : linePool.getLines()) {
                line.setFrequency((int) Math.round(model.getValue(f.get(line.getId()))));
            }
            return new DirectSolutionDescriptor(true, model.getDoubleAttribute(Model.DoubleAttribute.OBJ_VAL));
        }
        logger.debug("No feasible solution found");
        if (status == Model.Status.INFEASIBLE && parameters.getCommonFrequencyDivisor() == 1) {
            logger.debug("The problem is infeasible!");
            model.computeIIS("line-planning/direct-model.ilp");
        }
        return new DirectSolutionDescriptor(false, Double.NEGATIVE_INFINITY);
    }

    public boolean preprocessPassengersForFixedLines(OD od, Map<Pair<Integer, Integer>,
        Collection<Path<Stop, Link>>> preferablePaths, Graph<Stop, Link> ptn, LinePool fixedLines, Map<Line, Integer> fixedLineCapacities,
                                                     DirectParameters parameters) {
        Map<Pair<Integer, Integer>, Map<Integer, Path<Stop, Link>>> acceptableLineIds = computeAcceptableLineIds(fixedLines,
            preferablePaths, ptn);
        Solver solver = Solver.createSolver(parameters.getSolverType());
        Model model = solver.createModel();
        parameters.setSolverParameters(model);
        model.setSense(Model.OptimizationSense.MAXIMIZE);
        HashMap<Integer, HashMap<Integer, HashMap<Integer, Variable>>> d = new HashMap<>();
        for (Stop origin : ptn.getNodes()) {
            d.put(origin.getId(), new HashMap<>());
            for (Stop destination : ptn.getNodes()) {
                d.get(origin.getId()).put(destination.getId(), new HashMap<>());
                LinearExpression sumOfAllVariablesPerODPair = model.createExpression();
                if (origin.equals(destination) || od.getValue(origin.getId(), destination.getId()) == 0) {
                    continue;
                }
                for (int lineId : acceptableLineIds.get(new Pair<>(origin.getId(), destination.getId())).keySet()) {
                    d.get(origin.getId()).get(destination.getId()).put(lineId, model.addVariable(0, od.getValue
                        (origin.getId(), destination.getId()), Variable.VariableType.INTEGER, 1, "d_" + origin.getId() + "_" +
                        destination.getId() + "_" + lineId));
                    sumOfAllVariablesPerODPair.multiAdd(1, d.get(origin.getId()).get(destination.getId())
                        .get(lineId));
                }
                //Constraint 3.6 -> Ensure that only the number of passengers on an od pair can travel directly
                model.addConstraint(sumOfAllVariablesPerODPair, Constraint.ConstraintSense.LESS_EQUAL, od.getValue(origin.getId(),
                    destination.getId()), "od_constraint_" + origin.getId() + "_" + destination.getId());
            }
        }
        //Constraint 3.7 -> Ensure that the capacity of each line is not exceeded
        for (Link link : ptn.getEdges()) {
            HashMap<Integer, LinearExpression> directTravellersOnLineAndEdge = new HashMap<>();
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
                        directTravellersOnLineAndEdge.computeIfAbsent(line.getId(), k -> model.createExpression());
                        directTravellersOnLineAndEdge.get(line.getId()).multiAdd(1, d.get(origin.getId()).get
                            (destination.getId()).get(line.getId()));
                    }
                }
                LinearExpression capacityOfLine = model.createExpression();
                capacityOfLine.addConstant(fixedLineCapacities.get(line) * line.getFrequency());
                //There may be a line that is not acceptable for anybody
                LinearExpression directTravellersOnLine = directTravellersOnLineAndEdge.get(line.getId());
                if (directTravellersOnLine != null) {
                    capacityOfLine.multiAdd(-1, directTravellersOnLine);
                    model.addConstraint(capacityOfLine, Constraint.ConstraintSense.GREATER_EQUAL, 0,
                        "capacity_constraint_" + link.getId() + "_" + line.getId());
                }
            }
        }
        if (parameters.writeLpFile()) {
            model.write("line-planning/direct-model-preprocess.lp");
        }
        logger.debug("Start optimizing");
        model.solve();
        logger.debug("End optimization");
        Model.Status status = model.getStatus();
        if (model.getIntAttribute(Model.IntAttribute.NUM_SOLUTIONS) > 0) {
            logger.debug("Read back solution");
            for (Stop origin : ptn.getNodes()) {
                for (Stop destination : ptn.getNodes()) {
                    if (origin.equals(destination) || od.getValue(origin.getId(), destination.getId()) == 0) {
                        continue;
                    }
                    for (int lineId : acceptableLineIds.get(new Pair<>(origin.getId(), destination.getId())).keySet()) {
                        double passengersUsingLine = model.getValue(d.get(origin.getId()).get(destination.getId()).get(lineId));
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
        if (status == Model.Status.INFEASIBLE) {
            logger.debug("The problem is infeasible!");
            model.computeIIS("line-planning/direct-model-preprocess.ilp");
        }
        return false;

    }
}
