import net.lintim.algorithm.lineplanning.LinePlanningCostSolver;
import net.lintim.model.Graph;
import net.lintim.model.Link;
import net.lintim.model.impl.ArrayListGraph;
import net.lintim.solver.SolverParameters;
import net.lintim.util.Logger;

import java.util.HashMap;

/**
 * Class for transforming a feasibility problem of this architecture into a cost-problem in the "new" lintim core
 * structure.
 */
public class CostTransformer {
	private static Graph<net.lintim.model.Stop, Link> corePtn = null;
	private static HashMap<Link, Integer> originalMinLoads = null;
	private static final Logger logger = new Logger(CostTransformer.class);

	/**
	 * Will transform the parameters to appropriate input for the cost implementation and will call it. Will
	 * only transform the ptn, od matrix and config once! After the first call, only cached values will be used. Only
	 * te pool will be updated in each step
	 *
	 * @param ptn  the ptn
	 * @param pool the current line pool
	 * @param parameters the parameters for the line planning solver to use for the subproblem
	 * @return the solution for the cost line concept problem, or null if the solution is infeasible
	 */
	public static int[] transformAndSolve(PTN ptn, LinePool pool, SolverParameters parameters) {
		if (corePtn == null) {
			setPtn(ptn);
		}
		else {
			resetMinLoad();
		}
		// Now, transform the pool
		net.lintim.model.LinePool corePool = transformPool(pool, ptn);
		LinePlanningCostSolver solver = LinePlanningCostSolver.getLinePlanningCostSolver(parameters.getSolverType());
		boolean feasible = false;
		while (!feasible) {
			feasible = solver.solveLinePlanningCost(corePtn, corePool, parameters, false);
			if (!feasible) {
				logger.debug("\t\tReducing minimal loads");
				reduceMinLoads();
			}
		}
		int[] frequencies = new int[corePool.getLines().size()];
		for (int i = 0; i < frequencies.length; i++) {
			frequencies[i] = corePool.getLine(i + 1).getFrequency();
//            System.out.println("Line " + i + " freq " + frequencies[i]);
		}
		return frequencies;
	}

	private static void setPtn(PTN ptn) {
		corePtn = new ArrayListGraph<>();
		originalMinLoads = new HashMap<>();
		for (Stop stop : ptn.getStops()) {
			corePtn.addNode(new net.lintim.model.Stop(stop.getIndex(), stop.getShort_name(), stop.getLong_name(),
					stop.getX_coordinate(), stop.getY_coordinate()));
		}
		for (Edge edge : ptn.getEdges()) {
			Link coreLink = new Link(edge.getIndex(), corePtn.getNode(edge.getLeftStop().getIndex()), corePtn.getNode
					(edge.getRightStop().getIndex()), edge.getLength(), 0, 0, edge.isDirected());
			coreLink.setLoadInformation(edge.getWeight(), edge.getMinLoad(), edge.getMaxLoad());
			corePtn.addEdge(coreLink);
			originalMinLoads.put(coreLink, edge.getMinLoad());
		}
	}

	private static net.lintim.model.LinePool transformPool(LinePool pool, PTN ptn) {
		net.lintim.model.LinePool corePool = new net.lintim.model.LinePool();
		for (Line line : pool.getLines()) {
			net.lintim.model.Line coreLine = new net.lintim.model.Line(line.getIndex(), ptn.isDirected());
			for (Edge edge : line.getEdges()) {
				coreLine.addLink(corePtn.getEdge(edge.getIndex()));
			}
			coreLine.setCost(0);
			corePool.addLine(coreLine);
		}
		return corePool;
	}

	private static void resetMinLoad() {
		for(Link link: corePtn.getEdges()) {
			link.setLoadInformation(link.getLoad(), originalMinLoads.get(link), link.getUpperFrequencyBound());
		}
	}

	private static void reduceMinLoads() {
		for(Link link: corePtn.getEdges()) {
			link.setLoadInformation(link.getLoad(), link.getLowerFrequencyBound() - 1, link.getUpperFrequencyBound());
		}
	}
}
