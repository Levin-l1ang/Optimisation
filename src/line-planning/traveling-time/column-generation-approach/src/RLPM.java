import com.dashoptimization.*;
import net.lintim.solver.impl.XpressHelper;
import net.lintim.util.Logger;

import java.util.*;

/**
 * class setting up the restricted programming master
 * function for solving it using XPress
 * new paths can be added and it can be solved again
 * can also solve the corresponding integer program
 */

public class RLPM {

    private static final Logger logger = new Logger(RLPM.class);

    /**
     * this parameter sets the typ of the second block of constraints
     */
    protected int RELAXATION;

    /**
     * maximal budget available for the line concept
     */
    protected double BUDGET;

    protected boolean RELAXATION_CONSTRAINT;

    protected boolean PRINT_PATH_VAR;

    private final boolean writeLpFile;

    /**
     * corresponding change and go network
     */
    protected CAG cag;

    /**
     * list of of pairs with origin, destination and number of passengers
     */
    protected ArrayList<int[]> od;

    /**
     * this XPress problem is initialized with the LP relaxation of the time
     * traveling problem
     */
    protected XPRBprob rlpm;

    /**
     * contains one variable for each path
     * the array list contains an array list for each od pair containing all
     * paths taken into account
     */
    protected ArrayList<ArrayList<XPRBvar>> x;

    /**
     * contains one variable for each line
     */
    protected ArrayList<XPRBvar> y;

    /**
     * objective function of rlpm
     */
    protected XPRBctr objective;

    /**
     * constraints satisfying that one path is chosen for every od pair
     */
    protected ArrayList<XPRBctr> odConstr;

    /**
     * this constraint is initialized when the first relaxation is chosen
     * satisfies that a path can only contain a line, when the line is chosen
     * takes the sum of the constraints in lineConstr4 over all line edges
     * and od pairs for each line
     */
    protected ArrayList<XPRBctr> lineConstr1;

    /**
     * this constraint is initialized when the second relaxation is chosen
     * satisfies that a path can only contain a line, when the line is chosen
     * takes the sum of the constraints in lineConstr4 over all od pairs for each line
     */
    protected ArrayList<ArrayList<XPRBctr>> lineConstr2;

    /**
     * this constraint is initialized when the third relaxation is chosen
     * satisfies that a path can only contain a line, when the line is chosen
     * takes the sum of the constraints in lineConstr4 over all line edges for each line
     */
    protected ArrayList<ArrayList<XPRBctr>> lineConstr3;

    /**
     * this constraint is initialized when the fourth relaxation is chosen
     * satisfies that a path can only contain a line, when the line is chosen
     */
    protected ArrayList<ArrayList<ArrayList<XPRBctr>>> lineConstr4;

    /**
     * additional constraints to assure that the line variables are
     * smaller equal 1
     */

    protected ArrayList<XPRBctr> relaxConstr;

    /**
     * budget constraint
     */
    protected XPRBctr budgetConstr;

    /**
     * list of the current paths considered in the RLPM
     * ordered according to od pairs
     */
    protected ArrayList<ArrayList<Path>> paths;

//---------------Constructor-----------------------------

    /**
     * Constructor sets up restricted linear programming master
     *
     * @param paths Array List containing ArrayLists of paths for each od pair
     * @param pool  containing the Lines
     * @param cag   corresponding change and go graph
     */

    public RLPM(ArrayList<ArrayList<Path>> paths, Pool pool, CAG cag, Parameters parameters) {

        this.cag = cag;
        this.paths = paths;
        this.RELAXATION = 1;
        this.BUDGET = 10;
        this.RELAXATION_CONSTRAINT = true;
        this.PRINT_PATH_VAR = false;
        this.writeLpFile = parameters.writeLpFile();
        this.od = cag.getPTN().getOd();

        this.RELAXATION = parameters.getConstraintType();
        this.BUDGET = parameters.getBudget();
        this.RELAXATION_CONSTRAINT = parameters.relaxationConstraint();
        this.PRINT_PATH_VAR = parameters.printPathVar();

        XPRS.init();
        XPRB bcl = new XPRB();

        rlpm = bcl.newProb("RLPM");
        XpressHelper.setXpressSolverParameters(rlpm, parameters);

        //initialization of variables for each path (x) and each line (y)
        x = new ArrayList<>(0);
        y = new ArrayList<>(0);

        for (int i = 0; i < paths.size(); i++) {
            x.add(new ArrayList<>(0));
            for (int j = 0; j < paths.get(i).size(); j++) {
                x.get(i).add(rlpm.newVar("x_" + i + "_" + j, XPRB.PL, 0.0, Double.POSITIVE_INFINITY));
            }
        }
        for (int i = 0; i < pool.getLines().size(); i++) {
            y.add(rlpm.newVar("y_" + i, XPRB.PL, 0.0, Double.POSITIVE_INFINITY));
        }

        //initialization of objective function
        objective = rlpm.newCtr("objective");
        objective.setType(XPRB.N);
        for (int i = 0; i < x.size(); i++) {
            for (int j = 0; j < x.get(i).size(); j++) {
                objective.setTerm(x.get(i).get(j), paths.get(i).get(j).getWeight() * od.get(i)[2]);
            }
        }
        rlpm.setObj(objective);


        //initialization of constraints
        //first block of constraints: there has to be one path for every od pair

        odConstr = new ArrayList<>(0);
        for (int i = 0; i < paths.size(); i++) {
            odConstr.add(rlpm.newCtr("od_" + i));
            for (int j = 0; j < paths.get(i).size(); j++) {
                odConstr.get(i).setTerm(x.get(i).get(j), 1);
            }
            //set type of constraint to greater equal
            odConstr.get(i).setType(XPRB.G);
            //set constant term to one -> one path should be taken
            odConstr.get(i).setTerm(1);
        }

        //second block of constraints: satisfies that we can only use a line, when it is built

        //first variant: sum up constraints over all line edges and all od pairs corresponding to one line
        //∑_p |E(p) ∩ l| x_p ≤ M y_l  ∀ lines l, where M = |OD|*|l|.
        if (RELAXATION == 1) {
            lineConstr1 = new ArrayList<>(0);
            ArrayList<Integer> lineList;
            //initialisation of constraint and add y variables
            for (int i = 0; i < pool.getLines().size(); i++) {
                lineConstr1.add(rlpm.newCtr("line_" + i));
                lineConstr1.get(i).setTerm(y.get(i), -1 * cag.getPTN().getOd().size() *
                    pool.getLines().get(i).getEdges().size());
                lineConstr1.get(i).setType(XPRB.L);
            }
            int n = pool.getLines().size();
            for (int i = 0; i < x.size(); i++) {
                for (int j = 0; j < x.get(i).size(); j++) {
                    lineList = paths.get(i).get(j).getLineIds(n);
                    for (int k = 0; k < lineList.size(); k++) {
                        if (lineList.get(k) != 0) {
                            lineConstr1.get(k).setTerm(x.get(i).get(j), lineList.get(k));
                        }
                    }
                }
            }
        }

        //second variant: sum up constraints over all od pairs corresponding to one line
        else if (RELAXATION == 2) {
            lineConstr2 = new ArrayList<>(0);
            ArrayList<Node> nodeList;
            //initialization of constraint and add y variables
            for (int j = 0; j < cag.getLineEdges().size(); j++) {
                lineConstr2.add(new ArrayList<>(0));
                for (int i = 0; i < cag.getLineEdges().get(j).size(); i++) {
                    lineConstr2.get(j).add(rlpm.newCtr("line_" + i));
                    lineConstr2.get(j).get(i).setTerm(y.get(j), -1 * cag.getPTN().getOd().size());
                    lineConstr2.get(j).get(i).setType(XPRB.L);
                }
            }
            for (int i = 0; i < x.size(); i++) {
                for (int j = 0; j < x.get(i).size(); j++) {
                    nodeList = paths.get(i).get(j).getNodes();
                    for (int k = 1; k < nodeList.size() - 1; k++) {
                        if (nodeList.get(k).getLineId() > 0 && nodeList.get(k).getLineId() ==
                            nodeList.get(k + 1).getLineId()) {
                            if (nodeList.get(k).getPosition() < nodeList.get(k + 1).
                                getPosition()) {
                                lineConstr2.get(nodeList.get(k).getLineId() - 1).get(nodeList.
                                    get(k).getPosition()).setTerm(x.get(i).get(j), 1);
                            } else {
                                lineConstr2.get(nodeList.get(k).getLineId() - 1).get(nodeList.
                                    get(k + 1).getPosition()).setTerm(x.get(i).get(j), 1);
                            }
                        }
                    }
                }
            }
        }


        //third variant: sum up constraints over all line edges corresponding to one line
        else if (RELAXATION == 3) {
            lineConstr3 = new ArrayList<>(0);
            ArrayList<Integer> lineList;
            //initialization of constraint and add y variables
            for (int j = 0; j < paths.size(); j++) {
                lineConstr3.add(new ArrayList<>(0));
                for (int i = 0; i < pool.getLines().size(); i++) {
                    lineConstr3.get(j).add(rlpm.newCtr("line_" + i));
                    lineConstr3.get(j).get(i).setTerm(y.get(i), -1 * pool.getLines().get(i).
                        getEdges().size());
                    lineConstr3.get(j).get(i).setType(XPRB.L);
                }
            }
            int n = pool.getLines().size();
            for (int i = 0; i < x.size(); i++) {
                for (int j = 0; j < x.get(i).size(); j++) {
                    lineList = paths.get(i).get(j).getLineIds(n);
                    for (int k = 0; k < lineList.size(); k++) {
                        if (lineList.get(k) != 0) {
                            lineConstr3.get(i).get(k).setTerm(x.get(i).get(j), lineList.get(k));
                        }
                    }
                }
            }
        }


        //fourth variant: one constraint for every x_p, y_l with l used by p
        else if (RELAXATION == 4) {
            lineConstr4 = new ArrayList<>(0);
            ArrayList<Node> nodeList;
            //initialisation of constraint and add y variables
            // one constraint for each line
            for (int k = 0; k < x.size(); k++) {
                lineConstr4.add(new ArrayList<>(0));
                for (int j = 0; j < cag.getLineEdges().size(); j++) {
                    lineConstr4.get(k).add(new ArrayList<>(0));
                    for (int i = 0; i < cag.getLineEdges().get(j).size(); i++) {
                        lineConstr4.get(k).get(j).add(rlpm.newCtr("line_" + i));
                        lineConstr4.get(k).get(j).get(i).setTerm(y.get(j), -1);
                        lineConstr4.get(k).get(j).get(i).setType(XPRB.L);
                    }
                }
            }
            //each x_p is added to the constraint l of lineConstr2,
            //scalar: cardinality of intersection of edges from p and l
            for (int i = 0; i < x.size(); i++) {
                for (int j = 0; j < x.get(i).size(); j++) {
                    nodeList = paths.get(i).get(j).getNodes();
                    for (int k = 1; k < nodeList.size() - 1; k++) {
                        if (nodeList.get(k).getLineId() > 0 && nodeList.get(k).getLineId() ==
                            nodeList.get(k + 1).getLineId()) {
                            if (nodeList.get(k).getPosition() < nodeList.get(k + 1).getPosition()) {
                                lineConstr4.get(i).get(nodeList.get(k).getLineId() - 1).
                                    get(nodeList.get(k).getPosition()).setTerm(
                                    x.get(i).get(j), 1);
                            } else {
                                lineConstr4.get(i).get(nodeList.get(k).getLineId() - 1).
                                    get(nodeList.get(k + 1).getPosition()).setTerm(
                                    x.get(i).get(j), 1);
                            }
                        }
                    }
                }
            }
        }

        //third block: budget constraint

        budgetConstr = rlpm.newCtr("budget");

        for (int i = 0; i < y.size(); i++) {
            budgetConstr.addTerm(y.get(i), pool.getLines().get(i).getCost());
        }
        //set type of constr to smaller equal
        budgetConstr.setType(XPRB.L);
        //set constant term to BUDGET -> sum of costs for the lines have to be smaller
        budgetConstr.setTerm(BUDGET);

        //relaxation constraint
        if (RELAXATION_CONSTRAINT) {
            relaxConstr = new ArrayList<>(0);
            //satisfies that each y_l is smaller equal one
            for (int i = 0; i < pool.getLines().size(); i++) {
                relaxConstr.add(rlpm.newCtr("rel l_" + i));
                relaxConstr.get(i).setTerm(y.get(i), 1);
                relaxConstr.get(i).setTerm(1);
                relaxConstr.get(i).setType(XPRB.L);
            }
        }
    }

    //----------------Getter----------------------------------------------
    public XPRBprob getRLPM() {
        return rlpm;
    }

    public ArrayList<ArrayList<XPRBvar>> getPathsVar() {
        return x;
    }

    public ArrayList<XPRBvar> getLineVar() {
        return y;
    }

    public ArrayList<XPRBctr> getOdConstr() {
        return odConstr;
    }

    public ArrayList<XPRBctr> getLineConstr1() {
        return lineConstr1;
    }

    public ArrayList<ArrayList<XPRBctr>> getLineConstr2() {
        return lineConstr2;
    }

    public ArrayList<ArrayList<XPRBctr>> getLineConstr3() {
        return lineConstr3;
    }

    public ArrayList<ArrayList<ArrayList<XPRBctr>>> getLineConstr4() {
        return lineConstr4;
    }

    public XPRBctr getBudgetConstr() {
        return budgetConstr;
    }

    public CAG getCAG() {
        return this.cag;
    }

    public int getRelaxation() {
        return this.RELAXATION;
    }

    public ArrayList<ArrayList<Path>> getPaths() {
        return this.paths;
    }

//----------------solve--------------------------------------

    /**
     * function solves the Xpress problem rlpm
     */

    public void solve() throws InterruptedException {
        if (writeLpFile) {
            try {
                rlpm.exportProb(XPRB.LP, "line-planning/RLPM.lp");
            } catch (Exception e) {
                logger.warn("Could not write lp file: " + e.getMessage());
            }
        }
        //let rlpm be a minimization problem, minimize the travelling time
        rlpm.setSense(XPRB.MINIM);
        rlpm.lpOptimise();
        if (rlpm.getLPStat() == XPRB.LP_INFEAS) {
            logger.error("RLPM LP infeasible");
            rlpm.getXPRSprob().firstIIS(1);
            rlpm.getXPRSprob().writeIIS(0, "line-planning/RLPM.ilp", 0);
            System.exit(1);
        }
    }

//----------------solveIP--------------------------------------

    /**
     * function solves the Xpress problem rlpm as IP
     */

    public void solveIP() throws InterruptedException {
        if (writeLpFile) {
            try {
                rlpm.exportProb(XPRB.LP, "line-planning/RLPM.lp");
            } catch (Exception e) {
                logger.warn("Could not write lp file: " + e.getMessage());
            }
        }
        //let rlpm be a minimization problem, minimize the travelling time
        rlpm.setSense(XPRB.MINIM);
        rlpm.mipOptimise();
        if (rlpm.getLPStat() == XPRB.MIP_INFEAS) {
            logger.debug("RLPM IP infeasible!");
            rlpm.getXPRSprob().firstIIS(1);
            rlpm.getXPRSprob().writeIIS(0, "line-planning/RLPM.ilp", 0);
        }
    }


//--------------addPath----------------------------------------

    /**
     * adds new paths to the RLPM, i.e., new variables are added
     * modifies the constraints
     *
     * @param path has to be a path in the change and go graph from one od pair to another
     *             otherwise a NoPathException is thrown
     */
    public void addPath(Path path) throws NoPathException {
        if (path.getPath().getStartVertex().getLineId() != -1 || path.getPath().
            getStartVertex().getLineId() != -1) {
            throw new NoPathException("No path between od nodes, cannot be added to RLPM!");
        }
        //get position of od pair and the number of paths for this od pair
        int odPosition = getPosition(path.getPath().getStartVertex().getId() - 1,
            path.getPath().getEndVertex().getId() - 1);
        int numberOdPaths = x.get(odPosition).size();

        //add new path to path list
        this.paths.get(odPosition).add(path);

        //add new variable for the new path
        this.x.get(odPosition).add(rlpm.newVar("x_" + (odPosition - 1) + "_" + numberOdPaths, XPRB.PL,
            0.0, Double.POSITIVE_INFINITY));

        //add term with new path variable to objective
        this.objective.setTerm(x.get(odPosition).get(numberOdPaths),
            paths.get(odPosition).get(numberOdPaths).getWeight() * od.get(odPosition)[2]);

        //add term with new path variable to od constraint
        this.odConstr.get(odPosition).setTerm(x.get(odPosition).get(numberOdPaths), 1);

        //add term with new path variable to line constraint (depending on wich constraint
        //is uses)

        if (RELAXATION == 1) {
            ArrayList<Integer> lineList = path.getLineIds(lineConstr1.size());
            for (int k = 0; k < lineList.size(); k++) {
                if (lineList.get(k) != 0) {
                    lineConstr1.get(k).setTerm(x.get(odPosition).get(numberOdPaths),
                        lineList.get(k));
                }
            }
        } else if (RELAXATION == 2) {
            ArrayList<Node> nodeList = path.getNodes();
            for (int k = 1; k < nodeList.size() - 1; k++) {
                if (nodeList.get(k).getLineId() > 0 && nodeList.get(k).getLineId() ==
                    nodeList.get(k + 1).getLineId()) {
                    if (nodeList.get(k).getPosition() < nodeList.get(k + 1).getPosition()) {
                        lineConstr2.get(nodeList.get(k).getLineId() - 1).get(nodeList.get(k).
                            getPosition()).setTerm(x.get(odPosition).get(numberOdPaths), 1);
                    } else {
                        lineConstr2.get(nodeList.get(k).getLineId() - 1).get(nodeList.get(k + 1).
                            getPosition()).setTerm(x.get(odPosition).get(numberOdPaths), 1);
                    }
                }
            }
        } else if (RELAXATION == 3) {
            ArrayList<Integer> lineList = path.getLineIds(lineConstr3.get(odPosition).size());
            for (int k = 0; k < lineList.size(); k++) {
                if (lineList.get(k) != 0) {
                    lineConstr3.get(odPosition).get(k).setTerm(x.get(odPosition).
                        get(numberOdPaths), lineList.get(k));
                }
            }
        } else if (RELAXATION == 4) {
            ArrayList<Node> nodeList = path.getNodes();
            for (int k = 1; k < nodeList.size() - 1; k++) {
                if (nodeList.get(k).getLineId() > 0 && nodeList.get(k).getLineId() ==
                    nodeList.get(k + 1).getLineId()) {
                    if (nodeList.get(k).getPosition() < nodeList.get(k + 1).getPosition()) {
                        lineConstr4.get(odPosition).get(nodeList.get(k).getLineId() - 1).
                            get(nodeList.get(k).getPosition()).setTerm(
                            x.get(odPosition).get(numberOdPaths), 1);
                    } else {
                        lineConstr4.get(odPosition).get(nodeList.get(k).getLineId() - 1).
                            get(nodeList.get(k + 1).getPosition()).setTerm(
                            x.get(odPosition).get(numberOdPaths), 1);
                    }
                }
            }
        }
    }

//-------------getPosition-------getNumberOfOdPairs---------------------------------

    /**
     * @param od1, od2 two ids of two different od nodes
     * @return position of the paths from od1 to od2 in the ArrayList paths
     */

    public int getPosition(int od1, int od2) {
        return cag.getPTN().getOdPos()[od1][od2];
    }

    /**
     * @return number of od pairs in the graph
     */
    public int getNumberOfOdPairs() {
        int n = cag.getOdNodes().size();
        return (int) ((n - 1) * n / 2);
    }

//----------------changeToBooleanVariables---------------------------------------

    /**
     * changes the type of the line variables from continuous to boolean
     */
    public void changeToBooleanVariables() {
        for (int i = 0; i < y.size(); i++) {
            y.get(i).setType(XPRB.BV);
        }
        for (int i = 0; i < x.size(); i++) {
            for (int j = 0; j < x.get(i).size(); j++) {
                x.get(i).get(j).setType(XPRB.BV);
            }
        }
    }

//----------------changeToContinuousVariables---------------------------------------

    /**
     * changes the type of the line variables from continuous to boolean
     */
    public void changeToContinuousVariables() {
        for (int i = 0; i < y.size(); i++) {
            y.get(i).setType(XPRB.PL);
        }
        for (int i = 0; i < x.size(); i++) {
            for (int j = 0; j < x.get(i).size(); j++) {
                x.get(i).get(j).setType(XPRB.PL);
            }
        }
    }

//-----------------toString---------------------------------------------------------

    /**
     * overwrites toString method
     *
     * @return s String containing the objective value, the line variables and
     * if the parameter PRINT_PATH_VAR is set to true, also the path
     * variables
     */
    public String toString() {

        String s = "Objective Value : " + rlpm.getObjVal() + "\nLine variables:\n";

        for (int i = 0; i < y.size(); i++) {
            s = s + (i + 1) + "; " + y.get(i).getSol() + "\n";
        }
        if (PRINT_PATH_VAR) {
            s = s + "Path variables:\n";
            for (int i = 0; i < x.size(); i++) {
                s = s + "Od pair " + i + " : \n";
                for (int j = 0; j < x.get(i).size(); j++) {
                    s = s + x.get(i).get(j).getSol() + "\n";
                }
            }
        }
        return s;
    }
}
