import java.io.*;
import java.util.*;

public class PTNCSV {
	private static String infeasible="#No feasible sulotion has been found.";
//Read------------------------------------------------------------------------

	public static void fromFile(PTN ptn, File stop_file, File edge_file)
			throws IOException {
		readStops(ptn, stop_file);
		readEdges(ptn, edge_file);

	}

//Private methods read--------------------------------------------------------
	
	private static void readStops(PTN ptn, File stop_file)
			throws IOException {

		Stop current_stop;
		String line;
		String[] values;

		Scanner scan = new Scanner(new BufferedReader(new FileReader(stop_file)));
		while (scan.hasNext()) {
			line = scan.nextLine().trim();
			if (line.indexOf("#") == 0)
				continue;
			if (line.indexOf("#") > -1) {
				line = line.substring(0, line.indexOf("#") - 1);
			}
			if (line.contains(";")) {
				values = line.split(";");
				if (values.length != 5) {
					throw new IOException(
							"Wrong number of entries in line!");
				}
				current_stop = new Stop(Integer.parseInt(values[0].trim()), // index
						values[1].trim(), // short-name
						values[2].trim(), // long-name
						Double.parseDouble(values[3].trim()), // x-coordinate
						Double.parseDouble(values[4].trim())); // y-coordinate
				ptn.addStop(current_stop);
			}
		}
		scan.close();

	}
	

	private static void readEdges(PTN ptn, File edge_file)
			throws IOException {

		Edge current_edge;
		String line;
		String[] values;

		Scanner scan = new Scanner(new BufferedReader(new FileReader(edge_file)));
		while (scan.hasNext()) {
			line = scan.nextLine().trim();
			if (line.indexOf("#") == 0)
				continue;
			if (line.indexOf("#") > -1) {
				line = line.substring(0, line.indexOf("#") - 1);
			}
			if (line.contains(";")) {
				values = line.split(";");
				if (values.length != 6) {
					throw new IOException(
							"Wrong number of entries in line!");
				}
				current_edge = new Edge(
						ptn.isDirected(),
						Integer.parseInt(values[0].trim()), // index
						ptn.getStop(Integer.parseInt(values[1].trim())), // left-stop
						ptn.getStop(Integer.parseInt(values[2].trim())), // right-stop
						Double.parseDouble(values[3].trim()), // length
						Integer.parseInt(values[4].trim()), // lower-bound
						Integer.parseInt(values[5].trim())); // upper-bound
				current_edge.setOriginal_edge(current_edge);//originally in ptn
				ptn.addEdge(current_edge);
			}
		}
		scan.close();

	}

//Write---------------------------------------------------------------------------------
	
	public static void toFile(PTN ptn, File stop_file, File edge_file)
			throws IOException {
		writeStops(ptn, stop_file);
		writeEdges(ptn,edge_file);
	}
	
//Private methods write----------------------------------------------------------------
		
	private static void writeStops(PTN ptn, File stop_file) throws IOException{
		FileWriter writer = new FileWriter(stop_file);
		ptn.renameStops();
		LinkedList<Stop> stops=ptn.getStops();
		Iterator<Stop> it = stops.iterator();
		Stop current_stop;
		writer.write(Stop.printHeader() + "\n");
		while (it.hasNext()) {
			current_stop = it.next();
			writer.append(current_stop.toCSV() + "\n");
			writer.flush();
		}
		writer.close();
	}
	
	private static void writeEdges(PTN ptn, File edge_file) throws IOException{
		FileWriter writer = new FileWriter(edge_file);
		ptn.renameEdges();
		LinkedList<Edge> edges=ptn.getEdges();
		Iterator<Edge> it = edges.iterator();
		Edge current_edge;
		writer.write(Edge.printHeader() + "\n");
		while (it.hasNext()) {
			current_edge = it.next();
			writer.append(current_edge.toCSV() + "\n");
			writer.flush();
		}
		writer.close();
	}

//Infeasible--------------------------------------------------------------------
	public static void toFileInfeasible(File stop_file, File edge_file) throws IOException{
		writeInfeasible(stop_file);
		writeInfeasible(edge_file);
	}
	
//Private methods----------------------------------------------------------------
	private static void writeInfeasible(File file) throws IOException{
		FileWriter writer = new FileWriter(file);
		writer.write(PTNCSV.infeasible+"\n");
		writer.flush();
		writer.close();
	}

}
