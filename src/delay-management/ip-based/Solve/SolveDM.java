import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.exception.LinTimException;
import net.lintim.io.ConfigReader;
import net.lintim.io.StatisticWriter;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.Statistic;

import java.io.*;


public class SolveDM {

    private static final Logger logger = new Logger(SolveDM.class);

    private SolveDM() {
    }  // class only contains static methods


    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }

        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        Parameters parameters = new Parameters(config);
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        NonPeriodicEANetwork Net = IO.readNonPeriodicEANetwork(true, false);
        logger.info("Finished reading input data");

        switch (parameters.getMethod()) {
            case "DM2":
                DM.solveDM2(Net, false, parameters);
                break;
            case "propagate":
                Propagator.propagate(Net, parameters.getMaxWait(), parameters.shouldSwapHeadways());
                break;
            case "DM2-pre":
                DM.solveDM2(Net, true, parameters);
                break;
            case "FSFS":
                DM.solveFSFS(Net, parameters);
                break;
            case "FRFS":
                DM.solveFRFS(Net, parameters);
                break;
            case "EARLYFIX":
                DM.solveEARLYFIX(Net, parameters);
                break;
            case "PRIORITY":
                DM.solvePRIORITY(Net, parameters);
                break;
            case "PRIOREPAIR":
                DM.solvePRIOREPAIR(Net, parameters.getPercentage(), parameters);
                break;
            case "DM1":
                DM.solveDM1(Net, parameters);
                break;
            case "PASSENGERPRIOFIX":
                DM.solvePASSENGERPRIOFIX(Net, parameters);
                break;
            case "FIXFSFS":
                DM.solveFIXFSFS(Net, parameters);
                break;
            case "FIXFRFS":
                DM.solveFIXFRFS(Net, parameters);
                break;
            case "best-of-all":
                // Note that FSFS is always at least as good as PRIORITY and
                // that FRFS is always as good as EARLYFIX, see Diss. Schachtebeck,
                // Lemma 4.5 and Lemma 4.6; hence, we do not have to run
                // EARLYFIX and PRIORITY.

                //For evaluation of every step
                Statistic bestOfAllObjectives = new Statistic();


                int best = 1;
                logger.debug("\nrunning FSFS\n");
                double minimum = DM.solveFSFS(Net, parameters);
                bestOfAllObjectives.put("dm_FSFS_objective", minimum);


                logger.debug("\nrunning FRFS\n");
                double FRFS = DM.solveFRFS(Net, parameters);
                bestOfAllObjectives.put("dm_FRFS_objective", FRFS);

                if (FRFS <= minimum) {
                    minimum = FRFS;
                    best = 2;
                }

                // use PRIOREPAIR heuristics with 11 different values
                // 0, 10, 20, ..., 100 for the importance factor
                for (int k = 3; k <= 13; k++) {
                    int priority = (k - 3) * 10;
                    logger.debug("\nrunning PRIOREPAIR-" + priority + "\n");
                    double PRIORITY = DM.solvePRIOREPAIR(Net, priority, parameters);
                    bestOfAllObjectives.put("dm_PRIOREPAIR-" + priority + "_objective", PRIORITY);

                    if (PRIORITY <= minimum) {
                        minimum = PRIORITY;
                        best = k;
                    }
                }


                // depending on which heuristic was the best one,
                // we apply it again and use the solution as final solution

                switch (best) {
                    case 13:
                        // last solution is best solution
                        // DM.solvePRIOREPAIR(Net, 100);
                        break;
                    case 12:
                        DM.solvePRIOREPAIR(Net, 90, parameters);
                        break;
                    case 11:
                        DM.solvePRIOREPAIR(Net, 80, parameters);
                        break;
                    case 10:
                        DM.solvePRIOREPAIR(Net, 70, parameters);
                        break;
                    case 9:
                        DM.solvePRIOREPAIR(Net, 60, parameters);
                        break;
                    case 8:
                        DM.solvePRIOREPAIR(Net, 50, parameters);
                        break;
                    case 7:
                        DM.solvePRIOREPAIR(Net, 40, parameters);
                        break;
                    case 6:
                        DM.solvePRIOREPAIR(Net, 30, parameters);
                        break;
                    case 5:
                        DM.solvePRIOREPAIR(Net, 20, parameters);
                        break;
                    case 4:
                        DM.solvePRIOREPAIR(Net, 10, parameters);
                        break;
                    case 3:
                        DM.solvePRIOREPAIR(Net, 0, parameters);
                        break;
                    case 2:
                        DM.solveFRFS(Net, parameters);
                        break;
                    case 1:
                        DM.solveFSFS(Net, parameters);
                        break;
                    default:
                        throw new Exception("unexpected value for best");
                }
                String[] methods = {"FSFS", "FRFS", "PRIOREPAIR-0",
                    "PRIOREPAIR-10", "PRIOREPAIR-20",
                    "PRIOREPAIR-30", "PRIOREPAIR-40",
                    "PRIOREPAIR-50", "PRIOREPAIR-60",
                    "PRIOREPAIR-70", "PRIOREPAIR-80",
                    "PRIOREPAIR-90", "PRIOREPAIR-100"};
                logger.debug("\nbest method is " + methods[best - 1] + "\n");
                logger.debug("Used optimization method: " + parameters.getOptMethod());
                bestOfAllObjectives.put("dm_best_method", methods[best - 1]);
                if (parameters.shouldWriteBestOfAllObjectives()) {
                    new StatisticWriter.Builder().setStatistic(bestOfAllObjectives)
                        .setFileName(parameters.getBestOfAllObjectivesFile()).build().write();
                }
                break;
            default:
                throw new LinTimException("Solve DM: Unknown method " + parameters.getMethod());
        }
        logger.info("Finished computation of disposition timetable");
        logger.info("Begin writing output data");
        outputDispoTimetable(Net, parameters);
        logger.info("Finished writing output data");
    }

    private static void outputDispoTimetable(NonPeriodicEANetwork Net, Parameters parameters) throws IOException {
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(parameters.getDispoFile())));
        out.println("#" + parameters.getDispoFileHeader());
        for (NonPeriodicEvent e : Net.getEvents())
            out.println(e.getID() + "; " + e.getDispoTime());
        out.close();
    }
}
