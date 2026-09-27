import java.util.LinkedList;

public class CollapsedEvent {

	private NonPeriodicEvent originalEvent;
	private int time;
	private LinkedList<CollapsedActivity> outgoingActivities;
	private int ID;
	
	public CollapsedEvent(NonPeriodicEvent originalEvent, int time) {
		this.originalEvent = originalEvent;
		this.time = time;
		this.outgoingActivities = new LinkedList<CollapsedActivity>();
	}

	public NonPeriodicEvent getOriginalEvent() {
		return originalEvent;
	}

	public void setOriginalEvent(NonPeriodicEvent originalEvent) {
		this.originalEvent = originalEvent;
	}

	public int getTime() {
		return time;
	}

	public void setTime(int time) {
		this.time = time;
	}

	public CollapsedActivity[] getOutgoingActivities() {
		return outgoingActivities.toArray(new CollapsedActivity[0]);
		//return outgoingActivities.iterator();
	}
	
	public void addOutgoingActivity(CollapsedActivity a) {
		if (!outgoingActivities.contains(a))
			outgoingActivities.add(a);
	}

	public int getID() {
		return ID;
	}

	public void setID(int iD) {
		ID = iD;
	}
	
}
