package net.lintim.model;

import java.util.Comparator;

/**
 * Comparator class to compare to Edges by their keys
 */
public class EdgeComparator implements Comparator<Edge> {
	public int compare(Edge e1, Edge e2) {
		return e1.getKey() - e2.getKey();
	}
}
