package linePlanning.routeGeneration;

import graph.structures.digraph.DirectedGraphArc;
import graph.structures.digraph.DirectedGraphNodeIndex;
import graph.structures.digraph.activation.ActivationFunction;
import linePlanning.CGN.CGNArc;
import linePlanning.DPTN.DPTNArc;
import linePlanning.DPTN.DPTNDrivingArc;
import linePlanning.DPTN.DPTNDwellArc;
import linePlanning.DPTN.DPTNTransferArc;
import linePlanning.DPTN.DPTNArc.DEPTNArcType;

public class RouteActivationFunction implements ActivationFunction
{
	@Override
	public boolean isActiveNode(DirectedGraphNodeIndex node)
	{
		return true;
	}

	@Override
	public boolean isActiveArc(DirectedGraphArc<?, ?> arc)
	{
		if (arc.getData() instanceof CGNArc)
		{
			return true;
		}
		if (arc.getData() instanceof DPTNArc)
		{
			if (arc.getData() instanceof DPTNDrivingArc)
			{
				return true;
			}
			if (arc.getData() instanceof DPTNDwellArc)
			{
				return true;
			}
			if (arc.getData() instanceof DPTNTransferArc)
			{
				return true;
			}
			if (arc.getData() instanceof DPTNArc)
			{
				DPTNArc fdesnArc = (DPTNArc) arc.getData();
				if (fdesnArc.getArcType().equals(DEPTNArcType.BOARD))
				{
					return true;
				}
			}
		}
		return false;
	}
}
