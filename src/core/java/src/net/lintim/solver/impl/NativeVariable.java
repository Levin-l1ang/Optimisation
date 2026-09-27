package net.lintim.solver.impl;

import net.lintim.solver.Variable;

import java.util.Objects;

public class NativeVariable implements Variable {

    private final String name;
    private final VariableType type;
    private final double lowerBound;
    private final double upperBound;
    private double startValue;

    NativeVariable(String name, VariableType type, double lowerBound, double upperBound) {
        this.name = name;
        this.type = type;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public double getStartValue() {
        return startValue;
    }

    public void setStartValue(double startValue) {
        this.startValue = startValue;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public VariableType getType() {
        return type;
    }

    @Override
    public double getLowerBound() {
        return lowerBound;
    }

    @Override
    public double getUpperBound() {
        return upperBound;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NativeVariable variable = (NativeVariable) o;
        return Double.compare(variable.lowerBound, lowerBound) == 0 && Double.compare(variable.upperBound, upperBound) == 0 && Double.compare(variable.startValue, startValue) == 0 && Objects.equals(name, variable.name) && type == variable.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, lowerBound, upperBound);
    }

    @Override
    public String toString() {
        return "NativeVariable{" +
            "name='" + name + '\'' +
            ", type=" + type +
            ", lowerBound=" + lowerBound +
            ", upperBound=" + upperBound +
            '}';
    }
}
