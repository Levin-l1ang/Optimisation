package net.lintim.util;

/**
 * Enum for used solver types in LinTim
 */
public enum SolverType {
    XPRESS("Xpress"),
    GUROBI("Gurobi"),
    CPLEX("Cplex"),
    GLPK("Glpk"),
    SCIP("SCIP");

    private String name;

    /**
     * Initialize a solver type with the given name
     * @param name the name of the solver
     */
    SolverType(String name){
        this.name = name;
    }

    @Override
    public String toString(){
        return this.name;
    }
}
