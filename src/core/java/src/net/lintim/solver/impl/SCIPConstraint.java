package net.lintim.solver.impl;

import net.lintim.exception.SolverAttributeNotImplementedException;
import net.lintim.solver.Constraint;
import net.lintim.util.SolverType;

public class SCIPConstraint implements Constraint {

    private final jscip.Constraint constr;

    public SCIPConstraint(jscip.Constraint constr) {
        this.constr = constr;
    }

    @Override
    public String getName() {
        return constr.getName();
    }

    @Override
    public ConstraintSense getSense() {
        throw new SolverAttributeNotImplementedException(SolverType.SCIP, "GetSense for Constraint");
    }

    @Override
    public double getRhs() {
        throw new SolverAttributeNotImplementedException(SolverType.SCIP, "getRhs for Constraint");
    }

    @Override
    public void setRhs(double value) {
        throw new SolverAttributeNotImplementedException(SolverType.SCIP, "setRhs for Constraint");
    }
}
