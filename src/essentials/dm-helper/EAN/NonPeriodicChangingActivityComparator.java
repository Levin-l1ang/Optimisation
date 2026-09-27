

import java.util.Comparator;



public class NonPeriodicChangingActivityComparator implements Comparator<NonPeriodicChangingActivity>
{
	@Override
	public int compare(NonPeriodicChangingActivity a1, NonPeriodicChangingActivity a2)
	{
		if (a1.getWeight() < a2.getWeight())
			return -1;
		else if (a1.getWeight() == a2.getWeight())
			return 0;
		else return 1;
	}
}
