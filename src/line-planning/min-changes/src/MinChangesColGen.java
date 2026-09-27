import com.dashoptimization.*;
import net.lintim.exception.AlgorithmStoppingCriterionException;
import net.lintim.io.ConfigReader;
import net.lintim.util.Config;
import net.lintim.util.LogLevel;
import net.lintim.util.Logger;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.KShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths;
import org.jgrapht.alg.shortestpath.YenKShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

// Main class for solving the min-changes model with the column generation approach
public class MinChangesColGen {

    public static String newline = "\n";
    private static String line_concept_file_path;
    private static boolean undirected;
    private static int capacity;
    private static int nrChangeGoPaths;
    private static int columnsAddedPerIt;
    private static int nrPTNPaths;
    private static double mipGap;
    private static int timelimit;
    private static int threads;
    private static int maxReducedCostsForIP;
    private static boolean outputSolverMessages;
    private static boolean writeLpFile;
    private static String pricing_method;
    private static PTN ptn;
    private static LinePool lp;
    private static OD od;
    private static ChangeGoGraph cg;
    private static HashMap<String, ArrayList<PTNPath>> allShortestPTNPaths;
    private static HashMap<Integer, HashSet<Line>> edgeLineAdjacency;
    private static HashMap<String, ArrayList<ArrayList<ChangeGoPath>>> allChangeGoPaths;
    private static ArrayList<String> listOfNodesPathPricingOD;
    private static HashMap<Integer, Vertex> partChangeGoVertexMap;
    private static HashMap<DefaultWeightedEdge, Integer> mapOfSPEdgeIDtoArcID;
    private static XPRBvar[] f;
    private static XPRBctr[][] ctrDemand;
    private static XPRBctr[][] ctrCapacity;
    private static XPRBctr objective;
    private static HashMap<String, XPRBvar> d;
    private static double[][] capDual;
    private static double[][] demDual;
    private static int[][] pricedODChecked;
    private static int changeGoPathCounter;
    private static double objectiveValue;
    private static XPRBprob p;

    private static final Logger logger = new Logger(MinChangesColGen.class);

    public static void main(String[] args) throws IOException, InterruptedException, XPRMLicenseError {
        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        readConfig(config);
        logger.info("Finished reading configuration");
        logger.info("Begin reading input data");
        readInput(config);
        logger.info("Finished reading input data");
        logger.info("Begin computing min changes line concept with column generation");
        logger.debug("Compute Edge-Line Adjacency for C&G-Graph.");
        computeEdgeLineAdjacency();
        logger.debug(" Done!");
        logger.debug("Computing " + nrPTNPaths + " shortest paths. ");
        computeKShortestPTNPaths();
        logger.debug("Done!");
        logger.debug("Compute Change&Go.");
        long time = System.currentTimeMillis();

        // The principal and complete change&go-graph is computed
        cg = computeChangeGoGraph();
        calculateChangeGoPaths(cg);
        logger.debug(" Done!");
        logger.debug("Time to find all chang&go paths: " + (System.currentTimeMillis() - time) + " ms.");

        // Column generation procedure is initialized
        // Principal LP is set up
        time = System.currentTimeMillis();
        setUpInitialLP();
        logger.debug("LP construction time: " + (System.currentTimeMillis() - time) + " ms.");
        logger.debug("Starting column generation");
        startColumnGeneration(cg);
        logger.debug("Solving respective IP!");

        // Final IP is solved. Note that passenger path variables remain fractional
        solveIP();
        logger.debug("Overall time: " + ((double) (System.currentTimeMillis() - time)) / 1000.0 + " s.");
        logger.info("Finished computing line concept");
        logger.info("Begin writing output data");
        writeLineConcept(new File(line_concept_file_path));
        logger.info("Finished writing output data");
    }

    /******************
     * Initializations
     *******************/

    private static void readConfig(Config config) {
        undirected = config.getBooleanValue("ptn_is_undirected");
        capacity = config.getIntegerValue("gen_passengers_per_vehicle");
        if (capacity <= 0) {
            logger.warn("\nINCONSISTENCY: lc_passengers_per_vehicle must be bigger than 0!");
        }
        nrChangeGoPaths = config.getIntegerValue("lc_minchanges_nr_cg_paths_per_ptn_path");
        if (nrChangeGoPaths <= 0) {
            logger.warn("\nINCONSISTENCY: lc_minchanges_nr_cg_paths_per_ptn_path must be bigger than 0!");
        }
        columnsAddedPerIt = config.getIntegerValue("lc_minchanges_cg_var_per_it");
        if (columnsAddedPerIt <= 0) {
            logger.warn("\nINCONSISTENCY: lc_minchanges_cg_var_per_it must be bigger than 0!");
        }
        nrPTNPaths = config.getIntegerValue("lc_minchanges_nr_ptn_paths");
        if (nrPTNPaths <= 0) {
            logger.warn("\nINCONSISTENCY: lc_minchanges_nr_ptn_paths must be bigger than 0!");
        }
        maxReducedCostsForIP = config.getIntegerValue("lc_minchanges_max_reduced_costs_included_IP");
        if (maxReducedCostsForIP < 0) {
            logger.warn("\nINCONSISTENCY: lc_minchanges_max_reduced_costs_included_IP must not be smaller than 0!");
        }
        mipGap = config.getDoubleValue("lc_mip_gap");
        timelimit = config.getIntegerValue("lc_timelimit");
        threads = config.getIntegerValue("lc_threads");
        writeLpFile = config.getBooleanValue("lc_write_lp_file");
        outputSolverMessages = config.getLogLevel("console_log_level") == LogLevel.DEBUG;
        pricing_method = config.getStringValue("lc_minchanges_pricing_method");
        if (!pricing_method.equals("exact") && !pricing_method.equals("heuristic")) {
            logger.warn("\nINCONSISTENCY: lc_minchanges_pricing_method must be either \"exact\" or \"heuristic\"!");
        }
        line_concept_file_path = config.getStringValue("default_lines_file");
    }


    // Adjacency between edges and lines has to be computed
    private static void computeEdgeLineAdjacency() {
        edgeLineAdjacency = new HashMap<>();
        Iterator<Integer> it = lp.getLines().keySet().iterator();
        while (it.hasNext()) {
            Line line = lp.getLines().get(it.next());
            for (Edge edge : line.getEdges()) {
                if (!edgeLineAdjacency.containsKey(edge.getIndex()))
                    edgeLineAdjacency.put(edge.getIndex(), new HashSet<>());
                edgeLineAdjacency.get(edge.getIndex()).add(line);
            }
        }
    }


    /******************
     * Construction of CG starting tableau
     *******************/

    // From PTN data a graph is constructed and on this graph for every od-pair the k shortest paths are calculated.
    private static void computeKShortestPTNPaths() {
        int ptnPathCounter = 0;
        PTNPath ptnPath;
        Integer lastVertex;
        int currentVertex;
        List<GraphPath<Integer, DefaultWeightedEdge>> paths;
        Edge addEdge;
        DefaultWeightedEdge addedEdge;
        allShortestPTNPaths = new HashMap<>();
        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> kShortestPathsGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (Stop stop : ptn.getStops()) {
            kShortestPathsGraph.addVertex(stop.getIndex());
        }
        for (Edge edge : ptn.getEdges()) {
            addedEdge = kShortestPathsGraph.addEdge(edge.getLeft_stop().getIndex(), edge.getRight_stop().getIndex());
            kShortestPathsGraph.setEdgeWeight(addedEdge, edge.getLength());
            if (!ptn.isDirected()) {
                addedEdge = kShortestPathsGraph.addEdge(edge.getRight_stop().getIndex(), edge.getLeft_stop().getIndex());
                kShortestPathsGraph.setEdgeWeight(addedEdge, edge.getLength());
            }
        }
        KShortestPathAlgorithm<Integer, DefaultWeightedEdge> algorithm = new YenKShortestPath<>(kShortestPathsGraph);
        // Compute a shortest ptn-path for each origin-destination combination for which an od pair exists
        for (Stop origin : ptn.getStops()) {
            for (Stop destination : ptn.getStops()) {
                if (od.getPassengersAt(origin, destination) > 0) {
                    allShortestPTNPaths.put(origin.getIndex() + "-" + destination.getIndex(), new ArrayList<>());
                    paths = algorithm.getPaths(origin.getIndex(), destination.getIndex(), nrPTNPaths);
                    // Convert the path from the shortest path computation to internal structure
                    if (paths.isEmpty()) {
                        od.setPassengersAt(origin, destination, 0.0);
                        allShortestPTNPaths.put(origin.getIndex() + "-" + destination.getIndex(), null);
                    }
                    for (GraphPath<Integer, DefaultWeightedEdge> path : paths) {
                        ptnPathCounter++;
                        ptnPath = new PTNPath(ptnPathCounter, new ArrayList<>());
                        lastVertex = null;
                        for (Integer vertex : path.getVertexList()) {
                            if (lastVertex == null) {
                                lastVertex = vertex;
                            } else {
                                currentVertex = vertex;
                                addEdge = ptn.getEdge(lastVertex, currentVertex);
                                if (addEdge.getLeft_stop().getIndex() == lastVertex) {
                                    ptnPath.addEdge(addEdge, false);
                                } else {
                                    addEdge = ptn.getEdge(currentVertex, lastVertex);
                                    ptnPath.addEdge(addEdge, true);
                                }
                                ptnPath.setLength(ptnPath.getLength() + 1.0);
                                lastVertex = currentVertex;
                            }
                        }
                        allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()).add(ptnPath);
                    }
                }
            }
        }
    }

    // Compute the change&go-graph
    private static ChangeGoGraph computeChangeGoGraph() {
        cg = new ChangeGoGraph();
        // An OD vertex for all stops
        for (Stop stop : ptn.getStops()) {
            cg.addVertex(new Vertex(stop.getIndex(), stop.getIndex(), null));
        }
        Stop originStop;
        Vertex originVertex;
        Vertex destinationVertex;
        Line line;
        for (Map.Entry<Integer, Line> entry : lp.getLines().entrySet()) {
            line = entry.getValue();
            if (line.getEdges().size() > 1 && (line.getEdges().get(0).getRight_stop() == line.getEdges().get(1).getLeft_stop() || line.getEdges().get(0).getRight_stop() == line.getEdges().get(1).getRight_stop())) {
                originStop = line.getEdges().get(0).getLeft_stop();
            } else {
                originStop = line.getEdges().get(0).getRight_stop();
            }
            // A line vertex for the first halt of a line
            originVertex = new Vertex(cg.getVertices().size() + 1, originStop.getIndex(), line);
            cg.addVertex(originVertex);
            // An arc between the first line vertex and the corresponding OD vertex
            cg.addArc(new Arc(cg.getArcs().size() + 1, 0, !undirected, cg.getVertex(originStop.getIndex()), originVertex, line.getIndex(), 1.0));
            for (Edge edge : line.getEdges()) {
                // The direction of the edge has to be considered
                if (originStop == edge.getLeft_stop()) {
                    destinationVertex = new Vertex(cg.getVertices().size() + 1, edge.getRight_stop().getIndex(), line);
                    // A line vertex for all consecutive vertices of a line
                    cg.addVertex(destinationVertex);
                    // An arc between the line vertex and the corresponding OD vertex
                    cg.addArc(new Arc(cg.getArcs().size() + 1, 0, !undirected, cg.getVertex(edge.getRight_stop().getIndex()), destinationVertex, line.getIndex(), 1.0));
                    // An arc between the last and the current line vertices
                    cg.addArc(new Arc(cg.getArcs().size() + 1, edge.getIndex(), !undirected, originVertex, destinationVertex, line.getIndex(), 1.0));
                    originVertex = destinationVertex;
                    originStop = edge.getRight_stop();
                } else {
                    destinationVertex = new Vertex(cg.getVertices().size() + 1, edge.getLeft_stop().getIndex(), line);
                    cg.addVertex(destinationVertex);
                    cg.addArc(new Arc(cg.getArcs().size() + 1, 0, !undirected, cg.getVertex(edge.getLeft_stop().getIndex()), destinationVertex, line.getIndex(), 1.0));
                    cg.addArc(new Arc(cg.getArcs().size() + 1, edge.getIndex(), !undirected, originVertex, destinationVertex, line.getIndex(), 1.0));
                    originVertex = destinationVertex;
                    originStop = edge.getLeft_stop();
                }
            }
        }
        return cg;
    }

    // A set of initial paths on the Change&Go-Graph (ChangeGoPaths) is calculated.
    private static void calculateChangeGoPaths(ChangeGoGraph cg) {
        changeGoPathCounter = 0;
        allChangeGoPaths = new HashMap<>();
        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> partChangeGoGraph;
        ChangeGoPath expandedPath;
        ArrayList<ChangeGoPath> shortestPartChangeGoPaths;
        ArrayList<ChangeGoPath> expandedShortestPartChangeGoPaths;
        for (Stop origin : ptn.getStops()) {
            for (Stop destination : ptn.getStops()) {
                if (od.getPassengersAt(origin, destination) > 0 && allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()) != null && !allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()).isEmpty()) {
                    allChangeGoPaths.put(origin.getIndex() + "-" + destination.getIndex(), new ArrayList<>(allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()).size()));
                    for (PTNPath ptnPath : allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()))
                        allChangeGoPaths.get(origin.getIndex() + "-" + destination.getIndex()).add(new ArrayList<>());
                    for (PTNPath ptnPath : allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex())) {
                        partChangeGoGraph = constructPartChangeGoGraph(cg, ptnPath);
                        // This is where the k shortest paths on the change and go graph are calculated for each ptn path
                        shortestPartChangeGoPaths = computeKShortestPartChangeGoPaths(partChangeGoGraph, ptnPath, cg, nrChangeGoPaths);
                        expandedShortestPartChangeGoPaths = new ArrayList<>();
                        for (ChangeGoPath path : shortestPartChangeGoPaths) {
                            if (!path.usesALineTwice()) {
                                expandedPath = expandCompressedPath(cg, path);
                                expandedPath.setIndex(changeGoPathCounter++);
                                expandedPath.setPTNPathIndex(ptnPath.getIndex());
                                expandedShortestPartChangeGoPaths.add(expandedPath);
                            }
                        }
                        allChangeGoPaths.get(origin.getIndex() + "-" + destination.getIndex()).set(allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()).indexOf(ptnPath), expandedShortestPartChangeGoPaths);
                    }
                }
            }
        }
    }

    /******************
     * Column generation procedure
     *******************/


    // Column generation algorithm is initiated
    // One pricing step is done and if there is no improving paths found, the algorithm stops, otherwise the paths are added to the LP, the LP is solved and another pricing step ist done.
    public static void startColumnGeneration(ChangeGoGraph cg) {
        long time = System.currentTimeMillis();
        // First pricing step returning priced paths which result from shortest path computations in change&go-graph
        TreeSet<ChangeGoPath> pricedPaths = new TreeSet<>();
        if (pricing_method.equals("exact"))
            pricedPaths = compExactPathPricing(cg);
        else if (pricing_method.equals("heuristic"))
            pricedPaths = compHeuristicPathPricing(cg);
        logger.debug("Pricing time: " + (System.currentTimeMillis() - time) + " ms.");
        while (pricedPaths != null) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            //get current date time with Date()
            Date date = new Date();
            logger.debug(dateFormat.format(date));
            // solve current LP
            time = System.currentTimeMillis();
            addThingsToLP(pricedPaths);
            if (pricing_method.equals("exact"))
                pricedPaths = compExactPathPricing(cg);
            else if (pricing_method.equals("heuristic"))
                pricedPaths = compHeuristicPathPricing(cg);
            logger.debug("Pricing&Solving time: " + (System.currentTimeMillis() - time));
        }
        if (pricedPaths == null) {
            logger.debug("No priced paths can be found to improve the solution! ColGen Done!");
        }
    }

    // In the heuristic path pricing a shortest path in the change&go network is computed for each OD-pair.
    // If reduced costs of the path are negative and the path in the change&go is a representative of a path in the PTN it is added to the LP
    // It is not exact since there could exist a path which has negative reduced costs (not as negative as the one with the most negative costs) but which is not considered.
    private static TreeSet<ChangeGoPath> compHeuristicPathPricing(ChangeGoGraph cg) {
        logger.debug("Compute heuristic pricing... ");
        TreeSet<ChangeGoPath> pricedPaths = new TreeSet<>(new DualPricePathsComparator());
        SimpleWeightedGraph<Vertex, DefaultWeightedEdge> spgraph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        PTNPath basePath;
        for (Vertex vertex : cg.getVertices()) {
            spgraph.addVertex(vertex);
        }
        DefaultWeightedEdge defaultEdge;
        // SimpleWeightedGraph is not directed so only forward edges have to be added.
        for (Arc arc : cg.getArcs()) {
            if (arc.getRightVertex().getLine() == null || arc.getLeftVertex().getLine() == null) {
                defaultEdge = spgraph.addEdge(arc.getLeftVertex(), arc.getRightVertex());
                spgraph.setEdgeWeight(defaultEdge, 0.5);
            } else {
                defaultEdge = spgraph.addEdge(arc.getLeftVertex(), arc.getRightVertex());
                spgraph.setEdgeWeight(defaultEdge, capDual[ptn.getEdge(arc.getLeftVertex().getStopIndex(), arc.getRightVertex().getStopIndex()).getIndex() - 1][arc.getLineIndex() - 1]);
            }
        }
        // Compute all pairs shortest paths
        FloydWarshallShortestPaths<Vertex, DefaultWeightedEdge> fwsp = new FloydWarshallShortestPaths<>(spgraph);
        for (Stop origin : ptn.getStops()) {
            for (Stop destination : ptn.getStops()) {
                if (od.getPassengersAt(origin, destination) > 0 && demDual[origin.getIndex() - 1][destination.getIndex() - 1] > 0 && allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()) != null && !allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()).isEmpty()) {
                    ChangeGoPath changeGoPath;
                    double min = -demDual[origin.getIndex() - 1][destination.getIndex() - 1] + fwsp.getPathWeight(cg
                        .getVertex(origin.getIndex()), cg.getVertex(destination.getIndex()));
                    if (0 > min) {
                        changeGoPath = new ChangeGoPath((ArrayList<Vertex>) fwsp.getPath(cg
                            .getVertex(origin.getIndex()), cg.getVertex(destination.getIndex())).getVertexList());
                        changeGoPath.setDualCosts(min);
                        // Check if for the found path with negative reduced costs also a path in the ptn exists. If not this found path is not valid.
                        // In this case there might be another path with negative reduced costs (not has negative as the one found) which has a valid ptn path and which will not be found.
                        basePath = determineBasePTNPath(changeGoPath);
                        if (basePath != null) {
                            pricedPaths.add(changeGoPath);
                            allChangeGoPaths.get(changeGoPath.getFirst().getStopIndex() + "-" + changeGoPath.getLast().getStopIndex()).get(allShortestPTNPaths.get(changeGoPath.getFirst().getStopIndex() + "-" + changeGoPath.getLast().getStopIndex()).indexOf(basePath)).add(changeGoPath);
                        } else {
                            logger.debug("A change&go-path has been found which is not a representative of a ptn path. It may be more precise to use the exact pricing method.");
                        }
                    }
                }
            }
        }
        for (ChangeGoPath path : pricedPaths) {
            path.setIndex(changeGoPathCounter++);
        }
        logger.debug("Done !");
        if (pricedPaths.isEmpty()) {
            return null;
        }
        return pricedPaths;
    }

    // Compute shortest paths on subnetworks of the change&go-network
    // In the exact pricing for each OD-pair and each path in the PTN, the corresponding subnetwork of the change&go-graph is constructed.
    // Then a shortest path on this network is computed.
    private static TreeSet<ChangeGoPath> compExactPathPricing(ChangeGoGraph cg) {
        logger.debug("Compute exact ppricing... ");
        TreeSet<ChangeGoPath> pricedPaths = new TreeSet<>(new DualPricePathsComparator());
        ArrayList<PTNPath> ptnPaths;
        Stop destination;
        int originCount = 0;
        PTNPath basePath;
        pricedODChecked = new int[ptn.getStops().size()][ptn.getStops().size()];
        // Compute k shortest paths for all origin destination pairs and each ptn-path
        for (Stop origin : ptn.getStops()) {
            if (originCount % 10 == 0) {
                logger.debug((originCount++) + " of " + ptn.getStops().size() + " done.\n");
            } else {
                originCount++;
            }
            pricedODChecked[origin.getIndex() - 1] = new int[ptn.getStops().size()];
            for (int destIndex = ptn.getStops().size() - 1; destIndex >= 0; destIndex--) {
                destination = ptn.getStops().get(destIndex);
                if (od.getPassengersAt(origin, destination) > 0) {
                    ptnPaths = allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex());
                    ArrayList<ChangeGoPath> pricedPathsOD;
                    for (PTNPath ptnPath : ptnPaths) {
                        if (!hasPathBeenChecked(pricedODChecked[origin.getIndex() - 1][destination.getIndex() - 1], ptnPaths.indexOf(ptnPath))) {
                            // Compute the priced path per origin destination pair and ptn path
                            pricedPathsOD = compPathPricingOD(cg, ptnPath, origin, destination);
                            if (pricedPathsOD != null) {
                                for (ChangeGoPath pricedPath : pricedPathsOD) {
                                    basePath = determineBasePTNPath(pricedPath);
                                    if (basePath != null)
                                        allChangeGoPaths.get(pricedPath.getFirst().getStopIndex() + "-" + pricedPath.getLast().getStopIndex()).get(allShortestPTNPaths.get(pricedPath.getFirst().getStopIndex() + "-" + pricedPath.getLast().getStopIndex()).indexOf(basePath)).add(pricedPath);
                                }
                                pricedPaths.addAll(pricedPathsOD);
                            }
                        }
                        while (columnsAddedPerIt > 0 && pricedPaths.size() >= columnsAddedPerIt + 1) {
                            pricedPaths.pollFirst();
                        }
                    }
                }
            }
        }
        for (ChangeGoPath path : pricedPaths)
            path.setIndex(changeGoPathCounter++);
        logger.debug("done !");
        if (pricedPaths.isEmpty())
            return null;
        return pricedPaths;
    }

    // Construct the particular change&go graph which is a representation of only the ptnpath
    private static SimpleWeightedGraph<Integer, DefaultWeightedEdge> setUpSPPathPricingOD(ChangeGoGraph cg, PTNPath ptnPath) {
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> sp = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        mapOfSPEdgeIDtoArcID = new HashMap<>();
        //ShortestPathsGraph<Integer, Integer> sp = new ShortestPathsGraph<Integer, Integer>();
        listOfNodesPathPricingOD = new ArrayList<>();
        Integer leftVertex;
        Integer rightVertex;
        boolean isValidODArc;
        DefaultWeightedEdge defaultEdge;
        for (Arc arc : cg.getArcs()) {
            isValidODArc = false;
            if (arc.getEdgeIndex() == 0 && arc.getLeftVertex().getLine() == null) {
                for (Arc outgoingArc : cg.getOutgoingArcs(arc.getRightVertex())) {
                    if (outgoingArc.getEdgeIndex() != 0 && ptnPath.getEdges().contains(ptn.getEdge(outgoingArc.getEdgeIndex()))) {
                        isValidODArc = true;
                        break;
                    }
                }
            } else if (arc.getEdgeIndex() == 0 && arc.getRightVertex().getLine() == null) {
                for (Arc outgoingArc : cg.getOutgoingArcs(arc.getLeftVertex()))
                    if (outgoingArc.getEdgeIndex() != 0 && ptnPath.getEdges().contains(ptn.getEdge(outgoingArc.getEdgeIndex()))) {
                        isValidODArc = true;
                        break;
                    }
            }
            if (isValidODArc || (arc.getEdgeIndex() > 0 && ptnPath.getEdges().contains(ptn.getEdge(arc.getEdgeIndex())))) {
                if (arc.getLeftVertex().getLine() == null && listOfNodesPathPricingOD.contains(arc.getLeftVertex().getIndex() + ",")) {
                    leftVertex = listOfNodesPathPricingOD.indexOf(arc.getLeftVertex().getIndex() + ",") + 1;
                } else if (arc.getLeftVertex().getLine() != null && listOfNodesPathPricingOD.contains(arc.getLeftVertex().getIndex() + "," + arc.getLeftVertex().getLine().getIndex())) {
                    leftVertex = listOfNodesPathPricingOD.indexOf(arc.getLeftVertex().getIndex() + "," + arc.getLeftVertex().getLine().getIndex()) + 1;
                } else {
                    if (arc.getLeftVertex().getLine() == null) {
                        listOfNodesPathPricingOD.add(arc.getLeftVertex().getIndex() + ",");
                    } else {
                        listOfNodesPathPricingOD.add(arc.getLeftVertex().getIndex() + "," + arc.getLeftVertex().getLine().getIndex());
                    }
                    leftVertex = listOfNodesPathPricingOD.size();
                    sp.addVertex(leftVertex);
                }
                if (arc.getRightVertex().getLine() == null && listOfNodesPathPricingOD.contains(arc.getRightVertex().getIndex() + ",")) {
                    rightVertex = listOfNodesPathPricingOD.indexOf(arc.getRightVertex().getIndex() + ",") + 1;
                } else if (arc.getRightVertex().getLine() != null && listOfNodesPathPricingOD.contains(arc.getRightVertex().getIndex() + "," + arc.getRightVertex().getLine().getIndex())) {
                    rightVertex = listOfNodesPathPricingOD.indexOf(arc.getRightVertex().getIndex() + "," + arc.getRightVertex().getLine().getIndex()) + 1;
                } else {
                    if (arc.getRightVertex().getLine() == null) {
                        listOfNodesPathPricingOD.add(arc.getRightVertex().getIndex() + ",");
                    } else {
                        listOfNodesPathPricingOD.add(arc.getRightVertex().getIndex() + "," + arc.getRightVertex().getLine().getIndex());
                    }
                    rightVertex = listOfNodesPathPricingOD.size();
                    sp.addVertex(rightVertex);
                }
                if (arc.getLeftVertex().getLine() == null) {
                    defaultEdge = sp.addEdge(leftVertex, rightVertex);
                    mapOfSPEdgeIDtoArcID.put(defaultEdge, arc.getIndex());
                    sp.setEdgeWeight(defaultEdge, 0.5);
                    //if(undirected)
                    //	sp.addEdge(arc.getIndex(),rightVertex, leftVertex,0.5);
                } else if (arc.getRightVertex().getLine() == null) {
                    defaultEdge = sp.addEdge(leftVertex, rightVertex);
                    mapOfSPEdgeIDtoArcID.put(defaultEdge, arc.getIndex());
                    sp.setEdgeWeight(defaultEdge, 0.5);
                    //if(undirected)
                    //	sp.addEdge(arc.getIndex(),rightVertex, leftVertex,0.5);
                } else {
                    defaultEdge = sp.addEdge(leftVertex, rightVertex);
                    mapOfSPEdgeIDtoArcID.put(defaultEdge, arc.getIndex());
                    sp.setEdgeWeight(defaultEdge, capDual[ptn.getEdge(arc.getLeftVertex().getStopIndex(), arc.getRightVertex().getStopIndex()).getIndex() - 1][arc.getLineIndex() - 1]);
                    //if(undirected)
                    //	sp.addEdge(arc.getIndex(),rightVertex, leftVertex,capDual[ptn.getEdge(arc.getLeftVertex().getStopIndex(), arc.getRightVertex().getStopIndex()).getIndex()-1][arc.getLineIndex()-1]);
                }
            }
        }
        return sp;
    }


    // Compute the pricing of the paths for an origin and a destination, relying on the ptn path ptnPath
    private static ArrayList<ChangeGoPath> compPathPricingODFrom(SimpleWeightedGraph<Integer, DefaultWeightedEdge> sp, Integer originIndex, PTNPath ptnPath) {
        ArrayList<ChangeGoPath> pricedPaths = new ArrayList<>();
        ChangeGoPath path;
        FloydWarshallShortestPaths<Integer, DefaultWeightedEdge> fwsp = new FloydWarshallShortestPaths<>(sp);
        //fwsp.getShortestPaths(listOfNodesPathPricingOD.indexOf(originIndex+",") + 1);
        //sp.compute(listOfNodesPathPricingOD.indexOf(originIndex+",") + 1);
        boolean hasEqualPath = false;
        List<DefaultWeightedEdge> defaultEdges;
        LinkedList<Arc> edges;
        int destinationIndex;
        for (String vertexName : listOfNodesPathPricingOD) {
            if (vertexName.matches("\\p{Digit}+,") && od.getPassengersAt(ptn.getStop(originIndex), ptn.getStop(Integer.valueOf(vertexName.substring(0, vertexName.toCharArray().length - 1)))) > 0 && !hasPathBeenChecked(pricedODChecked[originIndex - 1][Integer.valueOf(vertexName.substring(0, vertexName.toCharArray().length - 1)) - 1], allShortestPTNPaths.get(originIndex + "-" + Integer.valueOf(vertexName.substring(0, vertexName.toCharArray().length - 1))).indexOf(ptnPath))) {
                destinationIndex = Integer.valueOf(vertexName.substring(0, vertexName.toCharArray().length - 1));
                double min = -demDual[originIndex - 1][destinationIndex - 1] + fwsp.getPathWeight(
                    listOfNodesPathPricingOD.indexOf(originIndex + ",") + 1, listOfNodesPathPricingOD.indexOf
                        (vertexName) + 1);
                pricedODChecked[originIndex - 1][destinationIndex - 1] += Math.pow(2, allShortestPTNPaths.get(originIndex + "-" + destinationIndex).indexOf(ptnPath));
                path = new ChangeGoPath();
                if (0 > min) {
                    defaultEdges = fwsp.getPath(listOfNodesPathPricingOD.indexOf(originIndex + ",") + 1,
                        listOfNodesPathPricingOD.indexOf(vertexName) + 1).getEdgeList();
                    edges = new LinkedList<>();
                    for (DefaultWeightedEdge defaultEdge : defaultEdges) {
                        edges.add(cg.getArc(mapOfSPEdgeIDtoArcID.get(defaultEdge)));
                    }
                    path = convertEdgeListToChangeGoPath(edges);

                    path.setDualCosts(min);
                    hasEqualPath = false;
                    for (ArrayList<ChangeGoPath> comparePathList : allChangeGoPaths.get(path.getFirst().getStopIndex() + "-" + path.getLast().getStopIndex())) {
                        for (ChangeGoPath comparePath : comparePathList)
                            if (path.passesSameStops(comparePath)) {
                                hasEqualPath = true;
                                break;
                            }
                    }
                }

                if (!path.getVertices().isEmpty() && !hasEqualPath) {
                    pricedPaths.add(path);
                }
            }
        }
        return pricedPaths;
    }

    // For an OD pair and a ptn path construct the corresponding change&go-graph and compute the shortest path on this
    private static ArrayList<ChangeGoPath> compPathPricingOD(ChangeGoGraph cg, PTNPath ptnPath, Stop origin, Stop destination) {
        if (demDual[origin.getIndex() - 1][destination.getIndex() - 1] <= 0)
            return null;
        SimpleWeightedGraph<Integer, DefaultWeightedEdge> sp = setUpSPPathPricingOD(cg, ptnPath);
        ArrayList<ChangeGoPath> pricedPaths = new ArrayList<>();
        pricedPaths.addAll(compPathPricingODFrom(sp, origin.getIndex(), ptnPath));
        if (pricedPaths.isEmpty())
            return null;
        return pricedPaths;
    }


    // Checks whether an index has been checked already
    private static boolean hasPathBeenChecked(int code, int index) {
        int code_copy = 0;
        for (int count = nrPTNPaths - 1; count >= 0; count--) {
            if (code_copy >= Math.pow(2, count) && count == index) {
                return true;
            } else if (code_copy >= Math.pow(2, count)) {
                code -= Math.pow(2, count);
                code_copy += Math.pow(2, count);
            }
        }
        return false;
    }

    // Convert the path as a list of Arcs into a ChangeGoPath
    private static ChangeGoPath convertEdgeListToChangeGoPath(LinkedList<Arc> edges) {
        Vertex originVertex;
        Arc prevArc;
        Arc currentArc;
        Arc nextArc;
        ChangeGoPath cgpath = new ChangeGoPath();
        if (edges.size() > 1) {
            currentArc = edges.get(0);
            nextArc = edges.get(1);
            if (currentArc.getRightVertex() == nextArc.getLeftVertex() || currentArc.getRightVertex() == nextArc.getRightVertex())
                originVertex = currentArc.getLeftVertex();
            else
                originVertex = currentArc.getRightVertex();
        } else {
            originVertex = edges.get(0).getLeftVertex();
        }
        prevArc = null;
        cgpath.addVertex(originVertex);

        for (Arc arc : edges) {
            currentArc = arc;
            if (currentArc.getLeftVertex() == cgpath.getLast() && currentArc.getEdgeIndex() == 0)
                cgpath.addVertex(currentArc.getRightVertex());
            else if (currentArc.getRightVertex() == cgpath.getLast() && currentArc.getEdgeIndex() == 0)
                cgpath.addVertex(currentArc.getLeftVertex());
            else if (currentArc.getEdgeIndex() == 0 && prevArc != null && (prevArc.getLeftVertex() == currentArc.getLeftVertex() || prevArc.getRightVertex() == currentArc.getLeftVertex()))
                cgpath.addVertex(currentArc.getRightVertex());
            else if (currentArc.getEdgeIndex() == 0 && prevArc != null && (prevArc.getLeftVertex() == currentArc.getRightVertex() || prevArc.getRightVertex() == currentArc.getRightVertex()))
                cgpath.addVertex(currentArc.getLeftVertex());
            prevArc = currentArc;
        }
        return cgpath;
    }

    // Determine the ptnPath which is the projection of the given ChangeGoPath on to the ptn, if it exists, otherwise return null
    private static PTNPath determineBasePTNPath(ChangeGoPath path) {
        boolean basePathFound;
        Stop startStop;
        Stop endStop = null;
        List<Stop> listOfPassedStops = null;

        for (PTNPath basePath : allShortestPTNPaths.get(path.getFirst().getStopIndex() + "-" + path.getLast().getStopIndex())) {
            basePathFound = true;
            startStop = null;
            for (Vertex vertex : path.getVertices()) {
                if (vertex.getLine() == null && startStop == null)
                    startStop = ptn.getStop(vertex.getStopIndex());
                else if (vertex.getLine() != null)
                    listOfPassedStops = vertex.getLine().getStops();
                else if (vertex.getLine() == null && startStop != null)
                    endStop = ptn.getStop(vertex.getStopIndex());
                if (startStop != null && endStop != null) {
                    if (listOfPassedStops.indexOf(startStop) > listOfPassedStops.indexOf(endStop))
                        listOfPassedStops = listOfPassedStops.subList(listOfPassedStops.indexOf(endStop), listOfPassedStops.indexOf(startStop) + 1);
                    else
                        listOfPassedStops = listOfPassedStops.subList(listOfPassedStops.indexOf(startStop), listOfPassedStops.indexOf(endStop) + 1);

                    if (listOfPassedStops.size() == 1) {
                        if (basePath.getStops().contains(listOfPassedStops.get(0))) {
                            basePathFound = false;
                            break;
                        }
                    } else if (listOfPassedStops.size() > 1) {
                        for (Stop stop : listOfPassedStops) {
                            if (listOfPassedStops.size() > listOfPassedStops.indexOf(stop) + 1 && (!basePath.getStops().contains(stop) || !basePath.getStops().contains(listOfPassedStops.get(listOfPassedStops.indexOf(stop) + 1)) || !(Math.abs(basePath.getStops().indexOf(stop) - basePath.getStops().indexOf(listOfPassedStops.get(listOfPassedStops.indexOf(stop) + 1))) == 1))) {
                                basePathFound = false;
                                break;
                            }
                        }
                    }
                }
                if (basePathFound && startStop != null && endStop != null) {
                    startStop = endStop;
                    endStop = null;
                    listOfPassedStops = null;
                } else if (!basePathFound) {
                    endStop = null;
                    listOfPassedStops = null;
                    break;
                }
            }
            if (basePathFound) {
                path.setPTNPathIndex(basePath.getIndex());
                return basePath;
            }
        }
        return null;
    }

    // The k (number_paths) shortest paths in the partChange&Go-Graph are calculated. They are based on one particular PTN-Path.
    private static ArrayList<ChangeGoPath> computeKShortestPartChangeGoPaths(SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph, PTNPath ptnPath, ChangeGoGraph cg, int number_paths) {
        ArrayList<ChangeGoPath> finalChangeGoPaths = new ArrayList<>();
        KShortestPathAlgorithm<Integer, DefaultWeightedEdge> yenAlg = new YenKShortestPath<>(graph);
        Vertex origin = null;
        Vertex destination = null;
        for (Vertex currentVertex : partChangeGoVertexMap.values()) {
            if (currentVertex.getLine() == null && ptnPath.getStops().get(0).getIndex() == currentVertex.getStopIndex())
                origin = currentVertex;
            else if (currentVertex.getLine() == null && ptnPath.getStops().get(ptnPath.getStops().size() - 1).getIndex() == currentVertex.getStopIndex())
                destination = currentVertex;
            if (origin != null && destination != null)
                break;
        }
        List<GraphPath<Integer, DefaultWeightedEdge>> list_of_paths = yenAlg.getPaths(origin.getIndex(), destination.getIndex(), number_paths);
        ChangeGoPath cg_path;
        for (GraphPath<Integer, DefaultWeightedEdge> path : list_of_paths) {
            cg_path = new ChangeGoPath();
            for (int vertex : path.getVertexList()) {
                cg_path.addVertex(partChangeGoVertexMap.get(vertex));
            }
            finalChangeGoPaths.add(cg_path);
        }
        return finalChangeGoPaths;
    }

    // Method constructs the part of the Change&Go-Graph which corresponds to the ptnPath (only those edges considered).
    private static SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> constructPartChangeGoGraph(ChangeGoGraph cg, PTNPath ptnPath) {
        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> partChangeGoGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Vertex originVertex;
        Vertex rightVertex;
        partChangeGoVertexMap = new HashMap<>();
        ArrayList<Vertex> odVertices = new ArrayList<>(ptn.getStops().size());
        while (odVertices.size() < ptn.getStops().size()) odVertices.add(null);
        for (Stop stop : ptnPath.getStops()) {
            // OD Vertex created
            originVertex = cg.getVertex(stop.getIndex(), null);
            //originVertex = new Vertex(partChangeGoGraph.get_vertex_list().size()+1,stop.getIndex(),null);
            partChangeGoVertexMap.put(originVertex.getIndex(), originVertex);
            odVertices.set(originVertex.getStopIndex() - 1, originVertex);
            partChangeGoGraph.addVertex(originVertex.getIndex());
        }
        for (Edge edge : ptnPath.getEdges()) {
            for (Line line : edgeLineAdjacency.get(edge.getIndex())) {
                rightVertex = cg.getVertex(ptnPath.getStops().get(ptnPath.getEdges().indexOf(edge)).getIndex(), line);
                partChangeGoVertexMap.put(rightVertex.getIndex(), rightVertex);
                partChangeGoGraph.addVertex(rightVertex.getIndex());
                partChangeGoGraph.addEdge(odVertices.get(rightVertex.getStopIndex() - 1).getIndex(), rightVertex.getIndex());
                partChangeGoGraph.addEdge(rightVertex.getIndex(), odVertices.get(rightVertex.getStopIndex() - 1).getIndex());
                for (int edgeCounter = ptnPath.getEdges().indexOf(edge); edgeCounter < ptnPath.getEdges().size(); edgeCounter++) {
                    // Connect all OD-vertices which are passed by the line to the correct line-vertex.
                    if (edgeLineAdjacency.get(ptnPath.getEdges().get(edgeCounter).getIndex()).contains(line)) {
                        partChangeGoGraph.addEdge(odVertices.get(ptnPath.getStops().get(edgeCounter + 1).getIndex() - 1)
                            .getIndex(), rightVertex.getIndex());
                        partChangeGoGraph.addEdge(rightVertex.getIndex(), odVertices.get(ptnPath.getStops().get
                            (edgeCounter + 1).getIndex() - 1).getIndex());
                    } else
                        break;
                }
            }
        }
        return partChangeGoGraph;
    }

    // For faster computation of shortest paths the change&go-graph is collapsed and afterwards the compressed paths are extended to fit to the original change&go-graph structure
    private static ChangeGoPath expandCompressedPath(ChangeGoGraph cg, ChangeGoPath compressedPath) {
        ChangeGoPath extendedPath = new ChangeGoPath();
        ArrayList<Integer> listOfStopIndices = new ArrayList<>();
        int lastStopIndex = 0;
        Vertex firstNonODVertex = null;
        Vertex lastVertex;
        Arc firstLineArc = null;
        boolean addedAVertex;
        for (Vertex vertex : compressedPath.getVertices())
            if (vertex.getLine() == null)
                listOfStopIndices.add(vertex.getStopIndex());
        for (Vertex vertex : compressedPath.getVertices()) {
            if (vertex.getLine() == null) {
                extendedPath.addVertex(vertex);
                lastStopIndex = vertex.getStopIndex();
                firstNonODVertex = null;
                firstLineArc = null;
            } else {
                while (listOfStopIndices.get(listOfStopIndices.indexOf(lastStopIndex) + 1) != extendedPath.getVertices().get(extendedPath.getVertices().size() - 1).getStopIndex()) {
                    addedAVertex = false;
                    lastVertex = extendedPath.getVertices().get(extendedPath.getVertices().size() - 1);
                    for (Arc arc : cg.getOutgoingArcs(lastVertex)) {
                        if (arc.getLeftVertex() == lastVertex && vertex.getLine() == arc.getRightVertex().getLine() && (firstNonODVertex == null || !extendedPath.getVertices().contains(arc.getRightVertex())) && (firstLineArc == null || arc != firstLineArc)) {
                            if (firstNonODVertex != null && firstLineArc == null) firstLineArc = arc;
                            if (firstNonODVertex == null) firstNonODVertex = arc.getRightVertex();
                            extendedPath.addVertex(arc.getRightVertex());
                            addedAVertex = true;
                            break;
                        } else if (arc.getRightVertex() == lastVertex && vertex.getLine() == arc.getLeftVertex().getLine() && (firstNonODVertex == null || !extendedPath.getVertices().contains(arc.getLeftVertex())) && (firstLineArc == null || arc != firstLineArc)) {
                            if (firstNonODVertex != null && firstLineArc == null) firstLineArc = arc;
                            if (firstNonODVertex == null) firstNonODVertex = arc.getLeftVertex();
                            extendedPath.addVertex(arc.getLeftVertex());
                            addedAVertex = true;
                            break;
                        }
                    }
                    if (!addedAVertex) {
                        extendedPath.getVertices().removeAll(extendedPath.getVertices().subList(extendedPath.getVertices().indexOf(firstNonODVertex) + 1, extendedPath.getVertices().size()));

                    }
                }
            }
        }
        return extendedPath;
    }

    /******************
     * Solver connection
     *******************/

    // The initial LP is set up
    private static void setUpInitialLP() throws InterruptedException {
        XPRS.init();
        XPRB bcl = new XPRB();
        p = bcl.newProb("Minimum Changes Approach to Line Planning solved via Column Generation.");
        f = new XPRBvar[lp.size()];
        ctrDemand = new XPRBctr[ptn.getStops().size()][ptn.getStops().size()];
        ctrCapacity = new XPRBctr[ptn.getEdges().size()][lp.size()];
        XPRBctr[] ctrUpperFrequency = new XPRBctr[ptn.getEdges().size()];
        d = new HashMap<>();
        objective = p.newCtr("objective");
        Iterator<Integer> lineIterator = lp.getLines().keySet().iterator();
        Line lineIter;
        // Frequency variables are initialized
        while (lineIterator.hasNext()) {
            lineIter = lp.getLines().get(lineIterator.next());
            f[lineIter.getIndex() - 1] = p.newVar("f_" + lineIter.getIndex(), XPRB.PL, 0.0, Double.POSITIVE_INFINITY);
        }
        // Set up objective and demand restriction
        for (Stop origin : ptn.getStops()) {
            ctrDemand[origin.getIndex() - 1] = new XPRBctr[ptn.getStops().size()];
            for (Stop destination : ptn.getStops()) {
                if (od.getPassengersAt(origin, destination) > 0 && allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()) != null && !allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()).isEmpty()) {
                    ctrDemand[origin.getIndex() - 1][destination.getIndex() - 1] = p.newCtr("C_" + origin.getIndex() + "," + destination.getIndex());
                    ctrDemand[origin.getIndex() - 1][destination.getIndex() - 1].setType(XPRB.G); // Nebenbedingung vom Typ <=
                    ctrDemand[origin.getIndex() - 1][destination.getIndex() - 1].setTerm(od.getPassengersAt(origin, destination));
                    for (ArrayList<ChangeGoPath> listOfPaths : allChangeGoPaths.get(origin.getIndex() + "-" + destination.getIndex())) {
                        for (ChangeGoPath path : listOfPaths) {
                            d.put(origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex(), p.newVar("d_" + origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex(), XPRB.PL, 0.0, XPRB.INFINITY));
                            ctrDemand[origin.getIndex() - 1][destination.getIndex() - 1].setTerm(d.get(origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex()), 1.0);
                            objective.setTerm(d.get(origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex()), path.getNumberChanges());
                        }
                    }
                }
            }
        }
        //Set up frequency and capacity restrictions
        for (Edge edge : ptn.getEdges()) {
            ctrCapacity[edge.getIndex() - 1] = new XPRBctr[lp.size()];
            ctrUpperFrequency[edge.getIndex() - 1] = p.newCtr("f^max_" + edge.getIndex());
            ctrUpperFrequency[edge.getIndex() - 1].setType(XPRB.G);
            ctrUpperFrequency[edge.getIndex() - 1].setTerm(-1.0 * edge.getUpperFrequencyBound());
            ctrCapacity[edge.getIndex() - 1] = new XPRBctr[lp.size()];
            if (edgeLineAdjacency.get(edge.getIndex()) != null)
                for (Line line : edgeLineAdjacency.get(edge.getIndex())) {
                    ctrUpperFrequency[edge.getIndex() - 1].setTerm(f[line.getIndex() - 1], -1.0);
                    ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1] = p.newCtr("f^cap_" + edge.getIndex() + "," + line.getIndex());
                    ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1].setType(XPRB.G);
                    ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1].setTerm(f[line.getIndex() - 1], capacity);
                    for (Stop origin : ptn.getStops())
                        for (Stop destination : ptn.getStops()) {
                            if (od.getPassengersAt(origin, destination) > 0 && allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()) != null && !allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()).isEmpty()) {
                                for (PTNPath ptnPath : allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex())) {
                                    if (ptnPath.getEdges().contains(edge)) {
                                        for (ChangeGoPath path : allChangeGoPaths.get(origin.getIndex() + "-" + destination.getIndex()).get(allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()).indexOf(ptnPath))) {
                                            if (path.usesLineOnEdge(line, edge)) {
                                                ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1].setTerm(d.get(origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex()), -1.0);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }
        }

        p.setObj(objective);
        p.setMsgLevel(outputSolverMessages ? 4 : 0);
        try {
            if (writeLpFile) {
                p.exportProb(XPRB.LP, "line-planning/minchangelp.lp");
            }
        } catch (Exception e) {
            logger.warn("Error when writing lp file: " + e.getMessage());
        }
        p.lpOptimise("pd");
        if (p.getLPStat() == XPRB.LP_INFEAS) {
            logger.warn("LP INFEASIBLE!");
            p.getXPRSprob().firstIIS(1);
            p.getXPRSprob().writeIIS(0, "line-planning/minchangelp.ilp", 0);
            System.exit(1);
        }
        objectiveValue = p.getObjVal();
        demDual = new double[ptn.getStops().size()][ptn.getStops().size()];
        capDual = new double[ptn.getEdges().size()][lp.size()];
        for (Stop origin : ptn.getStops()) {
            demDual[origin.getIndex() - 1] = new double[ptn.getStops().size()];
            for (Stop destination : ptn.getStops()) {
                if (od.getPassengersAt(origin, destination) > 0 && allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()) != null && !allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()).isEmpty()) {
                    if (ctrDemand[origin.getIndex() - 1][destination.getIndex() - 1].getDual() != 0.0)
                        if (ctrDemand[origin.getIndex() - 1][destination.getIndex() - 1].getDual() < 0)
                            demDual[origin.getIndex() - 1][destination.getIndex() - 1] = 0.0;
                        else {
                            demDual[origin.getIndex() - 1][destination.getIndex() - 1] = ctrDemand[origin.getIndex() - 1][destination.getIndex() - 1].getDual();
                        }
                }
            }
        }
        for (Edge edge : ptn.getEdges()) {
            capDual[edge.getIndex() - 1] = new double[lp.size()];
            if (edgeLineAdjacency.get(edge.getIndex()) != null)
                for (Line line : edgeLineAdjacency.get(edge.getIndex())) {
                    if (ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1].getDual() != 0.0) {
                        if (ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1].getDual() < 0)
                            capDual[edge.getIndex() - 1][line.getIndex() - 1] = 0.0;
                        else {
                            capDual[edge.getIndex() - 1][line.getIndex() - 1] = ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1].getDual();
                        }
                    }
                }
        }
    }

    // Paths which are promising (i.e. have negative reduced costs) are added to the master LP
    private static double addThingsToLP(TreeSet<ChangeGoPath> paths) {
        logger.debug("New round of LP-solving with " + paths.size() + " more variables.");
        Iterator<ChangeGoPath> pathIterator = paths.iterator();
        Stop origin;
        Stop destination;
        ChangeGoPath path;
        // Add variables to objective, demand, and capacity restrictions
        while (pathIterator.hasNext()) {
            path = pathIterator.next();
            origin = ptn.getStop(path.getFirst().getStopIndex());
            destination = ptn.getStop(path.getLast().getStopIndex());
            d.put(origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex(), p.newVar("d_" + origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex(), XPRB.PL, 0.0, XPRB.INFINITY));
            ctrDemand[origin.getIndex() - 1][destination.getIndex() - 1].setTerm(d.get(origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex()), 1.0);
            objective.setTerm(d.get(origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex()), path.getNumberChanges());
            for (Edge edge : ptn.getEdges()) {
                for (PTNPath ptnPath : allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()))
                    if (ptnPath.getIndex() == path.getPTNPathIndex() && ptnPath.getEdges().contains(edge))
                        for (Line line : edgeLineAdjacency.get(edge.getIndex())) {
                            if (path.usesLineOnEdge(line, edge)) {
                                ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1].setTerm(d.get(origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex()), -1.0);
                            }
                        }
            }
        }
        p.setObj(objective);
        p.setMsgLevel(outputSolverMessages ? 4 : 0);
        try {
            if (writeLpFile) p.exportProb(XPRB.LP, "line-planning/minchange.lp");
        } catch (Exception e) {
            logger.warn("Unable to write lp file: " + e.getMessage());
        }
        p.lpOptimise("pd");
        if (p.getLPStat() == XPRB.LP_INFEAS) {
            logger.warn("LP INFEASIBLE!");
            p.getXPRSprob().firstIIS(1);
            p.getXPRSprob().writeIIS(0, "line-planning/minchange.ilp", 0);
            System.exit(1);
        }
        objectiveValue = p.getObjVal();
        demDual = new double[ptn.getStops().size()][ptn.getStops().size()];
        capDual = new double[ptn.getEdges().size()][lp.size()];
        for (Stop orig : ptn.getStops()) {
            demDual[orig.getIndex() - 1] = new double[ptn.getStops().size()];
            for (Stop dest : ptn.getStops()) {
                if (od.getPassengersAt(orig, dest) > 0 && allShortestPTNPaths.get(orig.getIndex() + "-" + dest.getIndex()) != null && !allShortestPTNPaths.get(orig.getIndex() + "-" + dest.getIndex()).isEmpty()) {
                    if (ctrDemand[orig.getIndex() - 1][dest.getIndex() - 1].getDual() != 0.0) {
                        demDual[orig.getIndex() - 1][dest.getIndex() - 1] = ctrDemand[orig.getIndex() - 1][dest.getIndex() - 1].getDual();
                        if (demDual[orig.getIndex() - 1][dest.getIndex() - 1] < 0)
                            demDual[orig.getIndex() - 1][dest.getIndex() - 1] = 0;
                    }
                }
            }
        }
        for (Edge edge : ptn.getEdges()) {
            capDual[edge.getIndex() - 1] = new double[lp.size()];
            for (Line line : edgeLineAdjacency.get(edge.getIndex())) {
                if (ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1].getDual() != 0.0) {
                    capDual[edge.getIndex() - 1][line.getIndex() - 1] = ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1].getDual();
                    if (capDual[edge.getIndex() - 1][line.getIndex() - 1] < 0)
                        capDual[edge.getIndex() - 1][line.getIndex() - 1] = 0;
                }
            }
        }

        return objectiveValue;
    }

    // Convert all fractional frequency variables into integer variables and remove all variables which have reduced costs higher than maxReducedCostsForIP in the last LP solution
    public static void solveIP() {
        Iterator<String> path_it = d.keySet().iterator();
        String orDestID;
        ArrayList<String> toBeRemoved = new ArrayList<>();
        while (path_it.hasNext()) {
            orDestID = path_it.next();
            if (d.get(orDestID).getRCost() > maxReducedCostsForIP) {
                ctrDemand[Integer.valueOf(orDestID.split(",")[0]) - 1][Integer.valueOf(orDestID.split(",")[1]) - 1].delTerm(d.get(orDestID));
                for (Edge edge : ptn.getEdges()) {
                    for (Line line : edgeLineAdjacency.get(edge.getIndex())) {
                        for (PTNPath ptnPath : allShortestPTNPaths.get(orDestID.substring(0, orDestID.lastIndexOf(',')).replaceAll(",", "-"))) {
                            for (ChangeGoPath cgPath : allChangeGoPaths.get(orDestID.substring(0, orDestID.lastIndexOf(',')).replaceAll(",", "-")).get(allShortestPTNPaths.get(orDestID.substring(0, orDestID.lastIndexOf(',')).replaceAll(",", "-")).indexOf(ptnPath))) {
                                if (cgPath.getIndex() == Integer.valueOf(orDestID.substring(orDestID.lastIndexOf(',') + 1, orDestID.toCharArray().length)) && ptnPath.getEdges().contains(edge)) {
                                    ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1].delTerm(d.get(orDestID));
                                    break;
                                }
                            }
                        }
                    }
                }
                objective.delTerm(d.get(orDestID));
                toBeRemoved.add(orDestID);
            }
        }
        for (String string : toBeRemoved) {
            d.remove(string);
        }
        Iterator<Integer> lineIterator = lp.getLines().keySet().iterator();
        while (lineIterator.hasNext()) {
            f[lp.getLines().get(lineIterator.next()).getIndex() - 1].setType(XPRB.UI);
        }
        p.setMsgLevel(outputSolverMessages ? 4 : 0);
        XPRS.init();
        XPRSprob opt = p.getXPRSprob();
        if (mipGap > 0) {
            opt.setDblControl(XPRS.MIPRELSTOP, mipGap);
        }
        if (timelimit > 0) {
            opt.setIntControl(XPRS.MAXTIME, -1 * timelimit);
        }
        if (threads > 0) {
            opt.setIntControl(XPRS.THREADS, threads);
        }
        try {
            if (writeLpFile) p.exportProb(XPRB.LP, "line-planning/minchangelpip.lp");
        } catch (Exception e) {
            logger.warn("Unable to write lp file: " + e.getMessage());
        }
        p.setSense(XPRB.MINIM);
        p.mipOptimise();
        int status = p.getMIPStat();
        if (p.getXPRSprob().getIntAttrib(XPRS.MIPSOLS) == 0) {
            logger.warn("No feasible solution found");
            if (p.getMIPStat() == XPRB.MIP_INFEAS) {
                logger.debug("Problem is infeasible");
                p.getXPRSprob().firstIIS(1);
                p.getXPRSprob().writeIIS(0, "line-planning/minchangelpip.ilp", 0);
            }
            throw new AlgorithmStoppingCriterionException("LinePlanning Min-Changes");
        }
        if (status == XPRS.MIP_OPTIMAL) {
            logger.debug("Optimal solution found");
        } else {
            logger.debug("Feasible solution found");
        }
        objectiveValue = p.getObjVal();
        logger.debug("Objval IP: " + objectiveValue);
    }

    /******************
     * Input/Output
     *******************/

    // Read necessary input. This is Stops, Edges, Loads, OD, Linepool, Linepool-Costs.
    private static void readInput(Config config) throws IOException {
        ptn = new PTN(!undirected);
        BufferedReader in = new BufferedReader(new FileReader(config.getStringValue("default_stops_file")));
        String[] split;
        String row;
        while ((row = in.readLine()) != null) {
            if (!row.startsWith("#")) {
                split = row.split(";");
                if (split.length > 0) {
                    ptn.addStop(new Stop(Integer.parseInt(split[0].trim()), "", "", 0., 0.));
                } else {
                    // File does not contain sufficient information
                    logger.warn("Unable to read " + config.getStringValue("default_stops_file"));
                    System.exit(1);
                }
            }
        }
        in = new BufferedReader(new FileReader(config.getStringValue("default_edges_file")));
        while ((row = in.readLine()) != null) {
            if (!row.startsWith("#")) {
                split = row.split(";");
                if (split.length > 4) {
                    ptn.addEdge(new Edge(!undirected, Integer.parseInt(split[0].trim()), ptn.getStop(Integer.parseInt(split[1].trim())), ptn.getStop(Integer.parseInt(split[2].trim())), Double.parseDouble(split[3].trim()), Integer.parseInt(split[4].trim()), Integer.parseInt(split[5].trim())));
                } else {
                    // File does not contain sufficient information
                    logger.warn("Unable to read " + config.getStringValue("default_edges_file"));
                    System.exit(1);
                }
            }
        }
        in.close();
        in = new BufferedReader(new FileReader(config.getStringValue("default_loads_file")));
        while ((row = in.readLine()) != null) {
            if (!row.startsWith("#")) {
                split = row.split(";");
                if (split.length > 3) {
                    ptn.getEdge(Integer.parseInt(split[0].trim())).setUpperFrequencyBound(Integer.parseInt(split[3].trim()));
                } else {
                    // File does not contain sufficient information
                    logger.warn("Unable to read " + config.getStringValue("default_loads_file"));
                    System.exit(1);
                }
            }
        }
        in.close();

        od = new OD();
        // parse OD matrix
        in = new BufferedReader(new FileReader(config.getStringValue("default_od_file")));
        while ((row = in.readLine()) != null) {
            if (!row.startsWith("#")) {
                split = row.split(";");
                if (split.length > 2) {
                    if (Double.parseDouble(split[2].trim()) > 0) {
                        od.setPassengersAt(ptn.getStop(Integer.parseInt(split[0].trim())), ptn.getStop(Integer
                            .parseInt(split[1].trim())), Double.parseDouble(split[2].trim()));
                    } else if (Double.parseDouble(split[2].trim()) > 0) {
                        od.setPassengersAt(ptn.getStop(Integer.parseInt(split[0].trim())), ptn.getStop(Integer.parseInt(split[1].trim())), Double.parseDouble(split[2].trim()));
                    } else {
                        od.setPassengersAt(ptn.getStop(Integer.parseInt(split[0].trim())), ptn.getStop(Integer.parseInt(split[1].trim())), Double.parseDouble(split[2].trim()));
                    }
                } else {
                    // File does not contain sufficient information
                    logger.warn("Unable to read " + config.getStringValue("default_od_file"));
                    System.exit(1);
                }
            }
        }
        in.close();

        for (Stop origin : ptn.getStops()) {
            for (Stop destination : ptn.getStops()) {
                if ((od.getPassengersByOrigin(origin) == null || !od.getPassengersByOrigin(origin).containsKey(destination))) {
                    od.setPassengersAt(origin, destination, od.getPassengersAt(destination, origin));
                }
            }
        }
        in.close();
        lp = new LinePool();
        in = new BufferedReader(new FileReader(config.getStringValue("default_pool_file")));
        while ((row = in.readLine()) != null) {
            if (!row.startsWith("#")) {
                split = row.split(";");
                if (split.length > 2) {
                    if (lp.getLine(Integer.parseInt(split[0].trim())) == null) {
                        lp.addLine(new Line(!undirected, Integer.parseInt(split[0].trim())));
                    }
                    lp.getLine(Integer.parseInt(split[0].trim())).addEdge(Integer.parseInt(split[1].trim()), ptn.getEdge(Integer.parseInt(split[2].trim())));
                } else {
                    // File does not contain sufficient information
                    System.exit(1);
                }
            }
        }
        in.close();
        in = new BufferedReader(new FileReader(config.getStringValue("default_pool_cost_file")));
        while ((row = in.readLine()) != null) {
            if (!row.startsWith("#")) {
                split = row.split(";");
                if (split.length > 2) {
                    if (lp.getLine(Integer.parseInt(split[0].trim())) != null) {
                        lp.getLine(Integer.parseInt(split[0].trim())).setLength(Double.parseDouble(split[1].trim().replaceAll(",", "\\.")));
                        lp.getLine(Integer.parseInt(split[0].trim())).setCosts(Double.parseDouble(split[2].trim().replaceAll(",", "\\.")));
                    }
                } else {
                    // File does not contain sufficient information
                    System.exit(1);
                }
            }
        }
        in.close();
    }


    // The final solution writing method
    private static void writeLineConcept(File outputFile) throws IOException {
        // print the solution as line concept
        PrintStream ps = new PrintStream(outputFile);
        ps.print("# optimal line concept with minimization of changes found" + newline);
        Line line;
        Iterator<Integer> lineIterator = lp.getLines().keySet().iterator();
        while (lineIterator.hasNext()) {
            line = lp.getLines().get(lineIterator.next());
            line.setFrequency((int) f[line.getIndex() - 1].getSol());
            for (Edge edge : line.getEdges()) {
                ps.print(line.getIndex() + ";" + (line.getEdges().indexOf(edge) + 1) + ";" + edge.getIndex() + ";" + line.getFrequency() + newline);
            }
        }
        ps.close();
    }
}
