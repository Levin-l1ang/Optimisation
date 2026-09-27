import java.util.ArrayList;
import java.awt.Color;

public class Line{
	
	public boolean directed;
	public int index;
	public ArrayList<Edge> edges;
	public int frequency;
	public double length;
	public double costs;
	public Color color;
	public boolean isHighlighted;
	
	public Line(boolean directed, int index){
		this.directed = directed;
		this.index = index;
		this.edges = new ArrayList<Edge>();
		this.frequency = 0;
		this.length = 0.0;
		this.costs = 0.0;
		this.isHighlighted = true;
	}
	
	public Line(boolean directed, int index, ArrayList<Edge> edges){
		this.directed = directed;
		this.index = index;
		this.edges = edges;
		this.frequency = 0;
		this.length = 0.0;
		this.costs = 0.0;
		this.isHighlighted = true;
	}
	
	public void addEdge(int position, Edge edge){
		if(position != this.edges.size()+1){
			System.out.println("Data Inconsistency: Position of edge " + edge.getIndex() + " exceeds array size.");
			System.exit(1);
		}
		this.edges.add(position-1, edge);
	}
	
	public int getIndex(){
		return this.index;
	}
	
	public ArrayList<Edge> getEdges(){
		return this.edges;
	}
	
	public void setIndex(int index){
		this.index = index;
	}
	
	public void setEdges(ArrayList<Edge> edges){
		this.edges = edges;
	}
	
	public boolean containsEdge(Edge compareEdge){
		return edges.contains(compareEdge)?true:false;
	}
	
	public void setFrequency(int freq){
		this.frequency = freq;
	}
	
	public int getFrequency(){
		return this.frequency;
	}
	
	public void setCosts(double costs){
		this.costs = costs;
	}
	
	public double getCosts(){
		return this.costs;
	}
	
	public void setLength(double length){
		this.length = length;
	}
	
	public double getLength(){
		return this.length;
	}
	
	@Override
	public String toString(){
		return ""+this.index;
	}
	
	public void setColor(Color color){
			this.color = color;
	}
	
	public Color getColor(){
		return this.color;
	}
	
	public boolean isHighlighted(){
		return this.isHighlighted;
	}
	
	public void setHighlighted(boolean highlight){
		this.isHighlighted = highlight;
	}
}
