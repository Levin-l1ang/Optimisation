package linePlanning.lineGeneration;

import graph.structures.digraph.DirectedGraphArc;
import graph.structures.digraph.DirectedGraphNodeIndex;
import graph.structures.digraph.activation.ActivationFunction;
import linePlanning.DPTN.DPTNArc;
import linePlanning.DPTN.DPTNDrivingArc;
import linePlanning.DPTN.DPTNDwellArc;
import linePlanning.DPTN.DPTNArc.DEPTNArcType;

public class LineActivationFunction implements ActivationFunction
{
	@Override
	public boolean isActiveNode(DirectedGraphNodeIndex node)
	{
		return true;
	}

	@Override
	public boolean isActiveArc(DirectedGraphArc<?, ?> arc)
	{
		DPTNArc a = (DPTNArc) arc.getData();
		if (a instanceof DPTNDrivingArc)
		{
			return true;
		}
		if (a instanceof DPTNDwellArc)
		{
			return true;
		}
		if (a.getArcType().equals(DEPTNArcType.TERMINAL))
		{
			return true;
		}
		return false;
	}
}
