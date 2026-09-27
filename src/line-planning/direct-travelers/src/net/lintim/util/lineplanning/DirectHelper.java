package net.lintim.util.lineplanning;

import net.lintim.algorithm.Dijkstra;
import net.lintim.exception.ConfigTypeMismatchException;
import net.lintim.model.*;
import net.lintim.util.Logger;
import net.lintim.util.Pair;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class DirectHelper {

    private static final Logger logger = new Logger(DirectHelper.class);

    public static HashMap<Pair<Integer, Integer>, Collection<Path<Stop, Link>>> computeShortestPaths(Graph<Stop,
        Link> ptn, OD od, DirectParameters parameters) {
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
                if (origin.equals(destination) || od.getValue(origin.getId(), destination.getId()) == 0) {
                    continue;
                }
                Collection<Path<Stop, Link>> odPath = dijkstra.getPaths(destination);
                if (odPath == null || odPath.size() == 0) {
                    logger.warn("Found no path from " + origin + " to " + destination + "but there are "
                        + od.getValue(origin.getId(), destination.getId()) + " passengers");
                }
                paths.put(new Pair<>(origin.getId(), destination.getId()), odPath);
            }
        }
        return paths;
    }

    private static Pair<Boolean, Path<Stop, Link>> lineContainsPath(Line line, Collection<Path<Stop, Link>> paths) {
        for (Path<Stop, Link> path : paths) {
            if (line.getLinePath().contains(path)) {
                return new Pair<>(true, path);
            }
        }
        return new Pair<>(false, null);
    }

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
