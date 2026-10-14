package linePlanning.columnGeneration.branching;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import linePlanning.columnGeneration.columns.LineActivationColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.lineGeneration.Line;
import optimisation.BAP.AbstractBranchingRule;
import optimisation.BAP.BAPNode;
import optimisation.BAP.BranchingCandidate;
import util.Configuration;

public class LineActivationBranchingRule
		extends AbstractBranchingRule<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	public LineActivationBranchingRule(double priority)
	{
		super(priority);
	}

	@Override
	public List<BranchingCandidate<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> getBranchingCandidates(
			BAPNode<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> parent)
	{
		double PRECISION = Configuration.getConfiguration().getDoubleProperty("PRECISION");
		List<BranchingCandidate<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> candidates = new ArrayList<>();
		for (Entry<LinePlanningColumn, Double> entry : parent.getSolution().getColumnMap().entrySet())
		{
			double value = entry.getValue();
			double fractionalValue = Math.abs(value - Math.rint(value));
			if (fractionalValue < PRECISION)
			{
				continue;
			}
			if (!(entry.getKey() instanceof LineActivationColumn))
			{
				continue;
			}
			LineActivationColumn lineActivationColumn = (LineActivationColumn) entry.getKey();
			Line line = lineActivationColumn.getLine();

			BranchingCandidate<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> candidate = new BranchingCandidate<>(
					fractionalValue);
			candidate.addBranchingDecision(new LineActivationBranchingDecision(line, true));
			candidate.addBranchingDecision(new LineActivationBranchingDecision(line, false));
			candidates.add(candidate);
		}
		return candidates;
	}
}
