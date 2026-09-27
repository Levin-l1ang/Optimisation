package net.lintim.model;

public class PeriodicActivity {

	private int index;
	private int periodic_index;
	private String type;
	private PeriodicEvent fromEvent;
	private PeriodicEvent toEvent;
	private int lowerBound;
	private double passenger;
	private int delay;

	public PeriodicActivity(int index, int periodic_index, String type, PeriodicEvent fromEvent, PeriodicEvent toEvent, int lowerBound, double passenger, int delay) {
		this.index = index;
		this.periodic_index = periodic_index;
		this.type = type;
		this.fromEvent = fromEvent;
		this.toEvent = toEvent;
		this.lowerBound = lowerBound;
		this.passenger = passenger;
		this.delay = delay;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public PeriodicEvent getFromEvent() {
		return fromEvent;
	}

	public void setFromEvent(PeriodicEvent fromEvent) {
		this.fromEvent = fromEvent;
	}

	public PeriodicEvent getToEvent() {
		return toEvent;
	}

	public void setToEvent(PeriodicEvent toEvent) {
		this.toEvent = toEvent;
	}

	public int getLowerBound() {
		return lowerBound;
	}

	public void setLowerBound(int lowerBound) {
		this.lowerBound = lowerBound;
	}

	public int getPeriodic_index() {
		return periodic_index;
	}

	public void setPeriodic_index(int periodic_index) {
		this.periodic_index = periodic_index;
	}

	public double getPassenger() {
		return passenger;
	}

	public void setPassenger(double passenger) {
		this.passenger = passenger;
	}

	public int getDelay() {
		return delay;
	}

	public void setDelay(int delay) {
		this.delay = delay;
	}

}
