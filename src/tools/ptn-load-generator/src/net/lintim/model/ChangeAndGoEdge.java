package net.lintim.model;

import java.util.Objects;

/**
 */
public class ChangeAndGoEdge implements Edge<ChangeAndGoNode> {

    public static final int CHANGE_LINK = -1;

    private int linkId;
    private final int correspondingPtnLinkId;
    private final double length;
    private double load;
    private final ChangeAndGoNode leftNode;
    private final ChangeAndGoNode rightNode;

    /**
     * Create a new change and go link. Links in a change and go network are directed. A change and go link needs a
     * link to its corresponding ptn link, the id is used here. Use {@link #CHANGE_LINK}, if this is a link inside a
     * station, i.e., {@link ChangeAndGoNode#getStopId()} is the same for the left and the right node.
     * @param linkId the link id. This needs to be unique in the network
     * @param leftNode the left node, i.e., the source of this link
     * @param rightNode the right node, i.e., the target of this link
     * @param length the length of this link, i.e., the time needed to traverse this edge in minutes
     * @param correspondingPtnLinkId the id of the corresponding link in the ptn or {@link #CHANGE_LINK}, if there is
     *                                 none.
     */
    public ChangeAndGoEdge(int linkId, ChangeAndGoNode leftNode, ChangeAndGoNode rightNode, double length, int correspondingPtnLinkId){
        this.linkId = linkId;
        this.leftNode = leftNode;
        this.rightNode = rightNode;
        this.length = length;
        this.correspondingPtnLinkId = correspondingPtnLinkId;
        this.load = 0;
    }

    @Override
    public int getId() {
        return linkId;
    }

    @Override
    public void setId(int id) {
        this.linkId = id;
    }

    @Override
    public ChangeAndGoNode getLeftNode() {
        return leftNode;
    }

    @Override
    public ChangeAndGoNode getRightNode() {
        return rightNode;
    }

    @Override
    public boolean isDirected() {
        return true;
    }

    public int getCorrespondingPtnLinkId() {
        return correspondingPtnLinkId;
    }

    public double getLength() {
        return length;
    }

    public double getLoad() {
        return load;
    }

    public void setLoad(double load) {
        this.load = load;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChangeAndGoEdge that = (ChangeAndGoEdge) o;

        if (correspondingPtnLinkId != that.correspondingPtnLinkId) return false;
        if (Double.compare(that.length, length) != 0) return false;
        if (!Objects.equals(leftNode, that.leftNode)) return false;
        return Objects.equals(rightNode, that.rightNode);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = correspondingPtnLinkId;
        temp = Double.doubleToLongBits(length);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (leftNode != null ? leftNode.hashCode() : 0);
        result = 31 * result + (rightNode != null ? rightNode.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return this.getLeftNode() + "->" + this.getRightNode();
    }
}
