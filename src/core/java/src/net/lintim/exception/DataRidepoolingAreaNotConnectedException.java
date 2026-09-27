package net.lintim.exception;

/**
 *  Exception to throw if there is an inconsistency during the creation of a Ridepooling Pool Area.
 */
public class DataRidepoolingAreaNotConnectedException extends LinTimException {
    /**
     *  Exception to throw if there is an inconsistency during the creation of a Ridepooling Pool Area.
     *
     * @param areaId   id of the area which is not connected
     */
    public DataRidepoolingAreaNotConnectedException(int areaId) {
        super("Error D16: Ridepooling Pool area with id" + areaId + "can not be created!");
    }
}