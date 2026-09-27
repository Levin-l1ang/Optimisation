import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.util.Map;

public class Statistic {

    LinkedHashMap<String, String> data = new LinkedHashMap<String, String>();
    LinkedHashSet<Integer> lines_with_errors = new LinkedHashSet<Integer>();

    public Statistic() {

    }

    public Statistic(File input) throws IOException {
        readStatistic(input);
    }

    public void setValue(String name, String value) {
        data.put(name, value);
    }

    public String getStringValue(String name) {
        return data.get(name);
    }

    public Double getDoubleValue(String name) {
        return Double.parseDouble(data.get(name));
    }

    public Boolean getBooleanValue(String name) {
        return Boolean.parseBoolean(data.get(name));
    }

    public Integer getIntegerValue(String name) {
        return Integer.parseInt(data.get(name));
    }

    public Long getLongValue(String name) {
        return Long.parseLong(data.get(name));
    }

    public void setStringValue(String name, String value) {
        data.put(name, value);
    }

    public void setDoubleValue(String name, Double value) {
        data.put(name, "" + value);
    }

    public void setBooleanValue(String name, Boolean value) {
        data.put(name, value ? "true" : "false");
    }

    public void setIntegerValue(String name, Integer value) {
        data.put(name, "" + value);
    }

    public void setLongValue(String name, Long value) {
        data.put(name, "" + value);
    }

    public void readStatistic(File sourceFile) throws IOException {
        // First check if the file exists at all. Otherwise, we just want to have an empty statistic object
        if (!Files.exists(sourceFile.toPath())) {
            return;
        }
        BufferedReader rd = new BufferedReader(new FileReader(sourceFile));

        String line;
        String filename = sourceFile.getName();

        for (Integer line_counter = 0; (line = rd.readLine()) != null; line_counter++) {

            line_counter++;

            if (line.startsWith("#") || line.trim().length() == 0) {
                continue;
            }

            String[] fragments = line.split(";", 2);

            if (fragments.length != 2) {

                System.err.println("Error in line " + line_counter +
                    ", file " + filename +
                    ": malformed input, skipping line.");

                lines_with_errors.add(line_counter);

                continue;
            }

            String name = fragments[0].trim();
            String value = fragments[1].trim();

            if (value.length() >= 2 && value.charAt(0) == '"'
                && value.charAt(value.length() - 1) == '"') {

                value = value.substring(1, value.length() - 1);
            }

            data.put(name, value);

        }

        rd.close();
    }

    public void writeStatistic(File statisticFile)
        throws IOException, FileNotFoundException {

        FileWriter fr = new FileWriter(statisticFile);

        for (Map.Entry<String, String> e : data.entrySet()) {
            fr.write(e.getKey() + "; " + e.getValue() + "\n");
        }

        fr.close();
    }

    // For debugging only.
    void dumpStatistic() {
        for (Map.Entry<String, String> e : data.entrySet()) {

            System.out.println(e.getKey() + "; " + e.getValue());

        }
    }

    public static void main(String[] args) {

        try {
            Statistic myStatistic = new Statistic(new File(args[0]));
            myStatistic.dumpStatistic();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
