

public class NonPeriodicHeadwayActivity extends NonPeriodicActivity
{
	/************************************************************************
	 * private data fields                                                  *
	 ************************************************************************/

	private NonPeriodicHeadwayActivity correspodingHeadway;
	/**
	 * The status of the headways activity.
	 * 	0  = activity is fulfilled
	 * 	1  = activity is not fulfilled
	 * 	-1 = initial setting
	 */
	private int g;



	/************************************************************************
	 * constructors                                                         *
	 ************************************************************************/

	public NonPeriodicHeadwayActivity(int ID, NonPeriodicEvent source,
	                       NonPeriodicEvent target, int lowerBound, int upperBound,
	                       double weight, String type, int periodicID,
	                       NonPeriodicHeadwayActivity correspodingHeadway)
	{
		super(ID, source, target, lowerBound, upperBound, weight, type, periodicID);
		this.correspodingHeadway = correspodingHeadway;
		this.g = -1;
	}

	public NonPeriodicHeadwayActivity(int ID, NonPeriodicEvent source,
	                       NonPeriodicEvent target, int lowerBound, int upperBound,
                           double weight, String type, int periodicID)
	{
		super(ID, source, target, lowerBound, upperBound, weight, type, periodicID);
		this.correspodingHeadway = null;
		this.g = -1;
	}



	/************************************************************************
	 * getter/setter                                                        *
	 ************************************************************************/

	public NonPeriodicHeadwayActivity getCorrespodingHeadway()
	{
		return this.correspodingHeadway;
	}

	public void setCorrespodingHeadway(NonPeriodicHeadwayActivity correspodingHeadway)
	{
		this.correspodingHeadway = correspodingHeadway;
	}



	public int getG()
	{
		return this.g;
	}

	public void setG(int g)
	{
		this.g = g;
	}



	/************************************************************************
	 * misc functions                                                       *
	 ************************************************************************/

	@Override 
	public String toString()
	{
		StringBuilder string = new StringBuilder(256);
		string.append(super.toString());
		string.append("; corresponding headway: ");
		if (correspodingHeadway != null)
			string.append(correspodingHeadway.getID());
		else
			string.append("null");
		string.append("; g: ");
		string.append(g);
		return string.toString();
	}
}
