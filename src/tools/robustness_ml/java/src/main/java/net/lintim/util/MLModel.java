package net.lintim.util;

import net.lintim.util.impl.ANN1Model;
import net.lintim.util.impl.ANN4Model;
import net.lintim.util.impl.APIModel;

public abstract class MLModel {

    public static MLModel supplyMLModel(Parameters parameters) {
        if (parameters.shouldUseSVR()) {
            return new APIModel(parameters.getSvrPort());
        }
        if (parameters.useSingleANNModels()) {
            return new ANN4Model();
        }
        return new ANN1Model();
    }

    public abstract void readModel(String filename);

    public abstract double[] getResult(double[] input);

    public abstract double predict(double[] input);

}
