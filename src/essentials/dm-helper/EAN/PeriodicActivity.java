

public class PeriodicActivity extends Activity
{
	/************************************************************************
	 * private data fields                                                  *
	 ************************************************************************/

	private PeriodicEvent source;
	private PeriodicEvent target;
	private int upperBound;



	/************************************************************************
	 * constructor                                                          *
	 ************************************************************************/

	public PeriodicActivity(int ID, PeriodicEvent source, PeriodicEvent target,
	                        int lowerBound, int upperBound, double weight, String type)
	{
		super(ID, lowerBound, -1, weight, type);
		this.source = source;
		this.target = target;
		this.upperBound = upperBound;
	}



	/************************************************************************
	 * getter/setter                                                        *
	 ************************************************************************/

	public PeriodicEvent getSource()
	{
		return this.source;
	}

	public void setSource(PeriodicEvent source)
	{
		this.source = source;
	}



	public PeriodicEvent getTarget()
	{
		return this.target;
	}

	public void setTarget(PeriodicEvent target)
	{
		this.target = target;
	}



	public int getUpperBound()
	{
		return upperBound;
	}

	public void setUpperBound(int upperBound)
	{
		this.upperBound = upperBound;
	}



	/************************************************************************
	 * misc functions                                                       *
	 ************************************************************************/

	@Override 
	public String toString()
	{
		StringBuilder string = new StringBuilder(256);
		string.append(super.toString());
		string.append("; events: ");
		string.append(this.source.getID());
		string.append("->");
		string.append(this.target.getID());
		string.append("; upper bound: ");
		string.append(this.upperBound);
		return string.toString();
	}
}
