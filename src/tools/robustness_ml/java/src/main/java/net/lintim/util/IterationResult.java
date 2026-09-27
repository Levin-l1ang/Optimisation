package net.lintim.util;

import java.util.Objects;

public class IterationResult {
    private final double mlValue;
    private final double ttValue;

    public IterationResult(double mlValue, double ttValue) {
        this.mlValue = mlValue;
        this.ttValue = ttValue;
    }

    public IterationResult() {
        mlValue = Double.POSITIVE_INFINITY;
        ttValue = -1;
    }

    public double getMlValue() {
        return mlValue;
    }

    public double getTtValue() {
        return ttValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IterationResult that = (IterationResult) o;
        return Double.compare(that.mlValue, mlValue) == 0 && Double.compare(that.ttValue, ttValue) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mlValue, ttValue);
    }

    @Override
    public String toString() {
        return "IterationResult{" +
            "mlValue=" + mlValue +
            ", ttValue=" + ttValue +
            '}';
    }
}
