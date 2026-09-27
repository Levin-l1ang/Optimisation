package net.lintim.solver.impl;

import com.dashoptimization.*;
import net.lintim.exception.*;
import net.lintim.solver.Constraint;
import net.lintim.solver.LinearExpression;
import net.lintim.solver.Model;
import net.lintim.solver.Variable;
import net.lintim.util.LogLevel;
import net.lintim.util.Logger;
import net.lintim.util.SolverType;

import java.io.IOException;

public class XpressModel implements Model {

    private static final Logger logger = new Logger(XpressModel.class);

    private final XPRBprob model;
    private XPRBsol startingSolution = null;
    private XPRBexpr objFct;

    XpressModel(XPRBprob problem) {
        this.model = problem;
        this.objFct = new XPRBexpr();
    }

    static void throwForNonXpressLinearExpression(LinearExpression expression) {
        if (!(expression instanceof XpressLinearExpression)) {
            throw new LinTimException("Trying to work with non xpress expression in xpress context");
        }
    }

    static void throwForNonXpressVariable(Variable variable) {
        if (!(variable instanceof XpressVariable)) {
            throw new LinTimException("Trying to work with non xpress variable in xpress context");
        }
    }

    @Override
    public Object getOriginalModel() {
        return model;
    }

    @Override
    public Variable addVariable(double lowerBound, double upperBound, Variable.VariableType type, double objective, String name) {
        int vType;
        switch (type) {
            case BINARY:
                vType = XPRB.BV;
                break;
            case INTEGER:
                vType = XPRB.UI;
                break;
            case CONTINUOUS:
                vType = XPRB.PL;
                break;
            default:
                throw new SolverVariableTypeNotImplementedException(SolverType.XPRESS, type);
        }
        XPRBvar var = model.newVar(name, vType, lowerBound, upperBound);
        objFct.addTerm(objective, var);
        return new XpressVariable(var);
    }

    @Override
    public Constraint addConstraint(LinearExpression lhs, Constraint.ConstraintSense sense, double rhs, String name) {
        throwForNonXpressLinearExpression(lhs);
        XPRBexpr lhsExpr = ((XpressLinearExpression) lhs).getExpr();
        switch (sense) {
            case LESS_EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsExpr.lEql(rhs)));
            case GREATER_EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsExpr.gEql(rhs)));
            case EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsExpr.eql(rhs)));
            default:
                throw new LinTimException("Unknown constraint sense " + sense);
        }
    }

    @Override
    public Constraint addConstraint(LinearExpression lhs, Constraint.ConstraintSense sense, LinearExpression rhs, String name) {
        throwForNonXpressLinearExpression(lhs);
        throwForNonXpressLinearExpression(rhs);
        XPRBexpr lhsExpr = ((XpressLinearExpression) lhs).getExpr();
        XPRBexpr rhsExpr = ((XpressLinearExpression) rhs).getExpr();
        switch (sense) {
            case LESS_EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsExpr.lEql(rhsExpr)));
            case GREATER_EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsExpr.gEql(rhsExpr)));
            case EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsExpr.eql(rhsExpr)));
            default:
                throw new LinTimException("Unknown constraint sense " + sense);
        }
    }

    @Override
    public Constraint addConstraint(LinearExpression lhs, Constraint.ConstraintSense sense, Variable rhs, String name) {
        throwForNonXpressLinearExpression(lhs);
        throwForNonXpressVariable(rhs);
        XPRBexpr lhsExpr = ((XpressLinearExpression) lhs).getExpr();
        XPRBvar rhsVar = ((XpressVariable) rhs).getVar();
        switch (sense) {
            case LESS_EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsExpr.lEql(rhsVar)));
            case GREATER_EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsExpr.gEql(rhsVar)));
            case EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsExpr.eql(rhsVar)));
            default:
                throw new LinTimException("Unknown constraint sense " + sense);
        }
    }

    @Override
    public Constraint addConstraint(Variable lhs, Constraint.ConstraintSense sense, double rhs, String name) {
        throwForNonXpressVariable(lhs);
        XPRBvar lhsVar = ((XpressVariable) lhs).getVar();
        switch (sense) {
            case LESS_EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsVar.lEql(rhs)));
            case GREATER_EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsVar.gEql(rhs)));
            case EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsVar.eql(rhs)));
            default:
                throw new LinTimException("Unknown constraint sense " + sense);
        }
    }

    @Override
    public Constraint addConstraint(Variable lhs, Constraint.ConstraintSense sense, Variable rhs, String name) {
        throwForNonXpressVariable(lhs);
        throwForNonXpressVariable(rhs);
        XPRBvar lhsVar = ((XpressVariable) lhs).getVar();
        XPRBvar rhsVar = ((XpressVariable) rhs).getVar();
        switch (sense) {
            case LESS_EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsVar.lEql(rhsVar)));
            case GREATER_EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsVar.gEql(rhsVar)));
            case EQUAL:
                return new XpressConstraint(model.newCtr(name, lhsVar.eql(rhsVar)));
            default:
                throw new LinTimException("Unknown constraint sense " + sense);
        }
    }

    @Override
    public Variable getVariableByName(String name) {
        return new XpressVariable(model.getVarByName(name));
    }

    @Override
    public void setStartValue(Variable variable, double value) {
        if (startingSolution == null) {
            startingSolution = model.newSol();
        }
        throwForNonXpressVariable(variable);
        startingSolution.setVar(((XpressVariable) variable).getVar(), value);
    }

    @Override
    public void setObjective(LinearExpression objective, OptimizationSense sense) {
        throwForNonXpressLinearExpression(objective);
        objFct = ((XpressLinearExpression) objective).getExpr();
        switch (sense) {
            case MINIMIZE:
                model.setSense(XPRB.MINIM);
                break;
            case MAXIMIZE:
                model.setSense(XPRB.MAXIM);
                break;
            default:
                throw new LinTimException("Unknown optimization sense " + sense);
        }
    }

    @Override
    public LinearExpression getObjective() {
        return new XpressLinearExpression(objFct);
    }

    @Override
    public double getObjectiveValue() {
        return getDoubleAttribute(DoubleAttribute.OBJ_VAL);
    };

    @Override
    public LinearExpression createExpression() {
        return new XpressLinearExpression(new XPRBexpr());
    }

    @Override
    public OptimizationSense getSense() {
        switch (model.getSense()) {
            case XPRB.MINIM:
                return OptimizationSense.MINIMIZE;
            case XPRB.MAXIM:
                return OptimizationSense.MAXIMIZE;
            default:
                throw new LinTimException("Unknown Xpress optimization sense: " + model.getSense());
        }
    }

    @Override
    public void write(String filename) {
        model.setObj(objFct);
        try {
            model.exportProb(filename);
        } catch (IOException e) {
            logger.warn("Tried to write lp file with xpress, got IOException: " + e);
            throw new OutputFileException(filename);
        }

    }

    @Override
    public void setSense(OptimizationSense sense) {
        switch (sense) {
            case MAXIMIZE:
                model.setSense(XPRB.MAXIM);
                break;
            case MINIMIZE:
                model.setSense(XPRB.MINIM);
                break;
            default:
                throw new SolverAttributeNotImplementedException(SolverType.XPRESS, "OptimizationSense " + sense);
        }
    }

    @Override
    public int getIntAttribute(IntAttribute attribute) {
        switch (attribute) {
            case NUM_SOLUTIONS:
                return model.getXPRSprob().getIntAttrib(XPRS.MIPSOLS);
            case NUM_VARIABLES:
                return model.getXPRSprob().getIntAttrib(XPRS.COLS);
            case NUM_CONSTRAINTS:
                return model.getXPRSprob().getIntAttrib(XPRS.ROWS);
            case NUM_INT_VARIABLES:
            case NUM_BIN_VARIABLES:
            default:
                throw new SolverAttributeNotImplementedException(SolverType.XPRESS, attribute.name());
        }
    }

    @Override
    public Status getStatus() {
        switch (model.getMIPStat()) {
            case XPRB.MIP_SOLUTION:
                return Status.FEASIBLE;
            case XPRB.MIP_OPTIMAL:
                return Status.OPTIMAL;
            default:
                return Status.INFEASIBLE;
        }
    }

    @Override
    public void solve() {
        if (startingSolution != null) {
            model.addMIPSol(startingSolution);
        }
        model.setObj(objFct);
        model.mipOptimize();
    }

    @Override
    public void computeIIS(String fileName) {
        model.getXPRSprob().firstIIS(1);
        model.getXPRSprob().writeIIS(0, fileName, 0);
    }

    @Override
    public double getValue(Variable variable) {
        throwForNonXpressVariable(variable);
        return ((XpressVariable) variable).getVar().getSol();
    }

    @Override
    public double getDoubleAttribute(DoubleAttribute attribute) {
        switch (attribute) {
            case MIP_GAP:
                double bestObjective = model.getXPRSprob().getDblAttrib(XPRS.MIPBESTOBJVAL);
                double bestBound = model.getXPRSprob().getDblAttrib(XPRS.BESTBOUND);
                return Math.abs((bestObjective - bestBound)/bestObjective);
            case OBJ_VAL:
                return model.getObjVal();
            case RUNTIME:
                return model.getXPRSprob().getIntAttrib(XPRS.TIME);
            default:
                throw new SolverAttributeNotImplementedException(SolverType.XPRESS, attribute.name());
        }
    }

    @Override
    public void setIntParam(IntParam param, int value) {
        switch (param) {
            case TIMELIMIT:
                // LinTim timelimit is absolute, i.e., the solver should abort. This is only the case for negative
                // timelimits in xpress, otherwise a feasible solution needs to be found
                model.getXPRSprob().setIntControl(XPRS.MAXTIME, -1 * value);
                break;
            case THREAD_LIMIT:
                model.getXPRSprob().setIntControl(XPRS.THREADS, value);
                break;
            case OUTPUT_LEVEL:
                if (value == LogLevel.DEBUG.intValue()) {
                    model.setMsgLevel(4);
                }
                else if (value == LogLevel.INFO.intValue() || value == LogLevel.WARN.intValue()) {
                    model.setMsgLevel(2);
                }
                else {
                    model.setMsgLevel(0);
                }
                break;
            default:
                throw new SolverParamNotImplementedException(SolverType.XPRESS, param.name());
        }
    }

    @Override
    public void setDoubleParam(DoubleParam param, double value) {
        switch (param) {
            case MIP_GAP:
                model.getXPRSprob().setDblControl(XPRS.MIPRELSTOP, value);
            default:
                throw new SolverParamNotImplementedException(SolverType.XPRESS, param.name());
        }
    }

    @Override
    public void dispose() {
        model.finalize();
    }
}
