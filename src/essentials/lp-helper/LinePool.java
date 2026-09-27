import net.lintim.util.Logger;

import java.util.*;
import java.util.Map.Entry;

public class LinePool {

    private static final Logger logger = new Logger(LinePool.class);

	private final LinkedList<Line> pool;
	private final PTN ptn;
	private static double node_degree_ratio;

    //divide min_load by min_cover_factor to get how often an edge has to be
    //covered
    private static double min_cover_factor;


    //multiply max_load with max_cover_factor to get how often an edge is
    //allowed to be covered
    private static double max_cover_factor;

    private static boolean restrictTerminals;
    public static Set<Integer> terminals;


    // Constructor----------------------------------------------------------
    public LinePool(PTN ptn) {
        pool = new LinkedList<>();
        this.ptn = ptn;
    }

    // Setter---------------------------------------------------------------
    public static void setNodeDegreeRatio(double v) {
        node_degree_ratio = v;
    }

    public static void setMinCoverFactor(double n) {
        min_cover_factor = n;
    }

    public static void setMaxCoverFactor(double m) {
        max_cover_factor = m;
    }

    public static void setRestrictTerminals(boolean b) {
        restrictTerminals = b;
    }

    public static void setTerminals(Set<Integer> t) {
        terminals = t;
    }

    // Getter---------------------------------------------------------------
    public LinkedList<Line> getLines() {
        return pool;
    }

    public PTN getPTN() {
        return ptn;
    }

    // Methods-------------------------------------------------------------

    //Adding lines---------------------------------------------------------

    //only when reading a linepool
    public void addLineInput(Line line) {
        pool.add(line);
    }

    //add a constructed line to the pool
    private void addLine(Line line) {
        boolean covers_preferred_edge = false;
        boolean overuses_edge = false;
        // make sure that lines are not added in both directions
        if (!ptn.isDirected() && line.getFirstStop().getIndex()
            < line.getLastStop().getIndex()) {
            return;
        }
        for (Edge edge : line.getEdges()) {
            if (edge.isPreferred()) {
                covers_preferred_edge = true;
            }

            if (edge.getOveruse(max_cover_factor)) {
                overuses_edge = true;
                break;
            }
        }
        if (covers_preferred_edge && !overuses_edge) {
            pool.add(line);
            for (Edge edge : line.getEdges()) {
                edge.increaseUsage();
                if (edge.getUsage() >= edge.getCoverings(min_cover_factor)) {
                    edge.setPreferred(false);
                }
            }
        }
    }

    // creating linepool---------------------------------------------------

    public boolean poolFromMST(MinimalSpanningTree mst) {
        //Check if any edge is added to the linepool
        int old_number_of_lines=pool.size();

        // SetTerminals
        lineBasedToTerminal();
        HashMap<Stop, LinkedList<Edge>> mst_by_stops = mst.getMST();
        LinkedList<Line> unfinished_lines = new LinkedList<Line>();
        LinkedList<Line> unfinished_leaf_to_leaf = new LinkedList<Line>();
        Line current_line;
        Line new_line;
        for (Stop stop : mst_by_stops.keySet()) {
            if (stop.isLeaf() && !restrictTerminals || stop.isTerminal()) {
                for (Edge edge : mst_by_stops.get(stop)) {
                    current_line = new Line();
                    if (!current_line.addFirstEdge(edge, stop))
                        continue;
                    if (current_line.isComplete(restrictTerminals)) {
                        addLine(current_line);
                        if(Line.getMinimumEdges()==1 && stop.isLeaf() &&
                            !current_line.isLeafToLeaf() && !restrictTerminals){
                            unfinished_leaf_to_leaf.add(current_line);
                        }
                    } else {
                        unfinished_lines.add(current_line);
                        if (stop.isLeaf() && !restrictTerminals) {
                            unfinished_leaf_to_leaf.add(current_line);
                        }
                    }
                }
            }
        }

        //OLD STRUCTURE: first lines to and from terminal, then leaf-to-leaf
        // make lines to and from terminals
		/*while (!unfinished_lines.isEmpty()) {
			current_line = unfinished_lines.remove();

			for (Edge edge : mst_by_stops.get(current_line.getLastStop())) {
				new_line = new Line(current_line);
				if (!new_line.addEdge(edge)){
					continue;
				}
				if (new_line.isComplete()) {
					addLine(new_line);
				} else {
					unfinished_lines.add(new_line);
				}
			}
		}

		// make lines leaf_to_leaf
		while (!unfinished_leaf_to_leaf.isEmpty()) {
			current_line = unfinished_leaf_to_leaf.remove();
			for (Edge edge : mst_by_stops.get(current_line.getLastStop())) {
				new_line = new Line(current_line);
				if (!new_line.addEdge(edge))
					continue;
				if (new_line.isLeafToLeaf()) {
					addLine(new_line);
				} else {
					unfinished_leaf_to_leaf.add(new_line);
				}
			}
		}*/

        //END OLD STRUCTURE

        //NEW STRUCTURE: alternating to and from terminal and leaf-to-leaf
        while(!unfinished_lines.isEmpty()|| !unfinished_leaf_to_leaf.isEmpty()){
            // make lines to and from terminals
            if (!unfinished_lines.isEmpty()) {
                current_line = unfinished_lines.remove();

                for (Edge edge : mst_by_stops.get(current_line.getLastStop())) {
                    new_line = new Line(current_line);
                    if (new_line.getEdges().contains(edge) || !new_line.addEdge(edge)){
                        continue;
                    }
                    if (new_line.isComplete(restrictTerminals)) {
                        addLine(new_line);
                    } else {
                        unfinished_lines.add(new_line);
                    }
                }
            }

            // make lines leaf_to_leaf
            if (!unfinished_leaf_to_leaf.isEmpty()) {
                //System.out.println("Unfinished leaf-to-leaf: " + unfinished_leaf_to_leaf.size());
                current_line = unfinished_leaf_to_leaf.remove();
                for (Edge edge : mst_by_stops.get(current_line.getLastStop())) {
                    new_line = new Line(current_line);
                    if ( new_line.getEdges().contains(edge) || !new_line.addEdge(edge)){
                        continue;
                    }
                    if (new_line.isLeafToLeaf() && !restrictTerminals) {
                        addLine(new_line);
                    } else {
                        unfinished_leaf_to_leaf.add(new_line);
                    }
                }
            }
        }

        //END NEW STRUCTURE

        // make sure all stops are normal stops
        mst.resetStops(restrictTerminals);

        //Return whether any edge was added
        return pool.size()>old_number_of_lines;
    }

    public int poolFromKSP(OD od, int k) {
        return addKSPLines(od, 0, k);
    }

    public int addKSPLines(OD od, double ratio, int k) {
        Line line;
        double max_od_entry = od.getMaximumODEntry();
        Stop origin;
        HashMap<Stop, Double> destinations;
        int added_lines = 0;

        //Set all stops to be leaves in order to start lines at any stop
        for (Stop stop : ptn.getStops()) {
            stop.setIsLeaf(true);
        }

        KShortestPathsWrapper wrapper = new KShortestPathsWrapper(ptn, od);
        LinkedList<LinkedList<Edge>> pre_lines;
        for (Entry<Stop, HashMap<Stop, Double>> entry : od.getOD().entrySet()) {
            origin = entry.getKey();
            destinations = entry.getValue();
            pre_lines = wrapper.getPaths(origin, destinations, k, max_od_entry * ratio);
            for (LinkedList<Edge> pre_line : pre_lines) {
                line = pathToLine(pre_line);
                pool.add(line);
                added_lines++;
            }
        }
        return added_lines;
    }

    public void addSingleLinkLines() {
        for (Edge edge: ptn.getEdges()) {
            Line newLine = new Line();
            boolean added = newLine.addFirstEdge(edge, edge.getLeftStop());
            if (!added) {
                logger.warn("Could not add edge " + edge + " as line");
            }
            pool.add(newLine);
        }
    }

    private Line pathToLine(LinkedList<Edge> pre_line) {
        int edge_index = 0;
        Line line = new Line();
        Line left_to_right = new Line();
        Line right_to_left = new Line();
        for (Edge edge : pre_line) {
            edge_index++;
            edge.increaseUsage();
            if (edge_index == 1) {
                left_to_right.addFirstEdge(edge, edge.getLeftStop());
                right_to_left.addFirstEdge(edge, edge.getRightStop());
                if (pre_line.size() == 1) {
                    return left_to_right;
                }
            }
            if (edge_index == 2) {
                if (left_to_right.addEdge(edge)) {
                    line = new Line(left_to_right);
                } else if (right_to_left.addEdge(edge)) {
                    line = new Line(right_to_left);
                }
            }
            if (edge_index > 2) {
                line.addEdge(edge);
            }
        }
        return line;
    }


//Fix terminals-----------------------------------------------------------

    private int lineBasedToTerminal() {
        //Check if there are already any lines
        if(pool.isEmpty()){
            return ptn.stopsToTerminal(node_degree_ratio, restrictTerminals, terminals);
        }
        int sum = 0;
        if (restrictTerminals) {
            for (Stop stop: ptn.getStops()) {
                if (terminals.contains(stop.getIndex())) {
                    stop.setIsTerminal(true);
                    sum += 1;
                }
            }
        }
        else {
            int max_deg = 0;
            HashMap<Stop, Integer> number_of_lines = new HashMap<>();
            for (Stop stop : ptn.getStops()) {
                number_of_lines.put(stop, 0);
            }
            for (Line line : pool) {
                for (Stop stop : line.getStops()) {
                    number_of_lines.put(stop, number_of_lines.get(stop) + 1);
                }
            }
            // Compute maximum degree
            for (Entry<Stop, Integer> entry : number_of_lines.entrySet()) {
                if (max_deg < entry.getValue()) {
                    max_deg = entry.getValue();
                }
            }
            // Set Terminals
            for (Entry<Stop, Integer> entry : number_of_lines.entrySet()) {
                if (entry.getValue() >= node_degree_ratio * max_deg) {
                    entry.getKey().setIsTerminal(true);
                    sum++;
                }
            }
        }
        return sum;
    }

    // Identify problematic edges from line-concept

	public boolean preferProblematicEdges(int[] line_concept){
		if(line_concept.length!=pool.size()){
//			System.err.println("Line Concept: "+ line_concept.length);
//			System.err.println("Pool: "+pool.size());
			throw new RuntimeException("Line-Concept does not match Line-Pool!");
		}
		boolean feasible=true;

        //Compute the load per edge in given line-concept
        Hashtable<Edge, Integer> load = new Hashtable<>();
        for (Edge edge : ptn.getEdges()) {
            load.put(edge, 0);
        }
        int freq;
        for (Line line : pool) {
            freq = line_concept[line.getIndex() - 1];
            if (freq > 0) {
                for (Edge edge : line.getEdges()) {
                    load.put(edge, freq + load.get(edge));
                }
            }
        }
        Edge edge;
        for (Entry<Edge, Integer> entry : load.entrySet()) {
            edge = entry.getKey();
            if (edge.getMinLoad() > entry.getValue()) {
                feasible = false;
                edge.setPreferred(true);
            }
        }
        return feasible;
    }

    // CSV-----------------------------------------------------------------------

    //Prepare the linepool in order to create an output file
    public void finalizePool() {
        // Delete double entries
        Collections.sort(pool);
        Line current_line;
        Line last_line = null;
        Iterator<Line> it = pool.iterator();
        while (it.hasNext()) {
            current_line = it.next();
            if (current_line.equals(last_line)) {
                it.remove();
            } else {
                last_line = current_line;
            }
        }

        // Enumerate the lines
        int line_index = 0;
        for (Line line : pool) {
            line_index++;
            line.setLineIndex(line_index);
        }
    }


    //Prepare the linepool in order to create an output file in
    //case of SP
    public void finalizeSP() {
        // Delete double entries
        Collections.sort(pool);
        Line outer_line;
        Line inner_line;
        Iterator<Line> outer = pool.iterator();
        Iterator<Line> inner;
        while (outer.hasNext()) {
            outer_line = outer.next();
            inner = pool.iterator();
            while (inner.hasNext()) {
                inner_line = inner.next();
                if (outer_line == inner_line) {
                    continue;
                }
                if (outer_line.equals(inner_line)) {
                    outer.remove();
                    break;
                }
                if (inner_line.getEdges().containsAll(outer_line.getEdges())) {
                    outer.remove();
                    break;
                }
            }
        }

        // Enumerate the lines
        int line_index = 0;
        for (Line line : pool) {
            line_index++;
            line.setLineIndex(line_index);
        }
    }

}
