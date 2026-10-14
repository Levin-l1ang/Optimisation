package linePlanning.columnGeneration.branching;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import linePlanning.DPTN.DPTNArc;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import optimisation.BAP.AbstractBranchingRule;
import optimisation.BAP.BAPNode;
import optimisation.BAP.BranchingCandidate;
import util.Configuration;

public class DEPTNBranchingRule
		extends AbstractBranchingRule<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	public DEPTNBranchingRule(double priority)
	{
		super(priority);
	}

	@Override
	public List<BranchingCandidate<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> getBranchingCandidates(
			BAPNode<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> parent)
	{
		// Construct a mapping of fractional value per arc.
		Map<DPTNArc, Double> arcMap = new LinkedHashMap<>();
		for (Entry<LinePlanningColumn, Double> entry : parent.getSolution().getColumnMap().entrySet())
		{
			double value = entry.getValue();
		}

		// Generate candidates.
		double PRECISION = Configuration.getConfiguration().getDoubleProperty("PRECISION");
		List<BranchingCandidate<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> candidates = new ArrayList<>();
		for (Entry<DPTNArc, Double> entry : arcMap.entrySet())
		{
			double value = entry.getValue();
			double fractionalValue = Math.abs(value - Math.rint(value));
			if (fractionalValue < PRECISION)
			{
				continue;
			}
			DPTNArc arc = entry.getKey();

			BranchingCandidate<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> candidate = new BranchingCandidate<>(
					fractionalValue);
			candidate.addBranchingDecision(new DEPTNBranchingDecision(arc, (int) Math.ceil(value), true));
			candidate.addBranchingDecision(new DEPTNBranchingDecision(arc, (int) Math.floor(value), false));
			candidates.add(candidate);
		}
		return candidates;
	}
}
