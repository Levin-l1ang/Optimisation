package net.lintim.solver.impl;

import jscip.Scip;
import net.lintim.solver.Model;
import net.lintim.solver.Solver;

public class SCIPSolver extends Solver {

    public SCIPSolver() {
        System.loadLibrary("jscip");
    }

    @Override
    public Model createModel() {
        return new SCIPModel(new Scip());
    }

    @Override
    public void dispose() {
        // Nothing to do here for scip
    }
}
