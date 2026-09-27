package net.lintim.solver.impl;

import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBVar;
import net.lintim.exception.SolverGurobiException;
import net.lintim.solver.LinearExpression;
import net.lintim.solver.Variable;

public class GurobiLinearExpression implements LinearExpression {

    private final GRBLinExpr expr;

    GurobiLinearExpression(GRBLinExpr expr){
        this.expr = expr;
    }

    @Override
    public void add(LinearExpression otherExpression) {
        multiAdd(1, otherExpression);
    }

    @Override
    public void multiAdd(double multiple, LinearExpression otherExpression) {
        GurobiModel.throwForNonGurobiLinearExpression(otherExpression);
        GRBLinExpr other = ((GurobiLinearExpression) otherExpression).expr;
        try {
            this.expr.multAdd(multiple, other);
        } catch (GRBException e) {
            throw new SolverGurobiException(e.getMessage());
        }
    }

    @Override
    public void add(Variable variable) {
        multiAdd(1, variable);
    }

    @Override
    public void multiAdd(double multiple, Variable variable) {
        GurobiModel.throwForNonGurobiVariable(variable);
        GRBVar other = ((GurobiVariable) variable).getGRBVar();
        this.expr.addTerm(multiple, other);
    }

    @Override
    public void clear() {
        this.expr.clear();
    }

    @Override
    public void addConstant(double value) {
        this.expr.addConstant(value);
    }

    GRBLinExpr getExpr() {
        return expr;
    }
}
