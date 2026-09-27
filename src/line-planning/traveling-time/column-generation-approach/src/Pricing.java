import net.lintim.util.Logger;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.*;

import java.util.*;

/**
 * the shortest path pricing problems for the column generation algorithm
 */
public class Pricing {

    private static final Logger logger = new Logger(Pricing.class);

    /**
     * corresponding restricted linear programming master
     */
    protected RLPM rlpm;

    /**
     * corresponding change and go graph
     */
    protected CAG cag;

    /**
     * list of od nodes
     * in the pricing step shortest paths between these nodes are computed
     */
    protected ArrayList<Node> nodes;

    /**
     * tolerance parameter for pricing
     * if the shortest path is TOL smaller than the dual variable it is
     * within the tolerance
     */
    protected double TOL = 0.000000001;

    /**
     * best dual bound found in all pricing steps
     * can be used as additional stopping criterium
     */
    protected double dualBound = 0.0;

    protected ArrayList<int[]> od;


//----------Constructor-----------------------------------------------------------------

    /**
     * initialization of pricing
     *
     * @param rlpm corresponding restricted linear programming master
     */
    public Pricing(RLPM rlpm) {
        this.rlpm = rlpm;
        this.cag = rlpm.getCAG();
        this.nodes = cag.getOdNodes();
        this.od = cag.getPTN().getOd();
    }

//-----------getDualBound----------------------------------------------------------------

    /**
     * @return returns the best dual bound found so far in all pricing steps
     */
    public double getDualBound() {
        return dualBound;
    }

//------------------IsOptimal-------------------------------------------------------------

    /**
     * pricing of the current solution of the RLPM
     * computes the pricing problem for each od pair
     * this means solving a shortest path problem
     * depending on the line constraints, the weights of the change and go
     * graph have to be updated before computing the shortest paths
     * uses the dijkstra shortest path algorithm of jgrapht
     * if the current solution is not optimal, the shortest paths found by
     * the pricing problem are added
     *
     * @return returns true if the pricing step is satisfied for all od pairs
     * else it returns false
     */
    public boolean isOptimal() {
        logger.debug("\nPRICING....\n");

        //remains true if all pricing problems are satisfied and is returned
        boolean b = true;

        //to save new path
        Path path;

        //the corresponding pricing problems are solved
        //shortest paths problems for every od pair
        //differ in the update of the od graph

        if (rlpm.getRelaxation() == 1) {
            double newDualBound = rlpm.getRLPM().getObjVal();
            DijkstraShortestPath<Node, DefaultWeightedEdge> dijkstra;
            for (int i = 0; i < od.size(); i++) {

                //change weights of cag for pricing
                this.cag.update(rlpm.getLineConstr1(), od.get(i)[2]);

                //initialzation of dijkstra's algorithm
                dijkstra = new DijkstraShortestPath<>
                    (this.cag.getGraph());

                //solve shortest path problem for od pair, compare with dual
                //variable of the od constraint divided by the number of passengers
                //add path if it has a smaller weight
                try {
                    if (dijkstra.getPathWeight(nodes.get(od.get(i)[0]), nodes.get(od.get(i)[1])) + TOL < this.rlpm.getOdConstr().get(i).
                        getDual() / od.get(i)[2]) {
                        path = new Path(dijkstra.getPath(nodes.get(od.get(i)[0]), nodes.get(od.get(i)[1])));
                        newDualBound = newDualBound - this.rlpm.getOdConstr().get(i).getDual()
                            + od.get(i)[2] * path.getWeight();
                        path.changeWeight(cag.getDual());
                        this.rlpm.addPath(path);
                        b = false;
                    }
                } catch (NoPathException e) {
                    logger.error("Error while pricing: " + e.getMessage());
                    System.exit(1);
                }
            }
            if (newDualBound > dualBound) {
                dualBound = newDualBound;
            }
            logger.debug("Dual bound: ......." + dualBound);
        } else if (rlpm.getRelaxation() == 2) {
            double newDualBound = rlpm.getRLPM().getObjVal();
            DijkstraShortestPath<Node, DefaultWeightedEdge> dijkstra;
            for (int i = 0; i < od.size(); i++) {
                //change weights of cag for pricing
                this.cag.updateAllEdges(rlpm.getLineConstr2(), od.get(i)[2]);
                try {
                    //initialization of dijkstra's algorithm
                    dijkstra = new DijkstraShortestPath<>(this.cag.getGraph());

                    //solve shortest path problem for od pair, compare with dual
                    //variable of the od constraint divided by the number of passengers
                    //add path if it has a smaller weight
                    if (dijkstra.getPathWeight(nodes.get(od.get(i)[0]), nodes.get(od.get(i)[1])) + TOL < this.rlpm.getOdConstr().get(i).
                        getDual() / od.get(i)[2]) {
                        path = new Path(dijkstra.getPath(nodes.get(od.get(i)[0]), nodes.get(od.get(i)[1])));
                        newDualBound = newDualBound - this.rlpm.getOdConstr().get(i).getDual()
                            + od.get(i)[2] * path.getWeight();
                        path.changeWeightAllEdges(cag.getDualAllEdges());
                        this.rlpm.addPath(path);
                        b = false;
                    }
                } catch (NoPathException e) {
                    logger.error("Error while pricing: " + e.getMessage());
                    System.exit(1);
                }
            }
            if (newDualBound > dualBound) {
                dualBound = newDualBound;
            }
            logger.debug("Dual bound: ......." + dualBound);
        } else if (rlpm.getRelaxation() == 3) {
            double newDualBound = rlpm.getRLPM().getObjVal();
            DijkstraShortestPath<Node, DefaultWeightedEdge> dijkstra;
            for (int i = 0; i < od.size(); i++) {
                try {
                    //change weights of cag for pricing for every od pair
                    this.cag.update(rlpm.getLineConstr3().get(i), od.get(i)[2]);

                    //initialization of dijkstra's algorithm
                    dijkstra = new DijkstraShortestPath<>
                        (this.cag.getGraph());

                    //solve shortest path problem for od pair, compare with dual
                    //variable of the od constraint divided by the number of passengers
                    //add path if it has a smaller weight
                    if (dijkstra.getPathWeight(nodes.get(od.get(i)[0]), nodes.get(od.get(i)[1])) + TOL < this.rlpm.getOdConstr().
                        get(i).getDual() / od.get(i)[2]) {
                        path = new Path(dijkstra.getPath(nodes.get(od.get(i)[0]), nodes.get(od.get(i)[1])));
                        newDualBound = newDualBound - this.rlpm.getOdConstr().get(i).getDual()
                            + od.get(i)[2] * path.getWeight();
                        path.changeWeight(cag.getDual());
                        this.rlpm.addPath(path);
                        b = false;
                    }
                } catch (NoPathException e) {
                    logger.error("Error while pricing: " + e.getMessage());
                    System.exit(1);
                }
            }
            if (newDualBound > dualBound) {
                dualBound = newDualBound;
            }
            logger.debug("Dual bound: ......." + dualBound);
        } else if (rlpm.getRelaxation() == 4) {
            double newDualBound = rlpm.getRLPM().getObjVal();
            DijkstraShortestPath<Node, DefaultWeightedEdge> dijkstra;
            for (int i = 0; i < od.size(); i++) {
                try {
                    //change weights of cag for pricing for every od pair
                    this.cag.updateAllEdges(rlpm.getLineConstr4().get(i), od.get(i)[2]);

                    //initialization of dijkstra's algorithm
                    dijkstra = new DijkstraShortestPath<>(this.cag.getGraph());

                    //solve shortest path problem for od pair, compare with dual
                    //variable of the od constraint divided by the number of passengers
                    //add path if it has a smaller weight
                    if (dijkstra.getPathWeight(nodes.get(od.get(i)[0]), nodes.get(od.get(i)[1])) + TOL < this.rlpm.getOdConstr().
                        get(i).getDual() / od.get(i)[2]) {
                        path = new Path(dijkstra.getPath(nodes.get(od.get(i)[0]), nodes.get(od.get(i)[1])));
                        newDualBound = newDualBound - this.rlpm.getOdConstr().get(i).getDual()
                            + od.get(i)[2] * path.getWeight();
                        path.changeWeightAllEdges(cag.getDualAllEdges());
                        this.rlpm.addPath(path);
                        b = false;
                    }
                } catch (NoPathException e) {
                    logger.error("Error while pricing: " + e.getMessage());
                    System.exit(1);
                }
            }
            if (newDualBound > dualBound) {
                dualBound = newDualBound;
            }
            logger.debug("Dual bound: ......." + dualBound);
        }

        return b;
    }
}
