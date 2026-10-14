package linePlanning.scripts;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import ilog.concert.IloException;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.LinePlanningParameters;
import linePlanning.instance.LinePlanningSolution;
import linePlanning.instance.LinePlanningSolutionReader;
import util.Configuration;
import util.Reader;

public class VerifySolution
{
    public static void main(String[] args) throws IOException, IloException
    {
        String solutionFile = "bestSolutions/solutionFiles/Netherlands-IC_sparse_Blarge_TPlarge_LGmedium_LAlow.txt";

        Configuration.initialiseConfiguration("settings.properties");

        LinePlanningInstance instance = buildInstance(solutionFile);
        LinePlanningSolution solution = LinePlanningSolutionReader.read(solutionFile, instance);
        solution.evaluateTrueObjective();
        solution.printToConsole();
    }

    private static LinePlanningInstance buildInstance(String solutionFile) throws IOException
    {
        Map<String, String> settings = readSettings(solutionFile);

        String instanceFolder          = settings.get("Instance");
        String odMatrixVariant         = settings.get("ODMatrix");
        String transferPenaltyVariant  = settings.get("TransferPenalty").split(" \\(")[0];
        String budgetVariant           = settings.get("Budget").split(" \\(")[0];
        String lineActivationCostVariant = settings.get("LineActivationCost");

        String odMatrixFile       = instanceFolder + "/ODMatrix/" + odMatrixVariant + ".txt";
        String transferPenaltyFile = instanceFolder + "/transferPenalty/" + transferPenaltyVariant + ".txt";
        String budgetFolder = switch (lineActivationCostVariant) {
            case "low"    -> "budget-fixedCost-low";
            case "medium" -> "budget";
            case "high"   -> "budget-fixedCost-high";
            default -> throw new IllegalArgumentException("Unknown line activation cost: " + lineActivationCostVariant);
        };
        String budgetFile    = instanceFolder + "/" + budgetFolder + "/" + budgetVariant + ".txt";
        String fixedCostFile = instanceFolder + "/fixedCost/" + lineActivationCostVariant + ".txt";
        LinePlanningParameters.LINE_ACTIVATION_COST = Reader.readInt(fixedCostFile);

        return new LinePlanningInstance(instanceFolder, odMatrixFile, budgetFile, transferPenaltyFile);
    }

    private static Map<String, String> readSettings(String filePath) throws IOException
    {
        Map<String, String> settings = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath)))
        {
            String line;
            boolean inSettings = false;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null)
            {
                if (firstLine) { line = line.replace("﻿", ""); firstLine = false; }
                if (line.equals("# Settings"))        { inSettings = true;  continue; }
                if (line.startsWith("# "))             { inSettings = false; continue; }
                if (inSettings && line.contains(": "))
                {
                    int sep = line.indexOf(": ");
                    settings.put(line.substring(0, sep), line.substring(sep + 2));
                }
            }
        }
        return settings;
    }
}
