package net.lintim.csv;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

public class CsvReader {


    private CsvPreference csvPreference = new CsvPreference.Builder('"', ';', "\n").build();
    private CsvListReader reader;
    private FileReader fileReader;

    public CsvReader(File file) throws FileNotFoundException {
        this(new FileReader(file));
    }

    public CsvReader(FileReader fileReader) {
        this.reader = new CsvListReader(fileReader, csvPreference);
        this.fileReader = fileReader;
    }

    public List<String> read() throws IOException {
        List<String> line = null;

        while ((line = reader.read()) != null) {

            Iterator<String> itr = line.iterator();
            String key = new String(itr.next().trim());

            if (!itr.hasNext() && key.isEmpty() || key.startsWith("#")) {
                continue;
            } else {
                break;
            }
        }

        return line;
    }

    int getLineNumber() {
        return reader.getLineNumber();
    }

    /**
     * Closes the file.
     *
     * @throws IOException
     */
    public void close() throws IOException {
        reader.close();
        fileReader.close();
    }

}
