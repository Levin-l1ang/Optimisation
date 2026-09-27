package net.lintim.util;

/**
 * A trivial implementation of the {@link IterationProgressCounter}, which does
 * nothing. May be used to not report progress at all, i.e. silence.
 *
 */
public class NullIterationProgressCounter implements IterationProgressCounter {

    @Override
    public void reportIteration(){
        // do nothing
    }

    @Override
    public void setTotalNumberOfIterations(Integer totalNumberOfIterations){
        // do nothing
    }

}
