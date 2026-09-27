package net.lintim.solver.impl;

import ilog.concert.*;
import ilog.cplex.IloCplex;
import net.lintim.exception.*;
import net.lintim.solver.*;


/**
 * Implementation of the abstract {@link Solver} class for cplex. Use {@link Solver#createModel()}
 * to obtain a model. Note that this file will only be compiled, if cplex is present on the system CLASSPATH.
 */
public class CplexSolver extends Solver {

    private final IloCplex cplex;

    public CplexSolver() {
        try {
            cplex = new IloCplex();
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    @Override
    public Model createModel() {
        return new CplexModel(cplex);
    }

    @Override
    public void dispose() {
        cplex.end();
    }
}
