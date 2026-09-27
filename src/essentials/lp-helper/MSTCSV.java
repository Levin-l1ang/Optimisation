import java.io.*;
import java.util.*;


public class MSTCSV {
    //Write----------------------------------------------------------------------

    public static void toFile(MinimalSpanningTree mst, File edge_file)
        throws IOException {
        writeEdges(mst, edge_file);
    }

    //Private methods write------------------------------------------------------

    private static void writeEdges(MinimalSpanningTree mst, File edge_file)
        throws IOException {
        FileWriter writer = new FileWriter(edge_file);
        LinkedList<Edge> edges = mst.getEdgesMST();
        Iterator<Edge> it = edges.iterator();
        Edge current_edge;
        writer.write(Edge.printHeader() + "\n");
        while (it.hasNext()) {
            current_edge = it.next();
            writer.append(current_edge.toCSV()).append("\n");
            writer.flush();
        }
        writer.close();
    }
}
