package net.lintim.model;


public class PeriodicEvent {
	private Integer index;
	private Integer periodic_index;
	private String type;
	private Long time;
	private Double passengers;
	private int delay;

	public PeriodicEvent(Integer index, Integer periodic_index, String type, Long time, Double passengers, int delay) {
		this.index = index;
		this.periodic_index = periodic_index;
		this.type = type;
		this.time = time;
		this.passengers = passengers;
		this.delay = delay;
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

	public Integer getPeriodic_index() {
		return periodic_index;
	}

	public void setPeriodic_index(Integer periodic_index) {
		this.periodic_index = periodic_index;
	}

	public Long getTime() {
		return time;
	}

	public void setTime(Long time) {
		this.time = time;
	}

	public Double getPassengers() {
		return passengers;
	}

	public void setPassengers(Double passengers) {
		this.passengers = passengers;
	}

	public int getDelay() {
		return delay;
	}

	public void setDelay(int delay) {
		this.delay = delay;
	}

}
