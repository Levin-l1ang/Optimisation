package net.lintim.solver.impl;

import ilog.concert.*;
import ilog.cplex.IloCplex;
import net.lintim.exception.*;
import net.lintim.solver.*;
import net.lintim.util.LogLevel;
import net.lintim.util.Logger;
import net.lintim.util.SolverType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class CplexModel implements Model {

    private static final Logger logger = new Logger(CplexModel.class);

    private final IloCplex model;
    private IloLinearNumExpr objectiveExpression;
    private IloObjectiveSense sense = IloObjectiveSense.Minimize;

    CplexModel(IloCplex model) {
        this.model = model;
        try {
            objectiveExpression = model.linearNumExpr();
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    @Override
    public Object getOriginalModel() {
        return model;
    }

    @Override
    public Variable addVariable(double lowerBound, double upperBound, Variable.VariableType type, double objective, String name) {
        IloNumVarType varType = null;
        switch (type) {
            case CONTINUOUS:
                varType = IloNumVarType.Float;
                break;
            case INTEGER:
                varType = IloNumVarType.Int;
                break;
            case BINARY:
                varType = IloNumVarType.Bool;
                break;
        }
        try {
            IloNumVar cplexVar = model.numVar(lowerBound, upperBound, varType, name);
            Variable variable = new CplexVariable(cplexVar);
            if (objective != 0) {
                objectiveExpression.addTerm(objective, cplexVar);
            }
            return variable;
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    @Override
    public Constraint addConstraint(LinearExpression lhs, Constraint.ConstraintSense sense, double rhs, String name) {
        throwForNonCplexLinearExpression(lhs);
        IloRange constr = null;
        try {
            switch (sense) {
                case GREATER_EQUAL:
                    constr = model.addGe(((CplexLinearExpression) lhs).getExpr(), rhs, name);
                    break;
                case EQUAL:
                    constr = model.addEq(rhs, ((CplexLinearExpression) lhs).getExpr(), name);
                    break;
                case LESS_EQUAL:
                    constr = model.addLe(((CplexLinearExpression) lhs).getExpr(), rhs, name);
                    break;
            }
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
        return new CplexConstraint(constr);
    }

    @Override
    public Constraint addConstraint(LinearExpression lhs, Constraint.ConstraintSense sense, LinearExpression rhs, String name) {
        throwForNonCplexLinearExpression(lhs);
        throwForNonCplexLinearExpression(rhs);
        IloRange constr = null;
        try {
            IloNumExpr diff = model.diff(((CplexLinearExpression) lhs).getExpr(), ((CplexLinearExpression) rhs).getExpr());
            switch (sense) {
                case GREATER_EQUAL:
                    constr = model.addGe(diff, 0, name);
                    break;
                case EQUAL:
                    constr = model.addEq(diff, 0, name);
                    break;
                case LESS_EQUAL:
                    constr = model.addLe(diff, 0, name);
                    break;
            }
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
        return new CplexConstraint(constr);
    }

    @Override
    public Constraint addConstraint(LinearExpression lhs, Constraint.ConstraintSense sense, Variable rhs, String name) {
        throwForNonCplexLinearExpression(lhs);
        throwForNonCplexVariable(rhs);
        IloRange constr = null;
        try {
            IloNumExpr diff = model.diff(((CplexLinearExpression) lhs).getExpr(), ((CplexVariable) rhs).getVar());
            switch (sense) {
                case GREATER_EQUAL:
                    constr = model.addGe(diff, 0, name);
                    break;
                case EQUAL:
                    constr = model.addEq(diff, 0, name);
                    break;
                case LESS_EQUAL:
                    constr = model.addLe(diff, 0, name);
                    break;
            }
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
        return new CplexConstraint(constr);
    }

    @Override
    public Constraint addConstraint(Variable lhs, Constraint.ConstraintSense sense, double rhs, String name) {
        throwForNonCplexVariable(lhs);
        IloRange constr = null;
        try {
            switch (sense) {
                case GREATER_EQUAL:
                    constr = model.addGe(((CplexVariable) lhs).getVar(), rhs, name);
                    break;
                case EQUAL:
                    constr = model.addEq(rhs, ((CplexVariable) lhs).getVar(), name);
                    break;
                case LESS_EQUAL:
                    constr = model.addLe(((CplexVariable) lhs).getVar(), rhs, name);
                    break;
            }
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
        return new CplexConstraint(constr);
    }

    @Override
    public Constraint addConstraint(Variable lhs, Constraint.ConstraintSense sense, Variable rhs, String name) {
        throwForNonCplexVariable(lhs);
        throwForNonCplexVariable(rhs);
        IloRange constr = null;
        try {
            IloNumExpr diff = model.diff(((CplexVariable) lhs).getVar(), ((CplexVariable) rhs).getVar());
            switch (sense) {
                case GREATER_EQUAL:
                    constr = model.addGe(diff, 0, name);
                    break;
                case EQUAL:
                    constr = model.addEq(diff, 0, name);
                    break;
                case LESS_EQUAL:
                    constr = model.addLe(diff, 0, name);
                    break;
            }
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
        return new CplexConstraint(constr);
    }

    @Override
    public Variable getVariableByName(String name) {
        for (Iterator it = model.iterator(); it.hasNext(); ) {
            Object next = it.next();
            if (!(next instanceof IloNumVar)) {
                continue;
            }
            IloNumVar var = (IloNumVar) next;
            if (var.getName().equals(name)) {
                return new CplexVariable(var);
            }
        }
        return null;
    }

    @Override
    public void setStartValue(Variable variable, double value) {
        throwForNonCplexVariable(variable);
        try {
            model.addMIPStart(new IloNumVar[]{((CplexVariable) variable).getVar()}, new double[]{value});
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    @Override
    public void setObjective(LinearExpression objective, OptimizationSense sense) {
        throwForNonCplexLinearExpression(objective);
        this.sense = getSense(sense);
        this.objectiveExpression = ((CplexLinearExpression) objective).getExpr();
    }

    @Override
    public LinearExpression getObjective() {
        return new CplexLinearExpression(objectiveExpression);
    }

    @Override
    public double getObjectiveValue() {
        return getDoubleAttribute(DoubleAttribute.OBJ_VAL);
    };

    @Override
    public LinearExpression createExpression() {
        try {
            return new CplexLinearExpression(model.linearNumExpr());
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    @Override
    public void write(String filename) {
        try {
            model.exportModel(filename);
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    @Override
    public void setSense(OptimizationSense sense) {
        this.sense = getSense(sense);
    }

    private static IloObjectiveSense getSense(OptimizationSense sense) {
        switch (sense) {
            case MAXIMIZE:
                return IloObjectiveSense.Maximize;
            case MINIMIZE:
                return IloObjectiveSense.Minimize;
            default:
                throw new IllegalArgumentException("Unknown optimization sense: " + sense);
        }
    }

    @Override
    public int getIntAttribute(IntAttribute attribute) {
        switch (attribute) {
            case NUM_VARIABLES:
                return model.getNcols();
            case NUM_CONSTRAINTS:
                return model.getNrows();
            case NUM_BIN_VARIABLES:
                return model.getNbinVars();
            case NUM_INT_VARIABLES:
                return model.getNintVars();
            case NUM_SOLUTIONS:
                try {
                    return model.getSolnPoolNsolns();
                } catch (IloException e) {
                    throw new SolverCplexException(e.getMessage());
                }
            default:
                throw new SolverAttributeNotImplementedException(SolverType.CPLEX, attribute.name());
        }
    }

    public Status getStatus() {
        try {
            return transformCplexstatus(model.getStatus());
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    private Status transformCplexstatus(IloCplex.Status cplexStatus) {
        if (IloCplex.Status.Optimal.equals(cplexStatus)) {
            return Status.OPTIMAL;
        } else if (IloCplex.Status.Infeasible.equals(cplexStatus)) {
            return Status.INFEASIBLE;
        } else if (IloCplex.Status.Feasible.equals(cplexStatus)) {
            return Status.FEASIBLE;
        }
        throw new SolverCplexException("Got unsupported status code from cplex solver");
    }

    @Override
    public void solve() {
        try {
            model.addObjective(sense, objectiveExpression);
            model.solve();
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    @Override
    public void computeIIS(String fileName) {
        try {
            ArrayList<IloConstraint> constraintList = new ArrayList<>();
            Iterator it = model.iterator();
            while (it.hasNext()) {
                Object o = it.next();
                if (o instanceof IloConstraint) {
                    IloConstraint constraint = (IloConstraint) o;
                    constraintList.add(constraint);
                }
                else if (o instanceof IloNumVar) {
                    IloNumVar var = (IloNumVar) o;
                    if (var.getType() == IloNumVarType.Bool) {
                        continue;
                    }
                    constraintList.add(model.lowerBound(var));
                    constraintList.add(model.upperBound(var));
                }
            }
            IloConstraint[] constraints = constraintList.toArray(new IloConstraint[0]);
            double[] prefs = new double[constraints.length];
            Arrays.fill(prefs, 1);
            logger.warn("Cannot write iis file with cplex, output refined conflict to display");
            if (model.refineConflict(constraints, prefs)) {
                IloCplex.ConflictStatus[] status = model.getConflict(constraints);
                logger.info("Conflict:");
                for(int i = 0; i < constraints.length; i++) {
                    if (status[i] == IloCplex.ConflictStatus.Member) {
                        logger.info("Proved conflict member: " + constraints[i]);
                    }
                    else if (status[i] == IloCplex.ConflictStatus.PossibleMember){
                        logger.info("Possible conflict member: " + constraints[i]);
                    }
                }
            }
            else {
                logger.warn("Unable to refine conflict");
            }
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    @Override
    public double getValue(Variable variable) {
        throwForNonCplexVariable(variable);
        try {
            return model.getValue(((CplexVariable) variable).getVar());
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    @Override
    public double getDoubleAttribute(DoubleAttribute attribute) {
        try {
            switch (attribute) {
                case MIP_GAP:
                    return model.getMIPRelativeGap();
                case OBJ_VAL:
                    return model.getObjValue();
                default:
                    throw new SolverAttributeNotImplementedException(SolverType.CPLEX, attribute.name());
            }
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    @Override
    public void setIntParam(IntParam param, int value) {
        try {
            switch (param) {
                case TIMELIMIT:
                    if (value >= 0) {
                        model.setParam(IloCplex.Param.TimeLimit, value);
                    }
                    break;
                case OUTPUT_LEVEL:
                    if (value != LogLevel.DEBUG.intValue()) {
                        model.setOut(null);
                    }
                    break;
                default:
                    throw new SolverParamNotImplementedException(SolverType.GUROBI, param.name());
            }
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    @Override
    public void setDoubleParam(DoubleParam param, double value) {
        try {
            if (param == DoubleParam.MIP_GAP) {
                model.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, value);
            } else {
                throw new SolverParamNotImplementedException(SolverType.GUROBI, param.name());
            }
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    @Override
    public void dispose() {
        try {
            model.clearModel();
        } catch (IloException e) {
            throw new SolverCplexException(e.getMessage());
        }
    }

    @Override
    public OptimizationSense getSense() {
        if (sense == IloObjectiveSense.Maximize) {
            return OptimizationSense.MAXIMIZE;
        }
        else {
            return OptimizationSense.MINIMIZE;
        }
    }

    static void throwForNonCplexVariable(Variable var) {
        if(!(var instanceof CplexVariable)) {
            throw new LinTimException("Try to work with non cplex variable in cplex context");
        }
    }

    static void throwForNonCplexLinearExpression(LinearExpression expr) {
        if(!(expr instanceof CplexLinearExpression)) {
            throw new LinTimException("Try to work with non cplex expression in cplex context");
        }
    }
}
