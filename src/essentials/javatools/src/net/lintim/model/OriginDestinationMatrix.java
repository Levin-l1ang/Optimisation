package net.lintim.model;

import net.lintim.exception.DataInconsistentException;
import net.lintim.util.BiLinkedHashMap;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/**
 * Implementation of an origin destination matrix (OD matrix). It provides
 * pairwise usage data for all {@link Station}s in a
 * {@link PublicTransportationNetwork}.
 */

public class OriginDestinationMatrix {

    protected PublicTransportationNetwork ptn;
    protected BiLinkedHashMap<Station, Station, Double> matrix =
        new BiLinkedHashMap<Station, Station, Double>();

    protected Boolean isSymmetric = true;

    /**
     * Constructor.
     *
     * @param ptn The underlying public transportation network.
     */
    public OriginDestinationMatrix(PublicTransportationNetwork ptn) {
        this.ptn = ptn;
    }

    protected void addUnchecked(Station fromStation, Station toStation,
            Double amount){

        matrix.put(fromStation, toStation, amount);

    }

    /**
     * Adds an entry by station indices.
     *
     * @param fromStationIndex The index of the from station.
     * @param toStationIndex The index of the to station.
     * @param amount The number of passengers.
     * @throws DataInconsistentException
     */
    public void add(Integer fromStationIndex,
            Integer toStationIndex, Double amount) throws DataInconsistentException{

        Station from = ptn.getStationByIndex(fromStationIndex);
        Station to = ptn.getStationByIndex(toStationIndex);

        if(from == null){
            throw new DataInconsistentException("station with index " +
                    fromStationIndex + " not found");
        }

        if(to == null){
            throw new DataInconsistentException("station with index " +
                    toStationIndex + " not found");
        }

        if(matrix.containsKey(from) && matrix.get(from).containsKey(to)){
            throw new DataInconsistentException("origin destination matrix " +
                    "entry for stations (" + fromStationIndex + ", " +
                    toStationIndex + ") at least given twice");
        }

        if(amount == null){
            throw new DataInconsistentException("amount is null");
        }

        if(amount < 0){
            throw new DataInconsistentException("amount below zero");
        }

        addUnchecked(from, to, amount);
        isSymmetric = false;

    }

    /**
     * Adds two (symmetric) entries by station indices.
     *
     * @param leftStationIndex The index of the left station.
     * @param rightStationIndex The index of the right station.
     * @param amount The number of passengers.
     * @throws DataInconsistentException
     */
    public void addSymmetric(Integer leftStationIndex,
            Integer rightStationIndex, Double amount)
    throws DataInconsistentException{

        Station leftStation = ptn.getStationByIndex(leftStationIndex);
        Station rightStation = ptn.getStationByIndex(rightStationIndex);

        if(leftStation == null){
            throw new DataInconsistentException("station with index " +
                    leftStationIndex + " not found");
        }

        if(rightStation == null){
            throw new DataInconsistentException("station with index " +
                    rightStationIndex + " not found");
        }

        if(matrix.containsKey(leftStation) &&
                matrix.get(leftStation).containsKey(rightStation) ||
                matrix.containsKey(rightStation) &&
                matrix.get(rightStation).containsKey(leftStation)){

            throw new DataInconsistentException("origin destination matrix " +
                    "entry for stations (" + leftStationIndex + ", " +
                    rightStationIndex + ") at least given twice");
        }

        if(amount == null){
            throw new DataInconsistentException("amount is null");
        }

        if(amount < 0){
            throw new DataInconsistentException("amount below zero");
        }

        addUnchecked(leftStation, rightStation, amount);
        addUnchecked(rightStation, leftStation, amount);

    }

    /**
     * Adds an (asymmetric) entry by station objects.
     *
     * @param fromStation The from station.
     * @param toStation The to station.
     * @param amount The number of passengers.
     * @throws DataInconsistentException
     */
    public void add(Station fromStation, Station toStation,
            Double amount) throws DataInconsistentException{

        if(fromStation == null){
            throw new DataInconsistentException("fromStation is null");
        }

        if(!ptn.getStations().contains(fromStation)){
            throw new DataInconsistentException("fromStation not part of " +
                    "public transportation network");
        }

        if(toStation == null){
            throw new DataInconsistentException("toStation is null");
        }

        if(!ptn.getStations().contains(toStation)){
            throw new DataInconsistentException("toStation not part of " +
                    "public transportation network");
        }

        if(matrix.containsKey(fromStation) &&
                matrix.get(fromStation).containsKey(toStation)){

            throw new DataInconsistentException("origin destination matrix " +
                    "entry for stations (" + fromStation.getIndex() + ", "
                    + toStation.getIndex() + ") at least given twice");
        }

        if(amount == null){
            throw new DataInconsistentException("amount is null");
        }

        if(amount < 0){
            throw new DataInconsistentException("amount below zero");
        }

        addUnchecked(fromStation, toStation, amount);
        isSymmetric = false;

    }

    /**
     * Adds two (symmetric) entries by station objects.
     *
     * @param leftStation The left station.
     * @param rightStation The right station.
     * @param amount The number of passengers.
     * @throws DataInconsistentException
     */
    public void addSymmetric(Station leftStation, Station rightStation,
            Double amount) throws DataInconsistentException{

        if(leftStation == null){
            throw new DataInconsistentException("fromStation is null");
        }

        if(!ptn.getStations().contains(leftStation)){
            throw new DataInconsistentException("fromStation not part of " +
                    "public transportation network");
        }

        if(rightStation == null){
            throw new DataInconsistentException("toStation is null");
        }

        if(!ptn.getStations().contains(rightStation)){
            throw new DataInconsistentException("toStation not part of " +
                    "public transportation network");
        }

        if(matrix.containsKey(leftStation) &&
                matrix.get(leftStation).containsKey(rightStation) ||
                matrix.containsKey(rightStation) &&
                matrix.get(rightStation).containsKey(leftStation)){

            throw new DataInconsistentException("origin destination matrix " +
                    "entry for stations (" + leftStation.getIndex() + ", "
                    + rightStation.getIndex() + ") at least given twice");
        }

        if(amount == null){
            throw new DataInconsistentException("amount is null");
        }

        if(amount < 0){
            throw new DataInconsistentException("amount below zero");
        }

        addUnchecked(leftStation, rightStation, amount);
        addUnchecked(rightStation, leftStation, amount);

    }

    /**
     * Removes origin destination data for a station.
     *
     * @param station The station to remove.
     * @return
     */
    public LinkedHashMap<Station, Double> removeStation(Station station){
        return matrix.remove(station);
    }

    /**
     * Returns the number of passengers that travel
     * (<code>fromStation</code> -&gt; <code>toStation</code>).
     *
     * @param fromStation The from station.
     * @param toStation The to station.
     * @return
     */
    public Double get(Station fromStation, Station toStation){
        return matrix.get(fromStation, toStation);
    }

    /**
     * Access the underlying public transportation network.
     *
     * @return The underlying public transportation network.
     */
    public PublicTransportationNetwork getPublicTransportationNetwork() {
        return ptn;
    }

    /**
     * Checks whether for all station pairs there are entries and throws an
     * exception with a list of missing data if there is data missing.
     *
     * @throws DataInconsistentException
     */
    public void checkCompleteness() throws DataInconsistentException{
        LinkedHashSet<Station> stations = ptn.getStations();

        if(!stations.containsAll(matrix.keySet())){
            LinkedHashSet<Station> stationsDifference =
                new LinkedHashSet<Station>(stations);

            stationsDifference.removeAll(matrix.keySet());

            String errormsg = "origin destination data missing for " +
                    "stations ";

            Boolean firstrun = true;

            for(Station station : stationsDifference){
                Integer index = station.getIndex();
                if(firstrun){
                    errormsg += index;
                    firstrun = false;
                }
                else{
                    errormsg += ", " + index;
                }
            }

            throw new DataInconsistentException(errormsg);
        }

        Boolean isBroken = false;
        String errormsg = "origin destination data missing for the " +
                "following stations pairs: ";

        Boolean firstrun = true;

        for(Station s1 : stations){

            for(Station s2 : stations){

                Integer index1 = s1.getIndex();
                Integer index2 = s2.getIndex();
                Double value = matrix.get(s1, s2);

                if(isSymmetric && index2 < index1){
                    continue;
                }

                if(value == null && index1 != index2){

                    isBroken = true;

                    if(firstrun){
                        firstrun = false;
                    }
                    else{
                        errormsg += ", ";
                    }

                    errormsg += "(" + index1 + ", " + index2 + ")";

                }
            }
        }

        if(isBroken){
            throw new DataInconsistentException(errormsg);
        }

    }

    /**
     * Access matrix symmetry.
     *
     * @return Whether or not the entries are symmetric.
     */
    public Boolean isSymmetric() {
        return isSymmetric;
    }

    // =========================================================================
    // === Getters =============================================================
    // =========================================================================
    public BiLinkedHashMap<Station, Station, Double> getMatrix() {
        return matrix;
    }

}
