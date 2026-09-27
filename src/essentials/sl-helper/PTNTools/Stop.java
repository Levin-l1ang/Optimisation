import java.util.*;

public class Stop implements Comparable<Stop>{
	private static String header= "";

    private int index;
    private String short_name;
    private String long_name;
    private double x_coordinate;
    private double y_coordinate;
    private ArrayList<Integer> listOfPassingLines;

//Constructor-----------------------------------------------------------------
    public Stop(Integer index, String shortName, String longName,
    		Double x_coordinate, Double y_coordinate){

    	 this.index = index;
         this.short_name = shortName;
         this.long_name = longName;
         this.x_coordinate= x_coordinate;
         this.y_coordinate= y_coordinate;
    }


//Setter-----------------------------------------------------------------------
    public void setIndex(Integer index) {
        this.index = index;
    }

    public void setShort_name(String shortName) {
        this.short_name = shortName;
    }

    public void setLong_name(String longName) {
        this.long_name = longName;
    }


//Getter--------------------------------------------------------------------------
    public int getIndex() {
        return index;
    }

    public String getShort_name() {
        return short_name;
    }

    public String getLong_name() {
        return long_name;
    }

    public double getX_coordinate(){
    	return x_coordinate;
    }

    public double getY_coordinate(){
    	return y_coordinate;
    }

    public void addLine(Integer lineIndex){
		if (this.listOfPassingLines == null)
			this.listOfPassingLines = new ArrayList<Integer>();
		if(!this.listOfPassingLines.contains(lineIndex))
			this.listOfPassingLines.add(lineIndex);
	}

	public ArrayList<Integer> getListOfPassingLines(){
		return this.listOfPassingLines;
	}
    public void resetListOfPassingLines(){
	this.listOfPassingLines = new ArrayList<Integer>();
}

//CompareTo-------------------------------------------------------------------------

    @Override
    public int compareTo(Stop o) {
        return index-o.index;
    }

 //equals---------------------------------------------------------------------------
    public boolean equals(Object o){
    	if(o==null)
    		return false;
    	if(!(o instanceof Stop))
    		return false;
    	Stop other= (Stop) o;
    	if(other.getIndex()==index)
    		return true;
    	return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, x_coordinate, y_coordinate);
    }

    //CSV----------------------------------------------------------------------------------
    public static String printHeader(){
    	return "# "+header;
    }

    public String toCSV(){
    	return index + "; " + short_name + "; "+ long_name + "; " + x_coordinate + "; " + y_coordinate;
    }

         //static methods--------------------------------------------------------------------------
  	public static void setHeader(String head){
  		header=head;
  	}



}
