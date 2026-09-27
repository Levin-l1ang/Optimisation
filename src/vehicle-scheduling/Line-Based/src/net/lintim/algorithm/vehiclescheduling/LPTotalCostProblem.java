package net.lintim.algorithm.vehiclescheduling;

import net.lintim.model.vehiclescheduling.LineGraph;
import com.dashoptimization.*;

import java.io.IOException;

/**
 * Class implementing the linear program (LP^4)
 * Sub class of Model
 */
public class LPTotalCostProblem extends Model {
    //Variables
    private XPRBvar[][][][] x;                  //Binaries, x_{ijkl} in (LP^4)
    private XPRBvar[] y;                        //y_k in (LP^4)
    private XPRBctr objective;                  //objective function

    int T = super.getT();
    double TOL = 0.001;                 //Tolerance value for output of solution
    final int MAXEDGESINROUTE = Integer.MAX_VALUE;
    //MAXEDGESINROUTE can be set to a value if the range of l is to be reduced


    /**
     * empty constructor
     */
    public LPTotalCostProblem() {
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
        double[][] dist, time;

        //Transfer data from linegraph to local variables
        numberOfLines = linegraph.numberOfLines;
        int numberOfRoutes = linegraph.numberOfLines;
        int edgesInRoute = linegraph.numberOfLines;

        if (edgesInRoute > MAXEDGESINROUTE) {
            edgesInRoute = MAXEDGESINROUTE;
        }
        dist = new double[numberOfLines][numberOfLines];
        time = new double[numberOfLines][numberOfLines];

        for (int i = 0; i < numberOfLines; i++) {
            for (int j = 0; j < numberOfLines; j++) {
                dist[i][j] = linegraph.dist[i][j];
                time[i][j] = linegraph.time[i][j];
            }
        }

        //Initialize contraints
        //constraints
        XPRBctr[] ctrLineCovered = new XPRBctr[numberOfLines];
        XPRBctr[][] ctrInEqualsOut = new XPRBctr[numberOfLines][numberOfRoutes];
        XPRBctr[][] ctrAtMostOneEdge = new XPRBctr[numberOfRoutes][edgesInRoute];
        XPRBctr[][][][] ctrAdjacent = new
            XPRBctr[numberOfLines][numberOfLines][numberOfRoutes][edgesInRoute];
        XPRBctr[] ctrCeiling = new XPRBctr[numberOfRoutes];

        //Initialize variables
        x = new XPRBvar[numberOfLines][numberOfLines][numberOfRoutes]
            [edgesInRoute];
        y = new XPRBvar[numberOfRoutes];

        for (int k = 0; k < numberOfRoutes; k++) {
            y[k] = p.newVar("y_" + (k + 1), XPRB.PL, 0.0, Double.MAX_VALUE);//continuous
            for (int i = 0; i < numberOfLines; i++) {
                for (int j = 0; j < numberOfLines; j++)
                    for (int l = 0; l < edgesInRoute; l++)
                        x[i][j][k][l] = p.newVar("x_" + (i + 1) + "_" + (j + 1) + "_" + (k + 1) +
                            "_" + (l + 1), XPRB.PL, 0.0, 1.0);         //continuous
            }
        }

        //Set constraints OnePerLine and InEqualsOut
        for (int i = 0; i < numberOfLines; i++) {
            ctrLineCovered[i] = p.newCtr("LineCovered_" + (i + 1));
            ctrLineCovered[i].setType(XPRB.E);            //Equality-constraint
            for (int k = 0; k < numberOfRoutes; k++) {
                ctrInEqualsOut[i][k] = p.newCtr("InEqualsOut" + (i + 1) + "_" + (k + 1));
                ctrInEqualsOut[i][k].setType(XPRB.E);     //Equality-constraint
                for (int j = 0; j < numberOfLines; j++) {
                    for (int l = 0; l < edgesInRoute; l++) {
                        ctrLineCovered[i].addTerm(x[i][j][k][l]);
                        ctrInEqualsOut[i][k].addTerm(x[i][j][k][l]);
                        ctrInEqualsOut[i][k].addTerm(x[j][i][k][l], -1.0);
                    }
                }
                ctrInEqualsOut[i][k].setTerm(0.0);
            }
            ctrLineCovered[i].setTerm(1.0);
        }

        //Set constraints AtMostOneEdge and Adjacent and CeilY
        for (int k = 0; k < numberOfRoutes; k++) {
            ctrCeiling[k] = p.newCtr("Ceiling" + (k + 1));
            ctrCeiling[k].setType(XPRB.L);                      //<=-constraint
            ctrCeiling[k].addTerm(y[k], (-1) * T);
            for (int l = 0; l < edgesInRoute; l++) {
                ctrAtMostOneEdge[k][l] = p.newCtr("AtMostOneEdge" + (k + 1) + "_"
                    + (l + 1));
                ctrAtMostOneEdge[k][l].setType(XPRB.L);         //<=-constraint
                for (int i = 0; i < numberOfLines; i++) {
                    for (int j = 0; j < numberOfLines; j++) {
                        ctrCeiling[k].addTerm(x[i][j][k][l], time[i][j]);
                        ctrAtMostOneEdge[k][l].addTerm(x[i][j][k][l]);
                        ctrAdjacent[i][j][k][l] = p.newCtr("Adjacent" + (i + 1) + "_"
                            + (j + 1) + "_" + (k + 1) + "_" + (l + 1));
                        ctrAdjacent[i][j][k][l].setType(XPRB.L);//<=-constraint
                        ctrAdjacent[i][j][k][l].addTerm(x[i][j][k][l]);
                        for (int i3 = 0; i3 < numberOfLines; i3++) {
                            if (l < edgesInRoute - 1) {
                                ctrAdjacent[i][j][k][l].addTerm(
                                    x[j][i3][k][l + 1], -1.0);
                            }
                            ctrAdjacent[i][j][k][l].addTerm(
                                x[j][i3][k][0], -1.0);

                        }
                        ctrAdjacent[i][j][k][l].setTerm(0.0);

                    }
                }
                ctrAtMostOneEdge[k][l].setTerm(1.0);
            }
            ctrCeiling[k].setTerm(0.0);
        }

        //Set objective
        objective = p.newCtr("objective");
        objective.setType(XPRB.N);

        for (int k = 0; k < numberOfRoutes; k++) {
            objective.addTerm(y[k], WEIGHTFACTOR);
            for (int i = 0; i < numberOfLines; i++) {
                for (int j = 0; j < numberOfLines; j++) {
                    for (int l = 0; l < edgesInRoute; l++) {
                        objective.addTerm(x[i][j][k][l], dist[i][j]);
                    }
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
        int edgesInRoute = linegraph.numberOfLines;

        if (edgesInRoute > MAXEDGESINROUTE) {
            edgesInRoute = MAXEDGESINROUTE;
        }

        p.setObj(objective);

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
                        for (int l = 0; l < edgesInRoute; l++) {
                            if (x[i][j][k][l].getSol() > TOL) {
                                logger.debug(x[i][j][k][l].getName() + ":" + x[i][j][k][l].getSol());
                            }
                        }
                    }
                }
            }
            for (int k = 0; k < numberOfRoutes; k++)
                logger.debug(y[k].getName() + ":" + y[k].getSol());
        }
    }


}
