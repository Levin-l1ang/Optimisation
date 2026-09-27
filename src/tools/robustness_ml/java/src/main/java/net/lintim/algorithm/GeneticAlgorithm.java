package net.lintim.algorithm;

import net.lintim.io.Debug;
import net.lintim.model.*;
import net.lintim.util.*;

import java.sql.Time;
import java.util.*;
import java.util.stream.Collectors;

public class GeneticAlgorithm {

    private static final Logger logger = new Logger(GeneticAlgorithm.class);

    private final Graph<AperiodicEvent, AperiodicActivity> aperiodicEan;
    private final Graph<Stop, Link> ptn;
    private final OD od;
    private final Graph<PeriodicEvent, PeriodicActivity> periodicEan;
    private final LinePool lines;
    private final Collection<Trip> trips;
    private final GeneticAlgorithmParameters parameters;
    private final MLModel mlModel;
    private List<Pair<GeneticAlgorithmData, Double>> currentSolutions;
    private static Random rand;

    public GeneticAlgorithm(Graph<AperiodicEvent, AperiodicActivity> aperiodicEan, Graph<Stop, Link> ptn, OD od,
                            Graph<PeriodicEvent, PeriodicActivity> periodicEan, LinePool lines, Collection<Trip> trips,
                            GeneticAlgorithmParameters parameters) {

        this.aperiodicEan = aperiodicEan;
        this.ptn = ptn;
        this.od = od;
        this.periodicEan = periodicEan;
        this.lines = lines;
        this.trips = trips;
        this.parameters = parameters;
        this.mlModel = MLModel.supplyMLModel(parameters);
        this.currentSolutions = new ArrayList<>();
    }

    private void initializeData() {
        mlModel.readModel(parameters.getModelFileName());
    }

    private void generateStartingSolutions(Collection<Timetable<AperiodicEvent>> startTimetables) {
        GeneticAlgorithmData startSolution;
        for (Timetable<AperiodicEvent> timetable: startTimetables) {
            startSolution = new GeneticAlgorithmData(ptn, periodicEan, aperiodicEan, od, lines, timetable, trips,
                parameters);
            currentSolutions.add(new Pair<>(startSolution, mlModel.predict(startSolution.generateModelInput())));
        }
        GeneticAlgorithmData newSolution;
        int startTimetableToChoose;
        if (rand == null) {
            if (parameters.getSeed() > 0) {
                rand = new Random(parameters.getSeed());
            } else {
                rand = new Random();
            }
        }
        for (int i = 0; i < parameters.getSolutionPoolSize() - startTimetables.size(); i++) {
            startTimetableToChoose = rand.nextInt(startTimetables.size());
            startSolution = currentSolutions.get(startTimetableToChoose).getFirstElement();
            newSolution = startSolution.mutate(parameters.getNumberOfRandomMutationsAtStart(), parameters.getMaxMutationAmount());
            currentSolutions.add(new Pair<>(newSolution, mlModel.predict(newSolution.generateModelInput())));
        }
    }

    private void geneticAlgorithm(Collection<Timetable<AperiodicEvent>> startTimetables) {
        generateStartingSolutions(startTimetables);
        String solutionFolderName = "";
        if (parameters.outputEverySolution()) {
            solutionFolderName = Debug.initializeSolutionFolder(parameters);
        }
        for (int iteration = 1; iteration < parameters.getMaxIteration(); iteration++) {
            logger.info("Iteration " + iteration);
            // First, check if we are in a rerouting step
            if (iteration % parameters.getRerouteInterval() == 0) {
                logger.debug("Reroute all solutions");
                currentSolutions.forEach(s -> s.getFirstElement().reroutePassengers());
                currentSolutions = currentSolutions.stream()
                    .map(Pair::getFirstElement)
                    .map(d -> new Pair<>(d,
                        mlModel.predict(d.generateModelInput())))
                    .collect(Collectors.toList());
            }
            logger.debug("Breed new solutions");
            // Now breed new solutions
            if (rand == null) {
                if (parameters.getSeed() > 0) {
                    rand = new Random(parameters.getSeed());
                } else {
                    rand = new Random();
                }
            }
            GeneticAlgorithmData parent1, parent2, child;
            for (int i = 0; i < parameters.getBreedingsPerIteration(); i++) {
                // Choose the two solutions to breed
                int maxEntry = parameters.onlyBestBreeding() ? parameters.getSolutionPoolSize() / 2 : parameters.getSolutionPoolSize();
                parent1 = currentSolutions.get(rand.nextInt(maxEntry)).getFirstElement();
                parent2 = currentSolutions.get(rand.nextInt(maxEntry)).getFirstElement();
                child = GeneticAlgorithmData.breed(parent1, parent2, parameters);
                currentSolutions.add(new Pair<>(child, mlModel.predict(child.generateModelInput())));
            }
            // Choose the best solutions for the next generation
            selectNextGeneration();
            GeneticAlgorithmData currentSolution = currentSolutions.get(0).getFirstElement();
            double currentValue = currentSolutions.get(0).getSecondElement();
            double currentTravelTime = currentSolution.getTravelTime();
            logger.debug("Found new best solutions, average value is " + currentSolutions.stream().mapToDouble(Pair::getSecondElement).average() + ", best is " + currentValue + " with travel time " + currentTravelTime);
            if (parameters.outputEverySolution()) {
                logger.debug("Choose " + System.identityHashCode(currentSolution) + " as best solution");
                int index = 1;
                for (Pair<GeneticAlgorithmData, Double> solution: currentSolutions) {
                    Debug.appendToValuesFile(solutionFolderName, new IterationValue(iteration, solution.getSecondElement(), solution.getFirstElement().getTravelTime()));
                    double[] input = solution.getFirstElement().generateModelInput();
                    double[] result = mlModel.getResult(input);
                    solution.getFirstElement().readSolutionInEan();
                    Debug.outputSolution(aperiodicEan, solution.getFirstElement().getVs(), ptn, parameters, solutionFolderName, String.format("%04d", iteration) + "_" + index, input, result);
                    index += 1;
                }
            }
        }
        logger.debug("Reached iteration limit");
        Pair<GeneticAlgorithmData, Double> bestSolution = currentSolutions.get(0);
        logger.debug("Found solution with robustness value of " + bestSolution.getSecondElement());
        bestSolution.getFirstElement().readSolutionInEan();

    }

    private void selectNextGeneration() {
        logger.debug("Choose best solutions");
        switch (parameters.getSelectionType()) {
            case QUALITY:
                currentSolutions = currentSolutions.stream()
                    .sorted(Comparator.comparingDouble(Pair::getSecondElement))
                    .limit(parameters.getSolutionPoolSize())
                    .collect(Collectors.toList());
                break;
            case PARETO:
                // Determine all pareto solutions
                ArrayList<Pair<GeneticAlgorithmData, Double>> paretoSolutions = new ArrayList<>();
                currentSolutions.sort(Comparator.comparingDouble(Pair::getSecondElement));
                logger.debug("Sorted solutions: " + currentSolutions);
                double currentTravelTime = Double.MAX_VALUE;
                for (Pair<GeneticAlgorithmData, Double> solution: currentSolutions) {
                    double solutionTravelTime = solution.getFirstElement().getTravelTime();
                    if (solutionTravelTime < currentTravelTime) {
                        currentTravelTime = solutionTravelTime;
                        paretoSolutions.add(solution);
                    }
                }
                logger.debug("Found " + paretoSolutions.size() + " pareto solutions");
                if (paretoSolutions.size() < parameters.getSolutionPoolSize()) {
                    logger.debug("Need more, want " + parameters.getSolutionPoolSize());
                    // Need to find the best non-pareto solutions w.r.t. ml value
                    currentSolutions.removeAll(paretoSolutions);
                    currentSolutions = currentSolutions.stream().limit(parameters.getSolutionPoolSize() - paretoSolutions.size()).collect(Collectors.toList());
                    logger.debug("Limit remaining solutions to " + currentSolutions.size());
                    currentSolutions.addAll(paretoSolutions);
                }
                else {
                    currentSolutions = paretoSolutions;
                }
                logger.debug("Now, we have " + currentSolutions.size() + " current solutions");



                break;
            default:
                throw new RuntimeException("Unknown selection type " + parameters.getSelectionType());
        }

    }

    public static void robustify(Graph<AperiodicEvent, AperiodicActivity> aperiodicEan, Graph<Stop, Link> ptn, OD od,
                                 Graph<PeriodicEvent, PeriodicActivity> periodicEan, LinePool lines,
                                 Collection<Timetable<AperiodicEvent>> startTimetables, Collection<Trip> trips,
                                 GeneticAlgorithmParameters parameters) {
        GeneticAlgorithm algo = new GeneticAlgorithm(aperiodicEan, ptn, od, periodicEan, lines, trips,
            parameters);
        algo.initializeData();
        algo.geneticAlgorithm(startTimetables);
    }
}
