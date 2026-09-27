import java.io.*;
import java.util.*;

public class ODCSV {

    public static void fromFile(PTN ptn, OD od, File od_file) throws IOException {
        TreeMap<Integer, Stop> stops_by_index = new TreeMap<>();
        for (Stop stop : ptn.getStops()) {
            stops_by_index.put(stop.getIndex(), stop);
        }

        String line;
        String[] values;
        Stop origin;
        Stop destination;
        double passengers;

        Scanner scan = new Scanner(new BufferedReader(new FileReader(od_file)));
        while (scan.hasNext()) {
            line = scan.nextLine().trim();
            if (line.indexOf("#") == 0)
                continue;
            if (line.contains("#")) {
                line = line.substring(0, line.indexOf("#") - 1);
            }
            if (line.contains(";")) {
                values = line.split(";");
                if (values.length != 3) {
                    scan.close();
                    throw new IOException(
                        "Wrong number of entries in line!");
                }
                origin = stops_by_index.get(Integer.parseInt(values[0].trim()));
                destination = stops_by_index.get(Integer.parseInt(values[1].trim()));
                passengers = Double.parseDouble(values[2].trim());
                od.setPassengersAt(origin, destination, passengers);
            }
        }
        scan.close();
    }


}
