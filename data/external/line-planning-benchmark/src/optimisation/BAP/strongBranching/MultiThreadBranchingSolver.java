package optimisation.BAP.strongBranching;

import java.io.File;
import java.util.Map;

import ilog.cplex.IloCplex;
import util.Configuration;

public class MultiThreadBranchingSolver<T extends Object> implements Runnable
{
	private final Map<T, Double> minDeltaMap;
	private final Map<T, Double> maxDeltaMap;
	private final T object;
	private final String modelFile;

	public MultiThreadBranchingSolver(Map<T, Double> minDeltaMap, Map<T, Double> maxDeltaMap, T object, String modelFile)
	{
		this.minDeltaMap = minDeltaMap;
		this.maxDeltaMap = maxDeltaMap;
		this.object = object;
		this.modelFile = modelFile;
	}

	@Override
	public void run()
	{
		try
		{
			IloCplex model = new IloCplex();
			model.setOut(null);
			model.importModel(modelFile);
			int numThreads = Configuration.getConfiguration().getIntProperty("NUM_THREADS_STRONG_BRANCHING_SOLVER");
			model.setParam(IloCplex.Param.Threads, numThreads);
			model.solve();
			double delta = model.getObjValue();
			model.clearModel();
			model.close();
			
			if (minDeltaMap.containsKey(object))
			{
				minDeltaMap.put(object, Math.min(minDeltaMap.get(object), delta));
			}
			else
			{
				minDeltaMap.put(object, delta);
			}
			if (maxDeltaMap.containsKey(object))
			{
				maxDeltaMap.put(object, Math.max(maxDeltaMap.get(object), delta));
			}
			else
			{
				maxDeltaMap.put(object, delta);
			}
			
			// Delete file.
			File file = new File(modelFile);
			file.delete();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
