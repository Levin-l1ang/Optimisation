
public class Passenger {
	private int weight;
	private final DemandPoint origin_demand_point;
	private final DemandPoint destination_demand_point;
	private Stop origin_stop;
	private Stop destination_stop;
	private double traveling_time;
	private double upper_bound_interval;

//Constructor-------------------------------------------------------------------------
	public Passenger(DemandPoint origin, DemandPoint destination){
		origin_demand_point=origin;
		destination_demand_point=destination;
		this.weight=0;
	}

//Getter-------------------------------------------------------------------------------
	public double getWeight(){
		return weight;
	}

	public DemandPoint getOriginDemandPoint(){
		return origin_demand_point;
	}

	public DemandPoint getDestinationDemandPoint(){
		return destination_demand_point;
	}

	public Stop getOriginStop(){
		return origin_stop;
	}

	public Stop getDestinaitonStop(){
		return destination_stop;
	}

	public double getTravelingTime(){
		return traveling_time;
	}

	public boolean isEmpty(){
		return weight==0;
	}
//Setter---------------------------------------------------------------------------------
	public void setOriginStop(Stop origin_stop){
		this.origin_stop=origin_stop;
	}

	public void setDestinaitonStop(Stop destination_stop){
		this.destination_stop = destination_stop;
	}

	public void setTravelingTime(double traveling_time){
		this.traveling_time=traveling_time;
	}

	public void setUpperBoundInterval(double upper_bound_interval) { this.upper_bound_interval = upper_bound_interval; }

	public double getUpperBoundInterval() { return this.upper_bound_interval; }

	public void setWeight(int weight) { this.weight = weight; }
}
