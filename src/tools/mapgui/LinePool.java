import java.util.Vector;
import java.util.TreeMap;

public class LinePool{
	
	TreeMap<Integer, Line> lines;
	boolean directed;
	
	public LinePool(boolean directed){
		lines = new TreeMap<Integer, Line>();
	}
	
	public LinePool(boolean directed, TreeMap<Integer, Line> lines){
		this.lines = lines;
	}
	
	public Line getLine(Integer index){
		if(lines.containsKey(index))
			return lines.get(index);
		else
			return null;
	}
	
	// Indexing of lines according to space in ArrayList
	public void addLine(Line line){
			if(lines.containsKey(line.getIndex())){
				System.out.println("Data Inconsistency: LinePool already contains line " + line.getIndex());
				System.exit(1);
			}
			lines.put(line.getIndex(), line);
	}
	
	public int size(){
		return this.lines.size();
	}

	public TreeMap<Integer, Line> getLines(){
		return this.lines;
	}

}
