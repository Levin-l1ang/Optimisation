/**
 *
 */
package net.lintim.dump;

import net.lintim.util.IterationProgressCounter;

/**
 * Writes program process into the console.
 *
 */
public class IterationProgressCounterDump implements IterationProgressCounter {

    Integer counter = 0;
    Integer totalNumberOfIterations;
    Integer stepwidth = 10;

    /**
     * Reports an iteration. Every nth iteration is written to console, where
     * n depends on the total number of iterations, which can be set by
     * {@link #setTotalNumberOfIterations(Integer)} and is chosen in a way that
     * there are between 10 and 20 reports in total at multiples of 2 and 5.
     * The the first and last iteration are reported as well.
     */
    @Override
    public void reportIteration(){
        counter++;
        if(counter == 1 || counter % stepwidth == 0 ||
                counter == totalNumberOfIterations){

            System.err.println("Iteration " + counter + " of "
                    + totalNumberOfIterations);
        }
    }

    /**
     * Sets the total number of iterations; has to be known in advance.
     */
    @Override
    public void setTotalNumberOfIterations(Integer totalNumberOfIterations) {
        this.totalNumberOfIterations = totalNumberOfIterations;
        stepwidth = 10;
        counter = 0;
        int tenthOfTotalIterations = totalNumberOfIterations/20;
        while(stepwidth < tenthOfTotalIterations){
            stepwidth*=5;
            if(stepwidth < tenthOfTotalIterations){
                stepwidth*=2;
            }
        }
    }

}
