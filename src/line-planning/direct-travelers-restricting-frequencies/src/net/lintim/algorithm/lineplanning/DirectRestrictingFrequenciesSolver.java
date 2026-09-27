package net.lintim.algorithm.lineplanning;

import net.lintim.algorithm.Dijkstra;
import net.lintim.exception.ConfigTypeMismatchException;
import net.lintim.exception.LinTimException;
import net.lintim.model.*;
import net.lintim.util.Logger;
import net.lintim.util.Pair;
import net.lintim.util.SolverType;
import net.lintim.util.lineplanning.Parameters;

import java.util.*;
import java.util.function.Function;

public abstract class DirectRestrictingFrequenciesSolver {

    private static final Logger logger = new Logger(DirectRestrictingFrequenciesSolver.class);

    public static DirectRestrictingFrequenciesSolver getDirectRestrictingFrequenciesSolver(SolverType solverType) {
        Class<?> solverClass;
        try {
            switch (solverType) {
                case GUROBI:
                    logger.debug("Will use Gurobi for optimization");
                    solverClass = Class.forName("net.lintim.algorithm.lineplanning.DirectRestrictingFrequenciesGurobi");
                    return (DirectRestrictingFrequenciesSolver) solverClass.getDeclaredConstructor().newInstance();
                default:
                    logger.debug("Will use Core for optimization");
                    solverClass = Class.forName("net.lintim.algorithm.lineplanning.DirectRestrictingFrequenciesCore");
                    return (DirectRestrictingFrequenciesSolver) solverClass.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            logger.error("Could not load solver " + solverType + ", can you use it on your system?");
            throw new LinTimException(e.getMessage());
        }
    }

    /**
     * Solve the direct line planning problem while restricting the possible frequencies. Will use the shortest paths
     * as preferable paths (length of the path is determined by config parameter)
     *
     * @param ptn        the ptn
     * @param od         the od matrix
     * @param linePool   the pool of possible lines
     * @param parameters the settings of the model
     * @return whether a feasible solution could be found
     */
    public boolean solveLinePlanningDirect(Graph<Stop, Link> ptn, OD od, LinePool linePool, Parameters parameters) {
        return solveLinePlanningDirect(ptn, od, linePool, parameters, computeShortestPaths(ptn, od, parameters));
    }

    /**
     * Solve the direct line planning problem while restricting the possible frequencies.
     *
     * @param ptn             the ptn
     * @param od              the od matrix
     * @param linePool        the pool of possible lines
     * @param parameters      the settings of the model
     * @param preferablePaths the preferable paths of the passengers
     * @return whether a feasible solution could be found
     */
    public abstract boolean solveLinePlanningDirect(Graph<Stop, Link> ptn, OD od, LinePool linePool, Parameters parameters,
                                                  Map<Pair<Integer, Integer>, Collection<Path<Stop, Link>>>
                                                      preferablePaths);

    /**
     * Compute the shortest paths for all the passengers. For each od pair, the returned map will contain a
     * collection of shortest paths. The length of a path will be determined by the config parameter
     * "ean_model_weight_drive".
     *
     * @param ptn        the ptn
     * @param od         the od pairs
     * @param parameters the parameters
     * @return a map of all shortest paths
     */
    private static HashMap<Pair<Integer, Integer>, Collection<Path<Stop, Link>>> computeShortestPaths(Graph<Stop,
        Link> ptn, OD od, Parameters parameters) {
        HashMap<Pair<Integer, Integer>, Collection<Path<Stop, Link>>> paths = new HashMap<>();
        //First determine what the length of an edge in a shortest path should be
        Function<Link, Double> lengthFunction;
        switch (parameters.getWeightDrive()) {
            case "AVERAGE_DRIVING_TIME":
                lengthFunction = link -> (link.getLowerBound() + link.getUpperBound()) / 2.0;
                break;
            case "MINIMAL_DRIVING_TIME":
                lengthFunction = link -> (double) link.getLowerBound();
                break;
            case "MAXIMAL_DRIVING_TIME":
                lengthFunction = link -> (double) link.getUpperBound();
                break;
            case "EDGE_LENGTH":
                lengthFunction = Link::getLength;
                break;
            default:
                throw new ConfigTypeMismatchException("ean_model_weight_drive", "String", parameters.getWeightDrive());
        }
        //Now iterate all od pairs, compute shortest path and add them to the returned map
        for (Stop origin : ptn.getNodes()) {
            Dijkstra<Stop, Link, Graph<Stop, Link>> dijkstra = new Dijkstra<>(ptn, origin, lengthFunction);
            dijkstra.computeShortestPaths();
            for (Stop destination : ptn.getNodes()) {
                if (od.getValue(origin.getId(), destination.getId()) == 0) {
                    continue;
                }
                Collection<Path<Stop, Link>> odPath = dijkstra.getPaths(destination);
                if (odPath.size() == 0) {
                    logger.warn("Found no path from " + origin + " to " + destination + "but there are "
                        + od.getValue(origin.getId(), destination.getId()) + " passengers");
                }
                paths.put(new Pair<>(origin.getId(), destination.getId()), odPath);
            }
        }
        return paths;
    }

    /**
     * Determine whether the given line contain any of the given paths
     *
     * @param line  the line to contain paths
     * @param paths the paths to check
     * @return whether a path is contained in the line
     */
    private static Pair<Boolean, Path<Stop, Link>> lineContainsPath(Line line, Collection<Path<Stop, Link>> paths) {
        for (Path<Stop, Link> path : paths) {
            if (line.getLinePath().contains(path)) {
                return new Pair<>(true, path);
            }
        }
        return new Pair<>(false, null);
    }

    /**
     * Find all acceptable line ids for the given preferable paths. For each line, there will be a check, whether it
     * contains any shortest path for a passenger. If this is the case, the line id is added for this passenger
     *
     * @param linePool        the linepool
     * @param preferablePaths the preferable paths of the passengers
     * @param ptn             the ptn
     * @return a mapping of an od-pair to the set of acceptable line ids
     */
    public static Map<Pair<Integer, Integer>, Map<Integer, Path<Stop, Link>>> computeAcceptableLineIds(LinePool linePool,
                                                                                                       Map<Pair<Integer,
                                                                                                           Integer>,
                                                                                                           Collection<Path<Stop, Link>>> preferablePaths,
                                                                                                       Graph<Stop, Link> ptn) {
        Map<Pair<Integer, Integer>, Map<Integer, Path<Stop, Link>>> acceptableLineIds = new HashMap<>();
        for (Pair<Integer, Integer> odPair : preferablePaths.keySet()) {
            acceptableLineIds.put(odPair, new HashMap<>());
            for (Line line : linePool.getLines()) {
                if (!line.getLinePath().contains(ptn.getNode(odPair.getFirstElement())) || !line.getLinePath()
                    .contains(ptn.getNode(odPair.getSecondElement()))) {
                    continue;
                }
                Pair<Boolean, Path<Stop, Link>> isContained = lineContainsPath(line, preferablePaths.get(odPair));
                if (isContained.getFirstElement()) {
                    acceptableLineIds.get(odPair).put(line.getId(), isContained.getSecondElement());
                }
            }
        }
        return acceptableLineIds;
    }
}
