package net.lintim.solver.impl;

import com.dashoptimization.XPRB;
import com.dashoptimization.XPRBctr;
import net.lintim.exception.SolverAttributeNotImplementedException;
import net.lintim.solver.Constraint;
import net.lintim.util.SolverType;

public class XpressConstraint implements Constraint {

    private final XPRBctr constr;

    XpressConstraint(XPRBctr constr) {
        this.constr = constr;
    }

    @Override
    public String getName() {
        return constr.getName();
    }

    @Override
    public ConstraintSense getSense() {
        switch (constr.getType()) {
            case XPRB.L:
                return ConstraintSense.LESS_EQUAL;
            case XPRB.G:
                return ConstraintSense.GREATER_EQUAL;
            case XPRB.E:
                return ConstraintSense.EQUAL;
            default:
                throw new SolverAttributeNotImplementedException(SolverType.XPRESS, "ConstraintSense " + constr.getType());
        }
    }

    @Override
    public double getRhs() {
        return constr.getRHS();
    }

    @Override
    public void setRhs(double value) {
        constr.setTerm(value);
    }
}
