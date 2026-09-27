package net.lintim.solver.impl;

import jscip.SCIP_Vartype;
import jscip.Scip;
import jscip.Solution;
import net.lintim.exception.LinTimException;
import net.lintim.exception.SolverAttributeNotImplementedException;
import net.lintim.exception.SolverParamNotImplementedException;
import net.lintim.exception.SolverVariableTypeNotImplementedException;
import net.lintim.solver.Constraint;
import net.lintim.solver.LinearExpression;
import net.lintim.solver.Model;
import net.lintim.solver.Variable;
import net.lintim.util.LogLevel;
import net.lintim.util.Logger;
import net.lintim.util.SolverType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SCIPModel implements Model {

    private final Scip model;
    private final HashMap<String, Variable> variables;

    private static final Logger logger = new Logger(SCIPModel.class);

    SCIPModel(Scip model) {
        this.model = model;
        this.variables = new HashMap<>();
        model.create("");
    }

    @Override
    public Object getOriginalModel() {
        return model;
    }

    @Override
    public Variable addVariable(double lowerBound, double upperBound, Variable.VariableType type,
                                double objective, String name) {
        SCIP_Vartype vType;
        switch (type) {
            case BINARY:
                vType = SCIP_Vartype.SCIP_VARTYPE_BINARY;
                break;
            case INTEGER:
                vType = SCIP_Vartype.SCIP_VARTYPE_INTEGER;
                break;
            case CONTINUOUS:
                vType = SCIP_Vartype.SCIP_VARTYPE_CONTINUOUS;
                break;
            default:
                throw new SolverVariableTypeNotImplementedException(SolverType.SCIP, type);
        }
        SCIPVariable variable = new SCIPVariable(model.createVar(name, lowerBound, upperBound, objective, vType));
        variables.put(variable.getName(), variable);
        return variable;
    }

    @Override
    public Constraint addConstraint(LinearExpression expression, Constraint.ConstraintSense sense, double rhs, String name) {
        double modelLhs, modelRhs;
        switch (sense) {
            case LESS_EQUAL:
                modelLhs = -model.infinity();
                modelRhs = rhs;
                break;
            case GREATER_EQUAL:
                modelLhs = rhs;
                modelRhs = model.infinity();
                break;
            case EQUAL:
                modelLhs = rhs;
                modelRhs = rhs;
                break;
            default:
                throw new LinTimException("Unknown constraint sense " + sense);
        }
        NativeLinearExpression<SCIPVariable> scipExpr;
        try {
            scipExpr = (NativeLinearExpression<SCIPVariable>) expression;
        } catch (ClassCastException e) {
            // Since the type information are gone by runtime, there is no nice way to know whether expression
            // has the correct type
            throw new LinTimException("Try to work with unfitting or non-NativeLinearExpression in SCIP context");
        }
        List<Map.Entry<SCIPVariable, Double>> valueList = new ArrayList<>(scipExpr.getValues().entrySet());
        jscip.Variable[] variables = new jscip.Variable[valueList.size()];
        double[] vals = new double[valueList.size()];
        SCIPVariable var;
        for (int i = 0; i < valueList.size(); i++) {
            var = valueList.get(i).getKey();
            variables[i] = var.getVar();
            vals[i] = valueList.get(i).getValue();
        }
        jscip.Constraint cons = model.createConsLinear(name, variables, vals, modelLhs, modelRhs);
        model.addCons(cons);
        return new SCIPConstraint(cons);
    }

    @Override
    public Variable getVariableByName(String name) {
        return variables.get(name);
    }

    @Override
    public void setStartValue(Variable variable, double value) {
        throw new SolverAttributeNotImplementedException(SolverType.SCIP, "setStartValue");
    }

    @Override
    public void setObjective(LinearExpression objective, OptimizationSense sense) {
        throw new SolverAttributeNotImplementedException(SolverType.SCIP, "setObjective");
    }

    @Override
    public LinearExpression getObjective() {
        throw new SolverAttributeNotImplementedException(SolverType.SCIP, "getObjective");
    }

    @Override
    public double getObjectiveValue() {
        return getDoubleAttribute(DoubleAttribute.OBJ_VAL);
    };

    @Override
    public LinearExpression createExpression() {
        return new NativeLinearExpression<SCIPVariable>();
    }

    @Override
    public OptimizationSense getSense() {
        throw new SolverAttributeNotImplementedException(SolverType.SCIP, "getSense");
    }

    @Override
    public void write(String filename) {
        model.writeOrigProblem(filename);
    }

    @Override
    public void setSense(OptimizationSense sense) {
        switch (sense) {
            case MINIMIZE:
                model.setMinimize();
                return;
            case MAXIMIZE:
                model.setMaximize();
                return;
            default:
                throw new LinTimException("Unknown objective sense " + sense);
        }
    }

    @Override
    public int getIntAttribute(IntAttribute attribute) {
        switch (attribute) {
            case NUM_SOLUTIONS:
                return model.getNSols();
            case NUM_VARIABLES:
                return model.getNVars();
            default:
                throw new SolverAttributeNotImplementedException(SolverType.SCIP, attribute.name());
        }
    }

    @Override
    public Status getStatus() {
        if (model.getNSols() > 0) {
            return Status.FEASIBLE;
        }
        else {
            return Status.INFEASIBLE;
        }
    }

    @Override
    public void solve() {
        model.solve();
    }

    @Override
    public void computeIIS(String fileName) {
        logger.warn("Computing an IIS is not supported by SCIP, will not write output");
    }

    @Override
    public double getValue(Variable variable) {
        throwForNonScipVariable(variable);
        jscip.Variable var = ((SCIPVariable) variable).getVar();
        Solution solution = model.getSols()[0];
        return model.getSolVal(solution, var);
    }

    @Override
    public double getDoubleAttribute(DoubleAttribute attribute) {
        switch (attribute) {
            case OBJ_VAL:
                return model.getSolOrigObj(model.getSols()[0]);
            case MIP_GAP:
                return model.getGap();
            case RUNTIME:
            default:
                throw new SolverAttributeNotImplementedException(SolverType.SCIP, attribute.name());
        }
    }

    @Override
    public void setIntParam(IntParam param, int value) {
        switch (param) {
            case TIMELIMIT:
                if (value >= 0) {
                    model.setRealParam("limits/time", value);
                }
                break;
            case OUTPUT_LEVEL:
                if (value == LogLevel.DEBUG.intValue()) {
                    model.setIntParam("display/verblevel", 4);
                }
                else {
                    model.setIntParam("display/verblevel", 4);
                }
                break;
            case THREAD_LIMIT:
                if (value > 0) {
                    model.setIntParam("lp/threads", value);
                    model.setIntParam("parallel/maxnthreads", value);
                }
                break;
            default:
                throw new SolverParamNotImplementedException(SolverType.SCIP, param.name());
        }

    }

    @Override
    public void setDoubleParam(DoubleParam param, double value) {
        switch (param) {
            case MIP_GAP:
                if (value >= 0) {
                    model.setRealParam("limits/gap", value);
                }
                break;
            default:
                throw new SolverParamNotImplementedException(SolverType.SCIP, param.name());
        }
    }

    @Override
    public void dispose() {
        model.free();
    }

    static void throwForNonScipVariable(Variable var) {
        if (!(var instanceof SCIPVariable)) {
            throw new LinTimException("Try to work with non scip variable in scip context");
        }
    }
}
