package linePlanning.lineGeneration;

import graph.algorithms.shortestPath.arcWeightFunctions.AbstractArcWeightFunction;
import graph.structures.digraph.DirectedGraphArc;
import linePlanning.DPTN.DPTNDrivingArc;
import linePlanning.instance.StationNode;
import linePlanning.instance.Track;

public class DistanceWeightFunction<T> extends AbstractArcWeightFunction<StationNode, T>
{
	@Override
	public double getArcWeight(DirectedGraphArc<StationNode, T> arc)
	{
		if (arc.getData() instanceof DPTNDrivingArc)
		{
			DPTNDrivingArc drivingArc = (DPTNDrivingArc) arc.getData();
			return drivingArc.getDistance();
		}
		if (arc.getData() instanceof Track)
		{
			Track track = (Track) arc.getData();
			return track.getLength();
		}
		return 0;
	}
}
