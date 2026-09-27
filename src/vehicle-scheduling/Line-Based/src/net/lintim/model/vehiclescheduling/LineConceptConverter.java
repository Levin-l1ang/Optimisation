package net.lintim.model.vehiclescheduling;

import net.lintim.util.Logger;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Class Constructing a Line Plan Out of a Line Concept
 */
public class LineConceptConverter {

    private static final Logger logger = new Logger(LineConceptConverter.class);

    //global Variables
    private final int CONVERTLENGTHTOTIME = 1;
    //linetime=CONVERTLENGTHTOTIME*length
    private int numberOfLines, numberOfLinesF;
    private int numberOfStops;
    private int[] start, startF;
    private int[] end, endF;
    private double[] linetime, linetimeF;
    private String[] lineIDs, lineIDsF;
    private double[][] dist, distF;
    private double[][] time, timeF;
    private int[] frequency, frequencyF;
    private double[] linetimeCompletePool;
    private int[] startingPoint, endingPoint;
    private double[][] weightMatrix, distanceInPtn;


    /**
     * Reads a line concept to generate a line graph
     * to be regarded
     *
     * @return linegraph
     */
    public LineGraph convertLineConceptToLineGraph(Boolean regardFrequencies) throws Exception {

        getInformationForShortestPaths();

        getPtnInformation();
        getLineInformation();
        readLineConcept();

        shortestDistance();
        //Read out length of shortest paths for all starting and ending points
        for (int i = 0; i < numberOfLines; i++) {
            for (int j = 0; j < numberOfLines; j++)
                dist[i][j] = distanceInPtn[end[i] - 1][start[j] - 1];
        }

        setTime();

        if (regardFrequencies) {
            buildExtendedLineGraph();
        }


        if (regardFrequencies) {
            logger.debug("NumberOfLines: " + numberOfLinesF);
            return new LineGraph(startF, endF, linetimeF,
                frequencyF, numberOfLinesF, distF, timeF, lineIDsF);
        } else {
            logger.debug("NumberOfLines: " + numberOfLinesF);
            return new LineGraph(start, end, linetime, frequency,
                numberOfLines, dist, time, lineIDs);
        }
    }

    /**
     * Initialize weightMatrix and distanceInPtn for method shortestDistance
     */
    private void getInformationForShortestPaths() throws Exception {
        numberOfStops = getnumberOfStopsInPtn();
        weightMatrix = new double[numberOfStops][numberOfStops];
        distanceInPtn = new double[numberOfStops][numberOfStops];

        for (int i = 0; i < numberOfStops; i++) {
            for (int j = 0; j < numberOfStops; j++) {
                weightMatrix[i][j] = Double.MAX_VALUE;
            }
        }
    }

//*****************************************************************************/

    /**
     * Calculates shortest distances between all nodes in ptn using the weight
     * matrix and the Algorithm of Floyd
     */
    private void shortestDistance() {
        /* Algorithm of Floyd:
            (1) For all i,j  : d[i,j] = w[i,j]
            (2) For k = 1 ... n
            (3)   For all pairs i,j
            (4)     d[i,j] = min (d[i,j],d[i,k] + d[k,j])*/

        for (int i = 0; i < numberOfStops; i++) {
            for (int j = 0; j < numberOfStops; j++) {
                distanceInPtn[i][j] = weightMatrix[i][j];
            }
        }
        for (int k = 0; k < numberOfStops; k++) {
            for (int i = 0; i < numberOfStops; i++) {
                for (int j = 0; j < numberOfStops; j++) {
                    if (distanceInPtn[i][j] > distanceInPtn[i][k] +
                        distanceInPtn[k][j]) {
                        distanceInPtn[i][j] =
                            distanceInPtn[i][k] + distanceInPtn[k][j];
                    }
                }
                distanceInPtn[i][i] = 0;
            }
        }
    }

//*****************************************************************************/

    /**
     * Read out size of PTN
     *
     * @return number of stops in the PTN of the dataset
     */
    private int getnumberOfStopsInPtn() throws Exception {
        int numberOfStopsInPtn = 0;

        BufferedReader scanner = new BufferedReader(new FileReader("basis/Stop.giv"));

        String line;
        while ((line = scanner.readLine()) != null) {
            int position = line.indexOf('#');
            if (position != -1)
                line = line.substring(0, position);
            line = line.trim();
            if (line.isEmpty())
                continue;
            numberOfStopsInPtn = numberOfStopsInPtn + 1;
        }
        scanner.close();

        return numberOfStopsInPtn;
    }

//*****************************************************************************/

    /**
     * Read out start and ending point of Edges and other information from PTN
     */
    private void getPtnInformation() throws Exception {


        BufferedReader scanner = new BufferedReader(new FileReader("basis/Edge.giv"));

        int numberOfEdgesInPtn = 0;

        //Read first time to find out how many edges are contained in pool
        String line;
        while ((line = scanner.readLine()) != null) {
            int position = line.indexOf('#');
            if (position != -1)
                line = line.substring(0, position);
            line = line.trim();
            if (line.isEmpty())
                continue;
            numberOfEdgesInPtn = numberOfEdgesInPtn + 1;
        }
        scanner.close();

        //Initialize variables in according length
        startingPoint = new int[numberOfEdgesInPtn];
        endingPoint = new int[numberOfEdgesInPtn];
        double[] edgeLength = new double[numberOfEdgesInPtn];

        BufferedReader reader = new BufferedReader(new FileReader("basis/Edge.giv"));
        int counter = 0;

        //Read second time to get information on lines in pool
        while ((line = reader.readLine()) != null) {
            int position = line.indexOf('#');
            if (position != -1)
                line = line.substring(0, position);
            line = line.trim();
            if (line.isEmpty())
                continue;

            String[] tokens = line.split(";");

            if ((counter + 1) != Integer.parseInt(tokens[0].trim())) {
                logger.warn("Data is not in expected format");
            } else {
                startingPoint[counter] = Integer.parseInt(tokens[1].trim());
                endingPoint[counter] = Integer.parseInt(tokens[2].trim());
                edgeLength[counter] = Double.parseDouble(tokens[3].trim());

                weightMatrix[startingPoint[counter] - 1][endingPoint[counter] - 1] =
                    edgeLength[counter];
                weightMatrix[endingPoint[counter] - 1][startingPoint[counter] - 1] =
                    edgeLength[counter];

                counter = counter + 1;
            }
        }
        reader.close();
    }

//*****************************************************************************/

    /**
     * Get information on all lines in the line pool
     */
    private void getLineInformation() throws Exception {


        BufferedReader scanner = new BufferedReader(new FileReader("basis/Pool-Cost.giv"));
        int numberOfLinesInPool = 0;

        //Read first time to find out how many lines are contained in pool
        String line;
        while ((line = scanner.readLine()) != null) {
            int position = line.indexOf('#');
            if (position != -1)
                line = line.substring(0, position);
            line = line.trim();
            if (line.isEmpty())
                continue;
            numberOfLinesInPool = numberOfLinesInPool + 1;
        }
        scanner.close();

        //Initialize variables in according length
        linetimeCompletePool = new double[numberOfLinesInPool];
        int counter = 0;

        //Read second time to get information on lines in pool
        BufferedReader reader = new BufferedReader(new FileReader("basis/Pool-Cost.giv"));
        while ((line = reader.readLine()) != null) {
            int position = line.indexOf('#');
            if (position != -1)
                line = line.substring(0, position);
            line = line.trim();
            if (line.isEmpty())
                continue;

            String[] tokens = line.split(";");

            if ((counter + 1) != Integer.parseInt(tokens[0].trim())) {
                logger.warn("Data is not in expected format");
            } else {
                linetimeCompletePool[counter] =
                    Double.parseDouble(tokens[1].trim()) * CONVERTLENGTHTOTIME;
                counter = counter + 1;
            }
        }
        reader.close();
    }

//*****************************************************************************/

    /**
     * Get information on the line concept
     */
    private void readLineConcept() throws Exception {

        BufferedReader scanner = new BufferedReader(new FileReader("line-planning/Line-Concept.lin"));

        int numberOfLinesUsed = 0;
        int currentLineID = 0;
        int lastLineID;
        int currentFrequency;
        int lastFrequency;
        int currentEdgeID = 0;
        int lastEdgeID = 0;
        int lastlastEdgeID;

        //Read first time to find out how many lines are used
        String line;
        while ((line = scanner.readLine()) != null) {
            int position = line.indexOf('#');
            if (position != -1)
                line = line.substring(0, position);
            line = line.trim();
            if (line.isEmpty())
                continue;

            String[] tokens = line.split(";");
            lastLineID = currentLineID;
            currentLineID = Integer.parseInt(tokens[0].trim());
            currentEdgeID = Integer.parseInt(tokens[2].trim());
            currentFrequency = Integer.parseInt(tokens[3].trim());
            if ((lastLineID != currentLineID) && (currentFrequency != 0)) {
                numberOfLinesUsed = numberOfLinesUsed + 1;

            }
        }
        scanner.close();

        //Initialize line concept variables in according length
        numberOfLines = 2 * numberOfLinesUsed;     //each line in both directions
        start = new int[numberOfLines];
        end = new int[numberOfLines];
        lineIDs = new String[numberOfLines];
        linetime = new double[numberOfLines];
        frequency = new int[numberOfLines];
        dist = new double[numberOfLines][numberOfLines];
        time = new double[numberOfLines][numberOfLines];


        //Reset frequencies and lineIDs already read
        currentLineID = 0;
        currentFrequency = 0;
        int linesUsedCounter = 0;
        int edgeInLine = 0;
        int lastEdgeInLine;

        //Read second time to get information on lines used
        BufferedReader reader = new BufferedReader(new FileReader("line-planning/Line-Concept.lin"));
        while ((line = reader.readLine()) != null) {
            int position = line.indexOf('#');
            if (position != -1)
                line = line.substring(0, position);
            line = line.trim();
            if (line.isEmpty())
                continue;


            String[] tokens = line.split(";");
            lastLineID = currentLineID;
            lastFrequency = currentFrequency;
            lastlastEdgeID = lastEdgeID;
            lastEdgeID = currentEdgeID;
            lastEdgeInLine = edgeInLine;
            currentLineID = Integer.parseInt(tokens[0].trim());
            edgeInLine = Integer.parseInt(tokens[1].trim());
            currentEdgeID = Integer.parseInt(tokens[2].trim());
            currentFrequency = Integer.parseInt(tokens[3].trim());

            if (currentFrequency != 0) {
                if (edgeInLine == 1) {
                    linesUsedCounter++;
                    frequency[2 * (linesUsedCounter - 1)] = currentFrequency;
                    lineIDs[2 * (linesUsedCounter - 1)] = currentLineID + "forth";
                    linetime[2 * (linesUsedCounter - 1)] =
                        linetimeCompletePool[currentLineID - 1];
                    //lines in both directions, first one goes as indicated
                    //                                          in line concept
                    frequency[2 * (linesUsedCounter - 1) + 1] = currentFrequency;
                    lineIDs[2 * (linesUsedCounter - 1) + 1] = currentLineID + "back";
                    linetime[2 * (linesUsedCounter - 1) + 1] =
                        linetimeCompletePool[currentLineID - 1];
                }
                if (edgeInLine == 2) {
                    if ((startingPoint[lastEdgeID - 1] ==
                        startingPoint[currentEdgeID - 1]) ||
                        (startingPoint[lastEdgeID - 1] ==
                            endingPoint[currentEdgeID - 1])) {
                        start[2 * (linesUsedCounter - 1)] =
                            endingPoint[lastEdgeID - 1];
                        end[2 * (linesUsedCounter - 1) + 1] =
                            endingPoint[lastEdgeID - 1];
                    } else {
                        start[2 * (linesUsedCounter - 1)] =
                            startingPoint[lastEdgeID - 1];
                        end[2 * (linesUsedCounter - 1) + 1] =
                            startingPoint[lastEdgeID - 1];
                    }
                }
            }
            if ((currentLineID != lastLineID) && (lastFrequency != 0)) {
                int currentLineIsCounted = 0;
                if (currentFrequency != 0) {
                    currentLineIsCounted = 1;
                }
                if (lastEdgeInLine == 1) {
                    start[2 * (linesUsedCounter - 1 - currentLineIsCounted)] =
                        startingPoint[lastEdgeID - 1];
                    end[2 * (linesUsedCounter - 1 - currentLineIsCounted)] =
                        endingPoint[lastEdgeID - 1];
                    start[2 * (linesUsedCounter - 1 - currentLineIsCounted) + 1] =
                        endingPoint[lastEdgeID - 1];
                    end[2 * (linesUsedCounter - 1 - currentLineIsCounted) + 1] =
                        startingPoint[lastEdgeID - 1];
                } else if (lastEdgeInLine > 1) {
                    if (startingPoint[lastEdgeID - 1] ==
                        startingPoint[lastlastEdgeID - 1] ||
                        startingPoint[lastEdgeID - 1] ==
                            endingPoint[lastlastEdgeID - 1]) {
                        end[2 * (linesUsedCounter - 1 - currentLineIsCounted)] =
                            endingPoint[lastEdgeID - 1];
                        start[2 * (linesUsedCounter - 1 - currentLineIsCounted) + 1] =
                            endingPoint[lastEdgeID - 1];
                    } else {
                        end[2 * (linesUsedCounter - 1 - currentLineIsCounted)] =
                            startingPoint[lastEdgeID - 1];
                        start[2 * (linesUsedCounter - 1 - currentLineIsCounted) + 1] =
                            startingPoint[lastEdgeID - 1];
                    }
                }
            }
        }
        reader.close();

        if (currentFrequency != 0) {
            if (edgeInLine == 1) {
                start[2 * (linesUsedCounter - 1)] =
                    startingPoint[currentEdgeID - 1];
                end[2 * (linesUsedCounter - 1)] = endingPoint[currentEdgeID - 1];
                start[2 * (linesUsedCounter - 1) + 1] =
                    endingPoint[currentEdgeID - 1];
                end[2 * (linesUsedCounter - 1) + 1] =
                    startingPoint[currentEdgeID - 1];
            } else {
                if (startingPoint[currentEdgeID - 1] ==
                    startingPoint[lastEdgeID - 1] ||
                    startingPoint[currentEdgeID - 1] ==
                        endingPoint[lastEdgeID - 1]) {
                    end[2 * (linesUsedCounter - 1)] = endingPoint[currentEdgeID - 1];
                    start[2 * (linesUsedCounter - 1) + 1] =
                        endingPoint[currentEdgeID - 1];
                } else {
                    end[2 * (linesUsedCounter - 1)] =
                        startingPoint[currentEdgeID - 1];
                    start[2 * (linesUsedCounter - 1) + 1] =
                        startingPoint[currentEdgeID - 1];
                }
            }
        }
    }

//*****************************************************************************/

    /**
     * Determine the t_{ij}
     */
    private void setTime() {
        for (int i = 0; i < numberOfLines; i++) {
            for (int j = 0; j < numberOfLines; j++) {
                time[i][j] = dist[i][j] * CONVERTLENGTHTOTIME + linetime[i];
            }
        }
    }
//*****************************************************************************/

    /**
     * Calculate extended line Graph
     */
    private void buildExtendedLineGraph() {
        numberOfLinesF = 0;

        //Calculate number of lines in extended line graph,
        //                                      i.e. counted with frequency
        for (int i = 0; i < numberOfLines; i++) {
            numberOfLinesF = numberOfLinesF + frequency[i];
        }

        //Initialize line concept variables for extended line graph
        startF = new int[numberOfLinesF];
        endF = new int[numberOfLinesF];
        lineIDsF = new String[numberOfLinesF];
        linetimeF = new double[numberOfLinesF];
        frequencyF = new int[numberOfLinesF];
        distF = new double[numberOfLinesF][numberOfLinesF];
        timeF = new double[numberOfLinesF][numberOfLinesF];

        int icounter = 0;

        for (int i = 0; i < numberOfLines; i++) {
            for (int fi = 0; fi < frequency[i]; fi++) {
                startF[icounter] = start[i];
                endF[icounter] = end[i];
                lineIDsF[icounter] = lineIDs[i];
                linetimeF[icounter] = linetime[i];
                frequencyF[icounter] = 1;

                int jcounter = 0;
                for (int j = 0; j < numberOfLines; j++) {
                    for (int fj = 0; fj < frequency[j]; fj++) {
                        distF[icounter][jcounter] = dist[i][j];
                        timeF[icounter][jcounter] = time[i][j];
                        jcounter = jcounter + 1;
                    }
                }
                icounter = icounter + 1;
            }
        }
    }
}
