package net.lintim.algorithm.lineplanning;

import net.lintim.model.*;
import net.lintim.solver.*;
import net.lintim.util.Logger;
import net.lintim.util.Pair;
import net.lintim.util.lineplanning.Parameters;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DirectRestrictingFrequenciesCore extends DirectRestrictingFrequenciesSolver{

    private static final Logger logger = new Logger(DirectRestrictingFrequenciesSolver.class);
    @Override
    public boolean solveLinePlanningDirect(Graph<Stop, Link> ptn, OD od, LinePool linePool, Parameters parameters, Map<Pair<Integer, Integer>, Collection<Path<Stop, Link>>> preferablePaths) {
        Solver solver = Solver.createSolver(parameters.getSolverType());
        Model model = solver.createModel();
        parameters.setSolverParameters(model);
        model.setSense(Model.OptimizationSense.MAXIMIZE);
        logger.debug("Precomputation, see which lines can be used directly by which passengers");
        Map<Pair<Integer, Integer>, Map<Integer, Path<Stop, Link>>> acceptableLineIds = computeAcceptableLineIds(linePool,
            preferablePaths, ptn);
        logger.debug("Add variables");
        //Notation is used from the public transportation script of Prof. Schöbel. When you need explanation on
        // the meaning of the variables/constraints, please see the script. We will try to use the same names as
        // mentioned there.
        //d[i][j][l] = number of passenger directly travelling from i to j using line with id l
        Map<Integer, Map<Integer, Map<Integer, Variable>>> d = new HashMap<>();
        LinearExpression sumOfAllVariablesPerODPair;
        Variable directVar;
        double demand;
        for (Stop origin: ptn.getNodes()) {
            d.put(origin.getId(), new HashMap<>());
            for (Stop destination: ptn.getNodes()) {
                d.get(origin.getId()).put(destination.getId(), new HashMap<>());
                demand = od.getValue(origin.getId(), destination.getId());
                if (demand == 0) {
                    continue;
                }
                sumOfAllVariablesPerODPair = model.createExpression();
                for (int lineId: acceptableLineIds.get(new Pair<>(origin.getId(), destination.getId())).keySet()) {
                    directVar = model.addVariable(0, demand, Variable.VariableType.INTEGER, 1,
                        "d_" + origin.getId() + "_" + destination.getId() + "_" + lineId);
                    d.get(origin.getId()).get(destination.getId()).put(lineId, directVar);
                    sumOfAllVariablesPerODPair.add(directVar);
                }
                //Constraint 3.7 -> Ensure that only the number of passengers on an od pair can travel directly
                model.addConstraint(sumOfAllVariablesPerODPair, Constraint.ConstraintSense.LESS_EQUAL, demand,
                    "od_constraint_" + origin.getId() + "_" + destination.getId());
            }
        }
        //f[l][freq] = line l is used with frequency freq
        Map<Integer, Map<Integer, Variable>> f = new HashMap<>();
        LinearExpression lineFreqConstraint;
        Variable lineUsesFreq;
        for (Line line: linePool.getLines()) {
            f.put(line.getId(), new HashMap<>());
            lineFreqConstraint = model.createExpression();
            for (int freq = 0; freq <= parameters.getMaximalFrequency(); freq++) {
                lineUsesFreq = model.addVariable(0, 1, Variable.VariableType.BINARY, 0,
                    "f_" + line.getId() + "_" + freq);
                f.get(line.getId()).put(freq, lineUsesFreq);
                lineFreqConstraint.add(lineUsesFreq);
            }
            model.addConstraint(lineFreqConstraint, Constraint.ConstraintSense.EQUAL, 1,
                "line_" + line.getId() + "_uses_one_frequency");
        }
        logger.debug("Add capacity constraint");
        //Constraint 3.8 -> Ensure that the capacity of each line is not exceeded
        Map<Integer, LinearExpression> directTravellersOnLineAndEdge;
        LinearExpression capacityOfLine, directTravellersOnLine;
        for (Link link: ptn.getEdges()) {
            directTravellersOnLineAndEdge = new HashMap<>();
            for (Stop origin: ptn.getNodes()) {
                for (Stop destination: ptn.getNodes()) {
                    if (od.getValue(origin.getId(), destination.getId()) == 0) {
                        continue;
                    }
                    for (Map.Entry<Integer, Path<Stop, Link>> acceptableLinePart: acceptableLineIds
                        .get(new Pair<>(origin.getId(), destination.getId())).entrySet()) {
                        if (!acceptableLinePart.getValue().contains(link)) {
                            continue;
                        }
                        directTravellersOnLineAndEdge.computeIfAbsent(acceptableLinePart.getKey(), l -> model.createExpression())
                            .add(d.get(origin.getId()).get(destination.getId()).get(acceptableLinePart.getKey()));
                    }
                }
            }
            for (Line line: linePool.getLines()) {
                capacityOfLine = model.createExpression();
                for (int freq = 1; freq <= parameters.getMaximalFrequency(); freq++) {
                    capacityOfLine.multiAdd(freq * parameters.getCapacity(), f.get(line.getId()).get(freq));
                }
                directTravellersOnLine = directTravellersOnLineAndEdge.get(line.getId());
                if (directTravellersOnLine != null) {
                    model.addConstraint(directTravellersOnLine, Constraint.ConstraintSense.LESS_EQUAL, capacityOfLine,
                        "capacity_constraint_" + link.getId() + "_" + line.getId());
                }
            }
        }
        //Constraint 3.9 -> Ensure the upper and lower frequency bounds on the links
        logger.debug("Add upper and lower frequency bounds");
        LinearExpression sumOfFrequencies;
        for (Link link: ptn.getEdges()) {
            sumOfFrequencies = model.createExpression();
            for (Line line: linePool.getLines()) {
                if (line.getLinePath().contains(link)) {
                    for (int freq = 1; freq <= parameters.getMaximalFrequency(); freq++) {
                        sumOfFrequencies.multiAdd(freq, f.get(line.getId()).get(freq));
                    }
                }
            }
            model.addConstraint(sumOfFrequencies, Constraint.ConstraintSense.LESS_EQUAL, link.getUpperFrequencyBound(),
                "f_" + link.getId() + "_max");
            model.addConstraint(sumOfFrequencies, Constraint.ConstraintSense.GREATER_EQUAL, link.getLowerFrequencyBound(),
                "f_" + link.getId() + "min");
        }
        //Frequency constraints -> Restrict the number of frequencies
        LinearExpression freqConstraint;
        Variable freqUsed;
        if (parameters.getNumberOfPossibleFrequencies() > 0) {
            logger.debug("Add frequency constraint");
            freqConstraint = model.createExpression();
            for (int freq = 1; freq <= parameters.getMaximalFrequency(); freq++) {
                freqUsed = model.addVariable(0, 1, Variable.VariableType.BINARY, 0,
                    "freq_" + freq + "_used");
                freqConstraint.add(freqUsed);
                for (Line line: linePool.getLines()) {
                    model.addConstraint(freqUsed, Constraint.ConstraintSense.GREATER_EQUAL, f.get(line.getId()).get(freq),
                        "line_" + line.getId() + "_uses_freq_" + freq);
                }
            }
            model.addConstraint(freqConstraint, Constraint.ConstraintSense.LESS_EQUAL, parameters.getNumberOfPossibleFrequencies(), "freq_bound");
        }
        //Budget constraints -> Restrict the costs of the line concept
        if (parameters.getBudget() > 0) {
            logger.debug("Add budget constraint");
            LinearExpression costOfLineConcept = model.createExpression();
            for (Line line : linePool.getLines()) {
                for (int freq = 1; freq <= parameters.getMaximalFrequency(); freq++) {
                    costOfLineConcept.multiAdd(line.getCost() * freq, f.get(line.getId()).get(freq));
                }
            }
            model.addConstraint(costOfLineConcept, Constraint.ConstraintSense.LESS_EQUAL, parameters.getBudget(),
                "budget");
        }
        if (parameters.writeLpFile()) {
            logger.debug("Write lp file");
            model.write("line-planning/direct-restricting.lp");
        }
        logger.debug("Start optimizing");
        model.solve();
        logger.debug("End optimizing");

        Model.Status status = model.getStatus();
        if (model.getIntAttribute(Model.IntAttribute.NUM_SOLUTIONS) > 0) {
            if (status == Model.Status.OPTIMAL) {
                logger.debug("Optimal solution found");
            }
            else {
                logger.debug("Feasible solution found");
            }
            logger.debug("Read back solution");
            //Read the frequencies and set the lines accordingly
            for (Line line: linePool.getLines()) {
                for (int freq = 0; freq <= parameters.getMaximalFrequency(); freq++) {
                    if (Math.round(model.getValue(f.get(line.getId()).get(freq))) > 0) {
                        line.setFrequency(freq);
                        break;
                    }
                }
            }
            return true;
        }
        logger.debug("No feasible solution found");
        if (status == Model.Status.INFEASIBLE) {
            logger.debug("Compute IIS");
            model.write("line-planning/direct-restricting.ilp");
        }
        return false;
    }
}
