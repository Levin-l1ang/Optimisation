import java.util.Comparator;

/**
 * A comparator to sort paths by their weights.
 *
 */
public class PathComparator implements Comparator<Path> {

	@Override
	public int compare(Path p1, Path p2)
	{
		if (p1.getWeight() < p2.getWeight())
			return -1;
		else if (p1.getWeight() == p2.getWeight())
			return 0;
		else return 1;
	}
	
}
