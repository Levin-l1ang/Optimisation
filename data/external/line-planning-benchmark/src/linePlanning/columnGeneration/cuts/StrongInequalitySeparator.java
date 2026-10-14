package linePlanning.columnGeneration.cuts;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ilog.concert.IloException;
import linePlanning.CGN.CGNTravelArc;
import linePlanning.DPTN.DPTNArc;
import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.columns.RouteColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.columnGeneration.pricing.NetworkArc;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.ODPair;
import linePlanning.instance.Station;
import linePlanning.lineGeneration.Line;
import optimisation.columnGeneration.AbstractConstraint;
import optimisation.columnGeneration.AbstractSolution;
import optimisation.cuts.AbstractCutSeparator;

public class StrongInequalitySeparator
		extends AbstractCutSeparator<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final Map<Station, Map<Set<ODPair>, Double>> totalVolumeMap;

	public StrongInequalitySeparator(double violationThreshold, Map<Station, Map<Set<ODPair>, Double>> totalVolumeMap)
	{
		super(violationThreshold, false, true);

		this.totalVolumeMap = totalVolumeMap;
	}

	@Override
	public boolean separate(boolean isRootNode)
	{
		return true;
	}

	@Override
	public Set<AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> generateCuts(
			AbstractSolution<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> solution)
			throws IloException
	{
		Set<AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> violatedCuts = new LinkedHashSet<>();

		// Map line columns, and combinations of origin/group/arc, to fractional values.
		Map<Line, Double> lineMap = new LinkedHashMap<>();
		Map<DPTNArc, Double> arcMap = new LinkedHashMap<>();
		Map<Station, Map<Set<ODPair>, Map<NetworkArc, Double>>> flowMap = new LinkedHashMap<>();
		for (Station station : totalVolumeMap.keySet())
		{
			flowMap.put(station, new LinkedHashMap<>());
			for (Set<ODPair> pairs : totalVolumeMap.get(station).keySet())
			{
				flowMap.get(station).put(pairs, new LinkedHashMap<>());
			}
		}

		for (Entry<LinePlanningColumn, Double> entry : solution.getColumnMap().entrySet())
		{
			if (entry.getKey() instanceof LineFrequencyColumn)
			{
				LineFrequencyColumn lineColumn = (LineFrequencyColumn) entry.getKey();
				lineMap.put(lineColumn.getLine(), entry.getValue());

				for (DPTNArc arc : lineColumn.getLine().getArcPathForward())
				{
					arcMap.merge(arc, entry.getValue(), Double::sum);
				}
				for (DPTNArc arc : lineColumn.getLine().getArcPathBackward())
				{
					arcMap.merge(arc, entry.getValue(), Double::sum);
				}
			}
			if (entry.getKey() instanceof RouteColumn)
			{
				RouteColumn routeColumn = (RouteColumn) entry.getKey();
				if (routeColumn.getRoute() == null)
				{
					continue;
				}
				ODPair odPair = routeColumn.getODPair();
				Station origin = odPair.getOrigin();
				if (!flowMap.containsKey(origin))
				{
					continue;
				}
				for (Set<ODPair> group : flowMap.get(origin).keySet())
				{
					if (group.contains(odPair))
					{
						for (NetworkArc arc : routeColumn.getRoute().getArcs())
						{
							flowMap.get(origin).get(group).merge(arc, entry.getValue(), Double::sum);
						}
						break;
					}
				}
			}
		}

		// For each group, add only the most violated arc.
		for (Entry<Station, Map<Set<ODPair>, Map<NetworkArc, Double>>> originEntry : flowMap.entrySet())
		{
			Station origin = originEntry.getKey();
			for (Entry<Set<ODPair>, Map<NetworkArc, Double>> groupEntry : originEntry.getValue().entrySet())
			{
				Set<ODPair> group = groupEntry.getKey();
				double totalVolume = totalVolumeMap.get(origin).get(group);
				double threshold = Math.max(violationThreshold, violationThreshold * totalVolume);
				double bestViolation = threshold;
				AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> bestCut = null;

				for (Entry<NetworkArc, Double> arcEntry : groupEntry.getValue().entrySet())
				{
					NetworkArc arc = arcEntry.getKey();
					double violation;

					if (arc instanceof CGNTravelArc)
					{
						CGNTravelArc travelArc = (CGNTravelArc) arc;
						if (!lineMap.containsKey(travelArc.getLine()))
						{
							continue;
						}
						violation = arcEntry.getValue() - totalVolume * lineMap.get(travelArc.getLine());
					}
					else if (arc instanceof DPTNArc)
					{
						if (!arcMap.containsKey(arc))
						{
							continue;
						}
						violation = arcEntry.getValue() - totalVolume * arcMap.get(arc);
					}
					else
					{
						continue;
					}

					if (violation > bestViolation)
					{
						bestViolation = violation;
						bestCut = new StrongInequality(origin, group, totalVolume, arc);
					}
				}

				if (bestCut != null)
				{
					violatedCuts.add(bestCut);
				}
			}
		}
		return violatedCuts;
	}
}
