package net.lintim.util.impl;

import net.lintim.exception.LinTimException;
import net.lintim.util.Logger;
import net.lintim.util.MLModel;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.factory.Nd4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Arrays;

public class APIModel extends MLModel {
    private static final Logger logger = new Logger(APIModel.class.getCanonicalName());
    private final int apiPort;
    private static long predictionDurationSum = 0;
    private static long numberPredictions = 0;

    public APIModel(int svrPort) {
        this.apiPort = svrPort;
    }

    public void readModel(String filename) {
        // don't need to read the api models
    }

    public double[] getResult(double[] input) {
        // Get the result from the api
        try {
            long start = System.currentTimeMillis();
            Socket socket = new Socket("localhost", apiPort);
            PrintStream out = new PrintStream(socket.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String inputString = String.join("; ", Arrays.stream(input).mapToObj(Double::toString).toArray(String[]::new));
            out.println(inputString);
            String resultString = in.readLine();
            if (resultString == null) {
                throw new LinTimException("Got no result string from python3 svr evaluation!");
            }
            socket.close();
            String[] values = resultString.split(",");
            double[] result = new double[values.length];
            for (int i = 0; i < values.length; i++) {
                result[i] = Double.parseDouble(values[i]);
            }
            long end = System.currentTimeMillis();
            long duration = end-start;
            predictionDurationSum += duration;
            numberPredictions += 1;
            logger.debug("Prediction took " + (duration) + ", avg " + (double)predictionDurationSum/numberPredictions);
            return result;
        }
        catch (IOException e) {
            throw new LinTimException("Got exception when calling python for svr evaluation: " + e.getMessage());
        }
    }

    public double predict(double[] input) {
        double[] result = getResult(input);
        double returnValue = Arrays.stream(result).average().orElse(Double.NaN);
        logger.debug("Predicted " + Arrays.toString(result) + ", average " + returnValue);
        return returnValue;
    }
}
