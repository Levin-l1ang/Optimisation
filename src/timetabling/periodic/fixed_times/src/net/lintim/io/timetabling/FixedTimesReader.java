package net.lintim.io.timetabling;

import net.lintim.exception.GraphIncidentNodeNotFoundException;
import net.lintim.exception.InputFormatException;
import net.lintim.exception.InputTypeInconsistencyException;
import net.lintim.exception.LinTimException;
import net.lintim.io.CsvReader;
import net.lintim.model.Graph;
import net.lintim.model.PeriodicActivity;
import net.lintim.model.PeriodicEvent;
import net.lintim.util.Config;
import net.lintim.util.Pair;

import java.util.HashMap;
import java.util.Map;

/**
 * Class for reading fixed times for a timetabling problem. Can be created using a {@link Builder}, afterwards
 * {@link #read()} can be called for the actual reading process.
 */
public class FixedTimesReader {
	private final Graph<PeriodicEvent, PeriodicActivity> ean;
	private final Map<PeriodicEvent, Pair<Integer, Integer>> boundMap;
	private final String fixedTimesFileName;

	private FixedTimesReader(Builder builder) {
		this.ean = builder.ean;
		this.boundMap = builder.boundMap == null ? new HashMap<>() : builder.boundMap;
		this.fixedTimesFileName = "".equals(builder.fixedTimesFileName) ?
				builder.config.getStringValue("filename_tim_fixed_times") : builder.fixedTimesFileName;
	}

	/**
	 * Start the reading process. The behavior is controlled by the {@link Builder} object, this object was created
	 * with.
	 * @return the read time bounds in a map
	 */
	public Map<PeriodicEvent, Pair<Integer, Integer>> read() {
		CsvReader.readCsv(fixedTimesFileName, this::processFixedTime);
		return boundMap;
	}

	private void processFixedTime(String[] args, int lineNumber) throws InputFormatException,
			InputTypeInconsistencyException, GraphIncidentNodeNotFoundException {
		if (args.length != 3) {
			throw new InputFormatException(fixedTimesFileName, args.length, 3);
		}

		int eventId;
		int lowerBound;
		int upperBound;

		try {
			eventId = Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			throw new InputTypeInconsistencyException(fixedTimesFileName, 1, lineNumber, "int", args[0]);
		}
		try {
			lowerBound = Integer.parseInt(args[1]);
		} catch (NumberFormatException e) {
			throw new InputTypeInconsistencyException(fixedTimesFileName, 2, lineNumber, "int", args[1]);
		}
		try {
			upperBound = Integer.parseInt(args[2]);
		} catch (NumberFormatException e) {
			throw new InputTypeInconsistencyException(fixedTimesFileName, 3, lineNumber, "int", args[2]);
		}

		PeriodicEvent event = ean.getNode(eventId);

		if (event == null) {
			throw new LinTimException("An event with id " + eventId + " is referenced, but cannot be found in the ean");
		}

		boundMap.put(event, new Pair<>(lowerBound, upperBound));
	}

    /**
     * Builder object for a FixedTimesReader.
     */
	public static class Builder {
		private final Graph<PeriodicEvent, PeriodicActivity> ean;
		private Map<PeriodicEvent, Pair<Integer, Integer>> boundMap;
		private String fixedTimesFileName = "";
		private Config config = Config.getDefaultConfig();

		/**
		 * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
		 * <ul>
		 *     <li>
		 *         ean (set in constructor) - the ean to read the fixed times for
		 *     </li>
		 *     <li>
		 *         bound map (empty {@link HashMap}) - the map to store the bounds of the fixed times in
		 *     </li>
		 *     <li>
		 *         config ({@link Config#getDefaultConfig()}) - the config to read the file names from. This will only
		 *         happen, if the file names are not given, but queried.
		 *     </li>
		 *     <li>
		 *         fixed times file name (dependent on config) - the file name to read the fixed times from
		 *     </li>
		 * </ul>
		 * All values can be set using the corresponding setters of this class. If you are ready, call
		 * {@link #build()} to create a reader with the given parameters.
		 */
		public Builder(Graph<PeriodicEvent, PeriodicActivity> ean) {
			this.ean = ean;
		}

		public Builder setBoundMap(Map<PeriodicEvent, Pair<Integer, Integer>> boundMap) {
			this.boundMap = boundMap;
			return this;
		}

		public Builder setFixedTimesFileName(String fixedTimesFileName) {
			this.fixedTimesFileName = fixedTimesFileName;
			return this;
		}

		public Builder setConfig(Config config) {
			this.config = config;
			return this;
		}

		public FixedTimesReader build() {
			return new FixedTimesReader(this);
		}
	}
}
