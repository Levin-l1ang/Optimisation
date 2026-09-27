public class CollapsedActivity {

	private NonPeriodicActivity[] originalActivities;
	private CollapsedEvent source, target;

	public CollapsedActivity(CollapsedEvent source, CollapsedEvent target,
			NonPeriodicActivity[] originalActivities) {
		this.source = source;
		this.target = target;
		this.originalActivities = originalActivities;
	}
	
	public CollapsedEvent getSource() {
		return source;
	}

	public void setSource(CollapsedEvent source) {
		this.source = source;
	}

	public CollapsedEvent getTarget() {
		return target;
	}

	public void setTarget(CollapsedEvent target) {
		this.target = target;
	}

	public NonPeriodicActivity[] getOriginalActivities() {
		return originalActivities;
	}
	
}
