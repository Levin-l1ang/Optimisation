package net.lintim.solver.impl;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBConstr;
import com.gurobi.gurobi.GRBException;
import net.lintim.exception.SolverAttributeNotImplementedException;
import net.lintim.exception.SolverGurobiException;
import net.lintim.solver.Constraint;
import net.lintim.util.SolverType;

public class GurobiConstraint implements Constraint {

    private GRBConstr constr;

    GurobiConstraint(GRBConstr constr) {
        this.constr = constr;
    }

    @Override
    public String getName() {
        try {
            return constr.get(GRB.StringAttr.ConstrName);
        } catch (GRBException e) {
            throw new SolverGurobiException(e.getMessage());
        }
    }

    @Override
    public ConstraintSense getSense() {
        char sense;
        try {
            sense = constr.get(GRB.CharAttr.Sense);
        } catch (GRBException e) {
            throw new SolverGurobiException(e.getMessage());
        }
        switch (sense) {
            case GRB.LESS_EQUAL:
                return ConstraintSense.LESS_EQUAL;
            case GRB.EQUAL:
                return ConstraintSense.EQUAL;
            case GRB.GREATER_EQUAL:
                return ConstraintSense.GREATER_EQUAL;
            default:
                throw new SolverAttributeNotImplementedException(SolverType.GUROBI, "ConstraintSense " + sense);
        }
    }

    @Override
    public double getRhs() {
        try {
            return constr.get(GRB.DoubleAttr.RHS);
        } catch (GRBException e) {
            throw new SolverGurobiException(e.getMessage());
        }
    }

    @Override
    public void setRhs(double value) {
        try {
            constr.set(GRB.DoubleAttr.RHS, value);
        } catch (GRBException e) {
            throw new SolverGurobiException(e.getMessage());
        }
    }
}
