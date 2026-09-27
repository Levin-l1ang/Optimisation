

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

/** Test class for shortest paths.
 *
 */
public class ShortestPathsTest {

    public static void main(String[] args){
        try {

            if(args.length != 4){
                System.err.println("Wrong number of arguments. Requiring: "
                        + "NODES_FILE EDGES_FILE DISTANCES_FILE PATHS_FILE");
                System.exit(1);
            }

            File nodesFile = new File(args[0]);
            File edgesFile = new File(args[1]);

            ArrayList<Integer> nodes = new ArrayList<>();
            ArrayList<Integer> edges = new ArrayList<>();

            ShortestPathsGraph<Integer, Integer> sp =
                new ShortestPathsGraph<>();

            BufferedReader rd = new BufferedReader(new FileReader(nodesFile));

            String line;
            int lineCounter = 1;

            while ((line = rd.readLine()) != null) {

                line = line.trim();

                if(line.startsWith("#") || line.isEmpty()){
                    continue;
                }

                String[] fields = line.split(";");

                if(fields.length != 1){
                    throw new GraphMalformedException("malformed input in file " +
                            nodesFile.getAbsolutePath() + " on line " + lineCounter);
                }

                Integer nodeIndex = Integer.parseInt(fields[0].trim());
                nodes.add(nodeIndex);
                sp.addVertex(nodeIndex);

                lineCounter++;

            }

            rd.close();

            rd = new BufferedReader(new FileReader(edgesFile));
            lineCounter = 1;

            while ((line = rd.readLine()) != null) {

                line = line.trim();

                if(line.startsWith("#") || line.isEmpty()){
                    continue;
                }

                String[] fields = line.split(";");

                if(fields.length != 4){
                    throw new GraphMalformedException("malformed input in file " +
                            nodesFile.getAbsolutePath() + " on line " + lineCounter);
                }

                Integer edgeIndex = Integer.parseInt(fields[0].trim());
                edges.add(edgeIndex);
                sp.addEdge(edgeIndex,
                        Integer.parseInt(fields[1].trim()),
                        Integer.parseInt(fields[2].trim()),
                        Double.parseDouble(fields[3].trim()));

                lineCounter++;

            }

            rd.close();

            File distanceFile = new File(args[2]);
            File pathsFile = new File(args[3]);

            FileWriter fw1 = new FileWriter(distanceFile);
            FileWriter fw2 = new FileWriter(pathsFile);

            for(Integer nodeIndex1 : nodes){
                sp.compute(nodeIndex1);
                for(Integer nodeIndex2 : nodes){
                    fw1.write(nodeIndex1 + "; " + nodeIndex2 + "; "
                            + sp.getDistance(nodeIndex2) + "\n");
                    for(Integer edgeIndex : sp.trackPath(nodeIndex2)){
                        fw2.write(nodeIndex1 + "; " + nodeIndex2 + "; "
                                + edgeIndex + "\n");
                    }
                }
            }
            fw1.close();
            fw2.close();

        } catch (IOException | GraphMalformedException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}
