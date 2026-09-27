package net.lintim.io;

import net.lintim.exception.OutputFileException;
import net.lintim.model.Link;
import net.lintim.util.Config;
import net.lintim.util.Pair;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Writer for the evaluated passenger load.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the writer and use {@link #write()} afterwards.
 */
public class TimetablePassengerLoadWriter {

	private final Map<Link, Pair<Double, Integer>> loadsToWrite;
	private final String fileName;
	private final String header;

	private TimetablePassengerLoadWriter(Builder builder) {
		this.loadsToWrite = builder.loadsToWrite;
		this.fileName = "".equals(builder.fileName) ? builder.config.getStringValue("filename_invalid_loads") :
				builder.fileName;
		this.header = "".equals(builder.fileName) ? builder.config.getStringValue("invalid_load_header") : builder
				.header;
	}

	/**
	 * Start the writing process. The behavior is controlled by the {@link Builder} object, this object was
	 * created
	 *      * with.
	 */
	public void write(){
		CsvWriter writer = new CsvWriter(fileName, header);
		List<Map.Entry<Link, Pair<Double, Integer>>> loads = loadsToWrite.entrySet().stream().sorted(Comparator
				.comparingInt(l -> l.getKey().getId())).collect
				(Collectors.toList());
		try {
			for (Map.Entry<Link, Pair<Double, Integer>> e : loads) {
				writer.writeLine(
						String.valueOf(e.getKey().getId()),
						String.valueOf(e.getValue().getFirstElement()),
						String.valueOf(e.getValue().getSecondElement())
				);
			}
			writer.close();
		}
		catch (IOException e) {
			throw new OutputFileException(fileName);
		}
	}

	/**
	 * Builder object for a od writer.
	 *
	 * Use {@link #Builder(Map)} to create a builder with default options, afterwards use the setter to adapt it.
	 * The setters return this object, therefore they can be chained.
	 *
	 * For the possible parameters and their default values, see {@link #Builder(Map)}. To create a writer object,
	 * use {@link #build()} after setting all parameters.
	 */
	public static class Builder {
		private final Map<Link, Pair<Double, Integer>> loadsToWrite;
		private String fileName = "";
		private String header = "";
		private Config config = Config.getDefaultConfig();

		/**
		 * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
		 * <ul>
		 *     <li>
		 *         loadsToWrite (given in constructor) - the loads to write. Can only be set in the constructor
		 *     </li>
		 *     <li>
		 *         config ({@link Config#getDefaultConfig()}) - the config to read the file name and header
		 *         from. This will only happen, if the file name or the header are not given, but queried.
		 *     </li>
		 *     <li>
		 *          file name (dependent on config) - the file name to write the loads to
		 *     </li>
		 *     <li>
		 *          header (dependent on config) - the header to use for the load file
		 *     </li>
		 * </ul>
		 * All values can be set using the corresponding setters of this class. If you are ready, call
		 * {@link #build()} to create a writer with the given parameters.
		 * @param loadsToWrite the load to write
		 */
		public Builder(Map<Link, Pair<Double, Integer>> loadsToWrite) {
			Objects.requireNonNull(loadsToWrite);
			this.loadsToWrite = loadsToWrite;
		}

		public void setFileName(String fileName) {
			this.fileName = fileName;
		}

		public void setHeader(String header) {
			this.header = header;
		}

		public void setConfig(Config config) {
			this.config = config;
		}

		public TimetablePassengerLoadWriter build() {
			return new TimetablePassengerLoadWriter(this);
		}
	}
}
