package net.lintim.util.impl;

import net.lintim.exception.LinTimException;
import net.lintim.util.Logger;
import net.lintim.util.MLModel;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.factory.Nd4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Arrays;

public class ANN1Model extends MLModel {
    private static final Logger logger = new Logger(ANN1Model.class.getCanonicalName());
    private static long predictionDurationSum = 0;
    private static long numberPredictions = 0;

    private MultiLayerNetwork model;

    public ANN1Model() {
    }

    public void readModel(String filename) {
        try {
            model = KerasModelImport.importKerasSequentialModelAndWeights(filename);
        } catch (IOException | InvalidKerasConfigurationException | UnsupportedKerasConfigurationException e) {
            throw new LinTimException("Unable to read model, error: " + e.getMessage());
        }
    }

    public double[] getResult(double[] input) {
        long start = System.currentTimeMillis();
        double[] result = model.output(Nd4j.create(new double[][]{input,})).toDoubleVector();
        long end = System.currentTimeMillis();
        long duration = end-start;
        predictionDurationSum += duration;
        numberPredictions += 1;
        logger.debug("Prediction took " + (duration) + ", avg " + (double)predictionDurationSum/numberPredictions);
        return result;
    }

    public double predict(double[] input) {
        double[] result = getResult(input);
        double returnValue = Arrays.stream(result).average().orElse(Double.NaN);
        logger.debug("Predicted " + Arrays.toString(result) + ", average " + returnValue);
        return returnValue;
    }
}
