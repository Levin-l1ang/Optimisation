import java.util.*;

public class Demand {
	private LinkedList<DemandPoint> demand_points;
	
	public Demand(){
		demand_points= new LinkedList<DemandPoint>();
	}
	
//Getter-----------------------------------------------------------------
	public LinkedList<DemandPoint> getDemand_points(){
		return demand_points;
	}

	
//Methods----------------------------------------------------------------
	public void addDemandPoint(DemandPoint demand_point){
		demand_points.add(demand_point);
	}

	// If demand points are covered by existing nodes, those are removed previous to the optimization step
	
	public void removeCoveredDemandPoints(PTN ptn, Distance distance, double radius){
		Iterator<DemandPoint> it_demand=demand_points.iterator();
		Iterator<Stop> it_stop;
		LinkedList<Stop> stops=ptn.getStops();
		DemandPoint current_demand_point;
		Stop current_stop;
		while(it_demand.hasNext()){
			current_demand_point=it_demand.next();
			it_stop=stops.iterator();
			while(it_stop.hasNext()){
				current_stop=it_stop.next();
				if(current_demand_point.isCoveredBy(current_stop, radius, distance)){
					it_demand.remove();
					break;
				}
			}
		}
	}
	
	
	public void removeUncoveredDemandPoint(PTN ptn, Distance distance, double radius){
		Iterator<DemandPoint> it_demand=demand_points.iterator();
		DemandPoint current_demand_point;
		boolean covered=false;
		while(it_demand.hasNext()){
			current_demand_point=it_demand.next();
			covered=false;
			for(Stop stop: ptn.getStops()){
				if(current_demand_point.isCoveredBy(stop, radius, distance)){
					covered=true;
					break;
				}
			}
			if(!covered) {
				it_demand.remove();
			}
		}
	}

}
