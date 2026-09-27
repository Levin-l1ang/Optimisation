package net.lintim.io;

import net.lintim.exception.InputFormatException;
import net.lintim.exception.InputTypeInconsistencyException;
import net.lintim.model.OD;
import net.lintim.model.impl.FullOD;
import net.lintim.util.Config;

/**
 * Class to read files of od matrices.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class ODReader {
    private final String fileName;
    private final OD od;

    private ODReader(Builder builder) {
        this.od = builder.od;
        if (!builder.readNodeOd) {
            this.fileName = "".equals(builder.fileName) ? builder.config.getStringValue("default_od_file") :
                builder.fileName;
        }
        else {
            this.fileName = "".equals(builder.fileName) ? builder.config.getStringValue("filename_od_nodes_file") :
                builder.fileName;
        }
    }

    /**
     * Start the reading process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     * @return the read od matrix.
     */
    public OD read() {
        CsvReader.readCsv(fileName, this::processODLine);
        return od;
    }

    /**
     * Process the contents of an od matrix line.
     * @param args the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException if the line contains not exactly 3 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     */
    private void processODLine(String[] args, int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException {
        if (args.length != 3) {
            throw new InputFormatException(fileName, args.length, 3);
        }

        int origin;
        int destination;
        double passengers;

        try {
            origin = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 1, lineNumber, "int", args[0]);
        }
        try {
            destination = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 2, lineNumber, "int", args[1]);
        }
        try {
            passengers = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 3, lineNumber, "double", args[2]);
        }

        od.setValue(origin, destination, passengers);
    }

    /**
     * Builder object for an od reader.
     *
     * Use {@link #Builder(OD)} or {@link #Builder(int)} to create a builder with default options, afterwards use the
     * setter to adapt it. The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder(OD)} or {@link #Builder(int)}. To
     * create a reader object, use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private String fileName = "";
        private final OD od;
        private boolean readNodeOd = false;
        private Config config = Config.getDefaultConfig();
        private final int odSize;

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         od (set in constructor) - the od matrix to fill. This constructor needs an od matrix. If you don't
         *         want to provide an od matrix, use {@link #Builder(int)} instead.
         *     </li>
         *     <li>
         *         readNodeOd (false) - whether to read a node od file or a stop od file. Default is stop od file.
         *         If this is set to true, a node od file will be read instead.
         *     </li>
         *     <li>
         *         config {@link Config#getDefaultConfig()} - the config to read the file name from. This will only
         *         happen, if the file name is not given but queried.
         *     </li>
         *     <li>
         *         file name (dependent on config) - the file name to read the od from
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         * @param od the od matrix to fill
         */
        public Builder(OD od) {
            if (od == null) {
                throw new IllegalArgumentException("The od matrix in a od reader builder can not be null");
            }
            this.od = od;
            this.odSize = -1;
        }

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         od size (set in constructor) - the size of the od matrix to create. An empty {@link FullOD} object
         *         with the given size will be created and filled with the reader. If you want to provide your own
         *         od matrix, use {@link #Builder(OD)} instead.
         *     </li>
         *     <li>
         *         config {@link Config#getDefaultConfig()} - the config to read the file name from. This will only
         *         happen, if the file name is not given but queried.
         *     </li>
         *     <li>
         *         file name (dependent on config) - the file name to read the od from
         *     </li>
         * </ul>
         * @param odSize the size of the od matrix to create
         */
        public Builder(int odSize) {
            if (odSize <= 0) {
                throw new IllegalArgumentException("Cannot create an od matrix with size smaller or equal to 0");
            }
            this.odSize = odSize;
            this.od = new FullOD(odSize);
        }

        /**
         * Set the filename to read the od matrix from.
         * @param fileName the od file name
         * @return this object
         */
        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder readNodeOd(boolean readNodeOd) {
            this.readNodeOd = readNodeOd;
            return this;
        }

        /**
         * Set the config. The config is used to read file names, that are queried but not given.
         * @param config the config
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Create a new od reader with the current builder settings
         * @return the new reader. Use {@link ODReader#read()} for the reading process.
         */
        public ODReader build() {
            return new ODReader(this);
        }
    }

}
