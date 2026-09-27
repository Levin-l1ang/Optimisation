import com.dashoptimization.*;
import net.lintim.exception.AlgorithmStoppingCriterionException;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.exception.ConfigInvalidValueException;
import net.lintim.exception.InputFileException;
import net.lintim.io.ConfigReader;
import net.lintim.io.StatisticReader;
import net.lintim.io.StatisticWriter;
import net.lintim.util.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/**
 *
 */
public class CostDirect {

    private static final Logger logger = new Logger(CostDirect.class);

    public static String newline = "\n";

    public static void main(String[] args) throws IOException, InterruptedException {
        // read values from Config file(s)
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }
        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        String edgeFile = config.getStringValue("default_edges_file"); // "basis/Edge.giv";
        String demandFile = config.getStringValue("default_od_file"); // "basis/OD.giv";
        String lineFile = config.getStringValue("default_pool_file"); // "basis/Pool.giv";
        String loadFile = config.getStringValue("default_loads_file"); // "basis/Load.giv";
        String outputFile = config.getStringValue("default_lines_file"); // "line-planning/Line-Concept.lin";
        String linecostFile = config.getStringValue("default_pool_cost_file"); // "line-planning/Pool-Cost.giv";
        String lcHeader = config.getStringValue("lines_header");
        boolean undirected = config.getBooleanValue("ptn_is_undirected");
        boolean capRestricted = config.getBooleanValue("lc_mult_cap_restrict");
        double multicritRelation = config.getDoubleValue("lc_mult_relation");
        double tolerance = config.getDoubleValue("lc_mult_tolerance");
        int capacity = config.getIntegerValue("gen_passengers_per_vehicle");
        int commonFrequencyDivisor = config.getIntegerValue("lc_common_frequency_divisor");
        int periodLength = -1;
        if (commonFrequencyDivisor <= 0) {
            periodLength = config.getIntegerValue("period_length");
        }
        String optimizationModel = config.getStringValue("lc_model");
        int timeLimit = config.getIntegerValue("lc_timelimit");
        double mipGap = config.getDoubleValue("lc_mip_gap");
        int threads = config.getIntegerValue("lc_threads");
        boolean writeLpFile = config.getBooleanValue("lc_write_lp_file");
        boolean outputSolverMessages = config.getLogLevel("console_log_level") == LogLevel.DEBUG;
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        // parse edges
        // first time: count edges and find highest vertex index
        int n_vertices = findHighestNodeIndex(edgeFile);
        // second time: actually parse edges
        InputData input = new InputData(n_vertices);
        input.readEdges(edgeFile, undirected);
        input.readLoad(loadFile, undirected);
        input.readDemand(demandFile);
        input.readLinePool(lineFile, undirected);
        logger.info("Finished reading input data");

        logger.info("Begin computing line concept mult-cost-direct");
        // find all possible shortest paths in the ptn (not only within line
        // pool)
        String[][][] allPaths = Dijkstra.allShortestPaths(input.cost, input.names);
        // sum up numbers of possible shortest paths (only statistic)
        int x = 0;
        for (int i = 0; i < allPaths.length; i++)
            for (int j = i + 1; j < allPaths[i].length; j++)
                x += allPaths[i][j].length;

        input.readLineCosts(linecostFile);

        for (int l = 0; l < input.linePool.length; l++)
            logger.debug(input.linePool[l]);

        logger.debug(n_vertices + " stations read");
        logger.debug(x + " shortest pahts computed");
        logger.debug(input.linePool.length + " possible lines read");
        int[] bestSolution = null;

        // now set up the problem and solve it
        if (optimizationModel.equals("mult-cost-direct")) {
            Set<Integer> possibleSystemFrequencies = LinePlanningHelper.determinePossibleSystemFrequencies(commonFrequencyDivisor, periodLength);
            boolean optimalSolutionFound = false;
            double bestObjective = Double.NEGATIVE_INFINITY;
            Solution solution;
            double bestMipGap = 0;
            for (int commonFrequency : possibleSystemFrequencies) {
                solution = solve(input.linePool, input.lineVertices, allPaths, input.cost, input.names, input.demand,
                    input.lowerFrequency, input.upperFrequency, capacity, multicritRelation, input.linecost,
                    capRestricted, tolerance, commonFrequency, timeLimit, mipGap, writeLpFile, outputSolverMessages);
                if (solution == null) {
                    continue;
                }
                optimalSolutionFound = true;
                if (bestObjective < solution.getObjective()) {
                    // Found a better solution
                    logger.debug("New best objective is " + solution.getObjective() + ", before was " +
                        bestObjective);
                    bestObjective = solution.getObjective();
                    bestSolution = solution.getFrequencies();
                    bestMipGap = solution.getMipGap();
                }
            }
            if (!optimalSolutionFound) {
                logger.info("No feasible solution found");
                throw new AlgorithmStoppingCriterionException("lc mult-cost-direct");
            }
            logger.debug("Best found solution has objective value " + bestObjective);
            try {
                new StatisticReader.Builder().build().read();
            } catch (InputFileException exc) {
                logger.debug("Could not read statistic file, maybe it does not exist");
            }
            Statistic.putStatic("lc_mip_gap", String.format("%.2f", bestMipGap));
            new StatisticWriter.Builder().build().write();
        } else if (optimizationModel.equals("mult-cost-direct-relax")) {
            bestSolution = solveRelaxation(input.linePool, input.lineVertices, allPaths, input.cost, input.names,
                input.demand, input.lowerFrequency, input.upperFrequency, capacity, multicritRelation,
                input.linecost, capRestricted, tolerance, timeLimit, mipGap, threads, writeLpFile, outputSolverMessages);
        } else {
            throw new ConfigInvalidValueException(Arrays.asList("lc_model"));
        }
        logger.info("Finished computing line concept");

        logger.info("Begin writing output data");
        // print the solution as line concept
        PrintStream ps = new PrintStream(outputFile);
        ps.print("# " + lcHeader + newline);
        for (int l = 0; l < input.linePool.length; l++)
            ps.print(input.outputLines[l].replaceAll("\\$", "" + bestSolution[l]));
        ps.close();
        logger.info("Finished writing output data");
    }

    public static Solution solve(String[] linePool, TreeSet<Integer>[] lineVertices,
                                 String[][][] allPaths, double[][] edges, int[][] names,
                                 double[][] demand, double[][] lowerFrequency,
                                 double[][] upperFrequency, double vehicleCapacity,
                                 double multicritRelation, double[] linecost,
                                 boolean capRestricted, double tolerance, int commonFrequency,
                                 int timelimit, double mip_gap, boolean write_lp_file,
                                 boolean outputSolverMessages) {

        int n_vertices = edges.length;

        XPRS.init();
        XPRB bcl = new XPRB();

        XPRBprob p = bcl.newProb("Multicriteria Cost Model and Direct Travelers' Approach to Line Planning");

        XPRBvar[][][] d = new XPRBvar[n_vertices][n_vertices][linePool.length];
        XPRBvar[] f = new XPRBvar[linePool.length];
        XPRBctr[][] ctrDemand = new XPRBctr[n_vertices][n_vertices];
        XPRBctr[][][] ctrCapacity = new XPRBctr[n_vertices][n_vertices][linePool.length];
        XPRBctr[][] ctrUpperFrequency = new XPRBctr[n_vertices][n_vertices];
        XPRBctr[][] ctrLowerFrequency = new XPRBctr[n_vertices][n_vertices];
        XPRBctr objective = p.newCtr("objective");
        objective.setType(XPRB.N);

        logger.debug("Weight is set to : " + multicritRelation * 100 + "% cost-model and " + (1 - multicritRelation) * 100 +
            "% direct-travellers.");

        logger.debug("Create f variables");
        for (int l = 0; l < linePool.length; l++) {
            f[l] = p.newVar("f_" + l, XPRB.UI, 0.0, Double.POSITIVE_INFINITY);
            XPRBvar systemFrequencyMultiplier = p.newVar("g_" + l, XPRB.UI, 0, XPRB.INFINITY);
            p.newCtr("systemFrequency_" + l, f[l].eql(systemFrequencyMultiplier.mul
                (commonFrequency)));
        }

        logger.debug("Create d variables and demand constraints");
        for (int i = 0; i < n_vertices; i++) {
            if (Math.floor(100.0 * i / n_vertices) % 10 == 0)
                logger.debug("Node " + i + " of " + n_vertices);
            ctrDemand[i] = new XPRBctr[n_vertices];
            d[i] = new XPRBvar[n_vertices][linePool.length];
            for (int j = i + 1; j < n_vertices; j++) { // undirected demand!
                d[i][j] = new XPRBvar[linePool.length];
                ctrDemand[i][j] = p.newCtr("C_" + i + "," + j);
                ctrDemand[i][j].setType(XPRB.L); // Nebenbedingung vom Typ <=
                ctrDemand[i][j].setTerm(demand[i][j]); // right hand side = demand
                for (int l = 0; l < linePool.length; l++)
                    if (lineVertices[l].contains(i) && lineVertices[l].contains(j)) {
                        for (int k = 0; k < allPaths[i][j].length; k++) { // if there is a shortest path
                            if (linePool[l].contains(allPaths[i][j][k])) { // from i to j in line l
                                d[i][j][l] = p.newVar("d_" + i + "," + j + "," + l, XPRB.UI, 0.0, demand[i][j]);
                                ctrDemand[i][j].setTerm(d[i][j][l], 1.0); // add d_i,j,l to left hand side
                                objective.setTerm(d[i][j][l], (((double) Math.round((1 - multicritRelation) * 1000000)) / 1000000));
                            }
                        }
                    }
            }
        }
        logger.debug("Line costs: ");
        for (int l = 0; l < linePool.length; l++) {
            objective.addTerm(f[l], (((double) Math.round(-multicritRelation * linecost[l] * 1000000)) / 1000000)); // add
        }

        logger.debug("Create additional constraints");
        for (int i = 0; i < n_vertices; i++)
            for (int j = i + 1; j < n_vertices; j++)
                if (edges[i][j] != Double.POSITIVE_INFINITY) {
                    logger.debug("Node " + i + " of " + n_vertices);
                    ctrUpperFrequency[i][j] = p.newCtr("f^max_" + i + "," + j);
                    ctrUpperFrequency[i][j].setType(XPRB.L); // <=
                    ctrUpperFrequency[i][j].setTerm(upperFrequency[i][j]);
                    ctrLowerFrequency[i][j] = p.newCtr("f^min_" + i + "," + j);
                    ctrLowerFrequency[i][j].setType(XPRB.G); // >=
                    ctrLowerFrequency[i][j].setTerm(lowerFrequency[i][j]);
                    for (int l = 0; l < linePool.length; l++) {
                        if (linePool[l].contains("-" + names[i][j] + "-")) {
                            ctrUpperFrequency[i][j].setTerm(f[l], 1.0);
                            ctrLowerFrequency[i][j].setTerm(f[l], 1.0);
                            if (!capRestricted) {
                                vehicleCapacity = 100000000;
                            }
                            ctrCapacity[i][j][l] = p.newCtr("f^cap_" + i + "," + j + "," + l);
                            ctrCapacity[i][j][l].setType(XPRB.L); // <=
                            ctrCapacity[i][j][l].setTerm(0.0);
                            ctrCapacity[i][j][l].setTerm(f[l], (-1.0) * vehicleCapacity);
                            for (int ii : lineVertices[l])
                                for (int jj : lineVertices[l])
                                    if (ii < jj)
                                        for (int k = 0; k < allPaths[ii][jj].length; k++)
                                            if ((allPaths[ii][jj][k].contains("-" + names[i][j] + "-")) && (linePool[l].contains(allPaths[ii][jj][k]))) {
                                                ctrCapacity[i][j][l].setTerm(d[ii][jj][l], 1.0);
                                            }

                        }
                    }
                }

        p.setObj(objective);
        p.getXPRSprob().setDblControl(XPRS.FEASTOL, tolerance);
        p.getXPRSprob().setDblControl(XPRS.MIPTOL, tolerance);
        p.getXPRSprob().setDblControl(XPRS.OPTIMALITYTOL, tolerance);
        if (timelimit > 0) {
            p.getXPRSprob().setIntControl(XPRS.MAXTIME, timelimit);
        }
        if (mip_gap > 0) {
            p.getXPRSprob().setDblControl(XPRS.MIPRELSTOP, mip_gap);
        }
        if (outputSolverMessages) {
            p.setMsgLevel(4);
        } else {
            p.setMsgLevel(0);
        }
        if (write_lp_file) {
            try {
                p.exportProb(XPRB.LP, "line-planning/direct.lp");
            } catch (Exception e) {
                logger.warn("Cannot write lp file: " + e.getMessage());
            }
        }
        p.setSense(XPRB.MAXIM);
        p.mipOptimise();
        int[] f_sol = new int[linePool.length];
        double bestObjective = p.getXPRSprob().getDblAttrib(XPRS.MIPBESTOBJVAL);
        double bestBound = p.getXPRSprob().getDblAttrib(XPRS.BESTBOUND);
        double mipGap = Math.abs((bestObjective - bestBound) / bestObjective);
        double travellers = 0;
        double cost = 0;
        double[][] travellersOnEdge = new double[n_vertices][n_vertices];
        double[][][] ctrCapacityOnEdge = new double[n_vertices][n_vertices][linePool.length];
        int status = p.getMIPStat();
        if (p.getXPRSprob().getIntAttrib(XPRS.MIPSOLS) > 0) {
            if (status == XPRS.MIP_OPTIMAL) {
                logger.debug("Optimal solution found");
            } else {
                logger.debug("Feasible solution found");
            }
            for (int l = 0; l < linePool.length; l++) {
                logger.debug("Line " + (l + 1) + " with frequency " + f[l].getSol());
                f_sol[l] = (int) Math.round(f[l].getSol());
                cost += f_sol[l] * linecost[l];
                for (int i : lineVertices[l])
                    for (int j : lineVertices[l]) {
                        if ((d[i][j][l] != null)) {
                            travellersOnEdge[i][j] += d[i][j][l].getSol();
                        }
                    }
            }
            travellers = 0;
            for (int i = 0; i < n_vertices; i++)
                for (int j = i + 1; j < n_vertices; j++)
                    if (travellersOnEdge[i][j] > 0) {
                        travellers += travellersOnEdge[i][j];
                    }

            logger.debug("Optimal objective value: " + p.getObjVal() + ", Travellers: " + travellers + ", Cost: " + cost);

            return new Solution(f_sol, p.getObjVal(), mipGap);
        }
        logger.debug("No feasible solution found");

        if (p.getMIPStat() == XPRB.MIP_INFEAS) {
            logger.debug("Problem is infeasible");
            p.getXPRSprob().firstIIS(1);
            p.getXPRSprob().writeIIS(0, "line-planning/direct-r.ilp", 0);
        }
        return null;
    }

    public static int[] solveRelaxation(String[] linePool, TreeSet<Integer>[] lineVertices, String[][][] allPaths,
                                        double[][] edges, int[][] names, double[][] demand, double[][] lowerFrequency,
                                        double[][] upperFrequency, double vehicleCapacity, double multicritRelation,
                                        double[] linecost, boolean capRestricted, double tolerance, int timelimit,
                                        double mipGap, int threads, boolean writeLpFile, boolean outputSolverMessages) {

        int n_vertices = edges.length;

        XPRS.init();
        XPRB bcl = new XPRB();
        XPRBprob p = bcl.newProb("Multicriteria Cost Model and Direct Travelers' Approach to Line Planning with Relaxation");

        XPRBvar[][] d = new XPRBvar[n_vertices][n_vertices];
        XPRBvar[] f = new XPRBvar[linePool.length];
        // XPRBctr[][] ctrDemand = new XPRBctr[n_vertices][n_vertices];
        XPRBctr[][] ctrCapacity = new XPRBctr[n_vertices][n_vertices];
        XPRBctr[][] ctrUpperFrequency = new XPRBctr[n_vertices][n_vertices];
        XPRBctr[][] ctrLowerFrequency = new XPRBctr[n_vertices][n_vertices];
        XPRBctr objective = p.newCtr("objective");
        objective.setType(XPRB.N);

        logger.debug("Create f variables");
        for (int l = 0; l < linePool.length; l++)
            f[l] = p.newVar("f_" + l, XPRB.UI, 0.0, Double.POSITIVE_INFINITY);

        logger.debug("Create d variables and demand constraints");
        for (int i = 0; i < n_vertices; i++) {
            if (Math.floor(100.0 * i / n_vertices) % 10 == 0)
                logger.debug("Node " + i + " of " + n_vertices);
            // ctrDemand[i] = new XPRBctr[n_vertices];
            d[i] = new XPRBvar[n_vertices];
            for (int j = i + 1; j < n_vertices; j++) { // undirected demand!
                d[i][j] = p.newVar("D_" + i + "," + j, XPRB.UI, 0.0, demand[i][j]);
                // ctrDemand[i][j] = p.newCtr("C_" + i + "," + j);
                // ctrDemand[i][j].setType(XPRB.L); // Nebenbedingung vom Typ <=
                // ctrDemand[i][j].setTerm(demand[i][j]); // right hand side =
                // demand
                objective.setTerm(d[i][j], (((double) Math.round((1 - multicritRelation) * 1000000))) / 1000000);
            }
        }

        for (int l = 0; l < linePool.length; l++) {
            objective.addTerm(f[l], (((double) Math.round((-multicritRelation * linecost[l]) * 1000000)) / 1000000)); // add
            // cost
            // model
            // objective
            // function
        }

        logger.debug("Create additional constraints");
        for (int i = 0; i < n_vertices; i++)
            for (int j = i + 1; j < n_vertices; j++) {
                if (edges[i][j] != Double.POSITIVE_INFINITY) {
                    logger.debug("Node " + i + " of " + n_vertices);
                    ctrUpperFrequency[i][j] = p.newCtr("f^max_" + i + "," + j);
                    ctrUpperFrequency[i][j].setType(XPRB.L); // <=
                    ctrUpperFrequency[i][j].setTerm(upperFrequency[i][j]);
                    ctrLowerFrequency[i][j] = p.newCtr("f^min_" + i + "," + j);
                    ctrLowerFrequency[i][j].setType(XPRB.G); // >=
                    ctrLowerFrequency[i][j].setTerm(lowerFrequency[i][j]);
                    // ctrLowerFrequency[i][j].setTerm(0.0);
                    for (int l = 0; l < linePool.length; l++) {
                        if (linePool[l].contains("-" + names[i][j] + "-")) {
                            ctrUpperFrequency[i][j].setTerm(f[l], 1.0);
                            ctrLowerFrequency[i][j].setTerm(f[l], 1.0);
                        }
                    }
                }
                ctrCapacity[i][j] = p.newCtr("f^cap_" + i + "," + j);
                ctrCapacity[i][j].setType(XPRB.L); // <=
                ctrCapacity[i][j].setTerm(0.0);
                ctrCapacity[i][j].setTerm(d[i][j], 1.0);

            }
        if (!capRestricted) {
            vehicleCapacity = 100000000;
        }
        for (int l = 0; l < linePool.length; l++)
            for (int i : lineVertices[l])
                for (int j : lineVertices[l])
                    if (i < j) {
                        // for (int ii = 0; ii < n_vertices; ii++) for (int jj =
                        // ii + 1; jj < n_vertices; jj++)
                        for (int k = 0; k < allPaths[i][j].length; k++)
                            if (linePool[l].contains(allPaths[i][j][k])) {
                                ctrCapacity[i][j].setTerm(f[l], -1.0 * vehicleCapacity);
                            }
                    }

        p.setObj(objective);
        p.getXPRSprob().setDblControl(XPRS.FEASTOL, tolerance);
        p.getXPRSprob().setDblControl(XPRS.MIPTOL, tolerance);
        p.getXPRSprob().setDblControl(XPRS.OPTIMALITYTOL, tolerance);
        if (timelimit > 0) {
            p.getXPRSprob().setIntControl(XPRS.MAXTIME, -1 * timelimit);
        }
        if (mipGap > 0) {
            p.getXPRSprob().setDblControl(XPRS.MIPRELSTOP, mipGap);
        }
        if (threads > 0) {
            p.getXPRSprob().setIntControl(XPRS.THREADS, threads);
        }
        if (outputSolverMessages) {
            p.setMsgLevel(4);
        } else {
            p.setMsgLevel(0);
        }
        if (writeLpFile) {
            try {
                p.exportProb(XPRB.LP, "line-planning/direct_r.lp");
            } catch (Exception e) {
                logger.warn("Unable to write lp file: " + e.getMessage());
            }
        }
        p.setSense(XPRB.MAXIM);
        p.mipOptimise();
        double travellers = 0;
        double cost = 0;
        int[] f_sol = new int[linePool.length];
        int status = p.getMIPStat();
        if (p.getXPRSprob().getIntAttrib(XPRS.MIPSOLS) > 0) {
            if (status == XPRS.MIP_OPTIMAL) {
                logger.debug("Optimal solution found");
            } else {
                logger.debug("Feasible solution found");
            }
            for (int l = 0; l < linePool.length; l++) {
                logger.debug("Linie " + (l + 1) + ": " + f[l].getSol());
                f_sol[l] = (int) Math.round(f[l].getSol());
                cost += f_sol[l] * linecost[l];
            }
            for (int i = 0; i < n_vertices; i++)
                for (int j = i + 1; j < n_vertices; j++)
                    if ((d[i][j] != null)) {
                        travellers += d[i][j].getSol();
                    }

            logger.debug("Optimal objective value: " + p.getObjVal() + ", Direct Travelers: " + travellers + " Costs: " + cost);

            return f_sol;
        }
        logger.debug("No feasible solution found");
        if (p.getMIPStat() == XPRB.MIP_INFEAS) {
            System.out.println("Problem is infeasible");
            p.getXPRSprob().firstIIS(1);
            p.getXPRSprob().writeIIS(0, "line-planning/direct-r.ilp", 0);

        }
        return null;
    }

    private static int findHighestNodeIndex(String edgeFile) throws IOException {
        int n_vertices = 0;
        BufferedReader in = new BufferedReader(new FileReader(edgeFile));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.contains("#"))
                line = line.substring(0, line.indexOf("#"));
            if (!line.contains(";"))
                continue;
            line = line.substring(line.indexOf(";") + 1);
            if (!line.contains(";"))
                continue;
            int v = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            if (v > n_vertices)
                n_vertices = v;
            line = line.substring(line.indexOf(";") + 1);
            v = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            if (v > n_vertices)
                n_vertices = v;
        }
        in.close();
        return n_vertices;
    }

    private static class InputData {
        int highestNodeIndex;
        double[][] cost;
        int[][] names;
        Vector<Integer> left;
        Vector<Integer> right;
        double[][] upperFrequency;
        double[][] lowerFrequency;
        double[][] demand;
        String[] linePool;
        String[] outputLines;
        TreeSet<Integer>[] lineVertices;
        double[] linecost;

        public InputData(int highestNodeIndex) {
            this.highestNodeIndex = highestNodeIndex;
            cost = new double[highestNodeIndex][highestNodeIndex];
            names = new int[highestNodeIndex][highestNodeIndex];
            upperFrequency = new double[highestNodeIndex][highestNodeIndex];
            lowerFrequency = new double[highestNodeIndex][highestNodeIndex];
            left = new Vector<>();
            right = new Vector<>();
            demand = new double[highestNodeIndex][highestNodeIndex];
        }

        public void readEdges(String edgeFile, boolean undirected) throws IOException {
            String line;
            BufferedReader in = new BufferedReader(new FileReader(edgeFile));
            // initialize cost matrix with infinity (i.e. no connecting edge)
            for (int i = 0; i < highestNodeIndex; i++)
                for (int j = 0; j < highestNodeIndex; j++)
                    cost[i][j] = Double.POSITIVE_INFINITY;
            while ((line = in.readLine()) != null) {
                if (line.contains("#"))
                    line = line.substring(0, line.indexOf("#"));
                if (!line.contains(";"))
                    continue;
                int index = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
                line = line.substring(line.indexOf(";") + 1);
                if (!line.contains(";"))
                    continue;
                int v1 = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
                line = line.substring(line.indexOf(";") + 1);
                if (!line.contains(";"))
                    continue;
                int v2 = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
                line = line.substring(line.indexOf(";") + 1);
                if (!line.contains(";"))
                    continue;
                cost[v1 - 1][v2 - 1] = Double.parseDouble(line.substring(0, line.indexOf(";")).trim());
                if (undirected)
                    cost[v2 - 1][v1 - 1] = cost[v1 - 1][v2 - 1];
                line = line.substring(line.indexOf(";") + 1);
                if (!line.contains(";"))
                    continue;
                // lowerFrequency[v1 - 1][v2 - 1] =
                // Double.parseDouble(line.substring(0, line.indexOf(";")).trim());
                // lowerFrequency[v2 - 1][v1 - 1] = lowerFrequency[v1 - 1][v2 - 1];
                line = line.substring(line.indexOf(";") + 1);
                if (line.contains(";"))
                    line = line.substring(0, line.indexOf(";"));
                // upperFrequency[v1 - 1][v2 - 1] = Double.parseDouble(line.trim());
                // upperFrequency[v2 - 1][v1 - 1] = upperFrequency[v1 - 1][v2 - 1];
                if (left.size() <= index)
                    left.setSize(index + 1);
                if (right.size() <= index)
                    right.setSize(index + 1);
                left.set(index, v1 - 1);
                right.set(index, v2 - 1);
                names[v1 - 1][v2 - 1] = index;
                if (undirected)
                    names[v2 - 1][v1 - 1] = index;
            }
            in.close();
        }

        public void readLoad(String loadFile, boolean undirected) throws IOException {
            String line;
            BufferedReader in = new BufferedReader(new FileReader(loadFile));
            while ((line = in.readLine()) != null) {
                if (line.contains("#"))
                    line = line.substring(0, line.indexOf("#"));
                if (!line.contains(";"))
                    continue;
                int index = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
                int v1 = left.get(index);
                int v2 = right.get(index);
                line = line.substring(line.indexOf(";") + 1);
                if (!line.contains(";"))
                    continue;
                line = line.substring(line.indexOf(";") + 1);
                if (!line.contains(";"))
                    continue;
                lowerFrequency[v1][v2] = Double.parseDouble(line.substring(0, line.indexOf(";")).trim());
                if (undirected)
                    lowerFrequency[v2][v1] = lowerFrequency[v1][v2];
                if (!line.contains(";"))
                    continue;
                line = line.substring(line.indexOf(";") + 1);
                if (line.contains(";"))
                    line = line.substring(0, line.indexOf(";"));
                upperFrequency[v1][v2] = Double.parseDouble(line.trim());
                if (undirected)
                    upperFrequency[v2][v1] = upperFrequency[v1][v2];
            }
            in.close();
        }

        public void readDemand(String demandFile) throws IOException {
            String line;
            BufferedReader in = new BufferedReader(new FileReader(demandFile));
            while ((line = in.readLine()) != null) {
                if (line.contains("#"))
                    line = line.substring(0, line.indexOf("#"));
                if (!line.contains(";"))
                    continue;
                int v1 = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
                line = line.substring(line.indexOf(";") + 1);
                if (!line.contains(";"))
                    continue;
                int v2 = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
                line = line.substring(line.indexOf(";") + 1);
                if (line.contains(";"))
                    line = line.substring(0, line.indexOf(";"));
                demand[v1 - 1][v2 - 1] = Double.parseDouble(line.trim());
            }
            in.close();
        }

        public void readLinePool(String lineFile, boolean undirected) throws IOException {
            // lines are saved in the form "-1-2-3-4-"
            // (format used to determine whether a shortest path is contained within
            // a line
            // by doing string search)
            String line;
            BufferedReader in = new BufferedReader(new FileReader(lineFile));
            Vector<String> linePoolTemp = new Vector<>();
            Vector<String> output = new Vector<>();
            int ll = -1;
            int ii = 0;
            StringBuffer current = new StringBuffer();
            StringBuffer currentoutput = new StringBuffer();
            Vector<TreeSet<Integer>> lineVerticesTemp = new Vector<>();
            TreeSet<Integer> lvTemp = new TreeSet<>();
            while ((line = in.readLine()) != null) {
                if (line.contains("#"))
                    line = line.substring(0, line.indexOf("#"));
                if (!line.contains(";"))
                    continue;
                int l = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
                line = line.substring(line.indexOf(";") + 1);
                if (!line.contains(";"))
                    continue;
                int i = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
                line = line.substring(line.indexOf(";") + 1);
                if (line.contains(";"))
                    line = line.substring(0, line.indexOf(";"));
                int k = Integer.parseInt(line.trim());
                if (l < ll)
                    System.exit(1);
                if (l == ll) {
                    current.append("-").append(k);
                    if (undirected)
                        current.insert(0, k + "-");
                }
                if (l > ll) {
                    current.append("-");
                    current.insert(0, "-");
                    if (ll > 0) {
                        linePoolTemp.add(current.toString());
                        output.add(currentoutput.toString());
                    }
                    current = new StringBuffer();
                    currentoutput = new StringBuffer();
                    current.append(k);
                    ll = l;
                    ii = 0;
                    lvTemp = new TreeSet<>();
                    lineVerticesTemp.add(lvTemp);
                }
                lvTemp.add(left.get(k));
                lvTemp.add(right.get(k));
                if (i <= ii)
                    System.exit(2);
                ii = i;
                currentoutput.append(l).append(";").append(i).append(";").append(k).append(";$").append(newline);
            }
            current.append("-");
            current.insert(0, "-");
            if (ll > 0) {
                linePoolTemp.add(current.toString());
                output.add(currentoutput.toString());
            }
            in.close();
            linePool = linePoolTemp.toArray(new String[0]);
            outputLines = output.toArray(new String[0]);
            lineVertices = lineVerticesTemp.toArray(new TreeSet[0]);
        }

        public void readLineCosts(String lineCostFile) throws IOException {
            String line;
            BufferedReader in = new BufferedReader(new FileReader(lineCostFile));
            linecost = new double[linePool.length];
            while ((line = in.readLine()) != null) {
                if (line.contains("#"))
                    line = line.substring(0, line.indexOf("#"));
                if (!line.contains(";"))
                    continue;
                int linenumber = Integer.parseInt(line.substring(0, line.indexOf(";")));
                line = line.substring(line.indexOf(";") + 1);
                if (!line.contains(";"))
                    continue;
                line = line.substring(line.indexOf(";") + 1);
                double lcost = Double.parseDouble(line.trim());
                linecost[linenumber - 1] = lcost;
            }
        }
    }

    private static class Solution {
        private final int[] frequencies;
        private final double objective;
        private final double mipGap;

        public Solution(int[] frequencies, double objective, double mipGap) {
            this.frequencies = frequencies;
            this.objective = objective;
            this.mipGap = mipGap;
        }

        public int[] getFrequencies() {
            return frequencies;
        }

        public double getObjective() {
            return objective;
        }

        public double getMipGap() {
            return mipGap;
        }
    }

}
