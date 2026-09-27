package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;
import net.lintim.model.Configuration;

import java.io.*;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Handles the config CSV format and allows reading and writing.
 *
 * Syntax: key; value
 */

public class ConfigurationCSV {

    /**
     * Reads a configuration from file.
     *
     * @param config The config object to write to.
     * @param configFile The config file to read from.
     * @throws IOException
     * @throws DataInconsistentException
     * @throws FileNotFoundException
     */
    public static void fromFile(Configuration config, File configFile)
    throws IOException, DataInconsistentException, FileNotFoundException {

        CsvReader reader = new CsvReader(configFile);

        List<String> line;

        while ((line = reader.read()) != null) {
            try{

                if(line.size() != 2){
                    throw new DataInconsistentException("2 columns needed, " +
                            line.size() + " given");
                }

                Iterator<String> itr = line.iterator();

                String key = itr.next().trim();
                String value = itr.next().trim();

                if(key.equals("include") ){

                    fromFile(config, new File(configFile.getParent() +
                            "/" + value));

                }
                else if(key.equals("include_if_exists")){
                    File toInclude = new File(configFile.getParent() +
                            "/" + value);

                    if(toInclude.exists()){
                        fromFile(config, toInclude);
                    }

                }
                else{
                    config.setValue(key, value);
                }

            } catch (DataInconsistentException e){
                throw new DataInconsistentException("line " +
                        reader.getLineNumber() + " in file " +
                        configFile.getAbsolutePath() + " invalid: " +
                        e.getMessage());
            }
        }

        reader.close();
    }

    /**
     * Writes a configuration to file. Note that writing should only be done for
     * the state config.
     *
     * @param config The config object to read from.
     * @param configFile The config file to write to.
     * @param headerAnnotation An annotation for the header, will be commented
     * out line by line automatically.
     * @throws IOException
     * @throws DataInconsistentException
     * @throws FileNotFoundException
     */
    // TODO integrate into CSV write framework
    public static void toFile(Configuration config, File configFile,
            String headerAnnotation)
    throws IOException, DataInconsistentException, FileNotFoundException {

        configFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(configFile);

        BufferedReader annotationReader = new BufferedReader(
                new StringReader(headerAnnotation));

        String line;

        while ((line = annotationReader.readLine()) != null) {
            fw.write("# " + line + "\n");
        }

        annotationReader.close();

        for(Map.Entry<String, String> e : config.getData().entrySet()){
            fw.write(e.getKey() +  "; " + conditionalEncapsulate(e.getValue())
                    + "\n");
        }

        fw.close();
    }

    private static String conditionalEncapsulate(String input){
        String retval = input.trim();
        String lowercase = retval.toLowerCase();
        if(lowercase.equals("true") || lowercase.equals("false")){
            return lowercase;
        }
        try{
            Double.valueOf(retval);
            return retval;
        } catch (NumberFormatException e){
            return "\"" + retval + "\"";
        }
    }

}
