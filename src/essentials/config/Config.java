import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class Config {

    TreeMap<String, String> data = new TreeMap<String, String>();
    TreeSet<Integer> lines_with_errors = new TreeSet<Integer>();

    public Config() {

    }

    public Config(File input) throws IOException {
        readConfig(input);
    }

    public void readConfig(File sourceFile) throws IOException {

        readConfig(sourceFile, false);

    }

    public void readConfig(File sourceFile, Boolean only_if_exists) throws IOException {
        try {
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

                if (name.equals("include")) {

                    readConfig(new File(sourceFile.getParent() + File.separator +
                        value));

                } else if (name.equals("include_if_exists")) {
                    File toInclude = new File(sourceFile.getParent() +
                        File.separator + value);

                    if (toInclude.exists()) {
                        readConfig(toInclude);
                    }

                } else {

                    data.put(name, value);

                }

            }

            rd.close();
        } catch (IOException e) {
            if (only_if_exists) {
                return;
            }
            throw e;
        }
    }

    public String getStringValue(String name) {
        if (name.equals("default_activities_periodic_file")
            && data.keySet().contains("use_buffered_activities")
            && getBooleanValue("use_buffered_activities").booleanValue())
            name = "default_activity_buffer_file";
        if (name.equals("default_activities_periodic_unbuffered_file")
            && data.keySet().contains("use_buffered_activities")
            && getBooleanValue("use_buffered_activities").booleanValue()) {
            name = "default_activity_relax_file";
        } else if (name.equals("default_activities_periodic_unbuffered_file") && (!data.keySet().contains("use_buffered_activities") || !getBooleanValue("use_buffered_activities").booleanValue())) {
            name = "default_activities_periodic_file";
        }
        if (!data.keySet().contains(name)) {
            return null;
        }
        return data.get(name);
    }

    public Double getDoubleValue(String name) {
        if (!data.keySet().contains(name)) {
            return null;
        }
        return Double.parseDouble(data.get(name));
    }

    public Boolean getBooleanValue(String name) {
        if (!data.keySet().contains(name)) {
            return null;
        }
        return Boolean.parseBoolean(data.get(name));
    }

    public Integer getIntegerValue(String name) {
        if (!data.keySet().contains(name)) {
            return null;
        }
        return Integer.parseInt(data.get(name));
    }

    // For debugging only.
    void dumpConfiguration() {
        for (Map.Entry<String, String> e : data.entrySet()) {

            System.out.println(e.getKey() + "; " + e.getValue());

        }
    }

    public static void main(String[] args) {

        try {
            Config myConfig = new Config(new File(args[0]));
            myConfig.dumpConfiguration();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
