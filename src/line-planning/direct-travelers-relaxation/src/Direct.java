import com.dashoptimization.*;
import net.lintim.exception.AlgorithmStoppingCriterionException;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.util.Config;
import net.lintim.util.LogLevel;
import net.lintim.util.Logger;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Vector;
import java.io.PrintStream;
import java.util.TreeSet;

/**
 *
 */
public class Direct {

    public static String newline = "\n";

    private static final Logger logger = new Logger(Direct.class);

    public static void main(String[] args) throws IOException {
        // read values from Config file(s)
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }
        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        String edgeFile = config.getStringValue("default_edges_file"); //"basis/Edge.giv";
        String demandFile = config.getStringValue("default_od_file"); //"basis/OD.giv";
        String lineFile = config.getStringValue("default_pool_file"); //"basis/Pool.giv";
        String loadFile = config.getStringValue("default_loads_file"); //"basis/Load.giv";
        String outputFile = config.getStringValue("default_lines_file"); //"line-planning/Line-Concept.lin";
        boolean undirected = config.getBooleanValue("ptn_is_undirected");
        int capacity = config.getIntegerValue("gen_passengers_per_vehicle");
        int timeLimit = config.getIntegerValue("lc_timelimit");
        double mipGap = config.getDoubleValue("lc_mip_gap");
        int threadLimit = config.getIntegerValue("lc_threads");
        boolean writeLpFile = config.getBooleanValue("lc_write_lp_file");
        boolean outputSolverMessages = config.getLogLevel("console_log_level") == LogLevel.DEBUG;
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        // parse edges
        // first time: count edges and find highest vertex index
        int n_vertices = 0;
        BufferedReader in = new BufferedReader(new FileReader(edgeFile));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.contains("#"))
                line = line.substring(0, line.indexOf("#"));
            if (!line.contains(";")) continue;
            line = line.substring(line.indexOf(";") + 1);
            if (!line.contains(";")) continue;
            int v = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            if (v > n_vertices) n_vertices = v;
            line = line.substring(line.indexOf(";") + 1);
            v = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            if (v > n_vertices) n_vertices = v;
        }
        in.close();
        // second time: actually parse edges
        in = new BufferedReader(new FileReader(edgeFile));
        double[][] cost = new double[n_vertices][n_vertices];
        int[][] names = new int[n_vertices][n_vertices];
        double[][] upperFrequency = new double[n_vertices][n_vertices];
        double[][] lowerFrequency = new double[n_vertices][n_vertices];
        Vector<Integer> left = new Vector<>();
        Vector<Integer> right = new Vector<>();
        // initialize cost matrix with infinity (i.e. no connecting edge)
        for (int i = 0; i < n_vertices; i++)
            for (int j = 0; j < n_vertices; j++)
                cost[i][j] = Double.POSITIVE_INFINITY;
        while ((line = in.readLine()) != null) {
            if (line.contains("#"))
                line = line.substring(0, line.indexOf("#"));
            if (!line.contains(";")) continue;
            int index = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            line = line.substring(line.indexOf(";") + 1);
            if (!line.contains(";")) continue;
            int v1 = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            line = line.substring(line.indexOf(";") + 1);
            if (!line.contains(";")) continue;
            int v2 = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            line = line.substring(line.indexOf(";") + 1);
            if (!line.contains(";")) continue;
            cost[v1 - 1][v2 - 1] = Double.parseDouble(line.substring(0, line.indexOf(";")).trim());
            if (undirected) cost[v2 - 1][v1 - 1] = cost[v1 - 1][v2 - 1];
            line = line.substring(line.indexOf(";") + 1);
            if (!line.contains(";")) continue;
            //lowerFrequency[v1 - 1][v2 - 1] = Double.parseDouble(line.substring(0, line.indexOf(";")).trim());
            //lowerFrequency[v2 - 1][v1 - 1] = lowerFrequency[v1 - 1][v2 - 1];
            line = line.substring(line.indexOf(";") + 1);
            if (line.contains(";"))
                line = line.substring(0, line.indexOf(";"));
            //upperFrequency[v1 - 1][v2 - 1] = Double.parseDouble(line.trim());
            //upperFrequency[v2 - 1][v1 - 1] = upperFrequency[v1 - 1][v2 - 1];
            if (left.size() <= index) left.setSize(index + 1);
            if (right.size() <= index) right.setSize(index + 1);
            left.set(index, v1 - 1);
            right.set(index, v2 - 1);
            names[v1 - 1][v2 - 1] = index;
            if (undirected) names[v2 - 1][v1 - 1] = index;
        }
        in.close();

        // parse load
        in = new BufferedReader(new FileReader(loadFile));
        while ((line = in.readLine()) != null) {
            if (line.contains("#"))
                line = line.substring(0, line.indexOf("#"));
            if (!line.contains(";")) continue;
            int index = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            int v1 = left.get(index);
            int v2 = right.get(index);
            line = line.substring(line.indexOf(";") + 1);
            if (!line.contains(";")) continue;
            line = line.substring(line.indexOf(";") + 1);
            if (!line.contains(";")) continue;
            lowerFrequency[v1][v2] = Double.parseDouble(line.substring(0, line.indexOf(";")).trim());
            if (undirected) lowerFrequency[v2][v1] = lowerFrequency[v1][v2];
            if (!line.contains(";")) continue;
            line = line.substring(line.indexOf(";") + 1);
            if (line.contains(";"))
                line = line.substring(0, line.indexOf(";"));
            upperFrequency[v1][v2] = Double.parseDouble(line.trim());
            if (undirected) upperFrequency[v2][v1] = upperFrequency[v1][v2];
        }
        in.close();

        // parse OD matrix
        in = new BufferedReader(new FileReader(demandFile));
        double[][] demand = new double[n_vertices][n_vertices];
        while ((line = in.readLine()) != null) {
            if (line.contains("#"))
                line = line.substring(0, line.indexOf("#"));
            if (!line.contains(";")) continue;
            int v1 = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            line = line.substring(line.indexOf(";") + 1);
            if (!line.contains(";")) continue;
            int v2 = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            line = line.substring(line.indexOf(";") + 1);
            if (line.contains(";"))
                line = line.substring(0, line.indexOf(";"));
            demand[v1 - 1][v2 - 1] = Double.parseDouble(line.trim());
        }
        in.close();

        // parse line pool
        // lines are saved in the form "-1-2-3-4-"
        // (format used to determine whether a shortest path is contained within a line
        //  by doing string search)
        in = new BufferedReader(new FileReader(lineFile));
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
            if (!line.contains(";")) continue;
            int l = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            line = line.substring(line.indexOf(";") + 1);
            if (!line.contains(";")) continue;
            int i = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            line = line.substring(line.indexOf(";") + 1);
            if (line.contains(";"))
                line = line.substring(0, line.indexOf(";"));
            int k = Integer.parseInt(line.trim());
            if (l < ll) System.exit(1);
            if (l == ll) {
                current.append("-").append(k);
                if (undirected) current.insert(0, k + "-");
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
            if (i <= ii) System.exit(2);
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
        String[] linePool = linePoolTemp.toArray(new String[0]);
        String[] outputLines = output.toArray(new String[0]);
        TreeSet<Integer>[] lineVertices = lineVerticesTemp.toArray(new TreeSet[0]);

        logger.info("Finished reading input data");

        logger.info("Begin comuting of line concept direct-relaxation solution");
        // find all possible shortest paths in the ptn (not only within line pool)
        String[][][] allPaths = Dijkstra.allShortestPaths(cost, names);
        // sum up numbers of possible shortest paths (only statistic)
        int x = 0;
        for (int i = 0; i < allPaths.length; i++)
            for (int j = i + 1; j < allPaths[i].length; j++) {
                x += allPaths[i][j].length;
            }
        for (int l = 0; l < linePool.length; l++)
            logger.debug(linePool[l]);

        logger.debug(n_vertices + " stops read");
        logger.debug(x + " shortest paths computes");
        logger.debug(linePool.length + " possible lines read");

        // now set up the problem and solve it
        int[] solution = solveRelaxation(linePool, lineVertices, allPaths, cost, names, demand, lowerFrequency,
            upperFrequency, capacity, undirected, timeLimit, mipGap, threadLimit, writeLpFile, outputSolverMessages);

        if (solution == null) {
            logger.info("No feasible solution found");
            throw new AlgorithmStoppingCriterionException("lc direct-relaxation");
        }

        logger.info("Finished computing line concept");

        logger.info("Begin writing output data");
        // print the solution as line concept
        PrintStream ps = new PrintStream(outputFile);
        ps.print("# optimal line concept with relaxated Direct-travelers approach" + newline);
        for (int l = 0; l < linePool.length; l++)
            ps.print(outputLines[l].replaceAll("\\$", "" + solution[l]));
        ps.close();
        logger.info("Finished writing output data");

    }

    public static int[] solveRelaxation(String[] linePool, TreeSet<Integer>[] lineVertices, String[][][] allPaths,
                                        double[][] edges, int[][] names, double[][] demand, double[][] lowerFrequency,
                                        double[][] upperFrequency, double vehicleCapacity, boolean undirected,
                                        int timeLimit, double mipGap, int threads, boolean writeLpFile,
                                        boolean outputSolverMessages) {

        int n_vertices = edges.length;

        XPRS.init();
        XPRB bcl = new XPRB();

        XPRBprob p = bcl.newProb("Direct Travelers' Approach to Line Planning with Relaxation");

        XPRBvar[][] d = new XPRBvar[n_vertices][n_vertices];
        XPRBvar[] f = new XPRBvar[linePool.length];
        XPRBctr[][] ctrCapacity = new XPRBctr[n_vertices][n_vertices];
        XPRBctr[][] ctrUpperFrequency = new XPRBctr[n_vertices][n_vertices];
        XPRBctr[][] ctrLowerFrequency = new XPRBctr[n_vertices][n_vertices];
        XPRBctr objective = p.newCtr("objective");
        objective.setType(XPRB.N);

        logger.debug("create f-variables");
        for (int l = 0; l < linePool.length; l++)
            f[l] = p.newVar("f_" + l, XPRB.UI, 0.0, Double.POSITIVE_INFINITY);

        logger.debug("create d-variables and add demand constraints");
        for (int i = 0; i < n_vertices; i++) {
            if (Math.floor(100.0 * i / n_vertices) % 10 == 0)
                logger.debug("Stop " + i + " of " + n_vertices);
            d[i] = new XPRBvar[n_vertices];
            for (int j = 0; j < n_vertices; j++) {
                d[i][j] = p.newVar("D_" + i + "," + j, XPRB.UI, 0.0, demand[i][j]);
                objective.setTerm(d[i][j], 1.0);
            }
        }

        logger.debug("Add additional constraints");
        for (int i = 0; i < n_vertices; i++)
            for (int j = 0; j < n_vertices; j++) {
                if ((!undirected || (i < j)) && (edges[i][j] != Double.POSITIVE_INFINITY)) {
                    logger.debug("Origin " + i + " of " + n_vertices);
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
                        }
                    }
                }

                ctrCapacity[i][j] = p.newCtr("f^cap_" + i + "," + j);
                ctrCapacity[i][j].setType(XPRB.L); // <=
                ctrCapacity[i][j].setTerm(0.0);
                ctrCapacity[i][j].setTerm(d[i][j], 1.0);


            }

        for (int l = 0; l < linePool.length; l++)
            for (int i : lineVertices[l])
                for (int j : lineVertices[l]) {
                    for (int k = 0; k < allPaths[i][j].length; k++)
                        if (linePool[l].contains(allPaths[i][j][k])) {
                            ctrCapacity[i][j].setTerm(f[l], -1.0 * vehicleCapacity);
                        }
                }


        p.setObj(objective);
        if (timeLimit > 0) {
            p.getXPRSprob().setIntControl(XPRS.MAXTIME, -1 * timeLimit);
        }
        if (mipGap > 0) {
            p.getXPRSprob().setDblControl(XPRS.MIPRELSTOP, mipGap);
        }
        if (threads > 0) {
            p.getXPRSprob().setIntControl(XPRS.THREADS, threads);
        }
        if (writeLpFile) {
            try {
                p.exportProb(XPRB.LP, "line-planning/direct_r.lp");
            } catch (Exception e) {
                logger.warn("Cannot write lp file: " + e.getMessage());
            }
        }
        p.setMsgLevel(outputSolverMessages ? 4 : 0);
        p.setSense(XPRB.MAXIM);
        p.mipOptimise();
        int[] f_sol = new int[linePool.length];
        int status = p.getMIPStat();
        if (p.getXPRSprob().getIntAttrib(XPRS.MIPSOLS) > 0) {
            if (status == XPRS.MIP_OPTIMAL) {
                logger.debug("Optimal solution found");
            } else {
                logger.debug("Feasible solution found");
            }
            for (int l = 0; l < linePool.length; l++) {
                logger.debug("Line " + (l + 1) + ": " + f[l].getSol());
                f_sol[l] = (int) Math.round(f[l].getSol());
            }
            for (int i = 0; i < n_vertices; i++)
                for (int j = 0; j < n_vertices; j++)
                    if ((d[i][j] != null) && (d[i][j].getSol() > 0.2))
                        logger.debug("of " + (i + 1) + " to " + (j + 1) + ": " + d[i][j].getSol());

            logger.debug("Optimal objective value: " + p.getObjVal());

            return f_sol;
        }
        logger.debug("No feasible solution found");
        if (p.getMIPStat() == XPRB.MIP_INFEAS) {
            logger.debug("Problem is infeasible");
            p.getXPRSprob().firstIIS(1);
            p.getXPRSprob().writeIIS(0, "line-planning/direct-r.ilp", 0);

        }
        return null;
    }

}
