package net.lintim.solver;

/**
 * Class implementing a generic variable. Create a variable by calling
 * {@link Model#addVariable(double, double, VariableType, double, String)}.
 */
public interface Variable {

    /**
     * Get the name of the variable.
     * @return the name
     */
    public String getName();

    /**
     * Get the type of the variable. For the possibilities, see {@link VariableType}.
     * @return the type of the variable
     */
    public VariableType getType();

    /**
     * Get the lower bound of the variable.
     * @return the lower bound
     */
    public double getLowerBound();

    /**
     * Get the upper bound of the variable.
     * @return the upper bound
     */
    public double getUpperBound();

    /**
     * Possible variable types.
     */
    public enum VariableType{
        INTEGER, CONTINUOUS, BINARY
    }
}
