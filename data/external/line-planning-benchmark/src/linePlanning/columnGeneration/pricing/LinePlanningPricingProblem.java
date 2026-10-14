package linePlanning.columnGeneration.pricing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.ODPair;
import linePlanning.instance.Station;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;

public class LinePlanningPricingProblem extends AbstractPricingProblem<LinePlanningInstance>
{
	private final Station origin;
	private final Set<ODPair> ODPairs;

	private final Map<ODPair, Double> ODPairDualMap;
	private final Map<ODPair, Map<NetworkArc, Double>> arcDualMap;

	public LinePlanningPricingProblem(int index, Station origin, Set<ODPair> ODPairs)
	{
		super(index);

		this.origin = origin;
		this.ODPairs = ODPairs;

		this.ODPairDualMap = new LinkedHashMap<>();
		this.arcDualMap = new LinkedHashMap<>();
		resetArcDuals();
	}

	public Station getOrigin()
	{
		return origin;
	}

	public Set<ODPair> getODPairs()
	{
		return ODPairs;
	}

	public double getODPairDual(ODPair pair)
	{
		return ODPairDualMap.get(pair);
	}

	public void setODPairDual(ODPair pair, double dual)
	{
		ODPairDualMap.put(pair, dual);
	}

	public Map<NetworkArc, Double> getArcDualMap(ODPair pair)
	{
		return arcDualMap.get(pair);
	}

	public void addArcDual(ODPair pair, NetworkArc arc, double dual)
	{
		arcDualMap.get(pair).put(arc, arcDualMap.get(pair).getOrDefault(arc, 0.0) + dual);
	}

	public void resetArcDuals()
	{
		for (ODPair pair : ODPairs)
		{
			arcDualMap.put(pair, new LinkedHashMap<>());
		}
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(ODPairs, origin);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LinePlanningPricingProblem other = (LinePlanningPricingProblem) obj;
		return Objects.equals(ODPairs, other.ODPairs) && Objects.equals(origin, other.origin);
	}
}
