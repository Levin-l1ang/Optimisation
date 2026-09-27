package net.lintim.solver.impl;

import net.lintim.solver.Constraint;
import net.lintim.solver.Variable;

import java.util.Objects;

public class NativeConstraint<T extends Variable> implements Constraint {

    private final NativeLinearExpression<T> lhs;
    private double rhs;
    private final ConstraintSense sense;
    private final String name;

    public NativeConstraint(NativeLinearExpression<T> lhs, double rhs, ConstraintSense sense, String name) {
        this.lhs = lhs;
        this.rhs = rhs;
        this.sense = sense;
        this.name = name;
    }

    public NativeLinearExpression<T> getLhs() {
        return lhs;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ConstraintSense getSense() {
        return sense;
    }

    @Override
    public double getRhs() {
        return rhs;
    }

    @Override
    public void setRhs(double value) {
        this.rhs = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NativeConstraint<?> that = (NativeConstraint<?>) o;
        return Double.compare(that.rhs, rhs) == 0 && Objects.equals(lhs, that.lhs) && sense == that.sense && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, sense, name);
    }

    @Override
    public String toString() {
        return "GLPKConstraint{" +
            "lhs=" + lhs +
            ", rhs=" + rhs +
            ", sense=" + sense +
            ", name='" + name + '\'' +
            '}';
    }
}
