import java.util.*;

public abstract class Distance {
	
	final static double EPSILON = 1E-12;

	/**
	 * Abstract method to calculate the distance between (x1,x2) and (y1,y2) in
	 * R^2
	 * 
	 * @param x1
	 *            first coordinate of first point
	 * @param x2
	 *            second coordinate of second point
	 * @param y1
	 *            first coordinate of second point
	 * @param y2
	 *            second coordinate of second point
	 * @return distance between (x1,x2) and (y1,y2)
	 */
	public abstract double calcDist(double x1, double x2, double y1, double y2);
	
	public double calcDist(Stop stop, DemandPoint demand_point){
		return calcDist(stop.getX_coordinate(), stop.getY_coordinate(), 
				demand_point.getX_coordinate(), demand_point.getY_coordinate());
	}

	public double calcDist(Stop stop,Candidate candidate) {
		return calcDist(stop.getX_coordinate(), stop.getY_coordinate(),
				candidate.getX_coordinate(), candidate.getY_coordinate());
	}
	
	public double calcDist(Stop stop1, Stop stop2) {
		return calcDist(stop1.getX_coordinate(), stop1.getY_coordinate(),
				stop2.getX_coordinate(), stop2.getY_coordinate());
	}

	public double calcDist(Candidate candidate1, Candidate candidate2){
		return calcDist(candidate1.getX_coordinate(), candidate1.getY_coordinate(),
				candidate2.getX_coordinate(), candidate2.getY_coordinate());
	}

	public double calcDist(DemandPoint demandPoint, Candidate candidate){
		return calcDist(demandPoint.getX_coordinate(), demandPoint.getY_coordinate(),
				candidate.getX_coordinate(), candidate.getY_coordinate());
	}
	
	public double calcDist(DemandPoint demandPoint1, DemandPoint demandPoint2){
		return calcDist(demandPoint1.getX_coordinate(), demandPoint1.getY_coordinate(),
				demandPoint2.getX_coordinate(), demandPoint2.getY_coordinate());
	}
	
	public abstract LinkedList<Candidate> candidateOnEdge(DemandPoint demand_point, Edge edge, double radius);
	
	
//utility---------------------------------------------------
	protected Candidate convexCombination(double lambda, Edge edge){
		if(lambda <=0 || lambda >= 1)
			return null;
		Stop left_stop=edge.getLeft_stop();
		Stop right_stop=edge.getRight_stop();
		ArrayList<Edge> adjacent_edges = new ArrayList<Edge>();
		adjacent_edges.add(0,edge);
		return new Candidate(lambda*left_stop.getX_coordinate()+(1-lambda)*right_stop.getX_coordinate(),
				lambda*left_stop.getY_coordinate()+(1-lambda)*right_stop.getY_coordinate(),adjacent_edges);
	}


}
