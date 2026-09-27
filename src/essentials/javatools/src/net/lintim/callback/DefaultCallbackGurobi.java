package net.lintim.callback;

import com.gurobi.gurobi.GRBCallback;

/**
 * Checks whether the user wants to terminate/kill GUROBI and performs further
 * actions.
 *
 */
public class DefaultCallbackGurobi extends GRBCallback {

    DefaultCallback callback = new DefaultCallback();

    public DefaultCallbackGurobi() {
        callback.printUsageInformation();
    }

    protected void callback() {
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
