package net.lintim.solver.impl;

import net.lintim.exception.*;
import net.lintim.io.LPWriter;
import net.lintim.solver.Constraint;
import net.lintim.solver.LinearExpression;
import net.lintim.solver.Model;
import net.lintim.solver.Variable;
import net.lintim.util.LogLevel;
import net.lintim.util.Logger;
import net.lintim.util.SolverType;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GLPKModel implements Model {

    private static final Logger logger = new Logger(GLPKModel.class);

    private final HashMap<String, NativeVariable> variables;
    private final HashMap<String, NativeConstraint<NativeVariable>> constraints;
    private NativeLinearExpression<NativeVariable> objectiveFct;
    private OptimizationSense optSense;
    private final HashMap<DoubleParam, Double> doubleParams;
    private final HashMap<IntParam, Integer> intParams;
    private Status status = Status.INFEASIBLE;
    private double objectiveValue = Double.NEGATIVE_INFINITY;
    private final HashMap<NativeVariable, Double> solutionValues;

    private final static String GLPK_SOLUTION_COMMENT_HEADER = "c";
    private final static String GLPK_SOLUTION_INFO_HEADER = "s";
    private final static int GLPK_SOLUTION_INFO_STATUS_INDEX = 4;
    private final static String GLPK_SOLUTION_INFO_STATUS_OPTION_FEASIBLE = "f";
    private final static String GLPK_SOLUTION_INFO_STATUS_OPTION_OPTIMAL = "o";
    private final static String GLPK_SOLUTION_INFO_STATUS_OPTION_INFEASIBLE = "n";
    private final static int GLPK_SOLUTION_INFO_GAP_INDEX = 5;
    private final static String GLPK_SOLUTION_COLUMN_HEADER = "j";
    private final static int GLPK_SOLUTION_COLUMN_INDEX_INDEX = 1;
    private final static int GLPK_SOLUTION_COLUMN_VALUE_INDEX = 2;

    public GLPKModel() {
        this.variables = new HashMap<>();
        this.constraints = new HashMap<>();
        this.objectiveFct = new NativeLinearExpression<>();
        this.doubleParams = new HashMap<>();
        this.intParams = new HashMap<>();
        solutionValues = new HashMap<>();
    }

    @Override
    public void dispose() {
        variables.clear();
        constraints.clear();
        objectiveFct.clear();
        doubleParams.clear();
        intParams.clear();
        solutionValues.clear();
    }

    @Override
    public Object getOriginalModel() {
        throw new SolverAttributeNotImplementedException(SolverType.GLPK, "getOriginalModel");
    }

    @Override
    public Variable addVariable(double lowerBound, double upperBound, Variable.VariableType type, double objective, String name) {
        if (variables.containsKey(name)) {
            String newName = name + "1";
            logger.warn("Try to add a variable with name " + name + ", but a variable (" + variables.get(name) + " with this name is already present! Trying again with " + newName);
            return addVariable(lowerBound, upperBound, type, objective, newName);
        }
        NativeVariable variable = new NativeVariable(name, type, lowerBound, upperBound);
        variables.put(name, variable);
        if (objective != 0) {
            objectiveFct.multiAdd(objective, variable);
        }
        return variable;
    }

    @Override
    public Constraint addConstraint(LinearExpression expression, Constraint.ConstraintSense sense, double rhs, String name) {
        if (constraints.containsKey(name)) {
            String newName = name + "1";
            logger.warn("Try to add a constraint with name " + name + ", but a constraint (" + constraints + ") with" +
                "this name is already present. Trying with " + newName);
            return addConstraint(expression, sense, rhs, newName);
        }
        NativeConstraint<NativeVariable> constraint;
        try {
            constraint = new NativeConstraint<>((NativeLinearExpression<NativeVariable>) expression, rhs, sense, name);
        } catch (ClassCastException e) {
            // Since the type information are gone by runtime, there is no nice way to know whether expression
            // has the correct type
            throw new LinTimException("Try to work with unfitting or non-NativeLinearExpression in GLPK context");
        }
        constraints.put(name, constraint);
        return constraint;
    }

    @Override
    public Variable getVariableByName(String name) {
        return variables.get(name);
    }

    @Override
    public void setStartValue(Variable variable, double value) {
        throw new SolverAttributeNotImplementedException(SolverType.GLPK, "setStartValue");
    }

    @Override
    public void setObjective(LinearExpression objective, OptimizationSense sense) {
        try {
            this.objectiveFct = (NativeLinearExpression<NativeVariable>) objective;
        } catch (ClassCastException e) {
            // Since the type information are gone by runtime, there is no nice way to know whether objective
            // has the correct type
            throw new LinTimException("Try to work with unfitting or non-NativeLinearExpression in GLPK context");
        }
        this.optSense = sense;
    }

    @Override
    public LinearExpression getObjective() {
        return objectiveFct;
    }

    @Override
    public double getObjectiveValue() {
        return getDoubleAttribute(DoubleAttribute.OBJ_VAL);
    };

    @Override
    public LinearExpression createExpression() {
        return new NativeLinearExpression<NativeVariable>();
    }

    @Override
    public OptimizationSense getSense() {
        return optSense;
    }

    @Override
    public void write(String filename) {
        new LPWriter.Builder(variables.values(), constraints.values(), filename).setObjective(objectiveFct).setSense(optSense).build().write();
    }

    @Override
    public void setSense(OptimizationSense sense) {
        this.optSense = sense;
    }

    @Override
    public int getIntAttribute(IntAttribute attribute) {
        switch (attribute) {
            case NUM_VARIABLES:
                return variables.size();
            case NUM_CONSTRAINTS:
                return constraints.size();
            case NUM_BIN_VARIABLES:
                return (int) variables.values().stream().filter(v -> v.getType() == Variable.VariableType.BINARY).count();
            case NUM_INT_VARIABLES:
                return (int) variables.values().stream().filter(v -> v.getType() == Variable.VariableType.INTEGER).count();
            case NUM_SOLUTIONS:
                return getStatus() == Status.FEASIBLE || getStatus() == Status.OPTIMAL ? 1 : 0;
            default:
                throw new SolverAttributeNotImplementedException(SolverType.GLPK, attribute.name());
        }
    }

    @Override
    public void solve() {
        // Write LP file to temporary file
        String tempModelFile = "temp_lintim_core_glpk.lp";
        String tempSolutionFile = "temp_lintim_core_glpk.sol";
        List<Variable> listOfVariables = new LPWriter.Builder(variables.values(), constraints.values(), tempModelFile)
            .setObjective(objectiveFct).setSense(optSense).build().write();
        // Call glpsol
        // First, build the command to run
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("glpsol");
        commandList.add("--write");
        commandList.add(tempSolutionFile);
        commandList.add("--lp");
        commandList.add(tempModelFile);
        for (Map.Entry<IntParam, Integer> paramEntry: intParams.entrySet()) {
            switch (paramEntry.getKey()) {
                case THREAD_LIMIT:
                    if (paramEntry.getValue() > 0) {
                        throw new SolverParamNotImplementedException(SolverType.GLPK, paramEntry.getKey().name());
                    }
                    break;
                case TIMELIMIT:
                    if (paramEntry.getValue() > 0) {
                        commandList.add("--tmlim");
                        commandList.add(String.valueOf(paramEntry.getValue()));
                    }
                    break;
                case OUTPUT_LEVEL:
                    break;
                default:
                    throw new SolverParamNotImplementedException(SolverType.GLPK, paramEntry.getKey().name());
            }
        }
        for (Map.Entry<DoubleParam, Double> paramEntry: doubleParams.entrySet()) {
            switch (paramEntry.getKey()) {
                case MIP_GAP:
                    commandList.add("--mipgap");
                    commandList.add(String.valueOf(paramEntry.getValue()));
                    break;
                default:
                    throw new SolverParamNotImplementedException(SolverType.GLPK, paramEntry.getKey().name());
            }
        }
        if (intParams.containsKey(IntParam.OUTPUT_LEVEL) && intParams.get(IntParam.OUTPUT_LEVEL) != LogLevel.DEBUG.intValue()) {
//            commandList.add(">");
//            commandList.add("/dev/null");
        }
        logger.debug("Try to run command " + commandList);
        ProcessBuilder processBuilder = new ProcessBuilder(commandList);
        int returnValue = -1;
        try {
            Process process = processBuilder.start();
            InputStream streamToShow;
            if (intParams.containsKey(IntParam.OUTPUT_LEVEL) && intParams.get(IntParam.OUTPUT_LEVEL) == LogLevel.DEBUG.intValue()) {
                processBuilder.redirectErrorStream(true);
                streamToShow = process.getInputStream();
            }
            else {
                streamToShow = process.getErrorStream();
            }
            BufferedReader outputReader = new BufferedReader(new InputStreamReader(streamToShow));
            String line;
            while ((line = outputReader.readLine()) != null) {
                System.out.println(line);
            }
            returnValue = process.waitFor();
        } catch (IOException | InterruptedException e) {
            logger.warn("Unable to process glpk process output: " + e.getMessage());
            logger.warn("Will try to read back solution but this may fail");
        }
        if (returnValue != 0) {
            logger.warn("Could not solve problem, GLPK returned " + returnValue);
        }
        solutionValues.clear();
        try {
            BufferedReader solutionReader = new BufferedReader(new FileReader(Paths.get(tempSolutionFile).toString()));
            String line;
            String[] values;
            int varIndex;
            double varValue;
            Variable temp;
            while ((line = solutionReader.readLine()) != null) {
                if (line.startsWith(GLPK_SOLUTION_COMMENT_HEADER)) {
                    continue;
                }
                if (line.startsWith(GLPK_SOLUTION_INFO_HEADER)) {
                    // Status of the problem
                    values = line.split(" ");
                    switch (values[GLPK_SOLUTION_INFO_STATUS_INDEX]) {
                        case GLPK_SOLUTION_INFO_STATUS_OPTION_OPTIMAL:
                            status = Status.OPTIMAL;
                            break;
                        case GLPK_SOLUTION_INFO_STATUS_OPTION_FEASIBLE:
                            status = Status.FEASIBLE;
                            break;
                        case GLPK_SOLUTION_INFO_STATUS_OPTION_INFEASIBLE:
                            status = Status.INFEASIBLE;
                            break;
                        default:
                            logger.warn("Cannot process glpk solution status, was " + values[GLPK_SOLUTION_INFO_STATUS_INDEX]);
                    }
                    objectiveValue = Double.parseDouble(values[GLPK_SOLUTION_INFO_GAP_INDEX]);
                }
                if (line.startsWith(GLPK_SOLUTION_COLUMN_HEADER)) {
                    values = line.split(" ");
                    varIndex = Integer.parseInt(values[GLPK_SOLUTION_COLUMN_INDEX_INDEX]) - 1;
                    varValue = Double.parseDouble(values[GLPK_SOLUTION_COLUMN_VALUE_INDEX]);
                    temp = listOfVariables.get(varIndex);
                    try {
                        solutionValues.put((NativeVariable) temp, varValue);
                    } catch (ClassCastException e) {
                        throw new LinTimException("Got the wrong return type of variables in GLPK Solver");
                    }

                }
            }
        } catch (IOException e) {
            logger.warn("Unable to read back solution from glpk: " + e.getMessage());
        }

        try {
            // Remove the temporary files
            Files.delete(Paths.get(tempModelFile));
            Files.delete(Paths.get(tempSolutionFile));
        } catch (IOException e) {
            logger.warn("Unable to delete temporary files: " + e.getMessage());
        }

    }


    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public void computeIIS(String fileName) {
        logger.warn("Computing an IIS is not implemented for GLPK, skip");
    }

    @Override
    public double getValue(Variable variable) {
        throwForNonGLPKVariable(variable);
        return solutionValues.get(variable);
    }

    @Override
    public double getDoubleAttribute(DoubleAttribute attribute) {
        switch (attribute) {
            case OBJ_VAL:
                return objectiveValue;
            case MIP_GAP:
                // Not yet implemented
            default:
                throw new SolverAttributeNotImplementedException(SolverType.GLPK, attribute.name());
        }
    }

    @Override
    public void setIntParam(IntParam param, int value) {
        intParams.put(param, value);
    }

    @Override
    public void setDoubleParam(DoubleParam param, double value) {
        doubleParams.put(param, value);
    }


    static void throwForNonGLPKVariable(Variable var) {
        if(!(var instanceof NativeVariable)) {
            throw new LinTimException("Try to work with non glpk variable in glpk context");
        }
    }
}
