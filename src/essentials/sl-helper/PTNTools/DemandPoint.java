public class DemandPoint {
	private int id;
	private double x_coordinate;
	private double y_coordinate;
	private int demand;

	public DemandPoint(int id, double x_coordinate, double y_coordinate,
			int demand) {
		this.id=id;
		this.x_coordinate=x_coordinate;
		this.y_coordinate=y_coordinate;
		this.demand=demand;
	}

//methods-----------------------------------------------------------------
	public boolean isCoveredBy(Stop stop, double radius, Distance distance){
		return distance.calcDist(stop, this) <= radius+Distance.EPSILON;
	}

	public boolean isCoveredBy(Candidate candidate, double radius, Distance distance){
		return distance.calcDist(this,candidate) <= radius+Distance.EPSILON;
	}

//getter-------------------------------------------------------------------
	/**
	 * @return the id
	 */
	public int getId() {
		return id;
	}


	/**
	 * @return the x_coordinate
	 */
	public double getX_coordinate() {
		return x_coordinate;
	}


	/**
	 * @return the y_coordinate
	 */
	public double getY_coordinate() {
		return y_coordinate;
	}


	/**
	 * @return the demand
	 */
	public int getDemand() {
		return demand;
	}

//setter----------------------------------------------------------------------
	/**
	 * @param id the id to set
	 */
	public void setId(int id) {
		this.id = id;
	}


	/**
	 * @param x_coordinate the x_coordinate to set
	 */
	public void setX_coordinate(double x_coordinate) {
		this.x_coordinate = x_coordinate;
	}


	/**
	 * @param y_coordinate the y_coordinate to set
	 */
	public void setY_coordinate(double y_coordinate) {
		this.y_coordinate = y_coordinate;
	}


	/**
	 * @param demand the demand to set
	 */
	public void setDemand(int demand) {
		this.demand = demand;
	}

    @Override
    public String toString() {
        return "DemandPoint{" +
            "id=" + id +
            ", x_coordinate=" + x_coordinate +
            ", y_coordinate=" + y_coordinate +
            ", demand=" + demand +
            '}';
    }
}
