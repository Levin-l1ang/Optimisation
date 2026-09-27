package net.lintim.csv;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Formats numbers w.r.t. US american locale and no grouping to override
 * system default locales to ensure compatibility among differently configured
 * systems.
 *
 */
public class Formatter{

    private static NumberFormat format = null;

    /**
     * Formats a double.
     *
     * @param toFormat The double to format.
     * @return The double formatted w.r.t. US american locale and no grouping.
     */
    public static String format(Double toFormat){
        if(format == null){
            format = NumberFormat.getNumberInstance(Locale.US);
            format.setGroupingUsed(false);
        }
        return format.format(toFormat);
    }

}
