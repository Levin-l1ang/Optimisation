package optimisation.BAP.enumeration;

import java.util.List;

import ilog.concert.IloException;
import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import optimisation.columnGeneration.pricing.AbstractPricingProblemSolver;
import util.Pair;

public interface AbstractEnumerationRoutine<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	public abstract List<Pair<U, Double>> enumerateColumns(AbstractMasterProblem<T, U, V> masterProblem,
			AbstractPricingProblemSolver<T, U, V> pricingProblemSolver, T instance, double gap, int maxNumberColumns) throws IloException;
}
