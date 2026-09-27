/**
 *
 */
package net.lintim.util;

/**
 * Currently contains an adjustable small number used as epsilon for floating
 * point comparisons.
 *
 */
public class MathHelper {
    /**
     * Used for floating point comparisons, since <code>a == b</code> may fail
     * due to rounding errors. Therfore,
     * <code>Math.abs(a-b) &lt; MathHelper.epsilon</code> is preferable.
     */
    public static double epsilon = 1E-12;
}
