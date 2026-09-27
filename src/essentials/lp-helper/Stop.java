
public class Stop implements Comparable<Stop> {

    private int index;
    private String short_name;
    private String long_name;
    private final double x_coordinate;
    private final double y_coordinate;
    private boolean is_leaf;
    private boolean is_terminal;
    private static double coordinate_factor_drawing;
    private static double coordinate_factor_conversion;

    //Constructor-----------------------------------------------------------------
    public Stop(Integer index, String shortName, String longName,
                Double x_coordinate, Double y_coordinate) {

        this.index = index;
        this.short_name = shortName;
        this.long_name = longName;
        this.x_coordinate = x_coordinate;
        this.y_coordinate = y_coordinate;
        is_leaf = false;
        is_terminal = false;
    }


    //Setter-----------------------------------------------------------------------
    public void setIndex(Integer index) {
        this.index = index;
    }

    public void setShort_name(String shortName) {
        this.short_name = shortName;
    }

    public void setLong_name(String longName) {
        this.long_name = longName;
    }

    public void setIsLeaf(boolean is_leaf) {
        this.is_leaf = is_leaf;
    }

    public void setIsTerminal(boolean is_terminal) {
        this.is_terminal = is_terminal;
    }

    public static void setCoordinateFactorDrawing(double f) {
        coordinate_factor_drawing = f;
    }

    public static void setCoordinateFactorConversion(double f) {
        coordinate_factor_conversion = f;
    }

    //Getter--------------------------------------------------------------------------
    public int getIndex() {
        return index;
    }

    public String getShort_name() {
        return short_name;
    }

    public String getLong_name() {
        return long_name;
    }

    public double getX_coordinate() {
        return x_coordinate;
    }

    public double getY_coordinate() {
        return y_coordinate;
    }

    public boolean isLeaf() {
        return is_leaf;
    }

    public boolean isTerminal() {
        return is_terminal;
    }

//CompareTo-------------------------------------------------------------------------

    @Override
    public int compareTo(Stop o) {
        return index - o.index;
    }

    //Distance-------------------------------------------------------------------------
    public static double distance(Stop a, Stop b) {
        double diff_x = (a.getX_coordinate() - b.getX_coordinate());
        double diff_y = (a.getY_coordinate() - b.getY_coordinate());
        return Math.sqrt(diff_x * diff_x + diff_y * diff_y) * coordinate_factor_conversion;
    }

//toString--------------------------------------------------------------------------

    @Override
    public String toString() {
        return "" + index;
    }

    public String toDOT() {
        return "\t" + index + "[style=filled, pos=\""
            + x_coordinate * coordinate_factor_drawing + "," + y_coordinate * coordinate_factor_drawing
            + "\"];\n";
    }

    //equals---------------------------------------------------------------------------
    public boolean equals(Object o) {
        if (o == null)
            return false;
        if (!(o instanceof Stop))
            return false;
        Stop other = (Stop) o;
        return other.getIndex() == index;
    }


}
