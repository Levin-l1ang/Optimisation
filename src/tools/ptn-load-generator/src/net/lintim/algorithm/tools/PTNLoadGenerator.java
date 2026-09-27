package net.lintim.algorithm.tools;

import net.lintim.model.*;
import net.lintim.util.Logger;
import net.lintim.util.Pair;
import net.lintim.util.tools.LoadGenerationParameters;

import java.util.HashMap;
import java.util.Map;

/**
 */
public class PTNLoadGenerator {

    private static final Logger logger = new Logger(PTNLoadGenerator.class);
    private final Graph<Stop, Link> ptn;
    private final OD od;
    private final LoadRoutingNetwork loadRoutingNetwork;
    private final LoadGenerationParameters parameters;

    /**
     * Create a new load generator. The computation can be started afterwards using {@link #computeLoad()}.
     * @param ptn the baseline ptn
     * @param od the od matrix
     * @param linePool the linepool. If no change&go network is used, this may be null
     * @param additionalLoads the additional load, may be null if not used
     * @param parameters the parameters for the algorithm
     */
    public PTNLoadGenerator(Graph<Stop, Link> ptn, OD od, LinePool linePool,
                            Map<Integer, Map<Pair<Integer, Integer>, Double>> additionalLoads, LoadGenerationParameters parameters) {
        this.ptn = ptn;
        this.od = od;
        this.parameters = parameters;
        this.loadRoutingNetwork = new LoadRoutingNetwork(ptn, od, linePool, additionalLoads, parameters);
    }

    /**
     * Start the computation of the new loads.
     */
    public void computeLoad() {
        //Reset the load on all edges
        for (Link link : ptn.getEdges()) {
            link.setLoad(0);
        }
        switch (parameters.getLoadGeneratorType()) {
            case REDUCTION:
                computeReductionLoad();
                break;
            case SHORTEST_PATH:
                computeShortestPathLoad();
                break;
            case REWARD:
                computeShortestPathRewardLoad();
                break;
            case ITERATIVE:
                //TODO
                break;
        }
        computeLowerAndUpperBounds();
    }

    private void computeReductionLoad() {
        iterateLoadCalculation();
        long unusedLinks = ptn.getEdges().stream().filter(link -> link.getLoad() == 0).count();
        logger.debug("Reduced ptn network by " + unusedLinks + " links");
        logger.debug("Unused edges are:");
        ptn.getEdges().stream().filter(link -> link.getLoad() == 0).forEach(link -> logger.debug(link
            .toString()));
        loadRoutingNetwork.computeNewShortestPathRerouteReduction();
    }

    private void computeShortestPathLoad() {
        loadRoutingNetwork.computeNewShortestPaths();
    }

    private void computeShortestPathRewardLoad() {
        iterateLoadCalculationPerPassenger();
    }


    private void iterateLoadCalculation() {
        HashMap<Link, Double> lastLoad = getLoadInformation(ptn);
        for (int iteration = 1; iteration <= parameters.getMaxIterations(); iteration++) {
            logger.debug("Iteration " + iteration);
            loadRoutingNetwork.computeNewShortestPaths();
            HashMap<Link, Double> currentLoad = getLoadInformation(ptn);
            double loadDifference = computeLoadDifference(currentLoad, lastLoad);
            if (loadDifference < LoadGenerationParameters.getEpsilon()) {
                logger.debug("End in iteration " + iteration);
                break;
            }
            lastLoad = currentLoad;
        }
    }

    private void iterateLoadCalculationPerPassenger() {
        HashMap<Link, Double> lastLoad = getLoadInformation(ptn);
        for (int iteration = 1; iteration <= parameters.getMaxIterations(); iteration++) {
            logger.debug("Iteration " + iteration);
            for(Stop origin: ptn.getNodes()){
                for(Stop destination: ptn.getNodes()){
                    double odValue = od.getValue(origin.getId(), destination.getId());
                    for(int passenger = 1; passenger <= odValue; passenger++){
                        loadRoutingNetwork.computeNewShortestPaths(origin, destination, passenger);
                    }

                }
            }
            loadRoutingNetwork.distributeLoad();
            HashMap<Link, Double> currentLoad = getLoadInformation(ptn);
            double loadDifference = computeLoadDifference(lastLoad, currentLoad);
            if(loadDifference < LoadGenerationParameters.getEpsilon()){
                logger.debug("End in iteration " + iteration);
                break;
            }
            lastLoad = currentLoad;
        }
    }

    private void computeLowerAndUpperBounds() {
        for (Link link : ptn.getEdges()) {
            int lowerFrequency = (int) Math.ceil(parameters.getLowerFrequencyFactor() * link.getLoad() / parameters.getCapacity());
            link.setLowerFrequencyBound(lowerFrequency);
            if (parameters.useFixUpperFrequency()) {
                link.setUpperFrequencyBound(parameters.getFixUpperFrequency());
            } else {
                // When computing the upper frequency based on a factor, never set it lower than the lower frequency
                int upperFrequency = (int) Math.ceil(Math.max(link.getLowerFrequencyBound(), parameters.getUpperFrequencyFactor() * lowerFrequency));
                link.setUpperFrequencyBound(upperFrequency);
            }
        }
    }

    private static HashMap<Link, Double> getLoadInformation(Graph<Stop, Link> ptn) {
        HashMap<Link, Double> loadInformation = new HashMap<>();
        for (Link link : ptn.getEdges()) {
            loadInformation.put(link, link.getLoad());
        }
        return loadInformation;
    }

    private static double computeLoadDifference(HashMap<Link, Double> firstLoad, HashMap<Link, Double> secondLoad) {
        double sum = 0;
        for (Link link : firstLoad.keySet()) {
            sum += Math.pow(firstLoad.get(link) - secondLoad.get(link), 2);
        }
        return sum;
    }
}
