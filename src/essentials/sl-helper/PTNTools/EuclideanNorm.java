import java.lang.Math;
import java.util.*;

public class EuclideanNorm extends Distance{
	public double calcDist(double x1, double x2, double y1, double y2){
		return Math.sqrt(Math.pow(x1-y1,2.)+Math.pow(x2-y2,2.));
	}

	/**
	 * Solves quadratic equation to determine the candidates on an edge.
	 */
	public LinkedList<Candidate> candidateOnEdge(DemandPoint demand_point, Edge edge, double radius){
		LinkedList<Candidate> candidate = new LinkedList<>();
		Candidate point;
		double p1=demand_point.getX_coordinate();
		double p2=demand_point.getY_coordinate();
		double s1=edge.getLeft_stop().getX_coordinate();
		double s2=edge.getLeft_stop().getY_coordinate();
		double t1=edge.getRight_stop().getX_coordinate();
		double t2=edge.getRight_stop().getY_coordinate();

        // We write the line equation as (x,y) = lambda * (s1,s2) + (1 - lambda) * (t1,t2)
        // Using the circle equation (x-p1)^2+(y-p2)^2=r^2 and substituting x=lambda*s1+(1-lambda)*t1 and
        // y=lambda*s2+(1-lambda)*t2 will yield a quadratic equation in lambda that is solved in the following

		double denominator=Math.pow(t1-s1, 2)+Math.pow(t2-s2, 2); // the term before lambda^2

		if(denominator < EPSILON) {
            // The length of the edge is too small, the only intersection may be one of the vertices
            // (which are already candidates)
            return null;
        }

		double p_half= (t1-s1)*(p1-t1)+(t2-s2)*(p2-t2);
		p_half /= denominator;
		double q=Math.pow(p1-t1,2)+Math.pow(p2-t2,2)-Math.pow(radius, 2);
		q /= denominator;
		double discriminant= Math.pow(p_half,2)-q;

        // Now the possible solutions for lambda are -p_half +- sqrt(discriminant)
        if(Math.abs(discriminant)>EPSILON){
            if(discriminant < 0){
                // Discriminate < 0: The line and the circle do not intersect
                return null;
            }
            // Discriminate > 0: The line and the circle intersect in two points
            point=convexCombination(-p_half+Math.sqrt(discriminant),edge);
            if(point != null) {
                candidate.add(point);
            }
            point=convexCombination(-p_half-Math.sqrt(discriminant),edge);
            if(point != null) {
                candidate.add(point);
            }
        }
        else {
            // Discriminate == 0: The line and the circle intersect in a single point
            point=convexCombination(-p_half,edge);
            if(point != null) {
                candidate.add(point);
            }
        }
		return candidate;
	}
}
