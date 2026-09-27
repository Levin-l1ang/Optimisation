package net.lintim.util;

import java.util.Objects;

public class IterationValue {
    private final int iterationIndex;
    private final IterationResult result;

    public IterationValue(int iterationIndex, double mlValue, double ttValue) {
        this.iterationIndex = iterationIndex;
        this.result = new IterationResult(mlValue, ttValue);
    }

    public int getIterationIndex() {
        return iterationIndex;
    }

    public double getMlValue() {
        return result.getMlValue();
    }

    public double getTtValue() {
        return result.getTtValue();
    }

    public IterationResult getResult() {
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IterationValue that = (IterationValue) o;
        return iterationIndex == that.iterationIndex && Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(iterationIndex, result);
    }

    @Override
    public String toString() {
        return "IterationValue{" +
            "iterationIndex=" + iterationIndex +
            ", result=" + result +
            '}';
    }
}
