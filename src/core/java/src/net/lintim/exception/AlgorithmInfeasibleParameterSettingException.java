package net.lintim.exception;

/**
 * Exception to throw if algorithm is started with infeasible parameter setting.
 */
public class AlgorithmInfeasibleParameterSettingException extends LinTimException {
    /**
     * Exception to throw if algorithm is started with infeasible parameter setting.
     *
     * @param algorithm       name of the algorithm
     * @param configKey       config key
     * @param configParameter config parameter
     */
    public AlgorithmInfeasibleParameterSettingException(String algorithm, String configKey, String configParameter) {
        super("Error A2: Algorithm " + algorithm + " cannot be run with parameter setting " + configKey + "; " +
            configParameter + ".");
    }
}
