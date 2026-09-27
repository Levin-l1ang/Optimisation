package net.lintim.solver.impl;

import ilog.concert.IloException;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import net.lintim.exception.SolverCplexException;
import net.lintim.solver.Variable;

import java.util.Objects;

public class CplexVariable implements Variable {

    private final IloNumVar var;

    CplexVariable(IloNumVar var) {
        this.var = var;
    }

    @Override
    public String getName() {
        return var.getName();
    }

    @Override
    public VariableType getType() {
        IloNumVarType type;
        try {
            type = var.getType();
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
        if (type == IloNumVarType.Bool) {
            return VariableType.BINARY;
        }
        if (type == IloNumVarType.Float) {
            return VariableType.CONTINUOUS;
        }
        if (type == IloNumVarType.Int) {
            return VariableType.INTEGER;
        }
        else {
            throw new SolverCplexException("Unknown cplex variable type " + type);
        }
    }

    @Override
    public double getLowerBound() {
        try {
            return var.getLB();
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    @Override
    public double getUpperBound() {
        try {
            return var.getUB();
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    IloNumVar getVar() {
        return var;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CplexVariable that = (CplexVariable) o;
        return Objects.equals(var, that.var);
    }

    @Override
    public int hashCode() {
        return Objects.hash(var);
    }
}
