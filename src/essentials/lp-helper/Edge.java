public class Edge implements Comparable<Edge> {
    private static String header = "";

    private int index;
    private final Stop left_stop;
    private final Stop right_stop;
    private final double length;
    private double duration;
    private int min_load;
    private int max_load;
    private final boolean directed;
    private int usage;
    private boolean preferred;

//Constructor----------------------------------------------------------------

    public Edge(boolean directed, int index, Stop left_stop, Stop right_stop,
                double length) {
        this.directed = directed;
        this.index = index;
        this.left_stop = left_stop;
        this.right_stop = right_stop;
        this.length = length;
        this.usage = 0;
        this.preferred = false;
        this.duration = 0;
    }

    public Edge(boolean directed, int index, Stop left_stop, Stop right_stop,
                double length, double duration) {
        this(directed, index, left_stop, right_stop, length);
        this.duration = duration;
    }

    // Setter--------------------------------------------------------------------

    public void setIndex(int index) {
        this.index = index;
    }

    public void setMinLoad(int load) {
        min_load = load;
    }

    public void setMaxLoad(int load) {
        max_load = load;
    }

    public void setPreferred(boolean preferred) {
        this.preferred = preferred;
    }

    // Getter--------------------------------------------------------------------
    public int getIndex() {
        return index;
    }

    public Stop getLeftStop() {
        return left_stop;
    }

    public Stop getRightStop() {
        return right_stop;
    }

    //if the weight of a line in considered when constructing a mst,
    //preferred lines are considered with weight 0, all others with their length
    public double getWeight() {
        if (preferred)
            return -1;
        return length;
    }

    //when the costs of a line are considered, the length is needed
    public double getLength() {
        return length;
    }

    public boolean isDirected() {
        return directed;
    }


    public boolean isPreferred() {
        return preferred;
    }

    public int getCoverings(double min_cov) {
        return (int) Math.ceil((double) min_load / (double) min_cov);
    }

    public boolean getOveruse(double max_cov) {
        return usage > max_cov * max_load;
    }

    public int getMaxLoad() {
        return max_load;
    }

    public int getMinLoad() {
        return min_load;
    }

    public double getDuration() {
        return duration;
    }

    public int getUsage() {
        return usage;
    }

    //Usage----------------------------------------------------------------------
    public void increaseUsage() {
        usage++;
    }

    // CompareTo-----------------------------------------------------------------

    @Override
    public int compareTo(Edge o) {
        return (int) Math.signum(this.getWeight() - o.getWeight());
    }

    // equals-------------------------------------------------------------------
    public boolean equals(Object o) {
        if (o == null)
            return false;
        if (!(o instanceof Edge))
            return false;
        Edge other = (Edge) o;
        return other.getLeftStop().equals(left_stop)
            && other.getRightStop().equals(right_stop);
    }

    // toString------------------------------------------------------------------

    @Override
    public String toString() {
        return "(" + index + "," + left_stop + "," + right_stop + ")";
    }

    public String toDOT() {
        if (Line.isDirected()) {
            return "\t" + left_stop.getIndex() + "->" + right_stop.getIndex() + " ";
        } else {
            return "\t" + left_stop.getIndex() + "--" + right_stop.getIndex() + " ";
        }
    }

    // CSV-----------------------------------------------------------------------
    public static String printHeader() {
        return "# " + header;
    }

    public String toCSV() {
        return index + "; " + left_stop.getIndex() + "; " + right_stop.getIndex()
            + "; " + length;
    }

    // static
    // methods-------------------------------------------------------------------
    public static void setHeader(String head) {
        header = head;
    }
}
