package util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Logger
{
	public enum TimeQuantity
	{
		TIME_PRICING("TIME_PRICING"), TIME_RMP("TIME_RMP"), TIME_COL_MANAGEMENT("TIME_COL_MANAGEMENT"),
		TIME_ENUMERATION("TIME_ENUMERATION"), TIME_HEURISTIC("TIME_HEURISTIC"),
		TIME_REDUCED_COST_FIXING("TIME_REDUCED_COST_FIXING"), TIME_BRANCHING("TIME_BRANCHING"),
		TIME_CUT_SEPARATION("TIME_CUT_SEPARATION"), TIME_CUT_MANAGEMENT("TIME_CUT_MANAGEMENT"),
		TIME_TOTAL("TIME_TOTAL");

		public String name;

		private TimeQuantity(String name)
		{
			this.name = name;
		}
	}

	public enum CountQuantity
	{
		NUM_ITERATION_PRICING("NUM_ITERATION_PRICING"), NUM_GENERATED_COL("NUM_GENERATED_COL"),
		NUM_SELECTED_COL("NUM_SELECTED_COL"), NUM_REMOVED_COL("NUM_REMOVED_COL"),
		NUM_SEPARATED_CUT("NUM_SEPARATED_CUT"), NUM_REMOVED_CUT("NUM_REMOVED_CUT");

		public String name;

		private CountQuantity(String name)
		{
			this.name = name;
		}
	}

	public enum ValueQuantity
	{
		VALUE_OBJECTIVE("VALUE_OBJECTIVE"), VALUE_LOWER_BOUND("VALUE_LOWER_BOUND"),
		VALUE_UPPER_BOUND("VALUE_UPPER_BOUND");

		public String name;

		private ValueQuantity(String name)
		{
			this.name = name;
		}
	}

	private int node;
	private long time;

	private Map<TimeQuantity, Long> timeQuantities;
	private Map<CountQuantity, Integer> countQuantities;
	private Map<ValueQuantity, Double> valueQuantities;

	private StringBuilder stringBuilder;
	private String outputSeparator = Configuration.getConfiguration().getStringProperty("OUTPUT_SEPARATOR");

	private static Logger logger;

	private Logger()
	{
		node = 0;
		time = 0;

		timeQuantities = new LinkedHashMap<>();
		countQuantities = new LinkedHashMap<>();
		valueQuantities = new LinkedHashMap<>();
		initialiseQuantities(true);

		// Initialise StringBuilder with headings.
		this.stringBuilder = new StringBuilder();
		writeHeaders();
	}

	public static Logger getLogger()
	{
		return (logger == null) ? Logger.logger = new Logger() : logger;
	}

	public static Logger getDummyLogger()
	{
		return new Logger();
	}

	public int getNode()
	{
		return node;
	}

	public void resetLogger()
	{
		node = 0;
		time = 0;

		timeQuantities = new LinkedHashMap<>();
		countQuantities = new LinkedHashMap<>();
		valueQuantities = new LinkedHashMap<>();
		initialiseQuantities(true);

		// Initialise StringBuilder with headings.
		stringBuilder = new StringBuilder();
		writeHeaders();
	}

	private void initialiseQuantities(boolean overrideBounds)
	{
		for (TimeQuantity quantity : TimeQuantity.values())
		{
			timeQuantities.put(quantity, (long) 0);
		}
		for (CountQuantity quantity : CountQuantity.values())
		{
			countQuantities.put(quantity, 0);
		}
		for (ValueQuantity quantity : ValueQuantity.values())
		{
			if (quantity.equals(ValueQuantity.VALUE_LOWER_BOUND))
			{
				if (overrideBounds)
				{
					valueQuantities.put(quantity, 0.0);
				}
			}
			else
				if (quantity.equals(ValueQuantity.VALUE_UPPER_BOUND))
				{
					if (overrideBounds)
					{
						valueQuantities.put(quantity, Double.MAX_VALUE);
					}
				}
				else
				{
					valueQuantities.put(quantity, 0.0);
				}
		}
	}

	public void resetNode()
	{
		node = 0;
		time = 0;

		initialiseQuantities(true);
	}

	public void increaseNode()
	{
		// Write output.
		writeOutput();

		// Initialise new quantities.
		initialiseQuantities(false);

		node++;
	}

	public long getTime()
	{
		return time;
	}

	public long getTimeQuantity(TimeQuantity timeQuantity)
	{
		return timeQuantities.get(timeQuantity);
	}

	public int getCountQuantity(CountQuantity countQuantity)
	{
		return countQuantities.get(countQuantity);
	}

	public void startTimer(TimeQuantity timeQuantity)
	{
		timeQuantities.put(timeQuantity, timeQuantities.get(timeQuantity) - System.currentTimeMillis());
		time -= System.currentTimeMillis();
	}

	public void stopTimer(TimeQuantity timeQuantity)
	{
		timeQuantities.put(timeQuantity, timeQuantities.get(timeQuantity) + System.currentTimeMillis());
		time += System.currentTimeMillis();
		timeQuantities.put(TimeQuantity.TIME_TOTAL, time);
	}

	public void incrementCount(CountQuantity countQuantity, int count)
	{
		countQuantities.put(countQuantity, countQuantities.get(countQuantity) + count);
	}

	public void setValue(ValueQuantity valueQuantity, double value)
	{
		valueQuantities.put(valueQuantity, value);
	}

	private void writeHeaders()
	{
		stringBuilder.append("node" + outputSeparator);
		for (TimeQuantity quantity : timeQuantities.keySet())
		{
			stringBuilder.append(quantity.name + outputSeparator);
		}
		for (CountQuantity quantity : countQuantities.keySet())
		{
			stringBuilder.append(quantity.name + outputSeparator);
		}
		for (ValueQuantity quantity : valueQuantities.keySet())
		{
			stringBuilder.append(quantity.name + outputSeparator);
		}
	}

	private void writeOutput()
	{
		stringBuilder.append("\n");
		stringBuilder.append(node + outputSeparator);

		for (TimeQuantity quantity : timeQuantities.keySet())
		{
			stringBuilder.append(timeQuantities.get(quantity) + outputSeparator);
		}
		for (CountQuantity quantity : countQuantities.keySet())
		{
			stringBuilder.append(countQuantities.get(quantity) + outputSeparator);
		}
		for (ValueQuantity quantity : valueQuantities.keySet())
		{
			stringBuilder.append(Writer.formatDouble(valueQuantities.get(quantity)) + outputSeparator);
		}
	}

	public String getOutput()
	{
		return stringBuilder.toString();
	}

	public static double[] analyseLoggerSingleRun(String fileName) throws IOException
	{
		// We store the following values: timePricing, timeRMP, timeTotal, iterations
		// root node Bound, upperBound, gap.
		double[] values = new double[7];

		double timePricing = 0;
		double timeRMP = 0;
		double timeTotal = 0;
		int iterations = 0;
		double rootNodeBound = 0;
		double upperBound = Double.MAX_VALUE;

		BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName));
		Map<String, Integer> columnIndex = buildColumnIndex(bufferedReader.readLine());
		String line = bufferedReader.readLine();

		while (line != null)
		{
			String[] data = line.split(";");
			int node = Integer.valueOf(data[0]);
			timePricing += (double) Long.valueOf(data[columnIndex.get(TimeQuantity.TIME_PRICING.name)]) / 1000.0;
			timeRMP += (double) Long.valueOf(data[columnIndex.get(TimeQuantity.TIME_RMP.name)]) / 1000.0;
			timeTotal = (double) Long.valueOf(data[columnIndex.get(TimeQuantity.TIME_TOTAL.name)]) / 1000.0;
			iterations += Integer.valueOf(data[columnIndex.get(CountQuantity.NUM_ITERATION_PRICING.name)]);
			if (node == 0)
			{
				rootNodeBound = Double.valueOf(data[columnIndex.get(ValueQuantity.VALUE_LOWER_BOUND.name)]);
			}
			upperBound = Double.valueOf(data[columnIndex.get(ValueQuantity.VALUE_UPPER_BOUND.name)]);

			// Update values.
			line = bufferedReader.readLine();
		}
		bufferedReader.close();

		// Write the last line.
		values[0] = timePricing;
		values[1] = timeRMP;
		values[2] = timeTotal;
		values[3] = iterations;
		values[4] = rootNodeBound;
		values[5] = upperBound;
		values[6] = 100.0 * (values[5] - values[4]) / (values[5]);

		// Return results.
		return values;
	}

	public static LoggerResults analyse(String fileName) throws IOException
	{
		// Initialise result mapping.
		Map<TimeQuantity, Long> times = new LinkedHashMap<>();
		Map<CountQuantity, Integer> counts = new LinkedHashMap<>();
		Map<ValueQuantity, Double> values = new LinkedHashMap<>();

		BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName));
		Map<String, Integer> columnIndex = buildColumnIndex(bufferedReader.readLine());
		String line = bufferedReader.readLine();

		while (line != null)
		{
			String[] data = line.split(";");
			int node = Integer.valueOf(data[0]);

			for (TimeQuantity timeQuantity : TimeQuantity.values())
			{
				if (columnIndex.containsKey(timeQuantity.name))
				{
					if (timeQuantity.equals(TimeQuantity.TIME_TOTAL))
					{
						// Non-cumulative attribute.
						times.put(timeQuantity, Long.valueOf(data[columnIndex.get(timeQuantity.name)]));
					}
					else
					{
						// Cumulative attribute.
						times.put(timeQuantity, times.getOrDefault(timeQuantity, (long) 0)
								+ Long.valueOf(data[columnIndex.get(timeQuantity.name)]));
					}
				}
			}
			for (CountQuantity countQuantity : CountQuantity.values())
			{
				if (columnIndex.containsKey(countQuantity.name))
				{
					// Cumulative attribute.
					counts.put(countQuantity, counts.getOrDefault(countQuantity, 0)
							+ Integer.valueOf(data[columnIndex.get(countQuantity.name)]));
				}
			}
			if (node == 0)
			{
				if (columnIndex.containsKey(ValueQuantity.VALUE_LOWER_BOUND.name))
				{
					// Root node lower bound.
					values.put(ValueQuantity.VALUE_LOWER_BOUND,
							Double.valueOf(data[columnIndex.get(ValueQuantity.VALUE_LOWER_BOUND.name)]));
				}
			}
			if (columnIndex.containsKey(ValueQuantity.VALUE_UPPER_BOUND.name))
			{
				// Final primal solution.
				values.put(ValueQuantity.VALUE_UPPER_BOUND,
						Math.min(values.getOrDefault(ValueQuantity.VALUE_UPPER_BOUND, Double.MAX_VALUE),
								Double.valueOf(data[columnIndex.get(ValueQuantity.VALUE_UPPER_BOUND.name)])));
			}
			line = bufferedReader.readLine();
		}
		bufferedReader.close();

		// Return results.
		return new LoggerResults(times, counts, values);
	}

	public static List<double[]> analyseLogger(String fileName) throws IOException
	{
		// Initialise results array.
		List<double[]> results = new ArrayList<>();

		// We store the following values: timePricing, timeRMP, timeTotal, iterations
		// root node Bound, upperBound, gap.
		double[] values = new double[7];

		int currentNode = -1;
		double timePricing = 0;
		double timeRMP = 0;
		double timeTotal = 0;
		int iterations = 0;
		double rootNodeBound = 0;
		double upperBound = Double.MAX_VALUE;

		BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName));
		Map<String, Integer> columnIndex = buildColumnIndex(bufferedReader.readLine());
		String line = bufferedReader.readLine();

		while (line != null)
		{
			String[] data = line.split(";");
			int node = Integer.valueOf(data[0]);
			timePricing += (double) Long.valueOf(data[columnIndex.get(TimeQuantity.TIME_PRICING.name)]) / 1000.0;
			timeRMP += (double) Long.valueOf(data[columnIndex.get(TimeQuantity.TIME_RMP.name)]) / 1000.0;
			timeTotal = (double) Long.valueOf(data[columnIndex.get(TimeQuantity.TIME_TOTAL.name)]) / 1000.0;
			iterations += Integer.valueOf(data[columnIndex.get(CountQuantity.NUM_ITERATION_PRICING.name)]);
			if (node == 0)
			{
				rootNodeBound = Double.valueOf(data[columnIndex.get(ValueQuantity.VALUE_LOWER_BOUND.name)]);
			}
			upperBound = Double.valueOf(data[columnIndex.get(ValueQuantity.VALUE_UPPER_BOUND.name)]);

			// Write integral solution is the node index switches to 0.
			if (currentNode >= 0 && node == 0)
			{
				values[0] = timePricing;
				values[1] = timeRMP;
				values[2] = timeTotal;
				values[3] = iterations;
				values[4] = rootNodeBound;
				values[5] = upperBound;
				values[6] = 100.0 * (values[5] - values[4]) / (values[5]);
				results.add(values);
				values = new double[7];
			}

			// Update values.
			currentNode = node;
			line = bufferedReader.readLine();
		}
		bufferedReader.close();

		// Write the last line.
		values[0] = timePricing;
		values[1] = timeRMP;
		values[2] = timeTotal;
		values[3] = iterations;
		values[4] = rootNodeBound;
		values[5] = upperBound;
		values[6] = 100.0 * (values[5] - values[4]) / (values[5]);
		results.add(values);
		values = new double[7];

		// Return results.
		return results;
	}

	private static Map<String, Integer> buildColumnIndex(String headerLine)
	{
		String[] headers = headerLine.split(";");
		Map<String, Integer> columnIndex = new LinkedHashMap<>();
		for (int i = 0; i < headers.length; i++)
		{
			columnIndex.put(headers[i].trim(), i);
		}
		return columnIndex;
	}
}