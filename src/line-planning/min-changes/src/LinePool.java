import net.lintim.util.Logger;

import java.util.TreeMap;

public class LinePool {

    private static final Logger logger = new Logger(LinePool.class.getCanonicalName());

    TreeMap<Integer, Line> lines;

    public LinePool() {
        lines = new TreeMap<>();
    }

    public Line getLine(Integer index) {
        return lines.getOrDefault(index, null);
    }

    // Indexing of lines according to space in ArrayList
    public void addLine(Line line) {
        if (lines.containsKey(line.getIndex())) {
            logger.error("Data Inconsistency: LinePool already contains line " + line.getIndex());
            System.exit(1);
        }
        lines.put(line.getIndex(), line);
    }

    public int size() {
        return this.lines.size();
    }

    public TreeMap<Integer, Line> getLines() {
        return this.lines;
    }

}
