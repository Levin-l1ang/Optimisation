import java.util.*;

/**
 * A representation of a passenger path in the extended rollout or the DM1-delay management problem.
 *
 */
public class Path {
	
	private int weight;	
	private NonPeriodicEvent source;
	private NonPeriodicEvent target;
	private LinkedList<NonPeriodicChangingActivity> changes;
	
	/**
	 * Generates a new path out of a given weight, source, target and changes. It happens no cloning, so i.e. by changing the 
	 * changes of the path, the given list is changed
	 * @param weight the weight of the new path
	 * @param source the source of the new path
	 * @param target the target of the new path
	 * @param changes the changes of the new path
	 */
	public Path(int weight, NonPeriodicEvent source, NonPeriodicEvent target, LinkedList<NonPeriodicChangingActivity> changes){
		this.weight=weight;
		this.source=source;
		this.target=target;
		this.changes=changes;
	}
	
	/**
	 * Generates a new path without changes out of the given weight, source and target.
	 * @param weight
	 * @param source
	 * @param target
	 */
	public Path(int weight, NonPeriodicEvent source, NonPeriodicEvent target){
		this(weight,source,target,new LinkedList<NonPeriodicChangingActivity>());
	}
	
	/**
	 * Adds the given change activity to the path. There is no check if this results in a feasible way!
	 * @param change The new change for the path
	 */
	public void addChange(NonPeriodicChangingActivity change){
		if(change==null){
			return;
		}
		changes.add(change);
	}
	
	/**
	 * Returns the weight of the path.
	 * @return the weight of the path
	 */
	public int getWeight(){
		return weight;
	}
	
	/**
	 * Returns the source event of the path
	 * @return the source of the path
	 */
	public NonPeriodicEvent getSource(){
		return source;
	}
	
	/**
	 * Returns the target event of the path.
	 * @return the target of the path
	 */
	public NonPeriodicEvent getTarget(){
		return target;
	}
	
	/**
	 * Returns a list of the changes of the path. It happens no cloning, so by changing the returned list, the
	 * changes of the path are modified!
	 * @return The changes of the path
	 */
	public LinkedList<NonPeriodicChangingActivity> getChanges(){
		return changes;
	}
	
	@Override
	public String toString(){
		String return_string="Origin: "+source.getID()+", Destination: "+target.getID()+", Weight: "+weight+", Changes: ";
		for(NonPeriodicChangingActivity change:changes){
			return_string+=change.getID()+", ";
		}
		return return_string;
	}

}
