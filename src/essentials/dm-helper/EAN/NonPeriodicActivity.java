
public class NonPeriodicActivity extends Activity implements Comparable<NonPeriodicActivity>
{
	/************************************************************************
	 * private data fields                                                  *
	 ************************************************************************/

	private NonPeriodicEvent source;
	private NonPeriodicEvent target;
	private int periodicID;
	private int sourceDelay;
	private int delayKnownTime; //edit by Robert Wichmann



	/************************************************************************
	 * constructor                                                          *
	 ************************************************************************/

	/*public NonPeriodicActivity(int ID, NonPeriodicEvent source,
	                           NonPeriodicEvent target, int lowerBound,
	                           double weight, String type, int periodicID)
	{
		super(ID, lowerBound, -1, weight, type);
		this.source = source;
		this.target = target;
		this.periodicID = periodicID;
		this.sourceDelay = 0;
		this.delayKnownTime = this.getSource().getTime();
	}*/

	public NonPeriodicActivity(int ID, NonPeriodicEvent source,
	                           NonPeriodicEvent target, int lowerBound, int upperBound,
	                           double weight, String type, int periodicID)
	{
		super(ID, lowerBound, upperBound, weight, type);
		this.source = source;
		this.target = target;
		this.periodicID = periodicID;
		this.sourceDelay = 0;
		this.delayKnownTime = this.getSource().getTime();
	}



	/************************************************************************
	 * getter/setter                                                        *
	 ************************************************************************/

	public NonPeriodicEvent getSource()
	{
		return this.source;
	}

	public void setSource(NonPeriodicEvent source)
	{
		this.source = source;
	}



	public NonPeriodicEvent getTarget()
	{
		return this.target;
	}

	public void setTarget(NonPeriodicEvent target)
	{
		this.target = target;
	}



	public int getPeriodicID()
	{
		return periodicID;
	}

	public void setPeriodicID(int periodicID)
	{
		this.periodicID = periodicID;
	}



	public int getSourceDelay()
	{
		return sourceDelay;
	}

	public void setSourceDelay(int sourceDelay)
	{
		this.sourceDelay = sourceDelay;
	}

	/**************************************************************************/

	public int getDelayKnownTime() 
	{
		return delayKnownTime;		
	}

	public void setDelayKnownTime(int time) 
	{
		this.delayKnownTime = time;
	}


	// Vergleicht Kanten anhand der fahrplanmäßigen Zeit ihrer Zielknoten

	public int compareTo(NonPeriodicActivity a) {
		if(this.getTarget().getTime() > a.getTarget().getTime())
			return 1;
		if(this.getTarget().getTime() < a.getTarget().getTime())
			return -1;
		return 0;
	}

	/**************************************************************************/

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
		string.append("; periodicID: ");
		string.append(this.periodicID);
		string.append("; source delay: ");
		string.append(this.sourceDelay);
		return string.toString();
	}
}
