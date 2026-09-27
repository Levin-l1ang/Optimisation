package net.lintim.algorithm.vehiclescheduling;

import com.dashoptimization.*;
import net.lintim.model.vehiclescheduling.LineGraph;
import net.lintim.solver.impl.XpressHelper;
import net.lintim.util.Logger;
import net.lintim.util.vehiclescheduling.Parameters;

/**
 * Class for setting up the integer program in Xpress and solving it
 * Super class for the different variants of the vehicle scheduling IPs
 */
public abstract class Model {

    public static Logger logger = new Logger(Model.class);

    protected XPRB bcl;
    protected XPRBprob p;

    /**
     * empty constructor
     */
    public Model() {
    }


    /**
     * Initializing/modeling the IP in the sub class
     *
     * @param linegraph    linegraph
     * @param WEIGHTFACTOR variable \alpha in the IPs
     */
    protected abstract void modelProblem(LineGraph linegraph,
                                         double WEIGHTFACTOR);

    /**
     * Solving the IP
     *
     * @param linegraph    linegraph
     * @param WEIGHTFACTOR variable \alpha in the IPs
     * @param writeLpFile  whether to write a lp file, e.g., for debugging
     */
    protected abstract void solveProblem(LineGraph linegraph,
                                         double WEIGHTFACTOR, boolean writeLpFile) throws Exception;

    /**
     * Read out T for the subclasses
     *
     * @return value of the period T
     */
    protected int getT() {
        //duration of one period
        return 60;
    }

    /**
     * Set up IP and solve it
     *
     * @param linegraph linegraph
     */
    public void makeVehicleSchedule(LineGraph linegraph, Parameters parameters)
        throws Exception {

        logger.debug("Initialize model");
        XPRS.init();
        bcl = new XPRB();                           //Initialize BCL
        p = bcl.newProb("VS_MinNumberOfVeh");       //Create a problem
        XpressHelper.setXpressSolverParameters(p, parameters);
        //1: CPU-time, 2: process time, 0: wall on the clock-time
        logger.debug("Model problem");
        modelProblem(linegraph, parameters.getWeightFactor());      //Initialize IP
        logger.debug("Solve problem");
        solveProblem(linegraph, parameters.getWeightFactor(), parameters.writeLpFile());      //Solve IP, print solution

    }
}
