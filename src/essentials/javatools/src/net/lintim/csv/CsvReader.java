package net.lintim.csv;

import org.supercsv.comment.CommentStartsWith;
import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Generic CSV (semiColon Separated Value) reader, used in all classes of this
 * package.
 *
 */
public class CsvReader {

		private CsvPreference.Builder builder = new CsvPreference.Builder('"', ';', "\n").skipComments(new CommentStartsWith("#"));
    private CsvPreference csvPreference = builder.build();
    //private CsvPreference csvPreference = new CsvPreference('"', ';', "\n");
    private CsvListReader reader;

    private FileReader fileReader;

    /**
     * Constructor, redirects to {@link #CsvReader(FileReader)}.
     *
     * @param file The file to read.
     * @throws FileNotFoundException
     */
    public CsvReader(File file) throws FileNotFoundException{
        this(new FileReader(file));
    }

    /**
     * The actual constructor.
     *
     * @param fileReader The file reader to read from.
     */
    public CsvReader(FileReader fileReader){
        this.fileReader = fileReader;
        this.reader = new CsvListReader(fileReader, csvPreference);
    }

    /**
     * Reads a new row from the CSV file. Skips comments and empty lines.
     *
     * @return The columns of that file.
     * @throws IOException
     */
    public List<String> read() throws IOException{
        List<String> line = null;

        while ((line = reader.read()) != null) {

            Iterator<String> itr = line.iterator();
            String key = new String(itr.next().trim());

            if(!itr.hasNext() && key.isEmpty() || key.startsWith("#")){
                continue;
            }
            else{
            	for(int i = 0; i < line.size(); i++){
        				line.set(i, line.get(i).trim());
       				}
              break;
            }
        }
        

        return line;
    }

    /** Returns the current line number.
     *
     * @return The current line number.
     */
    int getLineNumber(){
        return reader.getLineNumber();
    }

    /** Closes the file.
     *
     * @throws IOException
     */
    public void close() throws IOException{
        reader.close();
        fileReader.close();
    }


}
