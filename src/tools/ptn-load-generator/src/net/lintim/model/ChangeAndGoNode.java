package net.lintim.model;

/**
 */
public class ChangeAndGoNode implements Node {

    public static final int START = -1;

    private int id;
    private final int stopId;
    private final int lineId;

    /**
     * Create a new change and go node with the given stop and line id.
     * @param id the id of the new node. Should be unique in the change and go network.
     * @param stopId the stop id of the change and go node
     * @param lineId the line id of the change and go node
     */
    public ChangeAndGoNode(int id, int stopId, int lineId){
        this.id = id;
        this.stopId = stopId;
        this.lineId = lineId;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    public int getStopId() {
        return stopId;
    }

    public int getLineId() {
        return lineId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChangeAndGoNode that = (ChangeAndGoNode) o;

        if (stopId != that.stopId) return false;
        return lineId == that.lineId;
    }

    @Override
    public int hashCode() {
        int result = stopId;
        result = 31 * result + lineId;
        return result;
    }

    @Override
    public String toString() {
        return "(" + this.getStopId() + "," + this.getLineId() + ")";
    }
}
