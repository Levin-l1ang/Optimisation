import java.util.*;
import java.io.*;

/**
 * class representing a line pool
 */
public class Pool {

    protected ArrayList<Line> lines;
    protected PTN ptn;
    protected int maxLength;

//--------------Constructor--------------------------------------------------

    /**
     * initialization of a line pool
     * reads the lines from the poolFile and
     * the lengths and costs from the poolCostFile
     * ptn is the corresponding public transportation network
     */
    public Pool(PTN ptn, String poolFile, String poolCostFile) {
        this.ptn = ptn;
        try {
            //reading lines from the poolFile
            lines = new ArrayList<>(0);
            ArrayList<Edge> edges = new ArrayList<>(0);
            BufferedReader in = new BufferedReader(new FileReader(poolFile));
            Scanner scan = new Scanner(in);
            String line;
            String[] values;
            int currentLineId = 0;
            while (scan.hasNext()) {
                line = scan.nextLine().trim();
                if (line.indexOf("#") == 0)
                    continue;
                if (line.contains("#")) {
                    line = line.substring(0, line.indexOf("#") - 1);
                }
                if (line.contains(";")) {
                    values = line.split(";");
                    currentLineId = Integer.parseInt(values[0].trim());
                    edges.add(ptn.getEdges().get(Integer.parseInt(values[2].trim()) - 1));
                    break;
                }

            }
            this.maxLength = 1;
            while (scan.hasNext()) {
                line = scan.nextLine().trim();
                if (line.indexOf("#") == 0)
                    continue;
                if (line.contains("#")) {
                    line = line.substring(0, line.indexOf("#") - 1);
                }
                if (line.contains(";")) {
                    values = line.split(";");
                    if (Integer.parseInt(values[0].trim()) == currentLineId) {
                        edges.add(ptn.getEdges().get(Integer.parseInt(values[2].trim()) - 1));
                    } else {
                        if (edges.size() > this.maxLength) {
                            this.maxLength = edges.size();
                        }
                        this.lines.add(new Line(currentLineId, edges));
                        currentLineId = Integer.parseInt(values[0].trim());
                        edges = new ArrayList<>(0);
                        edges.add(ptn.getEdges().get(Integer.parseInt(values[2].trim()) - 1));
                    }
                }
            }
            if (edges.size() > this.maxLength) {
                this.maxLength = edges.size();
            }
            lines.add(new Line(currentLineId, edges));
            in.close();

            //reading cost and length from poolCostFile
            in = new BufferedReader(new FileReader(poolCostFile));
            scan = new Scanner(in);
            while (scan.hasNext()) {
                line = scan.nextLine().trim();
                if (line.indexOf("#") == 0)
                    continue;
                if (line.contains("#")) {
                    line = line.substring(0, line.indexOf("#") - 1);
                }
                if (line.contains(";")) {
                    values = line.split(";");
                    lines.get(Integer.parseInt(values[0].trim()) - 1).
                        setLength(Double.parseDouble(
                            values[1].replaceAll(",", "").trim()));
                    lines.get(Integer.parseInt(values[0].trim()) - 1).
                        setCost(Double.parseDouble(
                            values[2].replaceAll(",", "").trim()));
                }
            }
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NoPathException e) {
            System.out.println(e.getMessage());
        }
    }

    public Pool(PTN ptn, ArrayList<Line> lines) {
        this.ptn = ptn;
        this.lines = lines;
    }

    //----------------------Getter------------------------------------------------


    public ArrayList<Line> getLines() {
        return this.lines;
    }

    public PTN getPTN() {
        return this.ptn;
    }

    public int getMaxLength() {
        return this.maxLength;
    }
    //-----------------------ToString-----------------------------------------------

    public String toString() {
        String s = "";
        s = s + "Linepool: \n";
        for (int i = 0; i < lines.size(); i++) {
            s = s + lines.get(i).toString() + "\n";
        }
        return s;
    }
}
