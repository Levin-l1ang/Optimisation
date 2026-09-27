package net.lintim.util;

/**
 * A class representing an unsorted pair, i.e. (1,3) = (3,1).
 */
public class Pair<T> {
	/**
	 * The first object
	 */
	private final T first;
	/**
	 * The second object
	 */
	private final T second;

	/**
	 * Create a new pair containing the given two objects. The order of the given elements only influences the getters of
	 * the class
	 * @param first the first object
	 * @param second the second object
	 */
	public Pair(T first, T second){
		this.first = first;
		this.second = second;
	}

	/**
	 * Get the first object, i.e. the object provided first to the constructor
	 * @return the first object
	 */
	public T getFirst(){
		return first;
	}

	/**
	 * Get the second object, i.e. the object provided second to the constructor
	 * @return the first object
	 */
	public T getSecond(){
		return second;
	}

	@Override
	public String toString(){
		return "(" + first.toString() + ", " + second.toString() + ")";
	}

	@Override
	public boolean equals(Object other){
		if(this == other){
			return true;
		}
		if(null == other){
			return false;
		}
		if(!(other instanceof Pair)){
			return false;
		}
		Pair otherPair = (Pair) other;
		return (otherPair.first.equals(first) && otherPair.second.equals(second)) ||
				(otherPair.second.equals(first) && otherPair.first.equals(second));
	}

	@Override
	public int hashCode(){
		int result = 17;
		result = 31 * result + first.hashCode() + second.hashCode();
		return result;
	}
}
