package net.lintim.io;

import net.lintim.model.*;
import net.lintim.util.Config;

import java.util.Collection;
import java.util.Comparator;

/**
 * Class to write infrastructure files.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the writer and use {@link #write()} afterwards.
 */
public class InfrastructureWriter {
    private final boolean writeNodes;
    private final boolean writeInfrastructureEdges;
    private final boolean writeWalkingEdges;
    private final String nodeFileName;
    private final String infrastructureEdgeFileName;
    private final String walkingEdgeFileName;
    private final String nodeHeader;
    private final String infrastructureEdgeHeader;
    private final String walkingEdgeHeader;
    private final double conversionFactorLength;
    private final double conversionFactorCoordinates;
    private final Graph<InfrastructureNode, InfrastructureEdge> infrastructureGraph;
    private final Graph<InfrastructureNode, WalkingEdge> walkingGraph;

    private InfrastructureWriter(Builder builder) {
        this.infrastructureGraph = builder.infrastructureGraph;
        this.walkingGraph = builder.walkingGraph;
        this.writeNodes = builder.writeNodes;
        this.writeInfrastructureEdges = builder.writeInfrastructureEdges;
        this.writeWalkingEdges = builder.writeWalkingEdges;
        if (writeNodes) {
            this.nodeFileName = "".equals(builder.nodeFileName) ? builder.config.getStringValue("filename_node_file") : builder.nodeFileName;
            this.nodeHeader = "".equals(builder.nodeHeader) ? builder.config.getStringValue("nodes_header") : builder.nodeHeader;
            this.conversionFactorCoordinates = builder.conversionFactorCoordinates == -1 ?
                builder.config.getDoubleValue("gen_conversion_coordinates") : builder.conversionFactorCoordinates;
        }
        else {
            this.nodeFileName = "";
            this.nodeHeader = "";
            this.conversionFactorCoordinates = 1;
        }
        if (writeInfrastructureEdges) {
            this.infrastructureEdgeFileName = "".equals(builder.infrastructureEdgeFileName) ? builder.config.getStringValue("filename_infrastructure_edge_file") : builder.infrastructureEdgeFileName;
            this.infrastructureEdgeHeader = "".equals(builder.infrastructureEdgeHeader) ? builder.config.getStringValue("infrastructure_edge_header") : builder.infrastructureEdgeHeader;
            this.conversionFactorLength = builder.conversionFactorLength == -1 ?
                builder.config.getDoubleValue("gen_conversion_length") : builder.conversionFactorLength;
        }
        else {
            this.infrastructureEdgeFileName = "";
            this.infrastructureEdgeHeader = "";
            this.conversionFactorLength = 1;
        }
        if (writeWalkingEdges) {
            this.walkingEdgeFileName = "".equals(builder.walkingEdgeFileName) ? builder.config.getStringValue("filename_walking_edge_file") : builder.walkingEdgeFileName;
            this.walkingEdgeHeader = "".equals(builder.walkingEdgeHeader) ? builder.config.getStringValue("walking_edge_header") : builder.walkingEdgeHeader;
        }
        else {
            this.walkingEdgeFileName = "";
            this.walkingEdgeHeader = "";
        }
    }

    /**
     * Start the writing process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     */
    public void write(){
        if (writeNodes) {
            Collection<InfrastructureNode> nodes = infrastructureGraph == null ?
                walkingGraph.getNodes() : infrastructureGraph.getNodes();
            CsvWriter.writeCollection(nodeFileName, nodeHeader, nodes, n -> n.toCsvStrings(conversionFactorCoordinates),
                Comparator.comparingInt(InfrastructureNode::getId));
        }
        if (writeInfrastructureEdges) {
            CsvWriter.writeCollection(infrastructureEdgeFileName, infrastructureEdgeHeader,
                infrastructureGraph.getEdges(), e -> e.toCsvStrings(conversionFactorLength),
                Comparator.comparingInt(InfrastructureEdge::getId));
        }
        if (writeWalkingEdges) {
            CsvWriter.writeCollection(walkingEdgeFileName, walkingEdgeHeader, walkingGraph.getEdges(),
                WalkingEdge::toCsvStrings, Comparator.comparingInt(WalkingEdge::getId));
        }
    }
    /**
     * Builder object for a infrastructure writer.
     *
     * Use {@link #Builder(Graph, Graph)} to create a builder with default options, afterwards use the setter to adapt
     * it. The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder(Graph, Graph)}. To create a writer
     * object, use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private boolean writeNodes = true;
        private boolean writeInfrastructureEdges = true;
        private boolean writeWalkingEdges = true;
        private String nodeFileName = "";
        private String infrastructureEdgeFileName = "";
        private String walkingEdgeFileName = "";
        private String nodeHeader = "";
        private String infrastructureEdgeHeader = "";
        private String walkingEdgeHeader = "";
        private double conversionFactorLength = -1;
        private double conversionFactorCoordinates = -1;
        private Graph<InfrastructureNode, InfrastructureEdge> infrastructureGraph;
        private Graph<InfrastructureNode, WalkingEdge> walkingGraph;
        private Config config = Config.getDefaultConfig();

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         writeNodes (true) - to write the nodes file. This will write the nodes from the infrastructure
         *         graph (if it is not null) and the nodes from the walking graph otherwise
         *     </li>
         *     <li>
         *         writeInfrastructureEdges (true) - whether to write the infrastructure edge file
         *     </li>
         *     <li>
         *         writeWalkingEdges (true) - whether to write the walking edge file
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()}) - the config to read the file names and headers from. This
         *          will only happen, if the file names are not given, but queried.
         *     </li>
         *     <li>
         *          nodeFileName (dependent on config) - the file name to write the nodes to
         *     </li>
         *     <li>
         *          infrastructureEdgeFileName (dependent on config) - the file name to write the infrastructure edges to
         *     </li>
         *     <li>
         *          walkingEdgeFileName (dependent on config) - the file name to write the walking edges to
         *     </li>
         *     <li>
         *          nodeHeader (dependent on config) - the header to use for the node file
         *     </li>
         *     <li>
         *          infrastructureEdgeHeader (dependent on config) - the header to use for the infrastructure edge
         *          file
         *     </li>
         *     <li>
         *          walkingEdgeHeader (dependent on config) - the header to use for the walking edge file
         *     </li>
         *     <li>
         *         conversionFactorLength (dependent on config) - the conversion factor to converse the length of
         *         an edge to km
         *     </li>
         *     <li>
         *         conversionFactorCoordinates (dependent on config) - the conversion factor for the coordinates
         *     </li>
         *     <li>
         *          infrastructureGraph (given in constructor) - the infrastructure graph to write.
         *          Can only be set in the constructor
         *     </li>
         *     <li>
         *          walkingGraph (given in constructor) - the walking graph to write. Can only be set in the constructor
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a writer with the given parameters.
         * @param infrastructureGraph the infrastructure to write
         * @param walkingGraph the walking graph to write
         */
        public Builder(Graph<InfrastructureNode, InfrastructureEdge> infrastructureGraph,
                       Graph<InfrastructureNode, WalkingEdge> walkingGraph) {
            this.infrastructureGraph = infrastructureGraph;
            this.walkingGraph = walkingGraph;
        }

        public Builder writeNodes(boolean writeNodes) {
            this.writeNodes = writeNodes;
            return this;
        }

        public Builder writeInfrastructureEdges(boolean writeInfrastructureEdges) {
            this.writeInfrastructureEdges = writeInfrastructureEdges;
            return this;
        }

        public Builder writeWalkingEdges(boolean writeWalkingEdges) {
            this.writeWalkingEdges = writeWalkingEdges;
            return this;
        }

        public Builder setNodeFileName(String nodeFileName) {
            this.nodeFileName = nodeFileName;
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

        public Builder setNodeHeader (String nodeHeader) {
            this.nodeHeader = nodeHeader;
            return this;
        }

        public Builder setInfrastructureEdgeHeader(String infrastructureEdgeHeader) {
            this.infrastructureEdgeHeader = infrastructureEdgeHeader;
            return this;
        }

        public Builder setWalkingEdgeHeader(String walkingEdgeHeader) {
            this.walkingEdgeHeader = walkingEdgeHeader;
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

        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Create a new infrastructure writer with the current builder settings
         * @return the new writer. Use {@link InfrastructureWriter#write()} for the writing process.
         */
        public InfrastructureWriter build() {
            return new InfrastructureWriter(this);
        }
    }
}
