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

public class ANN4Model extends MLModel {
    private static final Logger logger = new Logger(ANN4Model.class.getCanonicalName());
    private static long predictionDurationSum = 0;
    private static long numberPredictions = 0;
    private static final int NUMBER_OF_MODELS = 4;

    private MultiLayerNetwork[] models;

    public ANN4Model() {
    }

    public void readModel(String filename) {
        try {
            String[] tokens = filename.split("\\.(?=[^\\.]+$)");
            models = new MultiLayerNetwork[NUMBER_OF_MODELS];
            for (int i = 0; i < NUMBER_OF_MODELS; i++) {
                models[i] = KerasModelImport.importKerasSequentialModelAndWeights(tokens[0] + "_" + (i+1) + "." + tokens[1]);
            }
        } catch (IOException | InvalidKerasConfigurationException | UnsupportedKerasConfigurationException e) {
            throw new LinTimException("Unable to read model, error: " + e.getMessage());
        }
    }

    public double[] getResult(double[] input) {
        long start = System.currentTimeMillis();
        double[] result = new double[NUMBER_OF_MODELS];
        for (int i = 0; i < NUMBER_OF_MODELS; i++) {
            double[] temp = models[i].output(Nd4j.create(new double[][]{input,})).toDoubleVector();
            if (temp.length > 1) {
                throw new LinTimException("Got invalid result length " + temp.length);
            }
            result[i] = temp[0];
        }
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
