import java.util.*;

public class FullPeriodicEvent extends Event{
	
	public Stop station;
	public int line;
	public String type;
	public double passenger;
	
	public FullPeriodicEvent(int index, String type, Stop station, int line, double passenger){
		super(index, station.getIndex(), passenger, type.equals("arrival")?true:false,false,false);
		this.type = type;
		this.station = station;
		this.line = line;
	}
	
	public Stop getFullStation(){
		return this.station;
	}
	
	public int getLine(){
		return this.line;
	}
	
	public String getType(){
		return this.type;
	}
	
	public String toString(){
		return this.type + ", " + this.station.toString() + ", " + this.line;
	}
}
