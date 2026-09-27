package net.lintim.generator;

import net.lintim.exception.DataInconsistentException;
import net.lintim.graph.GraphMalformedException;
import net.lintim.graph.ShortestPathsGraph;
import net.lintim.model.*;
import net.lintim.util.IterationProgressCounter;
import net.lintim.util.NullIterationProgressCounter;

import java.util.LinkedHashSet;
import java.util.LinkedList;

/**
 * Computes the public transportation network edge load from either a passenger
 * distribution in an event activity network or by distributing passengers in
 * a public transportation network w.r.t. shortest paths.
 */

public class LoadGenerator {
    private PublicTransportationNetwork ptn;
    private OriginDestinationMatrix od;
    private LineCollection lc;
    private EventActivityNetwork ean;

    public enum LoadGeneratorModel {
        LOAD_FROM_EAN, LOAD_FROM_PTN
    }

    private LoadGeneratorModel lgenmodel;
    private PeriodicPassengerDistributionGenerator.InitialWeightDrive llmodel;
    private Boolean rememberOdPaths;

    private Double passengersPerVehicle;
    private Double lowerFrequencyFactor;
    private Double upperFrequencyFactor;
    private Boolean fixUpperFrequency;
    private Integer fixedUpperFrequency;

    private IterationProgressCounter iterationProgressCounter =
        new NullIterationProgressCounter();

    /**
     * Constructor.
     *
     * @param ptn The public transportation network.
     * @param od The origin destination matrix.
     * @param ean The event activity network, may be null if passengers are
     * routed through the PTN.
     * @param lgenmodel The load generator model.
     * @param llmodel The link load model.
     * @param rememberOdPaths Whether or not to compute and store PTN paths.
     * @param passengersPerVehicle The number of passengers per vehicle.
     * @param lowerFrequencyFactor Number to multiply the lower frequency,
     * before rounding.
     * @param upperFrequencyFactor Number to multiply the upper frequency,
     * before rounding.
     * @param fixUpperFrequency True if a fixed upper frequency should be used,
     * false otherwise.
     * @param fixedUpperFrequency If a fixed upper frequency is used, which?
     * @throws DataInconsistentException
     */
    public LoadGenerator(PublicTransportationNetwork ptn,
            OriginDestinationMatrix od,
            EventActivityNetwork ean, LoadGeneratorModel lgenmodel,
            PeriodicPassengerDistributionGenerator.InitialWeightDrive llmodel,
            Boolean rememberOdPaths,
            Double passengersPerVehicle, Double lowerFrequencyFactor,
            Double upperFrequencyFactor, Boolean fixUpperFrequency,
            Integer fixedUpperFrequency) throws DataInconsistentException {

        setPublicTransportationNetwork(ptn);
        setOriginDestinationMatrix(od);
        setLineConcept(ean == null ? null : ean.getLineConcept());
        setEventActivityNetwork(ean);
        setLoadGeneratorModel(lgenmodel);
        setLinkLoadModel(llmodel);
        setRememberOrginDestinationPaths(rememberOdPaths);
        setPassengersPerVehicle(passengersPerVehicle);
        setLowerFrequencyFactor(lowerFrequencyFactor);
        setUpperFrequencyFactor(upperFrequencyFactor);
        setFixedUpperFrequency(fixedUpperFrequency);
        setFixUpperFrequency(fixUpperFrequency);

    }

    /**
     * Config constructor, redirects to
     * {@link #LoadGenerator(PublicTransportationNetwork,
     * OriginDestinationMatrix, EventActivityNetwork, LoadGeneratorModel,
     * net.lintim.generator.PeriodicPassengerDistributionGenerator.InitialWeightDrive,
     * Boolean, Double, Double, Double, Boolean, Integer)
     *
     * @param ptn The public transportation network.
     * @param od The origin destination matrix.
     * @param ean The event activity network, may be null if passengers are
     * routed through the PTN.
     * @param config The configuration to extract the rest of the parameters from.
     * @throws DataInconsistentException
     */
    public LoadGenerator(PublicTransportationNetwork ptn,
            OriginDestinationMatrix od, EventActivityNetwork ean,
            Configuration config)
            throws DataInconsistentException {

        this(ptn, od, ean, LoadGeneratorModel.valueOf(config
                .getStringValue("load_generator_model")),
                PeriodicPassengerDistributionGenerator.InitialWeightDrive
                .valueOf(config.getStringValue("ean_model_weight_drive")),
                config.getBooleanValue("ptn_remember_od_paths"), config
                        .getDoubleValue("gen_passengers_per_vehicle"),
                config.getDoubleValue("load_generator_lower_frequency_factor"),
                config.getDoubleValue("load_generator_upper_frequency_factor"),
                config.getBooleanValue("load_generator_fix_upper_frequency"),
                config.getIntegerValue("load_generator_fixed_upper_frequency"));

    }

    /**
     * Shortest paths weight of a link.
     *
     * @param link The respective link.
     * @return The shortest paths weight.
     */
    private Double getWeight(Link link) {
        switch (llmodel) {
        case MINIMAL_DRIVING_TIME:
            return link.getLowerBound();
        case AVERAGE_DRIVING_TIME:
            return (link.getLowerBound() + link.getUpperBound()) / 2.0;
        case MAXIMAL_DRIVING_TIME:
            return link.getUpperBound();
        case EDGE_LENGTH:
            return link.getLength();
        default:
            return null;
        }
    }

    /**
     * Performs the actual all pairs shortest paths calculation.
     *
     * @throws DataInconsistentException
     */
    public void computeLinkLoad() throws DataInconsistentException {

        if (lgenmodel == LoadGeneratorModel.LOAD_FROM_PTN) {

            if (od == null) {
                throw new DataInconsistentException("origin destination "
                        + "matrix required for LOAD_FROM_PTN, but not given");
            }

            ShortestPathsGraph<Station, Link> sp =
                new ShortestPathsGraph<Station, Link>();

            LinkedHashSet<Station> stations = ptn.getStations();

            for (Station station : stations) {
                sp.addVertex(station);
            }

            for (Link link : ptn.getDirectedLinks()) {
                sp.addEdge(link, link.getFromStation(), link
                        .getToStation(), getWeight(link));
                link.setLoad(0.0);
            }

            iterationProgressCounter.setTotalNumberOfIterations(
                    ptn.getStations().size());

            for (Station source : stations) {
                iterationProgressCounter.reportIteration();

                try {
                    sp.compute(source);
                } catch (GraphMalformedException e) {
                    throw new DataInconsistentException("shortest paths" +
                            "calculation failed: " + e.getMessage());
                }

                for (Station target : stations) {
                    if (source == target) {
                        continue;
                    }

                    Double passengers = od.get(source, target);

                    LinkedList<Link> path = sp.trackPath(target);

                    if (path.size() == 0) {
                        throw new DataInconsistentException("there is no "
                                + "path from station " + source.getIndex()
                                + " to " + target.getIndex());
                    }

                    LinkedHashSet<Link> linkPath = rememberOdPaths ?
                            new LinkedHashSet<Link>() : null;

                    for (Link link : path) {
                        link.setLoad(link.getLoad() + passengers);
                        if(rememberOdPaths){
                            linkPath.add(link);
                        }
                    }

                    if(rememberOdPaths){
                        ptn.addOriginDestinationPath(source, target, linkPath);
                    }

                }

            }
            if(rememberOdPaths){
                ptn.completeOriginDestinationPathMap();
            }

        }

        else {

            if (ean == null) {
                throw new DataInconsistentException("event activity network "
                        + "required for LOAD_FROM_EAN, but not given");
            }

            LinkedHashSet<Link> links = ptn.getDirectedLinks();

            for (Link link : links) {
                link.setLoad(0.0);
            }

            for (Activity a : ean.getDriveActivities()) {
                Link link = a.getAssociatedLink();
                link.setLoad(link.getLoad() + a.getPassengers());
            }
        }

        if (passengersPerVehicle == null) {
            throw new DataInconsistentException("number of passengers per "
                    + "vehicle not given");
        }

        for (Link link : ptn.getDirectedLinks()) {
            link.setLowerFrequency((int) Math.ceil(link.getLoad()
                    / passengersPerVehicle * lowerFrequencyFactor));
            if (fixUpperFrequency) {
                link.setUpperFrequency(fixedUpperFrequency);
            } else {
                link.setUpperFrequency((int) Math.round(link.getLoad()
                        / passengersPerVehicle * upperFrequencyFactor));
            }

        }

    }

    // =========================================================================
    // === Setters =============================================================
    // =========================================================================
    public void setPassengersPerVehicle(Double passengersPerVehicle) {
        this.passengersPerVehicle = passengersPerVehicle;
    }

    public void setPublicTransportationNetwork(PublicTransportationNetwork ptn) {
        this.ptn = ptn;
    }

    public void setOriginDestinationMatrix(OriginDestinationMatrix od) {
        this.od = od;
    }

    public void setLineConcept(LineCollection lc) {
        this.lc = lc;
    }

    public void setEventActivityNetwork(EventActivityNetwork ean) {
        this.ean = ean;
    }

    public void setLoadGeneratorModel(LoadGeneratorModel lgenmodel) {
        this.lgenmodel = lgenmodel;
    }

    public void setLinkLoadModel(
            PeriodicPassengerDistributionGenerator.InitialWeightDrive llmodel) {

        this.llmodel = llmodel;
    }

    public void setFixUpperFrequency(Boolean fixUpperFrequency) {
        this.fixUpperFrequency = fixUpperFrequency;
    }

    public void setLowerFrequencyFactor(Double lowerFrequencyFactor) {
        this.lowerFrequencyFactor = lowerFrequencyFactor;
    }

    public void setUpperFrequencyFactor(Double upperFrequencyFactor) {
        this.upperFrequencyFactor = upperFrequencyFactor;
    }

    public void setFixedUpperFrequency(Integer fixedUpperFrequency) {
        this.fixedUpperFrequency = fixedUpperFrequency;
    }

    public void setRememberOrginDestinationPaths(Boolean rememberOdPaths) {
        this.rememberOdPaths = rememberOdPaths;
    }

    public void setIterationProgressCounter(
            IterationProgressCounter iterationProgressCounter) {
        this.iterationProgressCounter = iterationProgressCounter;
    }

    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public Double getPassengersPerVehicle() {
        return passengersPerVehicle;
    }

    public PublicTransportationNetwork getPublicTransportationNetwork() {
        return ptn;
    }

    public OriginDestinationMatrix getOriginDestinationMatrix() {
        return od;
    }

    public LineCollection getLineConcept() {
        return lc;
    }

    public EventActivityNetwork getEventActivityNetwork() {
        return ean;
    }

    public PeriodicPassengerDistributionGenerator.InitialWeightDrive getLinkLoadModel() {
        return llmodel;
    }

    public Double getLowerFrequencyFactor() {
        return lowerFrequencyFactor;
    }

    public Double getUpperFrequencyFactor() {
        return upperFrequencyFactor;
    }

    public Boolean getFixUpperFrequency() {
        return fixUpperFrequency;
    }

    public Integer getFixedUpperFrequency() {
        return fixedUpperFrequency;
    }

    public LoadGeneratorModel getLoadGeneratorModel() {
        return lgenmodel;
    }

    public Boolean getRememberOrginDestinationPaths() {
        return rememberOdPaths;
    }

}
