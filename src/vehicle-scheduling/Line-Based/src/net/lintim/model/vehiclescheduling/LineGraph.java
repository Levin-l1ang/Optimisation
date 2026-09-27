package net.lintim.model.vehiclescheduling;

/**
 * Class describing Line Graphs
 * containing all relevant information of the line graph
 */
public class LineGraph {
    //Variables coming from the line concept
    public int[] start, end, frequency;
    public String[] lineIDs;
    public double[] linetime;
    public int numberOfLines, numberOfEdges;
    public double[][] dist, time;

    /**
     * General Constructor
     *
     * @param nstart         starting points of the lines
     * @param nend           ending points of the lines
     * @param nlinetime      time needed to drive the lines
     * @param nfrequency     frequency of the lines
     * @param nnumberOfLines number of lines
     * @param ndist          transfer times between all lines
     * @param ntime          transfer times including time to drive preceding line
     * @param nlineIDs       IDs and directions of the lines
     */
    public LineGraph(int[] nstart, int[] nend, double[] nlinetime,
                     int[] nfrequency, int nnumberOfLines, double[][] ndist,
                     double[][] ntime, String[] nlineIDs) {

        numberOfLines = nnumberOfLines;

        start = new int[numberOfLines];
        end = new int[numberOfLines];
        frequency = new int[numberOfLines];
        lineIDs = new String[numberOfLines];
        linetime = new double[numberOfLines];
        dist = new double[numberOfLines][numberOfLines];
        time = new double[numberOfLines][numberOfLines];

        for (int i = 0; i < numberOfLines; i++) {
            start[i] = nstart[i];
            end[i] = nend[i];
            frequency[i] = nfrequency[i];
            linetime[i] = nlinetime[i];
            lineIDs[i] = nlineIDs[i];
            for (int j = 0; j < numberOfLines; j++) {
                dist[i][j] = ndist[i][j];
                time[i][j] = ntime[i][j];
            }
        }
        numberOfEdges = numberOfLines * numberOfLines;
    }


}
