package net.lintim.model;

import net.lintim.io.CsvWriter;

import java.util.Arrays;
import java.util.Objects;

/**
 * ==!! This Class is deprecated !! Should no longer be used !!==
 *
 * Representation of an infrastructure node. An infrastructure node is the smallest node unit in LinTim, e.g. this may
 * be an intersection or a potential stop point. May be connected by {@link InfrastructureEdge} or {@link WalkingEdge},
 * depending on the context.
 */
public class InfrastructureNode implements Node {

    private int nodeId;
    private final String name;
    private final double xCoordinate;
    private final double yCoordinate;
    private final boolean stopPossible;

    public InfrastructureNode(int nodeId, String name, double xCoordinate, double yCoordinate, boolean stopPossible) {
        this.nodeId = nodeId;
        this.name = name;
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        this.stopPossible = stopPossible;
    }

    @Override
    public int getId() {
        return nodeId;
    }

    @Override
    public void setId(int id) {
        this.nodeId = id;
    }

    public String getName() {
        return name;
    }

    public double getxCoordinate() {
        return xCoordinate;
    }

    public double getyCoordinate() {
        return yCoordinate;
    }

    public boolean isStopPossible() {
        return stopPossible;
    }

    public String[] toCsvStrings() {
        return toCsvStrings(1);
    }

    public String[] toCsvStrings(double conversionFactorCoordinates) {
        return new String[] {
            String.valueOf(nodeId),
            name,
            CsvWriter.shortenDecimalValueForOutput(xCoordinate / conversionFactorCoordinates),
            CsvWriter.shortenDecimalValueForOutput(yCoordinate / conversionFactorCoordinates),
            String.valueOf(stopPossible)
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InfrastructureNode that = (InfrastructureNode) o;
        return nodeId == that.nodeId &&
            Double.compare(that.xCoordinate, xCoordinate) == 0 &&
            Double.compare(that.yCoordinate, yCoordinate) == 0 &&
            stopPossible == that.stopPossible &&
            Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, name, xCoordinate, yCoordinate, stopPossible);
    }

    @Override
    public String toString() {
        return "InfrastructureNode{" +
            "nodeId=" + nodeId +
            ", name='" + name + '\'' +
            ", xCoordinate=" + xCoordinate +
            ", yCoordinate=" + yCoordinate +
            ", stopPossible=" + stopPossible +
            '}';
    }
}
