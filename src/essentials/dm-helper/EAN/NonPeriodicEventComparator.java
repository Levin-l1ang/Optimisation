import java.util.Comparator;

/**
 * Compared nonperiodic events first basend on the delayed time, then on the event id.
 *
 */
public class NonPeriodicEventComparator implements Comparator<NonPeriodicEvent> {

	@Override
	public int compare(NonPeriodicEvent arg0, NonPeriodicEvent arg1) {
		if (arg0.getDispoTime() < arg1.getDispoTime())
			return -1;
		else if (arg0.getDispoTime() > arg1.getDispoTime())
			return 1;
		else if (arg0.getID() < arg1.getID())
			return -1;
		else if (arg0.getID() > arg1.getID())
			return 1;
		else return 0;
	}

}
