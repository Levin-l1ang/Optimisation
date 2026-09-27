package net.lintim.solver.impl;

import jscip.SCIP_Vartype;
import net.lintim.exception.SolverAttributeNotImplementedException;
import net.lintim.solver.Variable;
import net.lintim.util.SolverType;

import java.util.Objects;

public class SCIPVariable implements Variable {
    private final jscip.Variable var;

    SCIPVariable(jscip.Variable var) {
        this.var = var;
    }

    @Override
    public String getName() {
        return var.getName();
    }

    @Override
    public VariableType getType() {
        SCIP_Vartype type = var.getType();
        if (type == SCIP_Vartype.SCIP_VARTYPE_BINARY) {
            return VariableType.BINARY;
        }
        else if (type == SCIP_Vartype.SCIP_VARTYPE_CONTINUOUS) {
            return VariableType.CONTINUOUS;
        }
        else if (type == SCIP_Vartype.SCIP_VARTYPE_INTEGER) {
            return VariableType.INTEGER;
        }
        else {
            throw new SolverAttributeNotImplementedException(SolverType.SCIP, "VariableType " + type);
        }
    }

    @Override
    public double getLowerBound() {
        return var.getLbGlobal();
    }

    @Override
    public double getUpperBound() {
        return var.getUbGlobal();
    }

    jscip.Variable getVar() {
        return var;
    }

    @Override
    public String toString() {
        return "SCIPVariable{" +
            "var=" + var +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SCIPVariable that = (SCIPVariable) o;
        return Objects.equals(var, that.var);
    }

    @Override
    public int hashCode() {
        return Objects.hash(var);
    }
}
