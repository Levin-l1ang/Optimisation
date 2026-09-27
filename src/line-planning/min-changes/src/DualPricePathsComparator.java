import java.util.Comparator;

public class DualPricePathsComparator implements Comparator<ChangeGoPath> {

    // With this comparator the dual prices of paths can be compared
    @Override
    public int compare(ChangeGoPath p1, ChangeGoPath p2) {
        if (p1.getFirst().getStopIndex() == p2.getFirst().getStopIndex() && p1.getLast().getStopIndex() == p2.getLast().getStopIndex() && p1.getDualCosts().equals(p2.getDualCosts()))
            return 0;
        else if (p1.getDualCosts().compareTo(p2.getDualCosts()) == 0)
            return 1;
        else {
            return -1 * p1.getDualCosts().compareTo(p2.getDualCosts());
        }
    }

}
