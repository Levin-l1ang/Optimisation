package optimisation.BAP.primalHeuristics;

import ilog.concert.IloException;
import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.AbstractSolution;
import optimisation.columnGeneration.ColumnGeneration;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;

public abstract class AbstractPrimalHeuristic<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	public abstract AbstractSolution<T, U, V> applyHeuristic(AbstractMasterProblem<T, U, V> masterProblem,
			ColumnGeneration<T, U, V> columnGeneration, double initialLowerBound, long timeLimit) throws IloException;
}
