package net.lintim.solver.impl;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBVar;
import net.lintim.exception.SolverAttributeNotImplementedException;
import net.lintim.exception.SolverGurobiException;
import net.lintim.solver.Variable;
import net.lintim.util.SolverType;

import java.util.Objects;

class GurobiVariable implements Variable {

    private final GRBVar var;

    GurobiVariable(GRBVar var) {
        this.var = var;
    }

    @Override
    public String getName() {
        try {
            return var.get(GRB.StringAttr.VarName);
        } catch (GRBException e) {
            throw new SolverGurobiException(e.getMessage());
        }
    }

    @Override
    public VariableType getType() {
        char type;
        try {
            type = var.get(GRB.CharAttr.VType);
        } catch (GRBException e) {
            throw new SolverGurobiException(e.getMessage());
        }
        switch (type) {
            case GRB.INTEGER:
                return VariableType.INTEGER;
            case GRB.CONTINUOUS:
                return VariableType.CONTINUOUS;
            case GRB.BINARY:
                return VariableType.BINARY;
            default:
                throw new SolverAttributeNotImplementedException(SolverType.GUROBI, "VariableType " + type);
        }
    }

    @Override
    public double getLowerBound() {
        try {
            return var.get(GRB.DoubleAttr.LB);
        } catch (GRBException e) {
            throw new SolverGurobiException(e.getMessage());
        }
    }

    @Override
    public double getUpperBound() {
        try {
            return var.get(GRB.DoubleAttr.UB);
        } catch (GRBException e) {
            throw new SolverGurobiException(e.getMessage());
        }
    }

    GRBVar getGRBVar() {
        return var;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GurobiVariable that = (GurobiVariable) o;
        return Objects.equals(var, that.var);
    }

    @Override
    public int hashCode() {
        return Objects.hash(var);
    }
}
