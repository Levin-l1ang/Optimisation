package net.lintim.model;

public class Activity {

	private int index;
	private String type;
	private Event fromEvent;
	private Event toEvent;
	private int lowerBound;
	private int upperBound;
	private int passenger;

	public Activity(int index, String type, Event fromEvent, Event toEvent, int lowerBound, int upperBound, int passenger) {
		this.index = index;
		this.type = type;
		this.fromEvent = fromEvent;
		this.toEvent = toEvent;
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
		this.passenger = passenger;
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

	public Event getFromEvent() {
		return fromEvent;
	}

	public void setFromEvent(Event fromEvent) {
		this.fromEvent = fromEvent;
	}

	public Event getToEvent() {
		return toEvent;
	}

	public void setToEvent(Event toEvent) {
		this.toEvent = toEvent;
	}

	public int getLowerBound() {
		return lowerBound;
	}

	public void setLowerBound(int lowerBound) {
		this.lowerBound = lowerBound;
	}

	public int getUpperBound() {
		return upperBound;
	}

	public void setUpperBound(int upperBound) {
		this.upperBound = upperBound;
	}

	public int getPassenger() {
		return passenger;
	}

	public void setPassenger(int passenger) {
		this.passenger = passenger;
	}

}
