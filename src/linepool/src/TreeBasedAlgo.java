import net.lintim.util.Logger;

public class TreeBasedAlgo {

    private static final Logger logger = new Logger(TreeBasedAlgo.class);

    public static LinePool generateLinePool(PTN ptn, OD od, ParametersTree parameters) {
        MinimalSpanningTree mst;
        LinePool pool = new LinePool(ptn);
        int[] line_concept;
        boolean feasible = false;
        boolean improvement;
        int count = 0;

        // Main Algorithm
        while (count < parameters.getMaxIterations() && !feasible) {
            count++;
            logger.debug("Iteration: "+count);

            //Reset edge-preference
            ptn.resetEdges();

            // Set preferred edges
            // Initialization: Preference by OD
            if (count == 1) {
                try {
                    for (Edge edge : od.calcSignificantEdges(parameters.getRatio())) {
                        edge.setPreferred(true);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            // Else: Preference by Line-Concept if not yet feasible
            else {
                line_concept = CostTransformer.transformAndSolve(ptn, pool, parameters);
                feasible = pool.preferProblematicEdges(line_concept);
                if (feasible) {
                    break;
                }
            }

            // Create Line-Pool
            improvement = true;
            while (improvement && ptn.hasPreferredEdge()) {
                mst = new MinimalSpanningTree(ptn);
                mst.findMSTKruskal();
                improvement = pool.poolFromMST(mst);
            }

            pool.finalizePool();
        }

        //Wrap-Up
        //Add shortest paths
        if(parameters.shouldAddShortestPaths()){
            logger.debug("\tCreate lines as shortest paths...");
            pool.addKSPLines(od, parameters.getRatioSp(), 1);
            logger.debug("\tdone!");
        }

        if (parameters.shouldAppendSingleEdges()) {
            logger.debug("\tCreate single link lines...");
            if (parameters.shouldRestrictTerminals()) {
                logger.warn("Restrict Terminals is set but should append single edges. Will override " +
                    "restrict terminals!");
                for (Stop stop: ptn.getStops()) {
                    stop.setIsTerminal(true);
                }
            }
            pool.addSingleLinkLines();
            logger.debug("\tdone!");
        }

        // Output Pool
        pool.finalizePool();


        //Check feasibility after adding shortest paths
        line_concept = CostTransformer.transformAndSolve(ptn, pool, parameters);
        feasible = pool.preferProblematicEdges(line_concept);


        if (!feasible) {
            logger.warn("\tMaximal number of iterations has been reached. "
                + "No feasible solution has been found.");
        }
        else{
            logger.debug("\tA feasible solution has been found!");
        }
        return pool;
    }

}
