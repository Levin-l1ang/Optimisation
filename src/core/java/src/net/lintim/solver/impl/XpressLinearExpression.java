package net.lintim.solver.impl;

import com.dashoptimization.XPRBexpr;
import net.lintim.solver.LinearExpression;
import net.lintim.solver.Variable;

public class XpressLinearExpression implements LinearExpression {

    private final XPRBexpr expr;

    XpressLinearExpression(XPRBexpr expr) {
        this.expr = expr;
    }

    XPRBexpr getExpr() {
        return expr;
    }

    @Override
    public void add(LinearExpression otherExpression) {
        XpressModel.throwForNonXpressLinearExpression(otherExpression);
        expr.add(((XpressLinearExpression) otherExpression).getExpr());
    }

    @Override
    public void add(Variable variable) {
        multiAdd(1, variable);
    }

    @Override
    public void multiAdd(double multiple, LinearExpression otherExpression) {

    }

    @Override
    public void multiAdd(double multiple, Variable variable) {
        XpressModel.throwForNonXpressVariable(variable);
        expr.addTerm(multiple, ((XpressVariable) variable).getVar());
    }

    @Override
    public void clear() {
        expr.reset();
    }

    @Override
    public void addConstant(double value) {
        expr.add(value);
    }
}
