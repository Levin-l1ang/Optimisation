package net.lintim.solver;

import net.lintim.exception.LinTimException;

/**
 * Class containing the model for a generic constraint. Can be included in a {@link Model}. Use
 * {@link Model#addConstraint(LinearExpression, ConstraintSense, double, String)} for the creation of a constraint.
 */
public interface Constraint {

    /**
     * Get the name of the constraint.
     * @return the name of the constraint
     */
    String getName();

    /**
     * Get the sense of the constraint. For a list of possible senses, see {@link ConstraintSense}.
     * @return the sense of the constraint
     */
    ConstraintSense getSense();

    /**
     * Get the right hand side of the constraint. Constraints will be reformulated s.t. all variables are on the
     * left, therefore the rhs is a constant
     * @return the right hand side
     */
    double getRhs();

    /**
     * Set the right hand side (rhs) of the constraint. Constraints will be reformulated s.t. all variables are on the
     * left, therefore the rhs is a constant
     * @param value the new rhs
     */
    void setRhs(double value);

    /**
     * Possible senses for a constraint, i.e., whether the expression should be less or equal, equal, or greater or
     * equal than the right hand side.
     */
    enum ConstraintSense {
        LESS_EQUAL, EQUAL, GREATER_EQUAL;

        public static ConstraintSense flipSense(ConstraintSense sense) {
            switch (sense) {
                case LESS_EQUAL:
                    return GREATER_EQUAL;
                case GREATER_EQUAL:
                    return LESS_EQUAL;
                case EQUAL:
                    return EQUAL;
                default:
                    throw new LinTimException("Unknown constraint sense " + sense);
            }
        }
    }
}
