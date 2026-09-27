package net.lintim.algorithm.vehiclescheduling;

import com.dashoptimization.*;
import net.lintim.exception.AlgorithmStoppingCriterionException;
import net.lintim.model.vehiclescheduling.LineGraph;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Class implementing the integer program (VS_W^2)
 * Sub class of Model
 */

public class WasteCostProblem extends Model {
    //Variables used for Xpress
    private XPRBvar[][] x;                        //Binaries, x_{ij} in (VS_W^2)
    private XPRBctr objective;                              //objective function

    int T = super.getT();

    /**
     * empty constructor
     */
    public WasteCostProblem() {
    }

    /**
     * Initializing/modeling the IP in the sub class
     *
     * @param linegraph    linegraph
     * @param WEIGHTFACTOR variable \alpha in the IPs
     */
    @Override
    protected void modelProblem(LineGraph linegraph, double WEIGHTFACTOR) {
        //Variables describing the line graph
        int numberOfLines;
        double[][] dist;

        //Transfer data from linegraph to local variables
        numberOfLines = linegraph.numberOfLines;
        dist = new double[numberOfLines][numberOfLines];

        for (int i = 0; i < numberOfLines; i++) {
            for (int j = 0; j < numberOfLines; j++) {
                dist[i][j] = linegraph.dist[i][j];
            }
        }

        //Initialize contraints
        XPRBctr[] constraintLinesInOut = new XPRBctr[numberOfLines];
        //constraints
        XPRBctr[] constraintLinesFre = new XPRBctr[numberOfLines];

        //Initialize variables
        x = new XPRBvar[numberOfLines][numberOfLines];
        for (int i = 0; i < numberOfLines; i++) {
            for (int j = 0; j < numberOfLines; j++) {
                x[i][j] = p.newVar("x_" + (i + 1) + "_" + (j + 1), XPRB.BV); //binary
            }
        }


        //Set constrains
        for (int i = 0; i < numberOfLines; i++) {
            constraintLinesInOut[i] = p.newCtr("in_i" + (i + 1));
            constraintLinesInOut[i].setType(XPRB.E);      //Equality-constraint
            constraintLinesFre[i] = p.newCtr("out_i" + (i + 1));
            constraintLinesFre[i].setType(XPRB.E);        //Equality-constraint

            for (int j = 0; j < numberOfLines; j++) {
                constraintLinesInOut[i].addTerm(x[i][j]);
                constraintLinesInOut[i].addTerm(x[j][i], -1.0);

                constraintLinesFre[i].addTerm(x[i][j]);

            }
            constraintLinesInOut[i].setTerm(0);
            constraintLinesFre[i].setTerm(1);
        }

        //Set objective
        objective = p.newCtr("objective");
        objective.setType(XPRB.N);

        for (int i = 0; i < numberOfLines; i++) {
            for (int j = 0; j < numberOfLines; j++) {
                objective.addTerm(x[i][j], dist[i][j]);
            }
        }
    }

    @Override
    protected void solveProblem(LineGraph linegraph, double WEIGHTFACTOR, boolean writeLpFile)
        throws Exception {

        //Transfering relevant data from the line graph
        int numberOfLines = linegraph.numberOfLines;
        double totalTime = 0;             // the time needed for all lines
        int minimalVehicleNumber;     // lower bound to number of vehicles

        p.setObj(objective);

        //export problem to file

        if (writeLpFile) {
            try {
                p.exportProb(XPRB.LP, "vehicle-scheduling/VS-Line-Based.lp");
            } catch (IOException e) {
                logger.debug("Could not export vehicle scheduling problem");
                throw new RuntimeException(e);
            }
        }

        p.setSense(XPRB.MINIM);           //Set sense of the obj to minimizing
        p.mipOptimize();

        //Get status of the MIP
        int status = p.getMIPStat();

        if (p.getXPRSprob().getIntAttrib(XPRS.MIPSOLS) > 0) {
            if (status == XPRB.MIP_OPTIMAL) {
                logger.debug("Optimal solution found");
            } else {
                logger.debug("Feasible solution found");
            }
            logger.debug("Objective value: " + p.getObjVal());
            for (int i = 0; i < numberOfLines; i++) {
                for (int j = 0; j < numberOfLines; j++) {
                    if (x[i][j].getSol() != 0) {
                        logger.debug(x[i][j].getName() + ": " +
                            x[i][j].getSol());
                    }
                }
            }
        } else {
            logger.error("No feasible solution found");
            if (status == XPRB.MIP_INFEAS) {
                logger.debug("Problem is infeasible");
                p.getXPRSprob().firstIIS(1);
                p.getXPRSprob().writeIIS(0, "vehicle-scheduling/VS-Line-Based.ilp", 0);
            }
            throw new AlgorithmStoppingCriterionException("VS Line Based");
        }

        int numberOfVehicles = calculateNumberOfVehicles(linegraph);

        // calculate the time needed to conduct all lines (gives the lower bound
        // if each line has transfer cost of 0 to at least one other line)
        for (int i = 0; i < numberOfLines; i++) {
            totalTime = totalTime + linegraph.linetime[i];
        }
        // determine the minimal number of vehicles to conduct all lines
        minimalVehicleNumber = (int) Math.ceil(totalTime / T);

        // calculate bounds
        double upperBound = numberOfVehicles * WEIGHTFACTOR + p.getObjVal();
        double lowerBound = minimalVehicleNumber * WEIGHTFACTOR + p.getObjVal();

        // output
        logger.debug(numberOfVehicles + "vehicles are needed");

        logger.debug("Objective Value with NumberOfVehicles included = " +
            upperBound);
        logger.debug("at least " + minimalVehicleNumber +
            " vehicles are needed to operate line graph");

        logger.debug("Objective Value with minimal VehicleNumber " +
            "included = " + lowerBound);

        logger.debug("Absolut gap: " + (upperBound - lowerBound));
    }

    /**
     * Calculates the number of vehicles needed to operate the vehicle schedule
     * <p>
     * This provides an upper bound
     *
     * @param linegraph the line graph
     * @return number of vehicles needed to operate the vehicle schedule
     * obtained by solving the IP
     */
    private int calculateNumberOfVehicles(LineGraph linegraph) throws Exception {
        int totalNumberOfVehicles = 0;
        int numberOfLines = linegraph.numberOfLines;
        int[] numberOfVehiclesInRoute;
        double[] timeInRoute;
        int[][] sol;
        int[] solI, solJ;       //initialize mit -1!!
        int routeCounter = 0;
        int lineInRouteCounter = 0;
        int counter = 0;

        logger.info("Begin writing output data");
        FileWriter fw = new FileWriter("VehicleSchedule_WasteCost.txt");
        BufferedWriter bw = new BufferedWriter(fw);

        numberOfVehiclesInRoute = new int[numberOfLines];
        timeInRoute = new double[numberOfLines];
        sol = new int[numberOfLines][numberOfLines];
        solI = new int[numberOfLines];
        solJ = new int[numberOfLines];

        for (int i = 0; i < numberOfLines; i++) {
            for (int j = 0; j < numberOfLines; j++) {
                long solution = Math.round(x[i][j].getSol());
                sol[i][j] = (int) solution;
                if (sol[i][j] == 1) {
                    solI[counter] = i;
                    solJ[counter] = j;
                    counter++;
                }
            }
        }

        bw.write("#Route, line no; line");
        bw.newLine();
        timeInRoute[routeCounter] = 0;
        int firstLineOfRoute;


        for (int iter = 0; iter < counter; iter++) {
            if (solI[iter] != -1) {
                int m = iter;
                firstLineOfRoute = solI[m];
                lineInRouteCounter++;
                bw.write((routeCounter + 1) + "; " + lineInRouteCounter +
                    "; " + linegraph.lineIDs[solI[m]]);
                bw.newLine();
                timeInRoute[routeCounter] = timeInRoute[routeCounter] +
                    linegraph.time[solI[m]][solJ[m]];
                solI[m] = -1;
                boolean doneWithRoute = false;

                while (!doneWithRoute) {

                    for (int n = 0; n < counter; n++) {
                        if (solI[n] == solJ[m]) {
                            if (solJ[n] == firstLineOfRoute) {
                                m = n;
                                lineInRouteCounter++;
                                bw.write((routeCounter + 1) + "; " +
                                    lineInRouteCounter + "; " +
                                    linegraph.lineIDs[solI[m]]);
                                bw.newLine();
                                timeInRoute[routeCounter] =
                                    timeInRoute[routeCounter] +
                                        linegraph.linetime[solI[m]] +
                                        linegraph.time[solI[m]][solJ[m]];
                                solI[m] = -1;

                                doneWithRoute = true;
                                routeCounter++;
                                timeInRoute[routeCounter] = 0;
                                lineInRouteCounter = 0;
                                firstLineOfRoute = -1;
                                break;
                            } else {
                                m = n;
                                lineInRouteCounter++;
                                bw.write((routeCounter + 1) + "; " +
                                    lineInRouteCounter + "; " +
                                    linegraph.lineIDs[solI[m]]);
                                bw.newLine();
                                timeInRoute[routeCounter] =
                                    timeInRoute[routeCounter] +
                                        linegraph.linetime[solI[m]] +
                                        linegraph.time[solI[m]][solJ[m]];
                                solI[m] = -1;


                                break;
                            }
                        }
                    }
                }
            }
        }

        for (int k = 0; k < numberOfLines; k++) {
            if (timeInRoute[k] != 0) {
                numberOfVehiclesInRoute[k] = (int) Math.ceil(timeInRoute[k] / T);
                totalNumberOfVehicles = totalNumberOfVehicles +
                    numberOfVehiclesInRoute[k];
                bw.write("#Route " + k + " needs " + numberOfVehiclesInRoute[k] +
                    " vehicles and its time is " + timeInRoute[k] + ".");
                bw.newLine();
            }
        }

        bw.write("#In total, " + totalNumberOfVehicles + " vehicles are needed.");

        bw.close();

        logger.info("Finished writing output data");

        return totalNumberOfVehicles;
    }


}
