package net.lintim.util.lineplanning;

/**
 * Class to describe a solution for the direct travelers model
 */
public class DirectSolutionDescriptor {
    private boolean isFeasible;
    private double objectiveValue;

    /**
     * Create a new descriptor with the given parameters
     *
     * @param isFeasible     whether the solution is feasible
     * @param objectiveValue the objective value of the solution. The objective value of an infeasible solution should
     *                       be {@link Double#NEGATIVE_INFINITY}.
     */
    public DirectSolutionDescriptor(boolean isFeasible, double objectiveValue) {
        this.isFeasible = isFeasible;
        this.objectiveValue = objectiveValue;
    }

    /**
     * Get the information whether the described solution is feasible
     *
     * @return whether the described solution is feasible
     */
    public boolean isFeasible() {
        return isFeasible;
    }

    /**
     * Get the objective value of the described solution
     *
     * @return the objective value
     */
    public double getObjectiveValue() {
        return objectiveValue;
    }
}
