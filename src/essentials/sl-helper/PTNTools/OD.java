import java.util.*;

public class OD {
	private HashMap<Stop,HashMap<Stop,Double>> od;
	
	public OD(){
		od=new HashMap<Stop,HashMap<Stop,Double>>();
	}
	
//get-set-methods-----------------------------------------------------------------------------------
	public double getPassengersAt(Stop origin, Stop destination){
		double value=0;
		if(od.containsKey(origin) && od.get(origin)!=null && od.get(origin).containsKey(destination))
			value=od.get(origin).get(destination);
		return value;
	}
	
	public void setPassengersAt(Stop origin, Stop destination, double value){
		if(!od.containsKey(origin) || od.get(origin) == null){
			od.put(origin, new HashMap<Stop,Double>());
		}
		od.get(origin).put(destination, value);
	}
	
	public void addPassenger(Passenger passenger){
		Stop origin=passenger.getOriginStop();
		Stop destination=passenger.getDestinaitonStop();
		if(!od.containsKey(origin) || od.get(origin) == null){
			od.put(origin, new HashMap<Stop,Double>());
		}
		if(!od.get(origin).containsKey(destination) || od.get(origin).get(destination) == null){
			od.get(origin).put(destination, 0.);
		}
		double old_value=od.get(origin).get(destination);
		od.get(origin).put(destination, passenger.getWeight()+old_value);
	}
	
	public HashMap<Stop,Double> getPassengersByOrigin(Stop origin){
		return od.get(origin);
	}
	
	public Set<Stop> getOrigins(){
		return od.keySet();
	}
}
