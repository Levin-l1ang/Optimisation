import com.dashoptimization.*;
import net.lintim.exception.AlgorithmStoppingCriterionException;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.util.Config;
import net.lintim.util.LogLevel;
import net.lintim.util.Logger;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.KShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.alg.shortestpath.YenKShortestPath;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.io.*;
import java.util.*;

// Main class which computes the minchanges IP
public class MinChangesIP {

    public static String newline = "\n";
    private static String line_concept_file_path;
    private static boolean undirected;
    private static int capacity;
    private static int maxnrchanges;
    private static int nrPTNPaths;
    private static double mipGap;
    private static int timelimit;
    private static int threads;
    private static boolean writeLpFile;
    private static boolean outputSolverMessages;
    private static PTN ptn;
    private static LinePool lp;
    private static OD od;
    private static HashMap<String, ArrayList<PTNPath>> allShortestPTNPaths;
    private static HashMap<Integer, HashSet<Line>> edgeLineAdjacency;
    private static HashMap<String, LinkedList<ChangeGoPath>> allCGPaths;

    private static final Logger logger = new Logger(MinChangesIP.class);

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }
        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        readConfig(config);
        logger.info("Finished reading configuration");
        logger.info("Begin reading input files");
        readInput(config);
        logger.info("Finished reading input files");
        logger.info("Begin computing min changes line concept");
        logger.debug("Compute Edge-Line Adjacency for C&G-Graph!");
        computeEdgeLineAdjacency();
        logger.debug(" Done!");
        logger.debug("Setting up IP!");
        allCGPaths = new HashMap<>();
        computeKShortestPTNPaths();
        long time = System.currentTimeMillis();
        setUpIP();
        logger.debug(" Done!");
        logger.debug("Overall seconds: " + ((double) (System.currentTimeMillis() - time)) / 1000.0 + " s.");
        logger.info("Finished computing line concept");
        logger.info("Begin writing output files");
        writeLineConcept(line_concept_file_path);
        logger.info("Finished writing output files");
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
        // This parameter bounds the number of considered change\&go-paths.
        // Since the number of transfers per path is considered in the objective, the considered paths are correlated with their number of transfers.
        // A value of 0 means that all paths are considered.
        maxnrchanges = config.getIntegerValue("lc_minchanges_nr_max_changes");
        nrPTNPaths = config.getIntegerValue("lc_minchanges_nr_ptn_paths");
        if (nrPTNPaths <= 0) {
            logger.warn("\nINCONSISTENCY: lc_minchanges_nr_ptn_paths must be bigger than 0!");
        }
        mipGap = config.getDoubleValue("lc_mip_gap");
        timelimit = config.getIntegerValue("lc_timelimit");
        threads = config.getIntegerValue("lc_threads");
        writeLpFile = config.getBooleanValue("lc_write_lp_file");
        outputSolverMessages = config.getLogLevel("console_log_level") == LogLevel.DEBUG;
        line_concept_file_path = config.getStringValue("default_lines_file");
    }


    private static void computeEdgeLineAdjacency() {
        edgeLineAdjacency = new HashMap<>();
        for (Integer integer : lp.getLines().keySet()) {
            Line line = lp.getLines().get(integer);
            for (Edge edge : line.getEdges()) {
                if (!edgeLineAdjacency.containsKey(edge.getIndex()))
                    edgeLineAdjacency.put(edge.getIndex(), new HashSet<>());
                edgeLineAdjacency.get(edge.getIndex()).add(line);
            }
        }
    }

    /******************
     * Construction of tableau
     *******************/
    // From PTN data a graph is constructed and on this graph for every od-pair the k shortest paths are calculated.
    private static void computeKShortestPTNPaths() {
        int ptnPathCounter = 0;
        PTNPath ptnPath;
        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> kShortestPathsGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultWeightedEdge addedEdge;
        List<GraphPath<Integer, DefaultWeightedEdge>> paths;
        Edge addEdge;
        int currentVertex;
        Integer lastVertex;
        allShortestPTNPaths = new HashMap<>();
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
        for (Stop origin : ptn.getStops()) {
            for (Stop destination : ptn.getStops()) {
                if (od.getPassengersAt(origin, destination) > 0) {
                    allShortestPTNPaths.put(origin.getIndex() + "-" + destination.getIndex(), new ArrayList<>());
                    paths = algorithm.getPaths(origin.getIndex(), destination.getIndex(), nrPTNPaths);
                    if (paths.size() == 0) {
                        od.setPassengersAt(origin, destination, 0.0);
                        allShortestPTNPaths.put(origin.getIndex() + "-" + destination.getIndex(), null);
                    }
                    else {
                        for (GraphPath<Integer, DefaultWeightedEdge> path: paths) {
                            ptnPathCounter++;
                            ptnPath = new PTNPath(ptnPathCounter, new ArrayList<>());
                            lastVertex = null;
                            for (Integer vertex : path.getVertexList()) {
                                if (lastVertex == null) {
                                    lastVertex = vertex;
                                } else {
                                    currentVertex = vertex;
                                    addEdge = ptn.getEdge(lastVertex, currentVertex);
                                    ptnPath.addEdge(addEdge, addEdge.getLeft_stop().getIndex() != lastVertex);
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
    }

    // Paths on the change&go-network are calculated. The number could become very large so only those up to a transfer number of maxnrchanges
    private static LinkedList<ChangeGoPath> calculateAllPaths(ChangeGo cg, PTNPath ptnPath) {
        AllDirectedPaths<Vertex, DefaultEdge> adpg;
        SimpleDirectedGraph<Vertex, DefaultEdge> dg = new SimpleDirectedGraph<>(DefaultEdge.class);
        for (Vertex vertex : cg.getVertices()) {
            dg.addVertex(vertex);
        }
        for (Arc arc : cg.getArcs()) {
            dg.addEdge(arc.getLeftVertex(), arc.getRightVertex());
            dg.addEdge(arc.getRightVertex(), arc.getLeftVertex());
        }
        adpg = new AllDirectedPaths<>(dg);
        List<GraphPath<Vertex, DefaultEdge>> adps = adpg.getAllPaths(cg.getRootVertex(), cg.getDestinationVertex(), true, ptnPath.getEdges().size() + 2 * maxnrchanges + 2);
        List<DefaultEdge> edgeList;
        LinkedList<ChangeGoPath> paths = new LinkedList<>();
        ChangeGoPath addPath;
        Vertex lastVertex;
        for (GraphPath<Vertex, DefaultEdge> graphPath : adps) {
            edgeList = graphPath.getEdgeList();
            addPath = new ChangeGoPath();
            addPath.addVertex(graphPath.getStartVertex());
            lastVertex = graphPath.getStartVertex();
            for (DefaultEdge edge : edgeList) {
                if (graphPath.getGraph().getEdgeSource(edge) == lastVertex) {
                    addPath.addVertex(graphPath.getGraph().getEdgeTarget(edge));
                    lastVertex = graphPath.getGraph().getEdgeTarget(edge);
                } else {
                    addPath.addVertex(graphPath.getGraph().getEdgeSource(edge));
                    lastVertex = graphPath.getGraph().getEdgeSource(edge);
                }
            }
            if (addPath.getNumberChanges() - 1 <= maxnrchanges && !addPath.usesAVertexTwice() && !addPath.usesALineTwice())
                paths.add(addPath);
        }
        return paths;
    }

    // Construct the particular change&go-graph representing the ptnPath
    private static ChangeGo constructPartCG(PTNPath ptnPath) {
        ChangeGo cg = new ChangeGo();
        boolean turnedEdge;
        Stop lastStop;
        Vertex originVertex;
        Vertex rightVertex;
        Vertex leftVertex;
        HashSet<Line> lastLines = new HashSet<>();
        // Initial OD vertex
        originVertex = new Vertex(cg.getVertices().size() + 1, ptnPath.getStops().get(0).getIndex(), null);
        cg.addVertex(originVertex);
        cg.setRootVertex(originVertex);
        lastStop = ptnPath.getStops().get(0);
        Arc arc;
        for (Edge edge : ptnPath.getEdges()) {
            turnedEdge = lastStop != null && lastStop == edge.getRight_stop();
            // OD Vertex
            originVertex = new Vertex(cg.getVertices().size() + 1, (turnedEdge ? edge.getLeft_stop().getIndex() : edge.getRight_stop().getIndex()), null);
            cg.addVertex(originVertex);
            if (originVertex.getStopIndex() == ptnPath.getStops().get(ptnPath.getStops().size() - 1).getIndex())
                cg.setDestinationVertex(originVertex);
            if (edgeLineAdjacency.get(edge.getIndex()) != null)
                for (Line line : edgeLineAdjacency.get(edge.getIndex())) {
                    // Right vertex of edge
                    rightVertex = new Vertex(cg.getVertices().size() + 1, (turnedEdge ? edge.getLeft_stop().getIndex() : edge.getRight_stop().getIndex()), line);
                    cg.addVertex(rightVertex);
                    // OD Arc
                    arc = new Arc(cg.getArcs().size() + 1, !undirected, originVertex, rightVertex, 1.0);
                    originVertex.addOutgoingArc(arc);
                    rightVertex.addOutgoingArc(arc);
                    cg.addArc(arc);
                    if (!lastLines.contains(line)) {
                        // In case necessary: Left vertex of edge
                        leftVertex = new Vertex(cg.getVertices().size() + 1, (turnedEdge ? edge.getRight_stop().getIndex() : edge.getLeft_stop().getIndex()), line);
                        cg.addVertex(leftVertex);
                        // OD Arc
                        for (Vertex vertex : cg.getVertices()) {
                            if (vertex.getLine() == null && vertex.getStopIndex() == (turnedEdge ? edge.getRight_stop().getIndex() : edge.getLeft_stop().getIndex())) {
                                arc = new Arc(cg.getArcs().size() + 1, !undirected, vertex, leftVertex, 1.0);
                                vertex.addOutgoingArc(arc);
                                leftVertex.addOutgoingArc(arc);
                                cg.addArc(arc);
                                break;
                            }
                        }
                    }

                    // Driving Arcs
                    for (Vertex vertex : cg.getVertices())
                        if (vertex.getStopIndex() == (turnedEdge ? edge.getRight_stop().getIndex() : edge.getLeft_stop().getIndex()) && vertex.getLine() == line) {
                            leftVertex = vertex;
                            arc = new Arc(cg.getArcs().size() + 1, !undirected, leftVertex, rightVertex, edge.getLength());
                            leftVertex.addOutgoingArc(arc);
                            rightVertex.addOutgoingArc(arc);
                            cg.addArc(arc);
                            break;
                        }
                }
            lastStop = (turnedEdge ? edge.getLeft_stop() : edge.getRight_stop());
            lastLines = edgeLineAdjacency.get(edge.getIndex());
        }
        return cg;
    }


    /******************
     * Solver connection
     *******************/

    private static void setUpIP() {
        XPRS.init();
        XPRB bcl = new XPRB();

        XPRBprob p = bcl.newProb("Minimum Changes Approach to Line Planning");
        HashMap<String, XPRBvar> d = new HashMap<>();
        XPRBvar[] f = new XPRBvar[lp.size()];
        XPRBctr[][] ctrDemand = new XPRBctr[ptn.getStops().size()][ptn.getStops().size()];
        XPRBctr[][] ctrCapacity = new XPRBctr[ptn.getEdges().size()][lp.size()];
        XPRBctr[] ctrUpperFrequency = new XPRBctr[ptn.getEdges().size()];
        XPRBctr objective = p.newCtr("objective");

        Iterator<Integer> lineIterator = lp.getLines().keySet().iterator();
        Line lineIter;
        while (lineIterator.hasNext()) {
            lineIter = lp.getLines().get(lineIterator.next());
            f[lineIter.getIndex() - 1] = p.newVar("f_" + lineIter.getIndex(), XPRB.UI, 0.0, Double.POSITIVE_INFINITY);
        }

        ChangeGo cg;
        LinkedList<ChangeGoPath> paths;
        int pathCounter = 0;
        for (Stop origin : ptn.getStops()) {
            for (Stop destination : ptn.getStops()) {
                if (od.getPassengersAt(origin, destination) > 0 && allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()) != null && !allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()).isEmpty()) {
                    for (PTNPath ptnPath : allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex())) {

                        cg = constructPartCG(ptnPath);
                        paths = calculateAllPaths(cg, ptnPath);
                        if (allCGPaths.get(origin.getIndex() + "-" + destination.getIndex()) == null) {
                            allCGPaths.put(origin.getIndex() + "-" + destination.getIndex(), paths);
                        } else {
                            allCGPaths.get(origin.getIndex() + "-" + destination.getIndex()).addAll(paths);
                        }
                        for (ChangeGoPath path : paths) {
                            path.setPTNPathIndex(ptnPath.getIndex());
                            path.setIndex(pathCounter++);
                        }
                    }
                }
            }
        }
        // Set up demand restriction and objective
        for (Stop origin : ptn.getStops()) {
            ctrDemand[origin.getIndex() - 1] = new XPRBctr[ptn.getStops().size()];
            ctrCapacity[origin.getIndex() - 1] = new XPRBctr[ptn.getStops().size()];
            for (Stop destination : ptn.getStops()) {
                if (od.getPassengersAt(origin, destination) > 0 && allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()) != null && !allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()).isEmpty()) {
                    ctrDemand[origin.getIndex() - 1][destination.getIndex() - 1] = p.newCtr("C_" + origin.getIndex() + "," + destination.getIndex());
                    ctrDemand[origin.getIndex() - 1][destination.getIndex() - 1].setType(XPRB.G); // Nebenbedingung vom Typ <=
                    ctrDemand[origin.getIndex() - 1][destination.getIndex() - 1].setTerm(od.getPassengersAt(origin, destination));
                    for (ChangeGoPath path : allCGPaths.get(origin.getIndex() + "-" + destination.getIndex())) {
                        d.put(origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex(), p.newVar("d_" + origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex(), XPRB.PL, 0.0, od.getPassengersAt(origin, destination)));
                        ctrDemand[origin.getIndex() - 1][destination.getIndex() - 1].setTerm(d.get(origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex()), 1.0);
                        objective.setTerm(d.get(origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex()), path.getNumberChanges());
                    }
                }
            }
        }

        // Set up frequency und capacity constraints
        for (Edge edge : ptn.getEdges()) {
            ctrUpperFrequency[edge.getIndex() - 1] = p.newCtr("f^max_" + edge.getIndex());
            ctrUpperFrequency[edge.getIndex() - 1].setType(XPRB.G);
            ctrUpperFrequency[edge.getIndex() - 1].setTerm(-1.0 * edge.getUpperFrequencyBound());
            ctrCapacity[edge.getIndex() - 1] = new XPRBctr[lp.size()];
            if (edgeLineAdjacency.get(edge.getIndex()) != null)
                for (Line line : edgeLineAdjacency.get(edge.getIndex())) {
                    ctrUpperFrequency[edge.getIndex() - 1].setTerm(f[line.getIndex() - 1], -1.0);
                    ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1] = p.newCtr("f^cap_" + edge.getIndex() + "," + line.getIndex());
                    ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1].setType(XPRB.L);
                    ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1].setTerm(f[line.getIndex() - 1], -1.0 * capacity);
                    for (Stop origin : ptn.getStops())
                        for (Stop destination : ptn.getStops())
                            if (od.getPassengersAt(origin, destination) > 0 && allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()) != null && !allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()).isEmpty())
                                for (PTNPath ptnPath : allShortestPTNPaths.get(origin.getIndex() + "-" + destination.getIndex()))
                                    if (ptnPath.getEdges().contains(edge))
                                        for (ChangeGoPath path : allCGPaths.get(origin.getIndex() + "-" + destination.getIndex()))
                                            if (path.getPTNPathIndex() == ptnPath.getIndex() && path.usesLineOnEdge(line, edge))
                                                ctrCapacity[edge.getIndex() - 1][line.getIndex() - 1].setTerm(d.get(origin.getIndex() + "," + destination.getIndex() + "," + path.getIndex()), 1.0);
                }
        }

        p.setObj(objective);
        XPRS.init();
        XPRSprob opt = p.getXPRSprob();
        p.setMsgLevel(outputSolverMessages ? 4 : 0);
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
            if (writeLpFile) p.exportProb(XPRB.LP, "line-planning/minchangeip.lp");
        } catch (Exception e) {
            logger.warn("Error when writing lp file: " + e.getMessage());
        }
        p.setSense(XPRB.MINIM);
        p.mipOptimise();
        int status = p.getMIPStat();
        if (p.getXPRSprob().getIntAttrib(XPRS.MIPSOLS) == 0) {
            logger.warn("No feasible solution found");
            if (p.getMIPStat() == XPRB.MIP_INFEAS) {
                logger.debug("Problem is infeasible");
                p.getXPRSprob().firstIIS(1);
                p.getXPRSprob().writeIIS(0, "line-planning/minchangeip.ilp", 0);
            }
            throw new AlgorithmStoppingCriterionException("LinePlanning Min-Changes");
        }
        if (status == XPRS.MIP_OPTIMAL) {
            logger.debug("Optimal solution found");
        } else {
            logger.debug("Feasible solution found");
        }
        lineIterator = lp.getLines().keySet().iterator();
        while (lineIterator.hasNext()) {
            lineIter = lp.getLines().get(lineIterator.next());
            lineIter.setFrequency((int) Math.round(f[lineIter.getIndex() - 1].getSol()));
        }
    }


    /******************
     * Input/Output
     *******************/


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
                    logger.warn("Invalid format of " + config.getStringValue("default_stops_file"));
                    System.exit(1);
                }

            }
        }

        in = new BufferedReader(new FileReader(config.getStringValue("default_edges_file")));
        while ((row = in.readLine()) != null) {
            if (!row.startsWith("#")) {
                split = row.split(";");
                if (split.length > 4) {
                    ptn.addEdge(new Edge(!undirected, Integer.parseInt(split[0].trim()), ptn.getStop(Integer.parseInt(split[1].trim())), ptn.getStop(Integer.parseInt(split[2].trim())), Double.parseDouble(split[3].trim()), Integer.parseInt(split[4].trim()), 0));
                } else {
                    // File does not contain sufficient information
                    logger.warn("Invalid format of " + config.getStringValue("default_edges_file"));
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
                    logger.warn("Invalid format of " + config.getStringValue("default_loads_file"));
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
                        od.setPassengersAt(ptn.getStop(Integer.parseInt(split[0].trim())), ptn.getStop(Integer.parseInt(split[1].trim())), Double.parseDouble(split[2].trim()));
                    } else if (Double.parseDouble(split[2].trim()) > 0) {
                        od.setPassengersAt(ptn.getStop(Integer.parseInt(split[0].trim())), ptn.getStop(Integer.parseInt(split[1].trim())), Double.parseDouble(split[2].trim()));
                    } else {
                        od.setPassengersAt(ptn.getStop(Integer.parseInt(split[0].trim())), ptn.getStop(Integer.parseInt(split[1].trim())), Double.parseDouble(split[2].trim()));
                    }
                } else {
                    // File does not contain sufficient information
                    logger.warn("Invalid format of " + config.getStringValue("default_od_file"));
                    System.exit(1);
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
                    logger.warn("Invalid format of " + config.getStringValue("default_pool_file"));
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
                    logger.warn("Invalid format of " + config.getStringValue("default_pool_cost_file"));
                    System.exit(1);
                }

            }
        }
        in.close();
    }

    // Write solution line concept
    private static void writeLineConcept(String filePath) throws IOException {
        // print the solution as line concept
        PrintStream ps = new PrintStream(filePath);
        ps.print("# optimal line concept with minimization of changes found" + newline);
        Iterator<Integer> lineIterator = lp.getLines().keySet().iterator();
        Line line;
        while (lineIterator.hasNext()) {
            line = lp.getLines().get(lineIterator.next());
            for (Edge edge : line.getEdges()) {
                ps.print(line.getIndex() + ";" + (line.getEdges().indexOf(edge) + 1) + ";" + edge.getIndex() + ";" + line.getFrequency() + newline);
            }
        }
        ps.close();
    }
}
