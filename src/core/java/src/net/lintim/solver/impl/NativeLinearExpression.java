package net.lintim.solver.impl;

import net.lintim.exception.LinTimException;
import net.lintim.solver.LinearExpression;
import net.lintim.solver.Variable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class NativeLinearExpression<T extends Variable> implements LinearExpression {

    private final HashMap<T, Double> values;
    private double constant;

    public NativeLinearExpression() {
        this.values = new HashMap<>();
        this.constant = 0;
    }

    @Override
    public void add(LinearExpression otherExpression) {
        multiAdd(1, otherExpression);
    }

    @Override
    public void add(Variable variable) {
        multiAdd(1, variable);
    }

    @Override
    public void multiAdd(double multiple, LinearExpression otherExpression) {
        try {
            for (Map.Entry<T, Double> entry : ((NativeLinearExpression<T>) otherExpression).values.entrySet()) {
                values.put(entry.getKey(), multiple * entry.getValue() + values.getOrDefault(entry.getKey(), 0.));
            }
        } catch (ClassCastException e) {
            // Since the type information are gone by runtime, there is no nice way to know whether otherExpression
            // has the correct type
            throw new LinTimException("Trying to work with non-NativeLinearExpression in NativeLinearExpressionContext");
        }

    }

    @Override
    public void multiAdd(double multiple, Variable variable) {
        try {
            T var = (T) variable;
            values.put(var, values.getOrDefault(var, 0.) + multiple);
        } catch (ClassCastException e) {
            throw new LinTimException("Calling NativeLinearExpression with the wrong type of variable!");
        }
    }

    @Override
    public void clear() {
        values.clear();
    }

    @Override
    public void addConstant(double value) {
        constant += value;
    }

    public double getConstant() {
        return constant;
    }

    public Map<T, Double> getValues() {
        return values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NativeLinearExpression<?> that = (NativeLinearExpression<?>) o;
        return Double.compare(that.constant, constant) == 0 && Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values, constant);
    }

    @Override
    public String toString() {
        return "NativeLinearExpression{" +
            "values=" + values +
            ", constant=" + constant +
            '}';
    }
}
