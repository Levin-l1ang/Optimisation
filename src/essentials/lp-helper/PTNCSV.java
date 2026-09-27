import java.io.*;
import java.util.*;

public class PTNCSV {
    // Read------------------------------------------------------------------------

    public static void fromFile(PTN ptn, File stop_file, File edge_file,
                                File load_file) throws IOException {
        readStops(ptn, stop_file);
        readEdges(ptn, edge_file);
        readLoad(ptn, load_file);
    }

    public static void fromFile(PTN ptn, File stop_file, File edge_file,
                                File load_file, String ean_model_weight_drive,
                                double conversionFactorLength) throws IOException {
        readStops(ptn, stop_file);
        readEdges(ptn, edge_file, ean_model_weight_drive, conversionFactorLength);
        readLoad(ptn, load_file);
    }

    public static void fromFile(PTN ptn, File stop_file, File edge_file)
        throws IOException {
        readStops(ptn, stop_file);
        readEdges(ptn, edge_file);
    }

    public static void fromFile(PTN ptn, File stop_file, File edge_file, String ean_model_weight_drive,
                                double conversionFactorLength)
        throws IOException {
        readStops(ptn, stop_file);
        readEdges(ptn, edge_file, ean_model_weight_drive, conversionFactorLength);
    }

    // Private methods
    // read--------------------------------------------------------

    private static void readStops(PTN ptn, File stop_file) throws IOException {

        Stop current_stop;
        String line;
        String[] values;

        Scanner scan = new Scanner(
            new BufferedReader(new FileReader(stop_file)));
        while (scan.hasNext()) {
            line = scan.nextLine().trim();
            if (line.indexOf("#") == 0)
                continue;
            if (line.contains("#")) {
                line = line.substring(0, line.indexOf("#") - 1);
            }
            if (line.contains(";")) {
                values = line.split(";");
                if (values.length != 5) {
                    scan.close();
                    throw new IOException("Wrong number of entries in line!");
                }
                current_stop = new Stop(Integer.parseInt(values[0].trim()), // index
                    values[1].trim(), // short-name
                    values[2].trim(), // long-name
                    Double.parseDouble(values[3].trim()), // x-coordinate
                    Double.parseDouble(values[4].trim())); // y-coordinate
                ptn.addStop(current_stop);
            }
        }
        scan.close();

    }

    private static void readEdges(PTN ptn, File edge_file) throws IOException {
        readEdges(ptn, edge_file, "", 1);
    }


    private static void readEdges(PTN ptn, File edge_file, String ean_model_weight_drive,
                                  double conversionFactorLength) throws IOException {
        Edge current_edge;
        String line;
        String[] values;
        int index;
        int left_stop_id;
        int right_stop_id;
        double length;
        double minimal_drive_time;
        double maximal_drive_time;
        double duration;

        Scanner scan = new Scanner(
            new BufferedReader(new FileReader(edge_file)));
        while (scan.hasNext()) {
            line = scan.nextLine().trim();
            if (line.indexOf("#") == 0)
                continue;
            if (line.contains("#")) {
                line = line.substring(0, line.indexOf("#") - 1);
            }
            if (line.contains(";")) {
                values = line.split(";");
                if (values.length != 4 && values.length != 6) {
                    scan.close();
                    throw new IOException("Wrong number of entries in line!");
                }
                index = Integer.parseInt(values[0].trim());
                left_stop_id = Integer.parseInt(values[1].trim());
                right_stop_id = Integer.parseInt(values[2].trim());
                length = Double.parseDouble(values[3].trim()) * conversionFactorLength;
                if (ean_model_weight_drive.equals("")) {
                    duration = 0;
                } else {
                    if (values.length != 6) {
                        scan.close();
                        throw new IOException("Wrong number of entries in line!");
                    }
                    minimal_drive_time = Double.parseDouble(values[4].trim());
                    maximal_drive_time = Double.parseDouble(values[5].trim());
                    switch (ean_model_weight_drive) {
                        case "MINIMAL_DRIVING_TIME":
                            duration = minimal_drive_time;
                            break;
                        case "MAXIMAL_DRIVING_TIME":
                            duration = maximal_drive_time;
                            break;
                        case "AVERAGE_DRIVING_TIME":
                            duration = (minimal_drive_time + maximal_drive_time) / 2;
                            break;
                        case "EDGE_LENGTH":
                            duration = length;
                            break;
                        default:
                            throw new IOException("Unsupported ean_drive_time_model");
                    }
                }
                current_edge = new Edge(ptn.isDirected(), index, ptn.getStop(left_stop_id),
                    ptn.getStop(right_stop_id), length, duration);
                ptn.addEdge(current_edge);
            }
        }
        scan.close();


    }

    private static void readLoad(PTN ptn, File load_file) throws IOException {

        HashMap<Integer, Edge> edge_by_index = new HashMap<>();
        for (Edge edge : ptn.getEdges()) {
            edge_by_index.put(edge.getIndex(), edge);
        }

        int index;
        int min_freq;
        int max_freq;
        Edge current_edge;
        String line;
        String[] values;

        Scanner scan = new Scanner(
            new BufferedReader(new FileReader(load_file)));
        while (scan.hasNext()) {
            line = scan.nextLine().trim();
            if (line.indexOf("#") == 0)
                continue;
            if (line.contains("#")) {
                line = line.substring(0, line.indexOf("#") - 1);
            }
            if (line.contains(";")) {
                values = line.split(";");
                if (values.length != 4) {
                    scan.close();
                    throw new IOException("Wrong number of entries in line!");
                }
                index = Integer.parseInt(values[0].trim());
                min_freq = Integer.parseInt(values[2].trim());
                max_freq = Integer.parseInt(values[3].trim());
                current_edge = edge_by_index.get(index);
                current_edge.setMinLoad(min_freq);
                current_edge.setMaxLoad(max_freq);
            }
        }
        scan.close();
    }

}
