package net.lintim.solver.impl;

import net.lintim.solver.Model;
import net.lintim.solver.Solver;

public class GLPKSolver extends Solver {


    @Override
    public Model createModel() {
        return new GLPKModel();
    }

    @Override
    public void dispose() {
        // Nothing to do for a cmd solver
    }
}
