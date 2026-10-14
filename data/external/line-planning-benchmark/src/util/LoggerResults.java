package util;

import java.util.Map;

import util.Logger.CountQuantity;
import util.Logger.TimeQuantity;
import util.Logger.ValueQuantity;

public class LoggerResults
{
	private final Map<TimeQuantity, Long> timeQuantities;
	private final Map<CountQuantity, Integer> countQuantities;
	private final Map<ValueQuantity, Double> valueQuantities;
	
	public LoggerResults(Map<TimeQuantity, Long> timeQuantities, Map<CountQuantity, Integer> countQuantities, Map<ValueQuantity, Double> valueQuantities)
	{
		this.timeQuantities = timeQuantities;
		this.countQuantities = countQuantities;
		this.valueQuantities = valueQuantities;
	}
	
	public long getTimeQuantity(TimeQuantity timeQuantity)
	{
		return timeQuantities.get(timeQuantity);
	}
	
	public int getCountQuantity(CountQuantity countQuantity)
	{
		return countQuantities.get(countQuantity);
	}
	
	public double getValueQuantity(ValueQuantity valueQuantity)
	{
		return valueQuantities.get(valueQuantity);
	}
}
