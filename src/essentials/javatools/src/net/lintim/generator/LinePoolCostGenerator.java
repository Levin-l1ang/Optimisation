package net.lintim.generator;

import net.lintim.model.Line;
import net.lintim.model.LineCollection;

/**
 * Generates costs for the lines in the line pool.
 */
public class LinePoolCostGenerator {

    protected LineCollection lpool;

    protected Double fixedCostPerLine;

    /**
     * Constructor.
     *
     * @param lpool The line pool.
     * @param fixedCostPerLine Fixed cost per line frequency instance.
     */
    public LinePoolCostGenerator(LineCollection lpool, Double fixedCostPerLine) {
        this.lpool = lpool;
        this.fixedCostPerLine = fixedCostPerLine;
    }

    /**
     * Performs the actual computation.
     */
    public void computeCosts(){
        for(Line line : lpool.getDirectedLines()){
            line.setCost(line.getLength() + fixedCostPerLine);
        }
    }


}
