package linePlanning.columnGeneration.pricing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.ODPair;
import linePlanning.instance.Station;
import optimisation.columnGeneration.pricing.AbstractPricingRoutine;

public class LinePlanningPricingRoutine
		extends AbstractPricingRoutine<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	public enum NetworkType
	{
		PTN("PTN"), DPTN("DPTN"), CGN("CGN");

		public String name;

		private NetworkType(String name)
		{
			this.name = name;
		}
	}

	private final NetworkType networkType;

	public LinePlanningPricingRoutine(NetworkType networkType)
	{
		this.networkType = networkType;
	}

	@Override
	protected void preProcessPricingProblems(LinePlanningInstance instance)
	{
		if (networkType.equals(NetworkType.DPTN))
		{
			instance.getDPTN().resetDuals();
		}
		if (networkType.equals(NetworkType.CGN))
		{
			instance.getCGN().resetDuals();
		}
	}

	@Override
	protected void preProcessPricingProblem(LinePlanningInstance instance, LinePlanningPricingProblem pricingProblem)
	{
		// Reset arc-specific duals.
		pricingProblem.resetArcDuals();
	}

	@Override
	protected List<LinePlanningPricingProblem> generatePricingProblems(LinePlanningInstance instance)
	{
		// Group OD-pairs by origin.
		Map<Station, Set<ODPair>> originMap = new LinkedHashMap<>();
		for (ODPair pair : instance.getODPairs())
		{
			Station origin = pair.getOrigin();
			if (!originMap.containsKey(origin))
			{
				originMap.put(origin, new LinkedHashSet<>());
			}
			originMap.get(origin).add(pair);
		}

		// Construct one pricing problem per origin.
		List<LinePlanningPricingProblem> pricingProblems = new ArrayList<>();
		for (Station origin : originMap.keySet())
		{
			pricingProblems.add(new LinePlanningPricingProblem(pricingProblems.size(), origin, originMap.get(origin)));
		}
		return pricingProblems;
	}
}
