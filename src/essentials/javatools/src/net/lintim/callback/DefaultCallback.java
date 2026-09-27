package net.lintim.callback;

import net.lintim.exception.DataInconsistentException;

import java.io.File;

/**
 * A helper and unified interface for callbacks for different linear program
 * solvers. Since every solver has its own callback class and one needs to
 * extend to override it, best practice is to create an instance of
 * DefaultCallback and redirect method calls to it, as seen in
 * {@link DefaultCallbackCplex} or {@link DefaultCallbackGurobi}
 *
 */
public class DefaultCallback {

    static String terminateFilename = "terminate-solver";
    static String killFilename = "kill-solver";

    Boolean killMessageSend = false;
    Boolean terminateMessageSend = false;

    /**
     * Run this method before initializing the solver to inform the user about
     * how to stop the solver and save an intermediate incumbent.
     */
    public void printUsageInformation(){
        System.err.println(
                "=========================================================\n"
              + "===== RUN \"make terminate-solver\" TO STOP CALCULATION ===\n"
              + "=====          AND SAVE THE CURRENT INCUMBENT         ===\n"
              + "=========================================================");
    }

    /**
     * Checks whether the terminate condition is satisfied, i.e. the user wants
     * to stop the calculation and save the current incumbent. Currently, this
     * is wheter a file named "terminate-solver" exists or not.
     *
     * Since the check relies on the filesystem, it may affect performance. In
     * the long run this could be replaced by a client-server request, but the
     * method will still retain its name and functionality.
     *
     * @return true if the user requested that the solver should terminate;
     * false otherwise.
     */
    public Boolean terminateCondition(){
        File checkfile = new File(terminateFilename);
        return checkfile.exists();
    }

    /**
     * Checks whether the kill condition is satisfied, i.e. the user wants
     * to stop the calculation without saving the current incumbent. Currently,
     * this is wheter a file named "kill-solver" exists or not.
     *
     * Since the check relies on the filesystem, it may affect performance. In
     * the long run this could be replaced by a client-server request, but the
     * method will still retain its name and functionality.
     *
     * @return true if the user requested to kill the solver; false otherwise.
     */
    public Boolean killCondition(){
        File checkfile = new File(killFilename);
        return checkfile.exists();
    }

    /**
     * Informs the user that the calculation will be stoppend and incumbent will
     * be saved in a uniform way.
     */
    public void terminateMessage(){
        if(!terminateMessageSend){
            System.err.println("Stopping calculation and saving current " +
                    "incumbent...");
            terminateMessageSend = true;
        }
    }

    /**
     * Informs the user that the system will be killed in a uniform way.
     */
    public void killMessage(){
        if(!killMessageSend){
            System.err.println("Killing System...");
            killMessageSend = true;
        }
    }

    /**
     * Run this before initializing the solver to cleanup from the last
     * termination/kill process.
     *
     * Currently, this will delete the files "terminate-solver" and
     * "kill-solver" if they are empty and throw a {@link
     * DataInconsistentException} otherwise.
     */
    public static void cleanup() throws DataInconsistentException{
        String[] involvedFiles = {terminateFilename, killFilename};

        for(String filename : involvedFiles){
            File file = new File(filename);
            if(file.exists()){
                if(file.isFile()){
                    if(file.length() == 0){
                        System.err.print("Deleting file \"" + filename
                                + "\"...");
                        file.delete();
                        System.err.println(" done!");
                    }
                    else{
                        throw new DataInconsistentException("file with name \""
                            + filename + "\" exists, but is not empty. " +
                                "Remove the file to enable " +
                                "solver control");
                    }
                } else {
                    throw new DataInconsistentException("file with name \""
                        + filename + "\" exists, but is not a regular " +
                            "file, e.g. a directory or link. Remove the " +
                            "file to enable solver control");
                }
            }
        }
    }

}
