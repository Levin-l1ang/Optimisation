package linePlanning.scripts;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import ilog.concert.IloException;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.LinePlanningParameters;
import linePlanning.instance.LinePlanningSolution;
import linePlanning.instance.LinePlanningSolutionReader;
import util.Configuration;
import util.Reader;

public class VerifyAllSolutions
{
    private static final String SOLUTION_DIR = "bestSolutions/solutionFiles";
    private static final String CSV_PATH     = "bestSolutions/overview.csv";
    private static final double TOLERANCE    = 0.01;

    public static void main(String[] args) throws IOException, IloException
    {
        Configuration.initialiseConfiguration("settings.properties");

        Map<String, Double> csv = loadCsv(CSV_PATH);

        File[] files = new File(SOLUTION_DIR).listFiles((d, n) -> n.endsWith(".txt"));
        if (files == null || files.length == 0)
            throw new IllegalStateException("No solution files found in " + SOLUTION_DIR);
        Comparator<File> order = Comparator
                .<File, Integer>comparing(f -> instanceOrder(f.getName()))
                .thenComparing(File::getName);
        Arrays.sort(files, order);

        int ok = 0;

        for (int i = 0; i < files.length; i++)
        {
            File file = files[i];
            System.out.printf("[%d/%d] %s%n", i + 1, files.length, file.getName());

            Map<String, String> settings = readSection(file.getPath(), "Settings");

            String csvKey = buildCsvKey(settings);
            Double csvObj = csv.get(csvKey);

            if (csvObj == null)
                throw new IllegalStateException("No CSV row found for: " + csvKey);

            LinePlanningInstance instance = buildInstance(settings);
            LinePlanningSolution solution = LinePlanningSolutionReader.read(file.getPath(), instance);
            solution.evaluateTrueObjective();
            double computed = solution.getObjectiveActual();

            double diff = computed - csvObj;
            if (Math.abs(diff) > TOLERANCE)
                throw new IllegalStateException(String.format(
                        "Objective mismatch for %s: computed=%.4f  csv=%.4f  diff=%+.4f",
                        file.getName(), computed, csvObj, diff));

            System.out.printf("  OK  %.4f%n", computed);
            ok++;
        }

        System.out.println();
        System.out.printf("=== All %d solutions verified OK ===%n", ok);
    }

    private static int instanceOrder(String fileName)
    {
        if (fileName.startsWith("Randstad-IC_"))    return 0;
        if (fileName.startsWith("Switzerland-Small_")) return 1;
        return 2;
    }

    private static LinePlanningInstance buildInstance(Map<String, String> settings) throws IOException
    {
        String instanceFolder         = settings.get("Instance");
        String odMatrixVariant        = settings.get("ODMatrix");
        String transferPenaltyVariant = settings.get("TransferPenalty").split(" \\(")[0];
        String budgetVariant          = settings.get("Budget").split(" \\(")[0];
        String lineActivationCost     = settings.get("LineActivationCost");

        String odMatrixFile       = instanceFolder + "/ODMatrix/" + odMatrixVariant + ".txt";
        String transferPenaltyFile = instanceFolder + "/transferPenalty/" + transferPenaltyVariant + ".txt";
        String budgetFolder = switch (lineActivationCost)
        {
            case "low"    -> "budget-fixedCost-low";
            case "medium" -> "budget";
            case "high"   -> "budget-fixedCost-high";
            default -> throw new IllegalArgumentException("Unknown line activation cost: " + lineActivationCost);
        };
        String budgetFile    = instanceFolder + "/" + budgetFolder + "/" + budgetVariant + ".txt";
        String fixedCostFile = instanceFolder + "/fixedCost/" + lineActivationCost + ".txt";
        LinePlanningParameters.LINE_ACTIVATION_COST = Reader.readInt(fixedCostFile);

        return new LinePlanningInstance(instanceFolder, odMatrixFile, budgetFile, transferPenaltyFile);
    }

    private static String buildCsvKey(Map<String, String> settings)
    {
        String instance           = settings.get("Instance");
        String odMatrix           = settings.get("ODMatrix");
        String budget             = settings.get("Budget").split(" \\(")[0];
        String transferPenalty    = settings.get("TransferPenalty").split(" \\(")[0];
        String lineGeneration     = settings.getOrDefault("LineGeneration", "");
        String lineActivationCost = settings.get("LineActivationCost");

        return instance + "|" + odMatrix + "|" + budget + "|" + transferPenalty
                + "|" + lineGeneration + "|" + lineActivationCost;
    }

    private static Map<String, Double> loadCsv(String path) throws IOException
    {
        Map<String, Double> map = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path)))
        {
            String header = br.readLine();
            if (header == null) throw new IllegalStateException("CSV is empty");
            header = header.replace("﻿", "");
            String[] cols = header.split(",");

            int iInstance = col(cols, "Instance");
            int iODMatrix = col(cols, "ODMatrix");
            int iBudget   = col(cols, "Budget");
            int iTP       = col(cols, "TransferPenalty");
            int iLG       = col(cols, "LineGeneration");
            int iLA       = col(cols, "LineActivationCost");
            int iObj      = col(cols, "BestObjectiveActual");

            String line;
            while ((line = br.readLine()) != null)
            {
                if (line.isBlank()) continue;
                String[] p = line.split(",");
                String key = p[iInstance] + "|" + p[iODMatrix] + "|" + p[iBudget]
                        + "|" + p[iTP] + "|" + p[iLG] + "|" + p[iLA];
                map.put(key, Double.parseDouble(p[iObj]));
            }
        }
        return map;
    }

    private static int col(String[] header, String name)
    {
        for (int i = 0; i < header.length; i++)
            if (header[i].equals(name)) return i;
        throw new IllegalStateException("Column not found in CSV: " + name);
    }

    private static Map<String, String> readSection(String filePath, String sectionName) throws IOException
    {
        Map<String, String> map = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath)))
        {
            String line;
            boolean inSection = false;
            boolean first = true;
            while ((line = br.readLine()) != null)
            {
                if (first) { line = line.replace("﻿", ""); first = false; }
                if (line.equals("# " + sectionName)) { inSection = true;  continue; }
                if (line.startsWith("# "))             { inSection = false; continue; }
                if (inSection && line.contains(": "))
                {
                    int sep = line.indexOf(": ");
                    map.put(line.substring(0, sep), line.substring(sep + 2));
                }
            }
        }
        return map;
    }
}
