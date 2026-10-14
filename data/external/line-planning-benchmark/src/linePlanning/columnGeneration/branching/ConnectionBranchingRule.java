package linePlanning.columnGeneration.branching;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import linePlanning.PTN.Connection;
import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.lineGeneration.Line;
import optimisation.BAP.AbstractBranchingRule;
import optimisation.BAP.BAPNode;
import optimisation.BAP.BranchingCandidate;
import util.Configuration;

public class ConnectionBranchingRule
		extends AbstractBranchingRule<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	public ConnectionBranchingRule(double priority)
	{
		super(priority);
	}

	@Override
	public List<BranchingCandidate<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> getBranchingCandidates(
			BAPNode<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> parent)
	{
		// Construct a mapping of fractional value per connection.
		Map<Connection, Double> connectionMap = new LinkedHashMap<>();
		for (Entry<LinePlanningColumn, Double> entry : parent.getSolution().getColumnMap().entrySet())
		{
			double value = entry.getValue();
			if (entry.getKey() instanceof LineFrequencyColumn)
			{
				LineFrequencyColumn lineColumn = (LineFrequencyColumn) entry.getKey();
				Line line = lineColumn.getLine();
				for (Connection connection : line.getConnectionPathForward())
				{
					connectionMap.put(connection, connectionMap.getOrDefault(connection, 0.0) + value);
				}
				for (Connection connection : line.getConnectionPathBackward())
				{
					connectionMap.put(connection, connectionMap.getOrDefault(connection, 0.0) + value);
				}
			}
		}

		// Generate candidates.
		double PRECISION = Configuration.getConfiguration().getDoubleProperty("PRECISION");
		List<BranchingCandidate<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> candidates = new ArrayList<>();
		for (Entry<Connection, Double> entry : connectionMap.entrySet())
		{
			double value = entry.getValue();
			double fractionalValue = Math.abs(value - Math.rint(value));
			if (fractionalValue < PRECISION)
			{
				continue;
			}
			Connection connection = entry.getKey();

			BranchingCandidate<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> candidate = new BranchingCandidate<>(
					fractionalValue);
			candidate.addBranchingDecision(new ConnectionBranchingDecision(connection, (int) Math.ceil(value), true));
			candidate.addBranchingDecision(new ConnectionBranchingDecision(connection, (int) Math.floor(value), false));
			candidates.add(candidate);
		}
		return candidates;
	}
}
