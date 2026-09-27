package net.lintim.io;

import net.lintim.exception.*;
import net.lintim.model.*;
import net.lintim.model.impl.ArrayListGraph;
import net.lintim.model.impl.SimpleMapGraph;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.Pair;

import java.util.Arrays;
import java.util.Collection;

/**
 * Class to read infrastructure files.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards
 */
public class InfrastructureReader {

    private static final Logger logger = new Logger(InfrastructureReader.class.getCanonicalName());

    private final Graph<InfrastructureNode, InfrastructureEdge> infrastructureGraph;
    private final Graph<InfrastructureNode, WalkingEdge> walkingGraph;
    private final boolean readNodes;
    private final boolean readInfrastructureEdges;
    private final boolean readWalkingEdges;
    private final String nodeFileName;
    private final String infrastructureEdgeFileName;
    private final String walkingEdgeFileName;
    private final boolean directedInfrastructure;
    private final boolean directedWalking;
    private final double maxWalkingTime;
    private final double conversionFactorLength;
    private final double conversionFactorCoordinates;

    private InfrastructureReader(Builder builder) {
        readNodes = builder.readNodes;
        readInfrastructureEdges = builder.readInfrastructureEdges;
        readWalkingEdges = builder.readWalkingEdges;
        if (readNodes || readInfrastructureEdges) {
            infrastructureGraph = builder.infrastructureGraph == null ? new SimpleMapGraph<>() :
                builder.infrastructureGraph;
        }
        else {
            infrastructureGraph = null;
        }
        if (readNodes || readWalkingEdges) {
            walkingGraph = builder.walkingGraph == null ? new SimpleMapGraph<>() : builder.walkingGraph;
        }
        else {
            walkingGraph = null;
        }
        if (readNodes) {
            nodeFileName = "".equals(builder.nodeFileName) ? builder.config.getStringValue("filename_node_file") :
                builder.nodeFileName;
            conversionFactorCoordinates = builder.conversionFactorCoordinates == null ? builder.config.getDoubleValue("gen_conversion_coordinates") :
                builder.conversionFactorCoordinates;
        }
        else {
            nodeFileName = "";
            conversionFactorCoordinates = 0;
        }
        if (readInfrastructureEdges) {
            infrastructureEdgeFileName = "".equals(builder.infrastructureEdgeFileName) ? builder.config.getStringValue("filename_infrastructure_edge_file") :
                builder.infrastructureEdgeFileName;
            directedInfrastructure = builder.directedInfrastructure == null ? !builder.config.getBooleanValue("ptn_is_undirected") :
                builder.directedInfrastructure;
        }
        else {
            infrastructureEdgeFileName = "";
            directedInfrastructure = false;
        }
        if (readWalkingEdges) {
            walkingEdgeFileName = "".equals(builder.walkingEdgeFileName) ? builder.config.getStringValue("filename_walking_edge_file") :
                builder.walkingEdgeFileName;
            directedWalking = builder.directedWalking == null ? builder.config.getBooleanValue("sl_walking_is_directed") :
                builder.directedWalking;
            maxWalkingTime = builder.maxWalkingTime == null ? builder.config.getDoubleValue("sl_max_walking_time") :
                builder.maxWalkingTime;
        }
        else {
            walkingEdgeFileName = "";
            directedWalking = false;
            maxWalkingTime = 0;
        }
        if (readInfrastructureEdges || readWalkingEdges) {
            conversionFactorLength = builder.conversionFactorLength == null ? builder.config.getDoubleValue("gen_conversion_length") :
                builder.conversionFactorLength;
        }
        else {
            conversionFactorLength = 0;
        }
    }

    /**
     * Read the infrastructure. To determine which files to read, a {@link Builder} object is used. See the
     * corresponding documentation for possible configuration options
     * @return the infrastructure and the walking graph
     */
    public Pair<Graph<InfrastructureNode, InfrastructureEdge>, Graph<InfrastructureNode, WalkingEdge>> read() {
        if (readNodes) {
            CsvReader.readCsv(nodeFileName, this::processNodeLine);
        }
        if (readInfrastructureEdges) {
            CsvReader.readCsv(infrastructureEdgeFileName, this::processInfrastructureEdgeLine);
        }
        if (readWalkingEdges) {
            CsvReader.readCsv(walkingEdgeFileName, this::processWalkingEdgeLine);
        }
        return new Pair<>(infrastructureGraph, walkingGraph);
    }

    private void processNodeLine(String[] args, int lineNumber) {
        if (args.length != 5) {
            throw new InputFormatException(nodeFileName, args.length, 5);
        }
        int nodeId;
        String name;
        double xCoord;
        double yCoord;
        boolean stopPossible;
        try {
            nodeId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(nodeFileName, 1, lineNumber, "int", args[0]);
        }
        name = args[1];
        try {
            xCoord = Double.parseDouble(args[2]) * conversionFactorCoordinates;
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(nodeFileName, 3, lineNumber, "double", args[2]);
        }
        try {
            yCoord = Double.parseDouble(args[3]) * conversionFactorCoordinates;
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(nodeFileName, 4, lineNumber, "double", args[3]);
        }
        String value = args[4].toLowerCase();
        if (value.equals("true")) {
            stopPossible = true;
        }
        else if (value.equals("false")) {
            stopPossible = false;
        }
        else {
            throw new InputTypeInconsistencyException(nodeFileName, 5, lineNumber, "boolean", args[4]);
        }

        InfrastructureNode node = new InfrastructureNode(nodeId, name, xCoord, yCoord, stopPossible);
        boolean stopAdded = infrastructureGraph.addNode(node) && walkingGraph.addNode(node);
        if (!stopAdded) {
            throw new GraphNodeIdMultiplyAssignedException(nodeId);
        }
    }

    private void processInfrastructureEdgeLine(String[] args, int lineNumber) {
        if (args.length != 6) {
            throw new InputFormatException(infrastructureEdgeFileName, args.length, 6);
        }
        int edgeId;
        int leftNodeId;
        int rightNodeId;
        double length;
        int lowerBound;
        int upperBound;

        try {
            edgeId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(infrastructureEdgeFileName, 1, lineNumber, "int", args[0]);
        }
        try {
            leftNodeId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(infrastructureEdgeFileName, 2, lineNumber, "int", args[1]);
        }
        try {
            rightNodeId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(infrastructureEdgeFileName, 3, lineNumber, "int", args[2]);
        }
        try {
            length = Double.parseDouble(args[3]) * conversionFactorLength;
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(infrastructureEdgeFileName, 4, lineNumber, "double", args[3]);
        }
        try {
            lowerBound = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(infrastructureEdgeFileName, 5, lineNumber, "int", args[4]);
        }
        try {
            upperBound = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(infrastructureEdgeFileName, 6, lineNumber, "int", args[5]);
        }

        InfrastructureNode leftNode = infrastructureGraph.getNode(leftNodeId);
        if (leftNode == null) {
            throw new GraphIncidentNodeNotFoundException(edgeId, leftNodeId);
        }
        InfrastructureNode rightNode = infrastructureGraph.getNode(rightNodeId);
        if (rightNode == null) {
            throw new GraphIncidentNodeNotFoundException(edgeId, rightNodeId);
        }
        InfrastructureEdge edge = new InfrastructureEdge(edgeId, leftNode, rightNode, length, lowerBound, upperBound,
            directedInfrastructure);

        boolean edgeAdded = infrastructureGraph.addEdge(edge);

        if (!edgeAdded) {
            throw new GraphEdgeIdMultiplyAssignedException(edgeId);
        }
    }

    private void processWalkingEdgeLine(String[] args, int lineNumber) {
        if (args.length != 4) {
            throw new InputFormatException(walkingEdgeFileName, args.length, 4);
        }

        int edgeId;
        int leftNodeId;
        int rightNodeId;
        double length;

        try {
            edgeId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(infrastructureEdgeFileName, 1, lineNumber, "int", args[0]);
        }
        try {
            leftNodeId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(infrastructureEdgeFileName, 2, lineNumber, "int", args[1]);
        }
        try {
            rightNodeId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(infrastructureEdgeFileName, 3, lineNumber, "int", args[2]);
        }
        try {
            length = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(infrastructureEdgeFileName, 4, lineNumber, "double", args[3]);
        }

        if (maxWalkingTime > 0 && length > maxWalkingTime) {
            return;
        }

        InfrastructureNode leftNode = walkingGraph.getNode(leftNodeId);
        if (leftNode == null) {
            throw new GraphIncidentNodeNotFoundException(edgeId, leftNodeId);
        }
        InfrastructureNode rightNode = walkingGraph.getNode(rightNodeId);
        if (rightNode == null) {
            throw new GraphIncidentNodeNotFoundException(edgeId, rightNodeId);
        }
        WalkingEdge edge = new WalkingEdge(edgeId, leftNode, rightNode, length, directedWalking);

        boolean edgeAdded = walkingGraph.addEdge(edge);

        if (!edgeAdded) {
            throw new GraphEdgeIdMultiplyAssignedException(edgeId);
        }
    }

    /**
     * Builder object for an infrastructure reader.
     *
     * Use {@link #Builder()} to create a builder with default options, afterwards use the setter to adapt it.
     * The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder()}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private Graph<InfrastructureNode, InfrastructureEdge> infrastructureGraph;
        private Graph<InfrastructureNode, WalkingEdge> walkingGraph;
        private Double maxWalkingTime;
        private boolean readNodes = true;
        private boolean readInfrastructureEdges = true;
        private boolean readWalkingEdges = true;
        private String nodeFileName = "";
        private String infrastructureEdgeFileName = "";
        private String walkingEdgeFileName = "";
        private Boolean directedInfrastructure;
        private Boolean directedWalking;
        private Double conversionFactorLength;
        private Double conversionFactorCoordinates;
        private Config config = Config.getDefaultConfig();

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         readNodes (true) - whether to read nodes
         *     </li>
         *     <li>
         *         readInfrastructureEdges (true) - whether to read infrastructure edges
         *     </li>
         *     <li>
         *         readWalkingEdges (true) - whether to read walking edges
         *     </li>
         *     <li>
         *         maxWalkingTime (dependent on config) - the maximal length of edges to read. Edges longer than that
         *         will be ignored. Set to -1 to read all edges
         *     </li>
         *     <li>
         *         nodeFileName (dependent on config) - the file to read the nodes from
         *     </li>
         *     <li>
         *         infrastructureEdgeFileName (dependent on config) - the file to read the infrastructure edges from
         *     </li>
         *     <li>
         *         walkingEdgeFileName (dependent on config) - the file to read the walking edges from
         *     </li>
         *     <li>
         *         directedInfrastructure (dependent on config) - whether the read infrastructure network should be
         *         interpreted as directed or not
         *     </li>
         *     <li>
         *         directedWalking (dependent on config) - whether the read walking network should be
         *         interpreted as directed or not
         *     </li>
         *     <li>
         *         conversionFactorLength (dependent on config) - the factor to obtain the length in kilometers from
         *         the given edge lengths
         *     </li>
         *     <li>
         *         conversionFactorCoordinates (dependent on config) - the factor to convert the distance between
         *         the coordinates to kilometers
         *     </li>
         *     <li>
         *          infrastructureGraph (empty {@link SimpleMapGraph}) - the infrastructure graph to write to
         *     </li>
         *     <li>
         *          walkingGraph (empty {@link SimpleMapGraph}) - the walking graph to write to
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()}) - the config to read the file name from. This will only
         *         happen, if the file name is not given, but queried.
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         */
        public Builder() {}

        public Builder readNodes(boolean readNodes) {
            this.readNodes = readNodes;
            return this;
        }

        public Builder readInfrastructureEdges(boolean readInfrastructureEdges) {
            this.readInfrastructureEdges = readInfrastructureEdges;
            return this;
        }

        public Builder readWalkingEdges(boolean readWalkingEdges) {
            this.readWalkingEdges = readWalkingEdges;
            return this;
        }

        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        public Builder setNodeFileName(String nodeFileName) {
            this.nodeFileName = nodeFileName;
            return this;
        }

        public Builder setInfrastructureGraph(Graph<InfrastructureNode, InfrastructureEdge> infrastructureGraph) {
            this.infrastructureGraph = infrastructureGraph;
            return this;
        }

        public Builder setWalkingGraph(Graph<InfrastructureNode, WalkingEdge> walkingGraph) {
            this.walkingGraph = walkingGraph;
            return this;
        }

        public Builder setInfrastructureEdgeFileName(String infrastructureEdgeFileName) {
            this.infrastructureEdgeFileName = infrastructureEdgeFileName;
            return this;
        }

        public Builder setWalkingEdgeFileName(String walkingEdgeFileName) {
            this.walkingEdgeFileName = walkingEdgeFileName;
            return this;
        }

        public Builder setDirectedInfrastructure(boolean directedInfrastructure) {
            this.directedInfrastructure = directedInfrastructure;
            return this;
        }

        public Builder setDirectedWalking(boolean directedWalking) {
            this.directedWalking = directedWalking;
            return this;
        }

        public Builder setConversionFactorLength(double conversionFactorLength) {
            this.conversionFactorLength = conversionFactorLength;
            return this;
        }

        public Builder setConversionFactorCoordinates(double conversionFactorCoordinates) {
            this.conversionFactorCoordinates = conversionFactorCoordinates;
            return this;
        }

        public Builder setMaxWalkingTime(double maxWalkingTime) {
            this.maxWalkingTime = maxWalkingTime;
            return this;
        }

        /**
         * Create a new infrastructure reader with the current builder settings.
         * @return the new reader. Use {@link InfrastructureReader#read()} for the reading process.
         */
        public InfrastructureReader build() {
            return new InfrastructureReader(this);
        }
    }
}
