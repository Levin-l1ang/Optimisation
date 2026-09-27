package net.lintim.algorithm.vehiclescheduling;

import com.dashoptimization.*;
import net.lintim.model.vehiclescheduling.LineGraph;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Class implementing the integer program (LPT^3)
 * Sub class of Model
 */

public class LPTotalCostProblemAlt extends Model {
    //Variables
    private XPRBvar[][][] x;           // x_{ijk} in (LP^3)
    private XPRBvar[] y, z;             //y_k and v_k in (LP^3)
    private XPRBctr objective;         //objective function

    int T = super.getT();
    double TOL = 0.001;                 //Tolerance value for output of solution
    //MAXEDGESINROUTE can be set to a value if the range of l is to be reduced


    /**
     * empty constructor
     */
    public LPTotalCostProblemAlt() {
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
        int numberOfLines, numberOfEdges;
        double[][] dist, time;

        //Transfer data from linegraph to local variables
        numberOfLines = linegraph.numberOfLines;
        int numberOfRoutes = linegraph.numberOfLines;
        dist = new double[numberOfLines][numberOfLines];
        time = new double[numberOfLines][numberOfLines];

        for (int i = 0; i < numberOfLines; i++) {
            for (int j = 0; j < numberOfLines; j++) {
                dist[i][j] = linegraph.dist[i][j];
                time[i][j] = linegraph.time[i][j];
            }
        }
        numberOfEdges = numberOfLines * numberOfLines;
        int bigM = numberOfEdges * numberOfEdges;

        //Calculate PowerSet of {1,...,numberOfLines} without the empty set
        int[] lineSet = new int[numberOfLines];
        for (int i = 0; i < numberOfLines; i++) {
            lineSet[i] = i;
        }
        ArrayList<ArrayList<Integer>> powerSet;
        powerSet = calcPowerSet(lineSet);

        //number of sets contained in powerSet
        int powersetSize = powerSet.size();


        //Initialize contraints
        XPRBctr[] ctrEdgesIn = new XPRBctr[numberOfLines];
        XPRBctr[][] ctrFlowInRoute = new XPRBctr[numberOfLines][numberOfRoutes];
        XPRBctr[][] ctrSetW = new XPRBctr[numberOfRoutes][powersetSize];
        XPRBctr[] ctrSetZ = new XPRBctr[numberOfRoutes];
        XPRBctr[][] ctrAdjacent = new XPRBctr[numberOfRoutes][powersetSize];
        //constraints
        XPRBctr[] ctrCeilY = new XPRBctr[numberOfRoutes];

        //Initialize variables
        x = new XPRBvar[numberOfLines][numberOfLines][numberOfRoutes];
        y = new XPRBvar[numberOfRoutes];
        z = new XPRBvar[numberOfRoutes];
        //w_{kS} in (LP^3)
        XPRBvar[][] w = new XPRBvar[numberOfRoutes][powersetSize];


        for (int k = 0; k < numberOfRoutes; k++) {
            y[k] = p.newVar("y_" + (k + 1), XPRB.PL, 0.0, Double.MAX_VALUE);//continuous
            z[k] = p.newVar("z_" + (k + 1), XPRB.PL, 0.0, Double.MAX_VALUE);//continuous
            for (int i = 0; i < numberOfLines; i++) {
                for (int j = 0; j < numberOfLines; j++)
                    x[i][j][k] = p.newVar("x_" + (i + 1) + "_" + (j + 1) + "_" +
                        (k + 1), XPRB.PL, 0.0, 1.0);   //continuous

            }

            for (int S = 0; S < powersetSize; S++) {
                w[k][S] = p.newVar("w_" + (k + 1) + "_" + S, XPRB.PL, 0, 1); //continuous
            }
        }

        //Set EdgesIn/Out constraints
        for (int i = 0; i < numberOfLines; i++) {
            ctrEdgesIn[i] = p.newCtr("EdgesIn_" + (i + 1));
            ctrEdgesIn[i].setType(XPRB.E);                //Equality-constraint
            for (int k = 0; k < numberOfRoutes; k++) {
                ctrFlowInRoute[i][k] = p.newCtr("FlowInRoute_" + (i + 1) + "_" + (k + 1));
                ctrFlowInRoute[i][k].setType(XPRB.E);     //Equality-constraint
                for (int j = 0; j < numberOfLines; j++) {
                    ctrEdgesIn[i].addTerm(x[i][j][k]);
                    ctrFlowInRoute[i][k].addTerm(x[i][j][k]);
                    ctrFlowInRoute[i][k].addTerm(x[j][i][k], (-1.0));
                }
                ctrFlowInRoute[i][k].setTerm(0.0);
            }
            ctrEdgesIn[i].setTerm(1.0);
        }

        //Set CeilY and SetZ constraint
        for (int k = 0; k < numberOfRoutes; k++) {
            ctrCeilY[k] = p.newCtr("CeilY" + (k + 1));
            ctrCeilY[k].setType(XPRB.L);                  //<=-constraint
            ctrCeilY[k].addTerm(y[k], (-1) * T);

            ctrSetZ[k] = p.newCtr("SetZ" + (k + 1));
            ctrSetZ[k].setType(XPRB.E);                   //Equality-constraint
            ctrSetZ[k].addTerm(z[k], (-1));
            for (int i = 0; i < numberOfLines; i++) {
                for (int j = 0; j < numberOfLines; j++) {
                    ctrCeilY[k].addTerm(x[i][j][k], time[i][j]);
                    ctrSetZ[k].addTerm(x[i][j][k]);
                }
            }
            ctrCeilY[k].setTerm(0.0);
        }

        //Set SetW and Adjacent constraint
        for (int k = 0; k < numberOfRoutes; k++) {
            int counter = 0;
            for (ArrayList<Integer> a : powerSet) {
                ctrAdjacent[k][counter] = p.newCtr("Adjacent_" + k + "_" + counter);
                ctrAdjacent[k][counter].setType(XPRB.L);      //<=-constraint
                ctrSetW[k][counter] = p.newCtr("SetW_" + k + "_" + counter);
                ctrSetW[k][counter].setType(XPRB.L);          //<=-constraint
                for (int i : a) {
                    for (int j : a) {
                        ctrAdjacent[k][counter].addTerm(x[i][j][k]);
                    }
                }
                ctrAdjacent[k][counter].addTerm(w[k][counter], bigM);
                ctrAdjacent[k][counter].setTerm((a.size() + bigM - 1));

                ctrSetW[k][counter].addTerm(z[k],
                    ((double) 1 / (double) numberOfLines));
                ctrSetW[k][counter].addTerm(w[k][counter], (-1));
                ctrSetW[k][counter].setTerm(
                    ((double) a.size() / (double) numberOfLines));
                counter++;
            }

        }

        //Set objective
        objective = p.newCtr("objective");
        objective.setType(XPRB.N);

        for (int k = 0; k < numberOfRoutes; k++) {
            objective.addTerm(y[k], WEIGHTFACTOR);
            for (int i = 0; i < numberOfLines; i++) {
                for (int j = 0; j < numberOfLines; j++) {
                    objective.addTerm(x[i][j][k], dist[i][j]);
                }
            }
        }
        logger.debug("Problem modeled.");
    }


    @Override
    protected void solveProblem(LineGraph linegraph, double WEIGHTFACTOR, boolean writeLpFile) {
        logger.debug("Solving Problem");

        //Transfering relevant data from line graph to local variables
        int numberOfLines = linegraph.numberOfLines;
        int numberOfRoutes = linegraph.numberOfLines;

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

        //Set sense of the obj function to minimizing
        p.setSense(XPRB.MINIM);
        p.lpOptimize();

        //Get status of the LP
        int status = p.getLPStat();

        if (status == XPRS.LP_OPTIMAL) {
            logger.debug("Optimal solution found");
            logger.debug("Objective value: " + p.getObjVal());
            for (int k = 0; k < numberOfRoutes; k++) {
                for (int i = 0; i < numberOfLines; i++) {
                    for (int j = 0; j < numberOfLines; j++) {
                        if (x[i][j][k].getSol() > TOL) {
                            logger.debug(x[i][j][k].getName() + ":" +
                                x[i][j][k].getSol());
                        }
                    }
                }
            }
            for (int k = 0; k < numberOfRoutes; k++)
                logger.debug(y[k].getName() + ":" + y[k].getSol());
            for (int k = 0; k < numberOfRoutes; k++)
                logger.debug(z[k].getName() + ":" + z[k].getSol());
        }

    }

    /**
     * Calculate the power set of a set S without the empty set
     *
     * @param S Set of which the power set is to be calculated
     * @return Power set of S without the empty set
     */
    public static ArrayList<ArrayList<Integer>> calcPowerSet(int[] S) {
        if (S == null)
            return null;

        ArrayList<ArrayList<Integer>> result =
            new ArrayList<>();
        //for all items in S
        for (int i = 0; i < S.length; i++) {
            ArrayList<ArrayList<Integer>> temp =
                new ArrayList<>();

            //get sets that are already in result
            for (ArrayList<Integer> a : result) {
                temp.add(new ArrayList<>(a));
            }

            //add S[i] to existing sets
            for (ArrayList<Integer> a : temp) {
                a.add(S[i]);
            }

            //add S[i] only as a set
            ArrayList<Integer> single = new ArrayList<>();
            single.add(S[i]);
            temp.add(single);

            result.addAll(temp);
        }
        return result;
    }

}
