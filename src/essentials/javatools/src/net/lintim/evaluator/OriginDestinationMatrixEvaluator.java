package net.lintim.evaluator;

import net.lintim.model.OriginDestinationMatrix;
import net.lintim.model.Station;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Evaluates different properties of an {@link OriginDestinationMatrix}.
 */
public class OriginDestinationMatrixEvaluator {
    /**
     * Computes the sum over all entries for a given
     * {@link OriginDestinationMatrix}.
     * @param od the given {@link OriginDestinationMatrix}.
     * @return the sum over all entries.
     */
    public static double overallSum(OriginDestinationMatrix od){
        double retval = 0.0;
        for(Map.Entry<Station, LinkedHashMap<Station, Double>> e1 :
            od.getMatrix().entrySet()){

            for(Map.Entry<Station, Double> e2 : e1.getValue().entrySet()){
                retval += e2.getValue();
            }
        }
        return retval;
    }

    /**
     * Computes the number of entries greater zero
     * for a given {@link OriginDestinationMatrix}.
     * @param od the given {@link OriginDestinationMatrix}.
     * @return the sum over all entries.
     */
    public static int entriesGreaterZero(OriginDestinationMatrix od){
        int retval = 0;
        for(Map.Entry<Station, LinkedHashMap<Station, Double>> e1 :
            od.getMatrix().entrySet()){

            for(Map.Entry<Station, Double> e2 : e1.getValue().entrySet()){
                if(e2.getValue() > 0){
                    retval++;
                }
            }
        }
        return retval;
    }


    /**
     * Computes an array that contains the ordered entries of a given
     * {@link OriginDestinationMatrix}.
     * @param od the given {@link OriginDestinationMatrix}.
     * @return the ordered entries; starts with the greatest and ends with the
     * least.
     */
    public static double[] orderedEntries(OriginDestinationMatrix od) {
        int stationsNumber = od.getPublicTransportationNetwork().getStations().size();
        int allValuesNumber = stationsNumber*stationsNumber;
        double[] values = new double[allValuesNumber];
        {
            int i = 0;
            for(Map.Entry<Station, LinkedHashMap<Station, Double>> e1 :
                od.getMatrix().entrySet()){

                int j = 0;
                for(Map.Entry<Station, Double> e2 : e1.getValue().entrySet()){
                    values[stationsNumber*i+j] = e2.getValue();
                    j++;
                }

                i++;
            }
        }
        Arrays.sort(values);

        return values;
    }

}
