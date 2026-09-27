package net.lintim.generator;

import net.lintim.evaluator.LineCollectionEvaluator;
import net.lintim.exception.DataInconsistentException;
import net.lintim.model.*;
import net.lintim.util.MathHelper;

import java.util.LinkedHashSet;

/**
 * Generates a line concept by solving the cost model.
 */
public abstract class LineConceptGenerator {

    public enum Solver{
        CPLEX
    }

    public enum LinearModel {
        BASIC, ONE_LINE_PER_EDGE
    }

    public enum ObjectiveFunctionModel {
        COST
    }

    PublicTransportationNetwork ptn;
    LineCollection lpool;
    LinkedHashSet<Line> lines;
    LinkedHashSet<Link> links;
    Integer periodLength;

    Solver solver = Solver.CPLEX;
    Boolean forceLinePairIntersectionsConnected = false;
    LinearModel linearModel = LinearModel.BASIC;
    ObjectiveFunctionModel objectiveFunctionModel = ObjectiveFunctionModel.COST;
    Double objectiveFunction = null;
    Boolean initialized = false;

    Boolean terminateAfterPresolve = false;
    Boolean ptnIsUndirected;

    /**
     * To be called upon construction. Not a constructor due to the necessity
     * of a class loader for different solvers.
     *
     * @param lpool The line pool to use.
     * @param config The config to use.
     * @throws DataInconsistentException
     */
    public void initialize(LineCollection lpool, Configuration config)
    throws DataInconsistentException{

        this.ptn = lpool.getPublicTransportationNetwork();
        this.lpool = lpool;
        this.ptnIsUndirected = ptn.isUndirected();
        this.lines =
            ptnIsUndirected ?
            lpool.getUndirectedLines() : lpool.getDirectedLines();
        this.links = ptnIsUndirected ? ptn.getUndirectedLinks() :
            ptn.getDirectedLinks();
        this.periodLength = config.getIntegerValue("period_length");
        this.initialized = true;

    }

    /**
     * Called in {@link #solve()}, to be overridden by the resp. solver class.
     *
     * @throws DataInconsistentException
     */
    protected abstract void solveInternal() throws DataInconsistentException;

    /**
     * Solves the actual problem with {@link #solveInternal()} after checking
     * whether {@link #initialize(LineCollection, Configuration)} has been run
     * and verifies objective function integrity.
     *
     * @throws DataInconsistentException
     */
    public void solve() throws DataInconsistentException{
        if(!initialized){
            throw new DataInconsistentException("please run the initialize " +
                    "method before solve");
        }

        solveInternal();

        Double referenceObjectiveFunction = null;

        if(objectiveFunctionModel == ObjectiveFunctionModel.COST){
            referenceObjectiveFunction = LineCollectionEvaluator.cost(lpool);
        }

        if(Math.abs(referenceObjectiveFunction - objectiveFunction) >
        MathHelper.epsilon){
            throw new DataInconsistentException("lineplanning objective " +
                    "function invalid: is " + objectiveFunction +
                    " but should be " + referenceObjectiveFunction);
        }

    }

    // =========================================================================
    // === Setters =============================================================
    // =========================================================================
    public void setSolver(Solver solver) {
        this.solver = solver;
    }

    public void setTerminateAfterPresolve(Boolean terminateAfterPresolve) {
        this.terminateAfterPresolve = terminateAfterPresolve;
    }
    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public Solver getSolver() {
        return solver;
    }

    public Double getObjectiveFunction() {
        return objectiveFunction;
    }

    public Boolean getTerminateAfterPresolve() {
        return terminateAfterPresolve;
    }

}
