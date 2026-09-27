package net.lintim.main.vehiclescheduling;

import net.lintim.algorithm.vehiclescheduling.*;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.ConfigReader;
import net.lintim.model.vehiclescheduling.LineConceptConverter;
import net.lintim.model.vehiclescheduling.LineGraph;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.vehiclescheduling.Parameters;

/**
 * Main class calculating a vehicle schedule.
 */
public class MakeVehicleSchedule {

    private static final Logger logger = new Logger(MakeVehicleSchedule.class);


    /**
     * Main method calculating a vehicle schedule with the parameters handed over
     *
     * @param args command-line arguments
     *             required parameters
     *             1 Dataset to calculate vehicle schedule on
     *             2 Model to be used. 2: (VS_W^2), 3: (VS_T^3), 4: (VS_T^4)
     *             3 Value of alpha
     */
    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }

        logger.info("Begin reading configuration");

        Config config = new ConfigReader.Builder(args[0]).build().read();
        Parameters parameters = new Parameters(config);

        logger.info("Finished reading configuration");

        Model vschedule;
        if (!parameters.shouldSolveLPRelax()) {
            if ((parameters.getModelType().equals("WasteCost") || parameters.getModelType().equals("EmptyRides"))
                || parameters.getModelType().equals("2")) {
                vschedule = new WasteCostProblem();
            } else {
                if ((parameters.getModelType().equals("alt") || parameters.getModelType().equals("alternative"))
                    || parameters.getModelType().equals("3")) {
                    vschedule = new TotalCostProblemAlt();
                } else {
                    vschedule = new TotalCostProblem();
                }
            }
        } else {
            if ((parameters.getModelType().equals("alt") || parameters.getModelType().equals("alternative"))
                || parameters.getModelType().equals("3")) {
                vschedule = new LPTotalCostProblemAlt();
            } else {
                vschedule = new LPTotalCostProblem();
            }
        }


        LineConceptConverter lineConcept = new LineConceptConverter();

        logger.info("Begin reading input data");
        LineGraph lineGraph = lineConcept.convertLineConceptToLineGraph(parameters.shouldRegardFrequencies());
        logger.info("Finished reading input data");

        logger.info("Begin computation of line based vehicle schedule");
        vschedule.makeVehicleSchedule(lineGraph, parameters);
        logger.info("Finished computation of line based vehicle schedule");
        logger.warn("Note that most of the line based algorithms do not produce a file output! Enable debug logging " +
            "to see the results of the computation");
    }


}
