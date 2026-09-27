package net.lintim.solver;


import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class representing a linear expression of variables.
 */
public interface LinearExpression {

    /**
     * Add the given linear expression to this expression.
     * @param otherExpression the expression to add.
     */
    void add(LinearExpression otherExpression);

    /**
     * Add the given variable to this expression.
     * @param variable the variable to add.
     */
    void add(Variable variable);

    /**
     * Add a multiple of the given linear expression to this expression.
     * @param multiple the multiple
     * @param otherExpression the expression to add
     */
    void multiAdd(double multiple, LinearExpression otherExpression);

    /**
     * Add a multiple of the given variable to this expression.
     * @param multiple the multiple
     * @param variable the variable to add
     */
    void multiAdd(double multiple, Variable variable);

    /**
     * Clear this expression.
     */
    void clear();

    /**
     * Add a constant to this expression
     * @param value the constant to add
     */
    void addConstant(double value);
}
