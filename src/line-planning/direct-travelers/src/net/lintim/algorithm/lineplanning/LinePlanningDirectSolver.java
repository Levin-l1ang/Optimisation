package net.lintim.algorithm.lineplanning;

import net.lintim.exception.LinTimException;
import net.lintim.model.*;
import net.lintim.util.Logger;
import net.lintim.util.Pair;
import net.lintim.util.SolverType;
import net.lintim.util.lineplanning.DirectParameters;
import net.lintim.util.lineplanning.DirectSolutionDescriptor;

import java.util.Collection;
import java.util.Map;

public abstract class LinePlanningDirectSolver {
    private static final Logger logger = new Logger(LinePlanningDirectSolver.class);

    public abstract DirectSolutionDescriptor solveLinePlanningDirect(Graph<Stop, Link> ptn, OD od, LinePool linePool,
                                                                     DirectParameters parameters);

    public abstract DirectSolutionDescriptor solveLinePlanningDirect(Graph<Stop, Link> ptn, OD od, LinePool linePool,
                                                            Map<Pair<Integer, Integer>, Collection<Path<Stop, Link>>>
                                                                preferablePaths, DirectParameters parameters);

    public abstract boolean preprocessPassengersForFixedLines(OD od, Map<Pair<Integer, Integer>,
        Collection<Path<Stop, Link>>> preferablePaths, Graph<Stop, Link> ptn, LinePool fixedLines,
                                                              Map<Line, Integer> fixedLineCapacities,
                                                              DirectParameters parameters);

    public static LinePlanningDirectSolver getLinePlanningDirectSolver(SolverType solverType) {
        Class<?> solverClass;
        try {
            switch (solverType) {
                case GUROBI:
                    logger.debug("Will use Gurobi for optimization");
                    solverClass = Class.forName("net.lintim.algorithm.lineplanning.DirectGurobi");
                    return (LinePlanningDirectSolver) solverClass.getDeclaredConstructor().newInstance();
                default:
                    logger.debug("Will use solver agnostic framework, solver type is " + solverType);
                    solverClass = Class.forName("net.lintim.algorithm.lineplanning.DirectSolverAgnostic");
                    return (LinePlanningDirectSolver) solverClass.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            logger.error("Could not load solver " + solverType + ", can you use it on your system?");
            throw new LinTimException(e.getMessage());
        }
    }
}
