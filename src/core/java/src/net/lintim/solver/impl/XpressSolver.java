package net.lintim.solver.impl;

import com.dashoptimization.*;
import net.lintim.exception.*;
import net.lintim.solver.*;
import net.lintim.util.LogLevel;
import net.lintim.util.SolverType;

import java.io.IOException;
import java.util.Map;

/**
 * Implementation of the abstract {@link Solver} class for xpress. Use {@link Solver#createSolver(SolverType)}
 * with type {@link SolverType#XPRESS} to obtain an instance of the class. Note that this file will only be compiled,
 * if xpress is present on the system CLASSPATH.
 */
public class XpressSolver extends Solver {

    private XPRB bcl;

    public XpressSolver() {
        if (!XPRS.isInitialized()) {
            XPRS.init();
        }
        bcl = new XPRB();
    }

    @Override
    public Model createModel() {
        return new XpressModel(bcl.newProb("lintim_core"));
    }

    @Override
    public void dispose() {
        bcl.finalize();
        bcl = null;
    }

}
