package net.lintim.callback;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;

/**
 * Checks whether the user wants to terminate/kill CPLEX and performs further
 * actions.
 *
 */
public class DefaultCallbackCplex extends IloCplex.MIPInfoCallback {

    final DefaultCallback callback = new DefaultCallback();

    public DefaultCallbackCplex() {
        callback.printUsageInformation();
    }

    public void main() throws IloException {
        if(callback.killCondition()){
            callback.killMessage();
            System.exit(1);
        }
        else if(callback.terminateCondition()){
            callback.terminateMessage();
            abort();
        }
    }

}
