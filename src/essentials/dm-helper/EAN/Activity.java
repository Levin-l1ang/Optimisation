

public abstract class Activity
{
	/************************************************************************
	 * private data fields                                                  *
	 ************************************************************************/

	private int ID;
	private int lowerBound;
	private int upperBound;
	private double weight;
	private String type;



	/************************************************************************
	 * constructor                                                          *
	 ************************************************************************/

	public Activity(int ID, int lowerBound, int upperBound, double weight,
	                String type)
	{
		this.ID = ID;
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
		this.weight = weight;
		this.type = type;
	}



	/************************************************************************
	 * getter/setter                                                        *
	 ************************************************************************/

	public int getID()
	{
		return this.ID;
	}

	public void setID(int ID)
	{
		this.ID = ID;
	}



	public int getLowerBound()
	{
		return this.lowerBound;
	}

	public void setLowerBound(int lowerBound)
	{
		this.lowerBound = lowerBound;
	}



	public int getUpperBound()
	{
		return this.upperBound;
	}

	public void setUpperBound(int upperBound)
	{
		this.upperBound = upperBound;
	}



	public double getWeight()
	{
		return weight;
	}

	public void setWeight(double weight)
	{
		this.weight = weight;
	}



	public String getType()
	{
		return this.type;
	}

	public void setType(String type)
	{
		this.type = type;
	}



	/************************************************************************
	 * misc functions                                                       *
	 ************************************************************************/

	@Override 
	public String toString()
	{
		StringBuilder string = new StringBuilder(256);
		string.append("Activity: ID: ");
		string.append(this.ID);
		string.append("; lower bound: ");
		string.append(this.lowerBound);
		string.append("; upper bound: ");
		string.append(this.upperBound);
		string.append("; weight: ");
		string.append(this.weight);
		string.append("; type: ");
		string.append(this.type);
		return string.toString();
	}
}
