package net.lintim.util;

import net.lintim.dump.IterationProgressCounterDump;

/**
 * Standardized interface for reporting progress, like writing text to console
 * with {@link IterationProgressCounterDump} or not reporting anything at all,
 * i.e. silencing by using {@link NullIterationProgressCounter}. However, it
 * may generally be used in graphical user interfaces as well.
 *
 */
public interface IterationProgressCounter {

    /**
     * Reports that another iteration has finished.
     * {@link #setTotalNumberOfIterations(Integer)} has to be run beforehand.
     */
    public void reportIteration();
    /**
     * Sets the total number of iterations, which has to be known it advance.
     *
     * @param totalNumberOfIterations
     */
    public void setTotalNumberOfIterations(Integer totalNumberOfIterations);

}
