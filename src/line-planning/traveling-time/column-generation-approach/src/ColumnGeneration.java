import java.util.*;

import net.lintim.util.Logger;
import com.dashoptimization.*;

import java.io.*;


/**
 * Column generation algorithm
 * sets up column generation, solves LP relaxation and approximates an integer solution
 * functions for output files
 */
public class ColumnGeneration {

    private static final Logger logger = new Logger(ColumnGeneration.class);

    protected long totalStartTime;
    protected String history;
    protected Parameters parameters;
    protected PTN ptn;
    protected Pool pool;
    protected CAG cag;
    protected ArrayList<ArrayList<Path>> paths;
    protected Pricing pricing;
    protected RLPM rlpm;
    protected int iterations;
    protected String destination;

    //-------------------Constructor------------------------------------------------------

    /**
     * initializes the column generation algorithm
     * computation of initial solution
     */
    public ColumnGeneration(Parameters parameters) {

        totalStartTime = System.currentTimeMillis();
        this.parameters = parameters;

        history = "";

        //initialization of PTN
        ptn = new PTN(parameters.getStopFileName(), parameters.getEdgeFileName(), parameters.getOdFileName());

        //initialization of line pool
        pool = new Pool(ptn, parameters.getPoolFileName(), parameters.getPoolCostFileName());

        //initialization of corresponding change and go graph
        cag = new CAG(ptn, pool, parameters);

        //computation of initial solution via covering problem and shortest path

        long startTime = System.currentTimeMillis();
        InitialSolution cover = new InitialSolution(pool, cag, parameters);
        paths = cover.makePaths();
        logger.debug(
            "\nThe computation of the initial solution via covering and shortest path took: "
                + (System.currentTimeMillis() - startTime) + "\n");
        history = history + "time initial solution" +
            (System.currentTimeMillis() - startTime) + "\n";
        destination = "line-planning/";

    }

//-----------------------Getter/Setter-------------------------------------------

    public RLPM getRLPM() {
        return this.rlpm;
    }

//------------------------solveRelaxation-----------------------------------------

    public void solveRelaxation() {
        //count number of iterations of the column generation algorithm
        iterations = 1;
        double startTime;
        try {
            rlpm = new RLPM(paths, pool, cag, parameters);
            logger.debug("\nSTEP " + iterations + " : ");
            history = history + "\nSTEP" + iterations + " : \n";
            startTime = System.currentTimeMillis();
            rlpm.solve();
            logger.debug("objective value RLPM: " + rlpm.getRLPM().getObjVal());
            history = history + "time rlpm: " + (System.currentTimeMillis() - startTime) + "\n";
            history = history + "objective value: " + rlpm.getRLPM().getObjVal() + "\n";
            logger.debug("\nRLPM time: " + (System.currentTimeMillis() - startTime) + "\n");
            pricing = new Pricing(rlpm);
            startTime = System.currentTimeMillis();
            iterations++;

            //as long as optimality condition (pricing) is not satisfied, paths are added
            //and RLPM is solved again
            while (iterations <= parameters.getMaxIter() && !pricing.isOptimal()) { //Optimality check
                history = history + "time pricing: " + (System.currentTimeMillis() - startTime)
                    + "\n";
                if (rlpm.getRLPM().getObjVal() / pricing.getDualBound() < 1 +
                    parameters.getTerminationValue()) {
                    logger.debug("Termination due to dual bound");
                    break;
                }
                logger.debug("\nPricing time: " + (System.currentTimeMillis() - startTime)
                    + "\n");
                history = history + "\nSTEP" + iterations + " : \n";
                logger.debug("\nSTEP " + iterations + " : ");
                startTime = System.currentTimeMillis();

                //Step 1: solve RLPM
                rlpm.solve();
                logger.debug("objective value RLPM: " + rlpm.getRLPM().getObjVal());
                history = history + "time rlpm: " + (System.currentTimeMillis() - startTime) + "\n";
                history = history + "objective value: " + rlpm.getRLPM().getObjVal() + "\n";
                logger.debug("\nRLPM time: " + (System.currentTimeMillis() - startTime) + "\n");
                startTime = System.currentTimeMillis();
                iterations++;
            }
            logger.debug("\nPricing time: " + (System.currentTimeMillis() - startTime) + "\n");
            history = history + "time pricing: " + (System.currentTimeMillis() - startTime) + "\n";
            history = history + "dual bound: " + pricing.getDualBound() + "\n";
            cag.update();

        } catch (InterruptedException e) {
            logger.error("Error: " + e.getMessage());
            System.exit(1);
        }
        history = rlpm + history;
    }


//-----------------solveIP------------------------------------------------------------

    public void solveIP() {
        try {
            if (parameters.solveIp()) {
                double startTime = System.currentTimeMillis();
                rlpm.changeToBooleanVariables();
                rlpm.solveIP();
                history = history + "\ntime IP: " + (System.currentTimeMillis() - startTime) + "\n";
                logger.debug("Solving the Integer Program took: " +
                    (System.currentTimeMillis() - startTime) + "\n");

                //computed shortest paths in the original CAG
                //history = history +"Best routing: " + EvalLineConcept.makeEval(rlpm,pool,ptn);

                //write line concept
                writeLineConcept(pool.getLines(), rlpm.getLineVar(), parameters.getLineConceptFileName());
            }
        } catch (InterruptedException e) {
            logger.error("Error: " + e.getMessage());
            System.exit(1);
        }
    }

//------------------writePaths()--------------------------------------------------------

    /**
     * writes the paths used in the optimization process in a file
     */
    public void writePaths(ArrayList<ArrayList<Path>> paths, String destination, String name) {
        String s = "# initial solution for column generation algorithm \n# weight; node id, line id ; ..";
        ArrayList<Node> nodes;
        for (int i = 0; i < paths.size(); i++) {
            s = s + "\nOD pair " + i;
            for (int j = 0; j < paths.get(i).size(); j++) {
                s = s + "\n" + paths.get(i).get(j).getWeight();
                nodes = paths.get(i).get(j).getNodes();
                for (int k = 0; k < nodes.size(); k++) {
                    s = s + " ; " + nodes.get(k);
                }
            }
        }

        toFile(s, destination + name);
    }


//-----------------toFile---------------------------------------------------------

    /**
     * writes string s in file name
     */
    public void toFile(String s, String name) {
        File f = new File(name);
        try {
            FileWriter writer = new FileWriter(f);
            writer.write(s);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//---------------writeLineConcept-------------------------------------------------

    /**
     * writes line concept in lintim shape
     */
    public void writeLineConcept(ArrayList<Line> lines, ArrayList<XPRBvar> lineVariables,
                                 String filename) {
        ArrayList<Edge> edges;
        String s = "# Line concept computed with column generation algorithm\n";
        s += "# line-id;edge-position;edge-id;frequency\n";
        for (int i = 0; i < lines.size(); i++) {
            edges = lines.get(i).getEdges();
            for (int j = 0; j < edges.size(); j++) {
                s = s + lines.get(i).getId() + ";" + (j + 1) + ";"
                    + edges.get(j).getId() + ";" + (int) lineVariables.get(i).getSol() + "\n";
            }
        }
        toFile(s, filename);
    }


//------------writeHistory------------------------------------------------------

    /**
     * writes the solution file including details of the algorithm
     */

    public void writeHistory() {

        String s = "Solution of column generation algorithm\nNumber of iterations: " +
            iterations;
        s = s + "\nOd costs:" + parameters.getWeightOdEdge();
        s = s + "\nBudget:" + parameters.getBudget();
        s = s + "\nTotal time : " + (System.currentTimeMillis() - totalStartTime) + "\n";


        s = s + "\n" + history;
        toFile(s, "line-planning/Sol_" + parameters.getConstraintType() + ".giv");

    }

}
