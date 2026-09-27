package net.lintim.csv;

import net.lintim.exception.DataInconsistentException;
import net.lintim.model.*;
import net.lintim.util.BiLinkedHashMap;
import net.lintim.util.MathHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Handles origin destination activity path CSV files, i.e. those that contain
 * paths between stations in the event activity network and allows reading and
 * writing.
 *
 * Syntax:
 * start station; end station; activity; passengers
 */

public class OriginDestinationPathActivityCSV {

    /**
     * Reads origin destination activity paths from file.
     *
     * @param ean The event activity network to write to.
     * @param od The origin destination matrix to doublecheck with.
     * @param odPathsFile The origin destination paths file to read from.
     * @throws IOException
     * @throws DataInconsistentException
     */
    public static void fromFile(EventActivityNetwork ean,
            OriginDestinationMatrix od, File odPathsFile) throws
            IOException, DataInconsistentException {

        PublicTransportationNetwork ptn = ean.getPublicTransportationNetwork();

        CsvReader reader = new CsvReader(odPathsFile);

        List<String> line;

        while ((line = reader.read()) != null) {

            try{

                int size = line.size();
                if(size != 4){
                    throw new DataInconsistentException("4 columns needed, " +
                            size + " given");
                }

                try{

                    Iterator<String> itr = line.iterator();
                    Integer firstStationIndex = Integer.parseInt(itr.next());
                    Integer secondStationIndex = Integer.parseInt(itr.next());
                    Integer activityIndex = Integer.parseInt(itr.next());
                    Double passengers = Double.parseDouble(itr.next());

                    Station s1 = ptn.getStationByIndex(firstStationIndex);
                    if(s1 == null){
                        throw new DataInconsistentException("station index " +
                                firstStationIndex + " does not exist");
                    }
                    Station s2 = ptn.getStationByIndex(secondStationIndex);
                    if(s2 == null){
                        throw new DataInconsistentException("station index " +
                                secondStationIndex + " does not exist");
                    }
                    Activity a = ean.getActivityByIndex(activityIndex);
                    if(a == null){
                        throw new DataInconsistentException("activity index " +
                                activityIndex + " does not exist");
                    }
                    if(!a.isPassengerUsable()){
                        throw new DataInconsistentException("activity index " +
                                activityIndex + " is not passenger usable");
                    }

                    if(Math.abs(passengers-od.get(s1, s2)) > MathHelper.epsilon){
                        throw new DataInconsistentException("origin " +
                                "destination data inconsistent");
                    }

                    BiLinkedHashMap<Station, Station, LinkedHashSet<Activity>>
                    pathMap = ean.getOriginDestinationPathMap();

                    LinkedHashSet<Activity> currentPath = pathMap.get(s1, s2);
                    if(currentPath == null){
                        currentPath = new LinkedHashSet<Activity>();
                    }

                    // TODO check path connectivity
                    currentPath.add(a);
                    pathMap.put(s1, s2, currentPath);

                } catch(NumberFormatException e){
                    throw new DataInconsistentException("some field should " +
                    "contain a number, but it does not");
                }


            } catch(DataInconsistentException e){
                throw new DataInconsistentException("line " +
                        reader.getLineNumber() + " in file " +
                        odPathsFile.getAbsolutePath()	+ " invalid: " +
                        e.getMessage());
            }
        }

        reader.close();

        ean.computePassengersFromActivityPaths(od);

    }

    /**
     * Writes origin destination activity paths to file.
     *
     * @param ean The event activity network to read from.
     * @param od The origin destination matrix to read from.
     * @param odPathsFile The origin destination paths file to write to.
     * @throws IOException
     * @throws DataInconsistentException
     */
    // TODO integrate into CSV write framework
    public static void toFile(EventActivityNetwork ean,
            OriginDestinationMatrix od, File odPathsFile) throws IOException,
            DataInconsistentException {

        odPathsFile.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(odPathsFile);

        Boolean odGiven = (od != null);

        fw.write("# from_station; to_station; activity_index"
                + (odGiven ? "; passengers" : "") + "\n");

        for (Map.Entry<Station, LinkedHashMap<Station,
                LinkedHashSet<Activity>>> e1 : ean
                .getOriginDestinationPathMap().entrySet()) {

            Station s1 = e1.getKey();

            for (Map.Entry<Station, LinkedHashSet<Activity>> e2 : e1.getValue()
                    .entrySet()) {

                Station s2 = e2.getKey();

                for (Activity activity : e2.getValue()) {

                    fw.write(s1.getIndex() + "; " + s2.getIndex() + "; "
                            + activity.getIndex() +  (odGiven ? "; "
                            + od.get(s1, s2) : "") + "\n");

                }
            }

        }

        fw.close();

    }

}
