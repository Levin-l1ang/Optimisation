import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Vector;

/**
 *
 */
public class Dijkstra {

   /* public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("expected: input file name");
            System.exit(1);
        }
        double cost[][] = parseFromFile(args[0]);
        Dijkstra d = new Dijkstra(cost.length, cost);
        for (int i = 0; i < cost.length; i++)
            d.solve(i, System.out);
    }*/

    public static String[][][] allShortestPaths(double[][] cost, int[][] names) {
        Vector<String[][]> allPaths = new Vector<String[][]>();
        Dijkstra d = new Dijkstra(cost.length, cost, names);
        for (int i = 0; i < cost.length; i++)
            allPaths.add(d.solve(i, null));
        return allPaths.toArray(new String[0][][]);

    }

    public static double[][] parseFromFile(String filename) throws IOException {
        return parseFromFile(filename, true);
    }

    public static double[][] parseFromFile(String filename, boolean undirected) throws IOException {
        int n_vertices = 0;
        double[][] cost = new double[0][0];
        BufferedReader in = new BufferedReader(new FileReader(filename));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.indexOf("#") > -1)
                line = line.substring(0, line.indexOf("#"));
            if (line.indexOf(";") == -1) continue;
            line = line.substring(line.indexOf(";") + 1);
            if (line.indexOf(";") == -1) continue;
            int v = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            if (v > n_vertices) n_vertices = v;
            line = line.substring(line.indexOf(";") + 1);
            v = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            if (v > n_vertices) n_vertices = v;
        }
        in.close();
        in = new BufferedReader(new FileReader(filename));
        cost = new double[n_vertices][n_vertices];
        for (int i = 0; i < n_vertices; i++) {
            //double[] cost[i] = new double[n_vertices];
            for (int j = 0; j < n_vertices; j++)
                cost[i][j] = Double.POSITIVE_INFINITY;
        }
        while ((line = in.readLine()) != null) {
            if (line.indexOf("#") > -1)
                line = line.substring(0, line.indexOf("#"));
            if (line.indexOf(";") == -1) continue;
            line = line.substring(line.indexOf(";") + 1);
            if (line.indexOf(";") == -1) continue;
            int v1 = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            line = line.substring(line.indexOf(";") + 1);
            if (line.indexOf(";") == -1) continue;
            int v2 = Integer.parseInt(line.substring(0, line.indexOf(";")).trim());
            line = line.substring(line.indexOf(";") + 1);
            if (line.indexOf(";") == -1) continue;
            cost[v1 - 1][v2 - 1] = Double.parseDouble(line.substring(0, line.indexOf(";")).trim());
            if (undirected) cost[v2 - 1][v1 - 1] = cost[v1 - 1][v2 - 1];
        }
        in.close();
        return cost;
    }

    private double[][] cost;
    private int n_vertices;
    private int[][] names;

    public Dijkstra(int n_vertices, double[][] cost, int[][] names) {
        this.cost = cost;
        this.n_vertices = n_vertices;
        if ((cost.length < n_vertices) || (cost[0].length < n_vertices))
            throw new ArrayIndexOutOfBoundsException();
        this.names = names;
    }

    public String[][] solve(int initial, PrintStream out) {
        String[][] paths = new String[n_vertices][];
        double min[] = new double[n_vertices];
        Vector<String>[] path = new Vector[n_vertices];
        boolean visited[] = new boolean[n_vertices];
        for (int i = 0; i < n_vertices; i++) {
            min[i] = Double.POSITIVE_INFINITY;
            path[i] = new Vector<String>();
        }
        min[initial] = 0;
        //path[initial].add("" + (initial + 1));
        path[initial].add("-");
        int current = initial;
        while (true) {
            visited[current] = true;
            for (int i = 0; i < n_vertices; i++)
                if ((cost[current][i] >= 0) && !visited[i]) {
                    if (cost[current][i] + min[current] < min[i]) {
                        min[i] = cost[current][i] + min[current];
                        path[i].clear();
                    }
                    if (cost[current][i] + min[current] == min[i])
                        for (String s : path[current])
                            //path[i].add(s + "-" + (i + 1));
                            path[i].add(s + names[current][i] + "-");
                }
            double least = Double.POSITIVE_INFINITY;
            int k = -1;
            for (int i = 0; i < n_vertices; i++)
                if (!visited[i] && (min[i] < least)) {
                    least = min[i];
                    k = i;
                }
            if (k == -1) break;
            current = k;
        }
        for (int i = 0; i < n_vertices; i++) {
            if (out != null)
                for (String s : path[i])
                    out.print((initial + 1) + ";" + (i + 1) + ";" + s + "\n");
            paths[i] = path[i].toArray(new String[0]);
        }
        return paths;
    }


}
