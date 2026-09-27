package net.lintim.util.stop_location;

import net.lintim.algorithm.Dijkstra;
import net.lintim.model.*;
import net.lintim.model.impl.ArrayListGraph;
import net.lintim.util.Logger;
import net.lintim.util.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class SLHelper {

    private static final Logger logger = new Logger(SLHelper.class.getCanonicalName());

    public static Pair<Set<Pair<Integer, Integer>>, Graph<Stop, Link>> createEdges(Graph<InfrastructureNode, InfrastructureEdge> infrastructureGraph,
                                                                                   Graph<Stop, Link> ptn, List<InfrastructureNode> stopsToBuild,
                                                                                   Set<Pair<Integer, Integer>> restrictedTurns,
                                                                                   Graph<InfrastructureNode, InfrastructureEdge> forbiddenEdges) {
        // Check which edges are between realised stops
        int nextLinkId = 1;
        // We need to find all connections between stops. For this, compute the shortest paths between all stop pairs
        // in the original infrastructure graph and add a direct connection if there is no other realised stop on
        // this path
        Set<Integer> nodeIdsToBuild = stopsToBuild.stream().map(InfrastructureNode::getId).collect(Collectors.toSet());
        boolean directedGraph = infrastructureGraph.isDirected();
        Graph<Stop, Link> forbiddenLinks = new ArrayListGraph<>();
        ptn.getNodes().forEach(forbiddenLinks::addNode);
        // We add a penalty for forbidden edges
        double penalty = forbiddenEdges != null ? 50000 : 0;
        Map<Link, Path<InfrastructureNode, InfrastructureEdge>> usedPaths = new HashMap<>();
        for (Stop origin: ptn.getNodes()) {
            logger.debug("Check " + origin + " for links");
            InfrastructureNode originNode = infrastructureGraph.getNode(Integer.parseInt(origin.getLongName()));
            Dijkstra<InfrastructureNode, InfrastructureEdge, Graph<InfrastructureNode, InfrastructureEdge>> dijkstra = new Dijkstra<>(infrastructureGraph, originNode, e -> e.getLowerBound() + ((forbiddenEdges != null && forbiddenEdges.getEdge(e.getId()) != null) ? penalty : 0));
            nextLinkId = processOriginStop(infrastructureGraph, ptn, restrictedTurns, forbiddenEdges, nextLinkId, nodeIdsToBuild, directedGraph, forbiddenLinks, usedPaths, origin, originNode, dijkstra);
            // For a forbidden network, we need to repeat the procedure again, but with the reversed penalty function.
            // This should allow to find the correct forbidden connections between stops as well
            if (forbiddenEdges != null) {
                dijkstra = new Dijkstra<>(infrastructureGraph, originNode, e -> e.getLowerBound() + ((forbiddenEdges.getEdge(e.getId()) != null) ? 0 : penalty));
                nextLinkId = processOriginStop(infrastructureGraph, ptn, restrictedTurns, forbiddenEdges, nextLinkId, nodeIdsToBuild, directedGraph, forbiddenLinks, usedPaths, origin, originNode, dijkstra);
            }
            for (Stop destination: ptn.getNodes()) {
                List<Link> links = ptn.getOutgoingEdges(origin).stream().filter(l -> l.getRightNode().equals(destination)).collect(Collectors.toList());
                if (links.size() > 0) {
                    logger.debug("Links from " + origin + " to " + destination + ":");
                    for (Link link : links) {
                        logger.debug("Link " + link + " usedPath: " + usedPaths.get(link));
                    }
                }
            }
        }
        Set<Pair<Integer, Integer>> linkTurns = new HashSet<>();
        // Iterate restricted turns and check if they are present in the ptn
        logger.debug("Checking for restricted turns");
        for (Pair<Integer, Integer> turn: restrictedTurns) {
            InfrastructureEdge firstEdge = infrastructureGraph.getEdge(turn.getFirstElement());
            InfrastructureEdge secondEdge = infrastructureGraph.getEdge(turn.getSecondElement());
            logger.debug("Looking for infrastructure turn " + firstEdge + ", " + secondEdge);
            // Check if we find corresponding stops and links
            Link firstLink = ptn.getEdge((Link l) -> l.getLeftNode().getLongName().equals(firstEdge.getLeftNode().getName()) && l.getRightNode().getLongName().equals(firstEdge.getRightNode().getName()), true);
            Link secondLink = ptn.getEdge((Link l) -> l.getLeftNode().getLongName().equals(secondEdge.getLeftNode().getName()) && l.getRightNode().getLongName().equals(secondEdge.getRightNode().getName()), true);
            logger.debug("Found links " + firstLink + ", " + secondLink);
            if (firstLink != null && secondLink != null) {
                System.out.println("Found link turn");
                linkTurns.add(new Pair<>(firstLink.getId(), secondLink.getId()));
            }
        }
        logger.debug("Found " + linkTurns.size() + " directly restricted turns");
        logger.debug("Search for restricted turns as part of links");
        // Another possibility would be if one link ends and another link starts with parts of a restricted turn. Check this as well
        for (Link firstLink: ptn.getEdges()) {
            InfrastructureEdge firstEdgeInFirst = usedPaths.get(firstLink).getEdges().get(0);
            InfrastructureEdge lastEdgeInFirst = usedPaths.get(firstLink).getEdges().get(usedPaths.get(firstLink).getEdges().size()-1);
            for (Link secondLink: ptn.getEdges()) {
                if (firstLink == secondLink) {
                    continue;
                }
                InfrastructureEdge firstEdgeInSecond = usedPaths.get(secondLink).getEdges().get(0);
                InfrastructureEdge lastEdgeInSecond = usedPaths.get(secondLink).getEdges().get(usedPaths.get(secondLink).getEdges().size()-1);
                if (restrictedTurns.contains(new Pair<>(lastEdgeInFirst.getId(), firstEdgeInSecond.getId()))) {
                    linkTurns.add(new Pair<>(firstLink.getId(), secondLink.getId()));
                    continue;
                }
                if (!ptn.isDirected()) {
                    // Check if a restricted turn is contained in the other direction
                    if (restrictedTurns.contains(new Pair<>(lastEdgeInSecond.getId(), firstEdgeInFirst.getId()))) {
                        linkTurns.add(new Pair<>(secondLink.getId(), firstLink.getId()));
                    }
                }
            }
        }
        logger.debug("Now have " + linkTurns.size() + " restricted turns");
        return new Pair<>(linkTurns, forbiddenLinks);
    }

    private static int processOriginStop(Graph<InfrastructureNode, InfrastructureEdge> infrastructureGraph, Graph<Stop, Link> ptn, Set<Pair<Integer, Integer>> restrictedTurns, Graph<InfrastructureNode, InfrastructureEdge> forbiddenEdges, int nextLinkId, Set<Integer> nodeIdsToBuild, boolean directedGraph, Graph<Stop, Link> forbiddenLinks, Map<Link, Path<InfrastructureNode, InfrastructureEdge>> usedPaths, Stop origin, InfrastructureNode originNode, Dijkstra<InfrastructureNode, InfrastructureEdge, Graph<InfrastructureNode, InfrastructureEdge>> dijkstra) {
        // We iterate the destination stops by computed distance. This way we check for connections at the nearest stops first
        dijkstra.computeShortestPaths();
        List<Stop> destinations = new ArrayList<>(ptn.getNodes());
        destinations.sort(Comparator.comparingDouble(x -> dijkstra.getDistance(infrastructureGraph.getNode(Integer.parseInt(x.getLongName())))));
        for (Stop destination: destinations) {
            boolean output = origin.getId() == 88 && destination.getId() == 92;
            if (output) {
                logger.debug("To " + destination);
            }
            if (origin.equals(destination) || !directedGraph && origin.getId() > destination.getId()) {
                if (output) {
                    logger.debug("Skip");
                }
                continue;
            }
            InfrastructureNode destinationNode = infrastructureGraph.getNode(Integer.parseInt(destination.getLongName()));
            dijkstra.computeShortestPath(destinationNode);
            Collection<Path<InfrastructureNode, InfrastructureEdge>> paths = dijkstra.getPaths(destinationNode);
            if (paths == null) {
                //logger.warn("The infrastructure nodes " + origin + " and " + destination + " are not connected");
                continue;
            }

            if (output) {
                logger.debug("Found " + paths.size() + " paths");
            }
            boolean inbetween = false;
            Path<InfrastructureNode, InfrastructureEdge> feasiblePath = null;
            boolean feasible = true;
            for (Path<InfrastructureNode, InfrastructureEdge> path: paths) {
                inbetween = path.getNodes().stream().anyMatch(n -> !n.equals(originNode) && !n.equals(destinationNode) && nodeIdsToBuild.contains(n.getId()));
                if (output) {
                    logger.debug("Path " + path);
                }
                if (inbetween) {
                    if (output) {
                        logger.debug("Found inbetween");
                    }
                    continue;
                }
                // Check if it contains a restricted turn
                for (int i = 0; i < path.getEdges().size() - 1; i++) {
                    if (restrictedTurns.contains(new Pair<>(path.getEdges().get(i).getId(), path.getEdges().get(i+1).getId()))) {
                        feasible = false;
                        break;
                    }
                }
                if (!feasible) {
                    continue;
                }
                // If we are here we did not find an inbetween stop and no restricted turn, we have therefore found one possible connection between the stops
                feasiblePath = path;
            }
            if (inbetween || feasiblePath == null) {
                continue;
            }

            // Create a new link
            int lowerBound = feasiblePath.getEdges().stream().mapToInt(InfrastructureEdge::getLowerBound).sum();
            int upperBound = feasiblePath.getEdges().stream().mapToInt(InfrastructureEdge::getUpperBound).sum();
            double length = feasiblePath.getEdges().stream().mapToDouble(InfrastructureEdge::getLength).sum();
            // Check if it should be forbidden
            if (output) {
                logger.debug("Creating link between " + origin + " and " + destination);
            }
            if (ptn.getEdge(origin, destination).isPresent()) {
                if (output) {
                    logger.debug("Skipping link ");
                }
                continue;
            }
            if (forbiddenEdges != null) {
                boolean allowed = paths.stream().anyMatch(p -> p.getEdges().stream().noneMatch(e -> forbiddenEdges.getEdges().contains(e)));
                if (!allowed) {
                    if (output) {
                        logger.debug("Is forbidden");
                    }
                    forbiddenLinks.addEdge(new Link(nextLinkId, origin, destination, length, lowerBound, upperBound, directedGraph));
                }
            }
            Link newLink = new Link(nextLinkId, origin, destination, length, lowerBound, upperBound, directedGraph);
            usedPaths.put(newLink, feasiblePath);
            ptn.addEdge(newLink);
            nextLinkId += 1;
        }
        return nextLinkId;
    }

    public static void createStops(double conversionFactorCoordinates, Graph<Stop, Link> ptn, List<InfrastructureNode> stopsToBuild) {
        int nextStopId = 1;
        for (InfrastructureNode node: stopsToBuild) {
            logger.debug("Building stop " + node.getId());
            Stop stop = new Stop(nextStopId, node.getName(), String.valueOf(node.getId()),
                node.getxCoordinate() / conversionFactorCoordinates, node.getyCoordinate() / conversionFactorCoordinates);
            ptn.addNode(stop);
            nextStopId += 1;
        }
    }


    public static PTNResult createPTN(Graph<InfrastructureNode, InfrastructureEdge> infrastructureGraph,
                                      List<InfrastructureNode> stopsToBuild, double conversionFactorCoordinates,
                                      Set<Pair<Integer, Integer>> restrictedTurns,
                                      Graph<InfrastructureNode, InfrastructureEdge> forbiddenEdges) {
        Graph<Stop, Link> ptn = new ArrayListGraph<>();
        createStops(conversionFactorCoordinates, ptn, stopsToBuild);
        Pair<Set<Pair<Integer, Integer>>, Graph<Stop, Link>> result = createEdges(infrastructureGraph, ptn, stopsToBuild, restrictedTurns, forbiddenEdges);
        return new PTNResult(ptn, result.getFirstElement(), result.getSecondElement());
    }

    public static class PTNResult {
        private final Graph<Stop, Link> ptn;
        private final Graph<Stop, Link> forbiddenEdges;
        private final Set<Pair<Integer, Integer>> restrictedTurns;

        public PTNResult(Graph<Stop, Link> ptn, Set<Pair<Integer, Integer>> restrictedTurns, Graph<Stop, Link> forbiddenEdges) {
            this.ptn = ptn;
            this.forbiddenEdges = forbiddenEdges;
            this.restrictedTurns = restrictedTurns;
        }

        public Graph<Stop, Link> getPtn() {
            return ptn;
        }

        public Graph<Stop, Link> getForbiddenEdges() {
            return forbiddenEdges;
        }

        public Set<Pair<Integer, Integer>> getRestrictedTurns() {
            return restrictedTurns;
        }
    }
}
