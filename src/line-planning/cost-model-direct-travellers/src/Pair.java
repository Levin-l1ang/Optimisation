/**
 * Utility class for a pair of two arbitrary classes.
 */
public class Pair<K, V> {
    private K firstElement;
    private V secondElement;

    /**
     * Create a new pair of the given firstElement and secondElement
     *
     * @param firstElement  the firstElement
     * @param secondElement the secondElement
     */
    public Pair(K firstElement, V secondElement) {
        this.firstElement = firstElement;
        this.secondElement = secondElement;
    }

    /**
     * Get the firstElement of the pair, i.e., the first entry.
     *
     * @return the first element
     */
    public K getFirstElement() {
        return firstElement;
    }

    /**
     * Get the secondElement of the pair, i.e., the first entry.
     *
     * @return the second element
     */
    public V getSecondElement() {
        return secondElement;
    }

    @Override
    public String toString() {
        return "(" + firstElement + "," + secondElement + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Pair<?, ?> pair = (Pair<?, ?>) o;

        if (getFirstElement() != null ? !getFirstElement().equals(pair.getFirstElement()) : pair.getFirstElement() != null)
            return false;
        return getSecondElement() != null ? getSecondElement().equals(pair.getSecondElement()) : pair.getSecondElement() == null;
    }

    @Override
    public int hashCode() {
        int result = getFirstElement() != null ? getFirstElement().hashCode() : 0;
        result = 31 * result + (getSecondElement() != null ? getSecondElement().hashCode() : 0);
        return result;
    }
}
