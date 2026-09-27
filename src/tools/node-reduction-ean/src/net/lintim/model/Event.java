package net.lintim.model;

import java.util.ArrayList;

public class Event {

	public enum LineDirection {
		FORWARDS, BACKWARDS
	}
	private Integer index;
	private String type;
	private Integer station;
	private Integer line;
	private Integer passengers;
	private int time;
	private LineDirection direction;
	private int frequencyRepetition;

	public Event(Integer index, String type, Integer station, Integer line, Integer passengers, LineDirection direction, int frequencyRepetition) {
		this.index = index;
		this.type = type;
		this.station = station;
		this.line = line;
		this.passengers = passengers;
		this.direction = direction;
		this.frequencyRepetition = frequencyRepetition;
	}

	public Event(Integer index, String type, Integer station, Integer line, Integer passengers, int time, LineDirection direction, int frequencyRepetition) {
		this.index = index;
		this.type = type;
		this.station = station;
		this.line = line;
		this.passengers = passengers;
		this.time = time;
		this.direction = direction;
		this.frequencyRepetition = frequencyRepetition;
	}

	public ArrayList<Activity> getIncomingActivities(ArrayList<Activity> listOfActivities) {
		ArrayList<Activity> listOfIncomingActivites = new ArrayList<Activity>();
		for (Activity activity : listOfActivities) {
			if (activity.getToEvent().getIndex() == this.index) {
				listOfIncomingActivites.add(activity);
			}
		}
		return listOfIncomingActivites;
	}



	public ArrayList<Activity> getIncomingActivitiesNoHead(ArrayList<Activity> listOfActivities) {
		ArrayList<Activity> listOfIncomingActivites = new ArrayList<Activity>();
		for (Activity activity : listOfActivities) {
			if (!activity.getType().equals("headway") && activity.getToEvent().getIndex() == this.index) {
				listOfIncomingActivites.add(activity);
			}
		}
		return listOfIncomingActivites;
	}

	public ArrayList<Activity> getOutgoingActivities(ArrayList<Activity> listOfActivities) {
		ArrayList<Activity> listOfOutgoingActivites = new ArrayList<Activity>();
		for (Activity activity : listOfActivities) {
			if (activity.getFromEvent().getIndex() == this.index) {
				listOfOutgoingActivites.add(activity);
			}
		}
		return listOfOutgoingActivites;
	}

	public ArrayList<Activity> getOutgoingActivitiesNoHead(ArrayList<Activity> listOfActivities) {
		ArrayList<Activity> listOfOutgoingActivites = new ArrayList<Activity>();
		for (Activity activity : listOfActivities) {
			if (!activity.getType().equals("headway") && activity.getFromEvent().getIndex() == this.index) {
				listOfOutgoingActivites.add(activity);
			}
		}
		return listOfOutgoingActivites;
	}

	public int getTime() {
		return time;
	}

	public void setTime(int time) {
		this.time = time;
	}

	public Integer getIndex() {
		return index;
	}

	public void setIndex(Integer index) {
		this.index = index;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Integer getStation() {
		return station;
	}

	public void setStation(Integer station) {
		this.station = station;
	}

	public Integer getLine() {
		return line;
	}

	public void setLine(Integer line) {
		this.line = line;
	}

	public Integer getPassengers() {
		return passengers;
	}

	public void setPassengers(Integer passengers) {
		this.passengers = passengers;
	}

	public LineDirection getLineDirection() {
		return direction;
	}

	public int getFrequencyRepetition(){
		return frequencyRepetition;
	}

	@Override
	public String toString() {
		return "Event [index=" + index + ", type=" + type + ", station=" + station + ", line=" + line + ", passengers=" + passengers + "]\n";
	}

}
