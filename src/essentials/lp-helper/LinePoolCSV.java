import java.io.*;
import java.util.Scanner;
import java.util.TreeMap;

public class LinePoolCSV {
    private static String pool_header;
    private static String pool_cost_header;

    // Write---------------------------------------------------------------------

    public static void toFile(LinePool pool, File pool_file, File cost_file, double conversionFactorLength)
        throws IOException {
        writePool(pool, pool_file);
        writeCost(pool, cost_file, conversionFactorLength);
    }

    public static void toCostFile(LinePool pool, File cost_file, double conversionFactorLength)
        throws IOException {
        writeCost(pool, cost_file, conversionFactorLength);
    }

    //Header----------------------------------------------------------------
    public static void setPoolHeader(String header) {
        pool_header = header;
    }

    public static void setPoolCostHeader(String header) {
        pool_cost_header = header;
    }

    // Private methods
    // write----------------------------------------------------------------

    private static void writePool(LinePool pool, File pool_file)
        throws IOException {
        FileWriter writer = new FileWriter(pool_file);
        writer.write("# " + pool_header + "\n");
        for (Line line : pool.getLines()) {
            writer.append(line.toCSV());
            writer.flush();
        }
        writer.close();
    }


    private static void writeCost(LinePool pool, File cost_file, double conversionFactorLength)
        throws IOException {
        FileWriter writer = new FileWriter(cost_file);
        writer.write("# " + pool_cost_header + "\n");
        for (Line line : pool.getLines()) {
            writer.append(line.toCSVCost(conversionFactorLength));
            writer.flush();
        }
        writer.close();
    }

    // DOT----------------------------------------------------------------------
    public static void toDOTFile(LinePool pool, File dot_file)
        throws IOException {
        FileWriter writer = new FileWriter(dot_file);
        if (Line.isDirected()) {
            writer.write("digraph G \n \t{ \n \tordering=out;\n");
        } else {
            writer.write("graph G \n \t{ \n \tordering=out;\n");
        }
        for (Stop stop : pool.getPTN().getStops()) {
            writer.append(stop.toDOT());
        }
        for (Line line : pool.getLines()) {
            for (Edge edge : line.getEdges()) {
                writer.append(edge.toDOT()).append("[label=").append(String.valueOf(line.getIndex())).append(",color=\"#000000\"];\n");
                writer.flush();
            }
        }
        writer.append("\t}");
        writer.close();
    }

    public static void toDOTFile(LinePool pool, File dot_file, double factor)
        throws IOException {
        FileWriter writer = new FileWriter(dot_file);
        Stop.setCoordinateFactorDrawing(factor);
        if (Line.isDirected()) {
            writer.write("digraph G \n \t{ \n \tordering=out;\n");
        } else {
            writer.write("graph G \n \t{ \n \tordering=out;\n");
        }
        for (Stop stop : pool.getPTN().getStops()) {
            writer.append(stop.toDOT());
        }
        for (Line line : pool.getLines()) {
            for (Edge edge : line.getEdges()) {
                writer.append(edge.toDOT()).append("[label=").append(String.valueOf(line.getIndex())).append(",color=\"#000000\"];\n");
                writer.flush();
            }
        }
        writer.append("\t}");
        writer.close();
    }

    public static void toDOTFile(LinePool pool, File dot_file, double factor, boolean colored)
        throws IOException {
        FileWriter writer = new FileWriter(dot_file);
        Stop.setCoordinateFactorDrawing(factor);
        if (Line.isDirected()) {
            writer.write("digraph G \n \t{ \n \tordering=out;\n");
        } else {
            writer.write("graph G \n \t{ \n \tordering=out;\n");
        }
        for (Stop stop : pool.getPTN().getStops()) {
            writer.append(stop.toDOT());
        }
        String color = "";
        Integer counter = 1;
        for (Line line : pool.getLines()) {
            if (colored) {
					float hValue = (float) counter/pool.getLines().size();
					color = hValue + ", " + 1.0 + ", " +  1.0;
				}
            counter = counter + 1;
            for (Edge edge : line.getEdges()) {
                writer.append(edge.toDOT()).append("[label=").append(String.valueOf(line.getIndex())).append(",color=\"").append(color).append("\"];\n");
                writer.flush();
            }
        }
        writer.append("\t}");
        writer.close();
    }

    // Read---------------------------------------------------------------------
    public static void fromFile(LinePool pool, File pool_file)
        throws IOException {
        fromFile(pool, pool_file, false);
    }


    public static void fromFile(LinePool pool, File pool_file, boolean use_line_concept_file)
        throws IOException {
        TreeMap<Integer, Edge> edges_by_index = new TreeMap<>();
        for (Edge edge : pool.getPTN().getEdges()) {
            edges_by_index.put(edge.getIndex(), edge);
        }
        for (Stop stop : pool.getPTN().getStops()) {
            stop.setIsLeaf(true);
        }

        String input_line;
        String[] values;
        int line_index;
        int edge_index;
        Edge edge;
        boolean line_used;
        Line current_line = new Line();
        // line starting at left vertex of first edge
        Line left_line = new Line();
        // line starting at right vertex of first edge
        Line right_line = new Line();
        boolean single_edge = true;

        Scanner scan = new Scanner(
            new BufferedReader(new FileReader(pool_file)));
        while (scan.hasNext()) {
            input_line = scan.nextLine().trim();
            if (input_line.indexOf("#") == 0)
                continue;
            if (input_line.contains("#")) {
                input_line = input_line.substring(0,
                    input_line.indexOf("#") - 1);
            }
            if (input_line.contains(";")) {
                values = input_line.split(";");
                if (!use_line_concept_file && values.length != 3 || use_line_concept_file && values.length != 4) {
                    scan.close();
                    throw new IOException("Wrong number of entries in line!");
                }
                line_index = Integer.parseInt(values[0].trim());
                edge_index = Integer.parseInt(values[1].trim());
                edge = edges_by_index.get(Integer.parseInt(values[2].trim()));
                if (use_line_concept_file) {
                    line_used = (Integer.parseInt(values[3].trim()) > 0);
                } else {
                    line_used = true;
                }
                //if the line which is considered is not used, we do not need to read it
                if (!line_used) {
                    continue;
                }
                if (left_line.getEdges().size() > 0 && edge_index == 1) {
                    if (single_edge) {
                        pool.addLineInput(left_line);
                    } else {
                        pool.addLineInput(current_line);
                    }
                }

                if (edge_index == 1) {
                    single_edge = true;
                    left_line = new Line();
                    right_line = new Line();
                    left_line.addFirstEdge(edge, edge.getLeftStop());
                    right_line.addFirstEdge(edge, edge.getRightStop());
                    left_line.setLineIndex(line_index);
                    right_line.setLineIndex(line_index);
                } else if (edge_index == 2) {
                    single_edge = false;
                    if (left_line.addEdge(edge)) {
                        current_line = left_line;
                    } else {
                        current_line = right_line;
                        if (!current_line.addEdge(edge)) {
                            scan.close();
                            throw new RuntimeException("Edge "
                                + edge.getIndex()
                                + " cannot be added to line " + line_index
                                + "!");
                        }
                    }
                } else {
                    if (!current_line.addEdge(edge)) {
                        scan.close();
                        throw new RuntimeException("Edge " + edge.getIndex()
                            + " cannot be added to line " + line_index
                            + "!");
                    }
                }
            }
        }
        // Add last line
        if (single_edge) {
            pool.addLineInput(left_line);
        } else {
            pool.addLineInput(current_line);
        }
        scan.close();
    }
}
