package net.lintim.io;

import net.lintim.exception.OutputFileException;
import net.lintim.util.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

/**
 * Helper class to write csv-Files, used for the formatting of the files. All IOExceptions are
 * raised to the caller
 */
public class CsvWriter {

    private static final Logger logger = new Logger(CsvWriter.class);

    /**
     * The writer of this class. All writing happens through this class member.
     */
    private BufferedWriter writer;

    private static DecimalFormat decimalFormat = provideDecimalFormat();

    private static DecimalFormat provideDecimalFormat() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        DecimalFormat result = new DecimalFormat("0.#####", symbols);
        result.setRoundingMode(RoundingMode.HALF_UP);
        return result;
    }

    /**
     * Create a new CsvWriter instance for a specific file. The file is opened with the given relative file name.
     *
     * @param fileName the relative file name
     * @throws OutputFileException if the file cannot be opened or written to
     */
    public CsvWriter(String fileName) throws OutputFileException {
        try {
            logger.debug("Writing to file " + fileName);
            Path writerFolderPathParent = Paths.get(fileName).getParent();
            if (writerFolderPathParent != null) {
                Files.createDirectories(writerFolderPathParent);
            }
            this.writer = Files.newBufferedWriter(Paths.get(fileName), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        } catch (IOException e) {
            logger.error(e.toString());
            throw new OutputFileException(fileName);
        }
    }

    /**
     * Create a new CsvWriter instance for a specific file. The file is opened with the given relative file name
     *
     * @param fileName the relative file name
     * @param header   the header to write in the first line as a comment
     * @throws OutputFileException if the file cannot be opened or written to
     */
    public CsvWriter(String fileName, String header) throws OutputFileException {
        this(fileName);
        try {
            this.writeLine("# " + header);
        } catch (IOException e) {
            logger.error(e.toString());
            throw new OutputFileException(fileName);
        }
    }

    /**
     * Write a line to the csv-file. The given values are csv-formatted and written to the file of this writer
     * instance.
     *
     * @param values the values to write
     * @throws OutputFileException if the file, this instance was created with, cannot be written to
     */
    public void writeLine(String... values) throws IOException {
        if (values.length == 0) {
            //There is nothing to print
            return;
        }
        //Now print the first values.length-1 items, each followed by a ";"
        for (int i = 0; i < values.length - 1; i++) {
            writer.write(values[i] + "; ");
        }
        //Now print the last item, without ";"
        writer.write(values[values.length - 1]);
        writer.newLine();
    }

    /**
     * Close the file after all writing has been done
     *
     * @throws IOException if the file cannot be closed
     */
    public void close() throws IOException {
        writer.close();
    }

    /**
     * Write the given collection. The list is sorted by the comparator beforehand, if it is given. For each
     * element in the collection, the outputFunction is used to determine the string to output.
     *
     * @param collection     the collection to write
     * @param outputFunction the function to determine the actual output
     * @param comparator     the comparator to sort the elements by
     * @throws IOException on any io error
     */
    public <T> void writeCollection(Collection<T> collection, Function<T, String[]> outputFunction, Comparator<T>
        comparator) throws IOException {
        LinkedList<T> listToWrite = new LinkedList<>(collection);
        if (comparator != null) {
            listToWrite.sort(comparator);
        }
        writeList(listToWrite, outputFunction);
    }

    /**
     * Write the given list. For each element of the list, the outputFunction is used to determine the string to output.
     *
     * @param list           the list of elements to write
     * @param outputFunction the function to determine the actual output
     * @throws IOException on any io-error
     */
    public <T> void writeList(List<T> list, Function<T, String[]> outputFunction) throws IOException {
        for (T object : list) {
            writeLine(outputFunction.apply(object));
        }
    }

    /**
     * Write the given collection to the given file. The collection is sorted by the comparator beforehand, if it is
     * given. For each element in the collection, the outputFunction is used to determine the string to output.
     *
     * @param fileName       the path of the file to write
     * @param header         the header to use
     * @param collection     the collection of elements to write
     * @param outputFunction the function to determine the actual output
     * @param comparator     the comparator to sort the elements by
     * @throws OutputFileException on io error
     */
    public static <T> void writeCollection(String fileName, String header, Collection<T> collection, Function<T,
        String[]> outputFunction, Comparator<T> comparator) throws OutputFileException {
        try {
            CsvWriter writer = new CsvWriter(fileName, header);
            writer.writeCollection(collection, outputFunction, comparator);
            writer.close();
        } catch (IOException e) {
            logger.warn("Encountered exception: " + e.toString());
            throw new OutputFileException(fileName);
        }
    }

    /**
     * Write the given list to the given file. For each element in the collection, the outputFunction is used to determine the string to output.
     *
     * @param fileName       the path of the file to write
     * @param header         the header to use
     * @param list           the list of elements to write
     * @param outputFunction the function to determine the actual output
     * @throws OutputFileException on io error
     */
    public static <T> void writeList(String fileName, String header, List<T> list, Function<T,
        String[]> outputFunction) throws OutputFileException {
        try {
            CsvWriter writer = new CsvWriter(fileName, header);
            writer.writeList(list, outputFunction);
            writer.close();
        } catch (IOException e) {
            logger.warn("Encountered exception: " + e.toString());
            throw new OutputFileException(fileName);
        }
    }

    /**
     * Shorten the given double value to a string representation with at most two decimal places.
     *
     * @param value the value to shorten
     * @return the shortened representation
     */
    public static String shortenDecimalValueForOutput(double value) {
        // See if the number is integer
        if (Math.abs(value - Math.round(value)) < 0.000005) {
            return String.valueOf((long) value);
        }
        // It is a double but not integer, lets round to two decimals
        return decimalFormat.format(value);
    }

    /**
     * Use {@link #shortenDecimalValueForOutput(double)} on numbers, return the input for the rest
     *
     * @param value the value to shorten
     * @return the input value or the shortened representation, if its a number
     */
    public static String shortenDecimalValueIfItsDecimal(String value) {
        // Check if value is a number
        try {
            return shortenDecimalValueForOutput(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return value;
        }

    }


}
