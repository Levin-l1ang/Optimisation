

public class NonPeriodicChangingActivity extends NonPeriodicActivity
{
	/************************************************************************
	 * private data fields                                                  *
	 ************************************************************************/

	/**
	 * The status of the changing activity.
	 * 	0  = activity is fulfilled
	 * 	1  = activity is not fulfilled
	 * 	-1 = initial setting
	 */
	private int z;



	/************************************************************************
	 * constructors                                                         *
	 ************************************************************************/

	public NonPeriodicChangingActivity(int ID, NonPeriodicEvent source,
	                        NonPeriodicEvent target, int lowerBound, int upperBound,
	                        double weight, String type, int periodicID)
	{
		super(ID, source, target, lowerBound, upperBound, weight, type, periodicID);
		this.z = -1;
	}



	/************************************************************************
	 * getter/setter                                                        *
	 ************************************************************************/

	public int getZ()
	{
		return this.z;
	}

	public void setZ(int z)
	{
		this.z = z;
	}



	/************************************************************************
	 * misc functions                                                       *
	 ************************************************************************/

	@Override 
	public String toString()
	{
		StringBuilder string = new StringBuilder(256);
		string.append(super.toString());
		string.append("; z: ");
		string.append(this.z);
		return string.toString();
	}
}
