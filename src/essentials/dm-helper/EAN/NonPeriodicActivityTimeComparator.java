import java.util.Comparator;

/**
 *	Comparator um die Verspätungen auf Aktivitäten nach ihrem Bekanntwerden zu
 * 	zu sortieren.
 */


public class NonPeriodicActivityTimeComparator implements Comparator<NonPeriodicActivity>
{
	@Override
	public int compare(NonPeriodicActivity a1, NonPeriodicActivity a2)
	{
		if (a1.getDelayKnownTime() < a2.getDelayKnownTime())
			return -1;
		else if (a1.getDelayKnownTime() == a2.getDelayKnownTime())
			return 0;
		else return 1;
	}
}
