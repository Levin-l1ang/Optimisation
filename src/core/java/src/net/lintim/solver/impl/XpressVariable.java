package net.lintim.solver.impl;

import com.dashoptimization.XPRB;
import com.dashoptimization.XPRBvar;
import net.lintim.exception.SolverAttributeNotImplementedException;
import net.lintim.solver.Variable;
import net.lintim.util.SolverType;

import java.util.Objects;

public class XpressVariable implements Variable {

    private final XPRBvar var;

    XpressVariable(XPRBvar var) {
        this.var = var;
    }

    XPRBvar getVar() {
        return var;
    }

    @Override
    public String getName() {
        return var.getName();
    }

    @Override
    public VariableType getType() {
        switch (var.getType()) {
            case XPRB.BV:
                return VariableType.BINARY;
            case XPRB.UI:
                return VariableType.INTEGER;
            case XPRB.PL:
                return VariableType.CONTINUOUS;
            default:
                throw new SolverAttributeNotImplementedException(SolverType.XPRESS, "VariableType " + var.getType());
        }
    }

    @Override
    public double getLowerBound() {
        return var.getLB();
    }

    @Override
    public double getUpperBound() {
        return var.getUB();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XpressVariable that = (XpressVariable) o;
        return Objects.equals(var, that.var);
    }

    @Override
    public int hashCode() {
        return Objects.hash(var);
    }
}
