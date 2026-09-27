package net.lintim.exception;

/**
 * Exception to throw if an algorithm terminates before finding a feasible/optimal solution.
 */
public class AlgorithmStoppingCriterionException extends LinTimException {
    /**
     * Exception to throw if an algorithm terminates before finding a feasible/optimal solution.
     *
     * @param algorithm name of the algorithm
     */
    public AlgorithmStoppingCriterionException(String algorithm) {
        super("Error A1: Stopping criterion of algorithm " + algorithm + " reached without finding a feasible/optimal" +
            " solution.");
    }
}
