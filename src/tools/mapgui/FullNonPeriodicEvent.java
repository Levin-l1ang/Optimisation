public class FullNonPeriodicEvent extends NonPeriodicEvent{
	
	FullPeriodicEvent pe;
	
	public FullNonPeriodicEvent(NonPeriodicEvent ne, FullPeriodicEvent pe){
		super(ne.getID(), ne.getTime(), ne.getWeight(), ne.isArrivalEvent(), ne.isStartofTrip(), ne.isEndOfTrip(), pe.getID());
		this.setDispoTime(ne.getDispoTime());
		this.pe=pe;
	}
	
	public FullNonPeriodicEvent(int ID, int time, double weight, boolean isArrivalEvent, FullPeriodicEvent pe){
		super(ID, time, weight, isArrivalEvent, false, false, pe.getID());
		this.pe = pe;
	}
	
	public FullPeriodicEvent getFullPeriodicEvent(){
		return this.pe;
	}
}
