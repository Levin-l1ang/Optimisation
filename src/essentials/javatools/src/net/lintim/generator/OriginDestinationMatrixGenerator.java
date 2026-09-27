package net.lintim.generator;

import net.lintim.evaluator.OriginDestinationMatrixEvaluator;
import net.lintim.model.OriginDestinationMatrix;
import net.lintim.model.Station;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tools to derive new origin destination matrices.
 *
 */
public class OriginDestinationMatrixGenerator {

    /**
     * Multiplies every entry of <code>od</code> by a random number within
     * <code>[1-noiseLevel, 1+noiseLevel]</code>.
     *
     * @param od
     * @param noiseLevel
     */
    public static void generateNoisyMatrix(OriginDestinationMatrix od, Double noiseLevel){
        for (LinkedHashMap<Station, Double> e1 : od.getMatrix().values()) {
            for(Map.Entry<Station, Double> e2 : e1.entrySet()){
                e2.setValue(e2.getValue()*(1.0-(Math.random()*2.0-1.0)*noiseLevel));
            }
        }
    }

    /**
     * Multiplies every entry of <code>od</code> by
     * <code>nominalOverallSum/OriginDestinationMatrixEvaluator.overallSum(od)</code>,
     * i.e. at the end the sum over all entries of <code>od</code> is
     * <code>nominalOverallSum</code>
     *
     * @param od
     * @param nominalOverallSum
     */
    public static void rescale(OriginDestinationMatrix od, Double nominalOverallSum){

        Double scale = nominalOverallSum/OriginDestinationMatrixEvaluator.overallSum(od);

        for (LinkedHashMap<Station, Double> e1 : od.getMatrix().values()) {
            for(Map.Entry<Station, Double> e2 : e1.entrySet()){
                e2.setValue(e2.getValue()*scale);
            }
        }

    }

    /**
     * Rounds the every entry of <code>od</code> to the closest integer.
     *
     * @param od
     */
    public static void roundEntries(OriginDestinationMatrix od){

        for (LinkedHashMap<Station, Double> e1 : od.getMatrix().values()) {
            for(Map.Entry<Station, Double> e2 : e1.entrySet()){
                e2.setValue((double)Math.round(e2.getValue()));
            }
        }

    }

}
