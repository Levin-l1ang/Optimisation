package net.lintim.solver;

import net.lintim.util.LogLevel;
import net.lintim.util.SolverType;

/**
 * Class containing a generic optimization model. Use an instance of this class to build a model and then solve it using
 * {@link Solver#createSolver(SolverType)} and {@link Solver#createModel()}.
 */
public interface Model {

    /**
     * Return the underlying optimization model. This can be used to manipulate properties that are not covered by this
     * generic interface
     * @return the original model object, dependent on the solver used to initiate this model
     */
    Object getOriginalModel();

    /**
     * Create a new variable and add it to the model.
     * @param lowerBound the lower bound of the variable
     * @param upperBound the upper bound of the variable
     * @param type the type of the variable
     * @param objective the objective of the variable. Dependent on the solver, this may be set later on using
     *             {@link #setObjective(LinearExpression, OptimizationSense)} as well.
     * @param name the name of the variable. The variable will obtain a unique name, i.e., the name will be replaced
     *             if it is not unique in the model.
     * @return the newly created variable
     */
    Variable addVariable(double lowerBound, double upperBound, Variable.VariableType type, double objective, String name);

    /**
     * Create a new variable and add it to the model. The variable will not be part of the objective
     * @param lowerBound the lower bound of the variable
     * @param upperBound the upper bound of the variable
     * @param type the type of the variable
     * @param name the name of the variable. The variable will obtain a unique name, i.e., the name will be replaced
     *             if it is not unique in the model.
     * @return the newly created variable
     */
    default Variable addVariable(double lowerBound, double upperBound, Variable.VariableType type, String name) {
        return addVariable(lowerBound, upperBound, type, 0, name);
    }

    /**
     * Create a new variable and add it to the model. The lower bound will be set to 0, the upper bound to infinity.
     * The variable will not be part of the objective.
     * @param type the type of the variable
     * @param name the name of the variable. The variable will obtain a unique name, i.e., the name will be replaced
     *             if it is not unique in the model.
     * @return the newly created variable
     */
    default Variable addVariable(Variable.VariableType type, String name) {
        return addVariable(0, Double.POSITIVE_INFINITY, type, 0, name);
    }

    /**
     * Create a new variable and add it to the model. The lower bound will be set to 0, the upper bound to infinity.
     * @param type the type of the variable
     * @param objective the objective of the variable. Dependent on the solver, this may be set later on using
     *             {@link #setObjective(LinearExpression, OptimizationSense)} as well.
     * @param name the name of the variable. The variable will obtain a unique name, i.e., the name will be replaced
     *             if it is not unique in the model.
     * @return the newly created variable
     */
    default Variable addVariable(Variable.VariableType type, double objective, String name) {
        return addVariable(0, Double.POSITIVE_INFINITY, type, objective, name);
    }

    /**
     * Create a new variable and add it to the model. Will create a continuous variable. The variable will not be part
     * of the objective.
     * @param lowerBound the lower bound of the variable
     * @param upperBound the upper bound of the variable
     * @param objective the objective of the variable. Dependent on the solver, this may be set later on using
     *             {@link #setObjective(LinearExpression, OptimizationSense)} as well.
     * @param name the name of the variable. The variable will obtain a unique name, i.e., the name will be replaced
     *             if it is not unique in the model.
     * @return the newly created variable
     */
    default Variable addVariable(double lowerBound, double upperBound, double objective, String name) {
        return addVariable(lowerBound, upperBound, Variable.VariableType.CONTINUOUS, objective, name);
    }

    /**
     * Create a new variable and add it to the model. Will create a continuous variable.
     * @param lowerBound the lower bound of the variable
     * @param upperBound the upper bound of the variable
     * @param name the name of the variable. The variable will obtain a unique name, i.e., the name will be replaced
     *             if it is not unique in the model.
     * @return the newly created variable
     */
    default Variable addVariable(double lowerBound, double upperBound, String name) {
        return addVariable(lowerBound, upperBound, Variable.VariableType.CONTINUOUS, 0, name);
    }

    /**
     * Create a new variable and add it to the model. Will create a continuous variable. The lower bound will be set to
     * 0, the upper bound to infinity
     * @param objective the objective of the variable. Dependent on the solver, this may be set later on using
     *             {@link #setObjective(LinearExpression, OptimizationSense)} as well.
     * @param name the name of the variable. The variable will obtain a unique name, i.e., the name will be replaced
     *             if it is not unique in the model.
     * @return the newly created variable
     */
    default Variable addVariable(double objective, String name) {
        return addVariable(0, Double.POSITIVE_INFINITY, Variable.VariableType.CONTINUOUS, objective, name);
    }

    /**
     * Create a new variable and add it to the model. Will create a continuous variable. The lower bound will be set to
     * 0, the upper bound to infinity. The variable will not be in the objective.
     * @param name the name of the variable. The variable will obtain a unique name, i.e., the name will be replaced
     *             if it is not unique in the model.
     * @return the newly created variable
     */
    default Variable addVariable(String name) {
        return addVariable(0, Double.POSITIVE_INFINITY, Variable.VariableType.CONTINUOUS, 0, name);
    }

    /**
     * Create a new constraint and add it to the model
     * @param lhs the left hand side expression of the constraint. To create an expression, use
     * {@link #createExpression()}.
     * @param sense the sense of the constraint
     * @param rhs the right hand side constant of the constraint
     * @param name the name of the constraint
     * @return the newly created constraint
     */
    Constraint addConstraint(LinearExpression lhs, Constraint.ConstraintSense sense, double rhs, String name);

    default Constraint addConstraint(LinearExpression lhs, Constraint.ConstraintSense sense, LinearExpression rhs, String name) {
        LinearExpression newLhs = createExpression();
        newLhs.add(lhs);
        newLhs.multiAdd(-1, rhs);
        return addConstraint(newLhs, sense, 0, name);
    }

    default Constraint addConstraint(LinearExpression lhs, Constraint.ConstraintSense sense, Variable rhs, String name) {
        LinearExpression newLhs = createExpression();
        newLhs.add(lhs);
        newLhs.multiAdd(-1, rhs);
        return addConstraint(newLhs, sense, 0, name);
    }

    default Constraint addConstraint(Variable lhs, Constraint.ConstraintSense sense, double rhs, String name) {
        LinearExpression newLhs = createExpression();
        newLhs.add(lhs);
        return addConstraint(newLhs, sense, rhs, name);
    }

    default Constraint addConstraint(Variable lhs, Constraint.ConstraintSense sense, Variable rhs, String name) {
        LinearExpression newLhs = createExpression();
        newLhs.add(lhs);
        newLhs.multiAdd(-1, rhs);
        return addConstraint(newLhs, sense, 0, name);
    }

    default Constraint addConstraint(Variable lhs, Constraint.ConstraintSense sense, LinearExpression rhs, String name) {
        return addConstraint(rhs, Constraint.ConstraintSense.flipSense(sense), lhs, name);
    }
    default Constraint addConstraint(double lhs, Constraint.ConstraintSense sense, Variable rhs, String name) {
        return addConstraint(rhs, Constraint.ConstraintSense.flipSense(sense), lhs, name);
    }
    default Constraint addConstraint(double lhs, Constraint.ConstraintSense sense, LinearExpression rhs, String name) {
        return addConstraint(rhs, Constraint.ConstraintSense.flipSense(sense), lhs, name);
    }




    /**
     * Get a variable by its name.
     * @param name the name of the variable
     * @return the variable with the given name
     */
    Variable getVariableByName(String name);


    /**
     * Set a starting value for the given variable.
     * @param variable the variable to set a starting value for
     * @param value the starting value to set
     */
    void setStartValue(Variable variable, double value);

    /**
     * Set the given objective with the given sense for the model.
     * @param objective the objective to set
     * @param sense the sense of the objective
     */
    void setObjective(LinearExpression objective, OptimizationSense sense);

    default void setObjective(Variable variable, OptimizationSense sense) {
        LinearExpression expr = createExpression();
        expr.add(variable);
        setObjective(expr, sense);
    };

    default void setObjective(double constant, OptimizationSense sense) {
        LinearExpression expr = createExpression();
        expr.addConstant(constant);
        setObjective(expr, sense);
    };

    /**
     * Get the current objective function of the model
     * @return the objective
     */
    LinearExpression getObjective();

    /**
     * Get the objective function value of the current solution. Can only be called on solvers with status OPTIMAL or
     * FEASIBLE and a feasible solution is present.
     * @return the objective function value
     */
    double getObjectiveValue();

    /**
     * Create a new expression
     * @return the newly created expression
     */
    LinearExpression createExpression();

    /**
     * Get the sense of the objective function, i.e. {@link OptimizationSense#MINIMIZE} or
     * {@link OptimizationSense#MAXIMIZE}.
     * @return the sense of the objective function
     */
    OptimizationSense getSense();

    /**
     * Write the model to the given file.
     * @param filename the file to write to.
     */
    void write(String filename);

    /**
     * Set the optimization sense of this model.
     * @param sense the new sense
     */
    void setSense(OptimizationSense sense);

    /**
     * Possible optimization senses. Can be set using {@link #setSense(OptimizationSense)} or
     * {@link #setObjective(LinearExpression, OptimizationSense)}.
     */
    enum OptimizationSense {
        MINIMIZE, MAXIMIZE
    }

    /**
     * Different attributes of the model that can be queried using {@link #getIntAttribute(IntAttribute)}.
     *
     * The attributes have the following meaning:
     * <ul>
     *     <li>NUM_VARIABLES: The number of variables in the model</li>
     *     <li>NUM_CONSTRAINTS: The number of constraints in the model</li>
     *     <li>NUM_INT_VARIABLES: The number of integer variables in the model</li>
     *     <li>NUM_BIN_VARIABLES: The number of binary variables in the model</li>
     *     <li>NUM_SOLUTIONS: The number of solutions found. Note that this may throw an error when queried before the
     *         optimization</li>
     * </ul>
     */
    enum IntAttribute {
        /**
         * The number of variables in the model
         */
        NUM_VARIABLES,
        /**
         * The number of constraints in the model
         */
        NUM_CONSTRAINTS,
        /**
         * The number of integer variables in the model
         */
        NUM_INT_VARIABLES,
        /**
         * The number of binary variables in the model
         */
        NUM_BIN_VARIABLES,
        /**
         * The number of feasible solutions
         */
        NUM_SOLUTIONS
    }

    /**
     * Get the value of an int attribute for the model. For a description of the possible attributes, see
     * {@link IntAttribute}.
     * @param attribute the attribute to query
     * @return the value of the attribute
     */
    int getIntAttribute(IntAttribute attribute);

    /**
     * Get the status of the optimization problem. Note that this needs to be called after solving the problem. For
     * possible values, see {@link Status}.
     * @return the current status
     */
    Status getStatus();

    /**
     * Solve the current model with the given settings.
     */
    void solve();

    /**
     * Compute an IIS for the model and store it in the given file. The file ending depends on the used solver.
     * @param fileName the file name to use, without a file ending.
     */
    void computeIIS(String fileName);

    /**
     * Get the value of the given variable. Can only be queried, if there is a feasible solution, i.e., if
     * {@link #getStatus()} is {@link Status#OPTIMAL} or {@link Status#FEASIBLE}. Otherwise, the method will throw!
     * @param variable the variable to query.
     * @return the current value of the variable.
     */
    double getValue(Variable variable);

    /**
     * Get the value of the given attribute. For a list of attributes, see {@link Model.DoubleAttribute}.
     * @param attribute the attribute to query.
     * @return the value of the given attribute
     */
    double getDoubleAttribute(DoubleAttribute attribute);

    /**
     * Set the given int parameter. For a list of possible parameters, see {@link Model.IntParam}.
     * @param param the parameter to set
     * @param value the value to set the parameter to.
     */
    void setIntParam(IntParam param, int value);

    /**
     * Set the given double parameter. For a list of possible parameters, see {@link Model.DoubleParam}.
     * @param param the parameter to set
     * @param value the value to set the parameter to.
     */
    void setDoubleParam(DoubleParam param, double value);

    void dispose();

    /**
     * Possible status values for the model.
     */
    enum Status {
        /**
         * Represents that an optimal solution could be found.
         */
        OPTIMAL,
        /**
         * Represents that no feasible solution was found. Does not necessarily mean that the problem is infeasible.
         */
        INFEASIBLE,
        /**
         * Represents that a feasible, but no optimal solution, was found. Probably due to hitting the timelimit.
         */
        FEASIBLE,
    }


    /**
     * Possible double parameters to set.
     */
    enum DoubleParam {
        /**
         * The mip gap to achieve. A mip gap of 0.1 represents 10%.
         */
        MIP_GAP,
    }

    /**
     * Possible integer parameters to set.
     */
    enum IntParam {
        /**
         * The timelimit to set. A negative values is interpreted as no timelimit at all, positive integers
         * represent the number of seconds  allowed for solving.
         */
        TIMELIMIT,
        /**
         * The desired output level of the solver. Use the values of {@link LogLevel}.
         */
        OUTPUT_LEVEL,
        /**
         * The number of threads the solver is allowed to use. Use -1 for no restriction.
         */
        THREAD_LIMIT,
    }

    /**
     * All possible double attributes that can be queried from a model after calling {@link #solve()}.
     */
    enum DoubleAttribute {
        /**
         * The objective value of the current solution. Can only be called on solvers with status
         * {@link Status#OPTIMAL} or {@link Status#FEASIBLE} and a feasible solution is present.
         */
        OBJ_VAL,
        /**
         * The mip gap of the current solution. Can only be called on solvers with status
         * {@link Status#OPTIMAL} or {@link Status#FEASIBLE} and a feasible solution is present.
         */
        MIP_GAP,
        /**
         * The runtime needed to solve the problem. Note that this may throw an error when queried before the
         * optimization
         */
        RUNTIME,
    }


}
