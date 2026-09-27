package net.lintim.algorithm;

import net.lintim.exception.AlgorithmDijkstraNegativeEdgeLengthException;
import net.lintim.exception.AlgorithmDijkstraQueryDistanceBeforeComputationException;
import net.lintim.exception.AlgorithmDijkstraQueryPathBeforeComputationException;
import net.lintim.model.Graph;
import net.lintim.model.Link;
import net.lintim.model.Path;
import net.lintim.model.Stop;
import net.lintim.model.impl.ArrayListGraph;
import net.lintim.util.TestHelper;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.beans.Transient;
import java.util.ArrayList;
import java.util.Collection;

/**
 */
public class DijkstraTest {

    private static final double DELTA = 1e-15;

    @BeforeClass
    public static void setupClass() {
        TestHelper.disableLogging();
    }

    @Test
    public void canFindShortestPath() {
        Graph<Stop, Link> graph = new ArrayListGraph<>();
        Stop stop1 = new Stop(1, "1", "1", 1, 1);
        Stop stop2 = new Stop(2, "2", "2", 2, 2);
        Stop stop3 = new Stop(3, "3", "3", 3, 3);
        graph.addNode(stop1);
        graph.addNode(stop2);
        graph.addNode(stop3);
        Link link1 = new Link(1, stop1, stop2, 1, 1, 1, true);
        Link link2 = new Link(2, stop1, stop3, 1, 1, 1, true);
        Link link3 = new Link(3, stop3, stop2, 1, 1, 1, true);
        graph.addEdge(link1);
        graph.addEdge(link2);
        graph.addEdge(link3);
        Dijkstra<Stop, Link, Graph<Stop, Link>> dijkstra = new Dijkstra<>(graph, stop1, Link::getLength);
        Assert.assertEquals(1, dijkstra.computeShortestPath(stop2), DELTA);
        Assert.assertEquals(1, dijkstra.getDistance(stop2), DELTA);
        Path<Stop, Link> path = dijkstra.getPath(stop2);
        Assert.assertEquals(1, path.getEdges().size());
        Assert.assertEquals(2, path.getNodes().size());
        Assert.assertTrue(path.contains(link1));
        Assert.assertTrue(path.contains(stop1));
        Assert.assertTrue(path.contains(stop2));
        Assert.assertThrows(AlgorithmDijkstraQueryDistanceBeforeComputationException.class, () -> dijkstra.getDistance(stop3));
        Assert.assertThrows(AlgorithmDijkstraQueryPathBeforeComputationException.class, () -> dijkstra.getPath(stop3));

    }

    @Test
    public void canFindShortestPathWithMoreLinks() {
        Graph<Stop, Link> graph = new ArrayListGraph<>();
        Stop stop1 = new Stop(1, "1", "1", 1, 1);
        Stop stop2 = new Stop(2, "2", "2", 2, 2);
        Stop stop3 = new Stop(3, "3", "3", 3, 3);
        graph.addNode(stop1);
        graph.addNode(stop2);
        graph.addNode(stop3);
        Link link1 = new Link(1, stop1, stop2, 3, 1, 1, true);
        Link link2 = new Link(2, stop1, stop3, 1, 1, 1, true);
        Link link3 = new Link(3, stop3, stop2, 1, 1, 1, true);
        graph.addEdge(link1);
        graph.addEdge(link2);
        graph.addEdge(link3);
        Dijkstra<Stop, Link, Graph<Stop, Link>> dijkstra = new Dijkstra<>(graph, stop1, Link::getLength);
        Assert.assertEquals(2, dijkstra.computeShortestPath(stop2), DELTA);
        Assert.assertEquals(2, dijkstra.getDistance(stop2), DELTA);
        Assert.assertEquals(1, dijkstra.getDistance(stop3), DELTA);
        Path<Stop, Link> path = dijkstra.getPath(stop2);
        Assert.assertEquals(2, path.getEdges().size());
        Assert.assertEquals(3, path.getNodes().size());
        Assert.assertTrue(path.contains(link2));
        Assert.assertTrue(path.contains(link3));
        Assert.assertTrue(path.contains(stop1));
        Assert.assertTrue(path.contains(stop3));
        Assert.assertTrue(path.contains(stop2));
    }

    @Test
    public void worksWithNegativeEdgeLengthsToZeroDegreeNodes() {
        Graph<Stop, Link> graph = new ArrayListGraph<>();
        Stop stop1 = new Stop(1, "1", "1", 1, 1);
        Stop stop2 = new Stop(2, "2", "2", 2, 2);
        Stop stop3 = new Stop(3, "3", "3", 3, 3);
        graph.addNode(stop1);
        graph.addNode(stop2);
        graph.addNode(stop3);
        Link link1 = new Link(1, stop1, stop2, -1, 0, 0, true);
        Link link2 = new Link(2, stop1, stop3, 3, 0, 0, true);
        Link link3 = new Link(3, stop2, stop3, 2, 0, 0, true);
        graph.addEdge(link1);
        graph.addEdge(link2);
        graph.addEdge(link3);
        Dijkstra<Stop, Link, Graph<Stop, Link>> dijkstra = new Dijkstra<>(graph, stop1, Link::getLength);
        Assert.assertEquals(1, dijkstra.computeShortestPath(stop3), DELTA);
    }

    @Test
    public void failsWithNegativeEdgeLength() {
        Graph<Stop, Link> graph = new ArrayListGraph<>();
        Stop stop1 = new Stop(1, "1", "1", 1, 1);
        Stop stop2 = new Stop(2, "2", "2", 2, 2);
        Stop stop3 = new Stop(3, "3", "3", 3, 3);
        Stop stop4 = new Stop(4, "4", "4", 4, 4);
        graph.addNode(stop1);
        graph.addNode(stop2);
        graph.addNode(stop3);
        graph.addNode(stop4);
        Link link1 = new Link(1, stop1, stop2, 1, 0, 0, true);
        Link link2 = new Link(2, stop1, stop3, 0, 0, 0, true);
        Link link3 = new Link(3, stop1, stop4, 99, 0, 0, true);
        Link link4 = new Link(4, stop4, stop2, -300, 0, 0, true);
        Link link5 = new Link(5, stop2, stop3, 1, 0, 0, true);
        graph.addEdge(link1);
        graph.addEdge(link2);
        graph.addEdge(link3);
        graph.addEdge(link4);
        graph.addEdge(link5);
        Assert.assertThrows(AlgorithmDijkstraNegativeEdgeLengthException.class, () -> new Dijkstra<>(graph, stop1, Link::getLength));
    }

    @Test
    public void canComputeAllShortestPaths() {
        Graph<Stop, Link> graph = new ArrayListGraph<>();
        Stop stop1 = new Stop(1, "1", "1", 1, 1);
        Stop stop2 = new Stop(2, "2", "2", 2, 2);
        Stop stop3 = new Stop(3, "3", "3", 3, 3);
        graph.addNode(stop1);
        graph.addNode(stop2);
        graph.addNode(stop3);
        Link link1 = new Link(1, stop1, stop2, 2, 1, 1, true);
        Link link2 = new Link(2, stop1, stop3, 1, 1, 1, true);
        graph.addEdge(link1);
        graph.addEdge(link2);
        Dijkstra<Stop, Link, Graph<Stop, Link>> dijkstra = new Dijkstra<>(graph, stop1, Link::getLength);
        dijkstra.computeShortestPaths();
        Path<Stop, Link> path = dijkstra.getPath(stop2);
        Assert.assertEquals(1, path.getEdges().size());
        path = dijkstra.getPath(stop3);
        Assert.assertEquals(1, path.getEdges().size());
    }

    @Test
    public void canComputeShortestPathWithMultipleShortestSubpaths() {
        Graph<Stop, Link> graph = new ArrayListGraph<>();
        Stop stop1 = new Stop(1, "1", "1", 1, 1);
        Stop stop2 = new Stop(2, "2", "2", 2, 2);
        Stop stop3 = new Stop(3, "3", "3", 3, 3);
        Stop stop4 = new Stop(4, "4", "4", 4, 4);
        graph.addNode(stop1);
        graph.addNode(stop2);
        graph.addNode(stop3);
        graph.addNode(stop4);
        Link link1 = new Link(1, stop1, stop2, 10, 10, 10, true);
        Link link2 = new Link(2, stop1, stop3, 10, 10, 10, true);
        Link link3 = new Link(3, stop2, stop4, 10, 10, 10, true);
        Link link4 = new Link(4, stop3, stop4, 10, 10, 10, true);
        graph.addEdge(link1);
        graph.addEdge(link2);
        graph.addEdge(link3);
        graph.addEdge(link4);
        Dijkstra<Stop, Link, Graph<Stop, Link>> dijkstra = new Dijkstra<>(graph, stop1, Link::getLength);
        dijkstra.computeShortestPaths();
        Path<Stop, Link> path = dijkstra.getPath(stop4);
        Assert.assertEquals(2, path.getEdges().size());
    }

    @Test
    public void canComputeMultipleShortestPaths() {
        Graph<Stop, Link> graph = new ArrayListGraph<>();
        Stop stop1 = new Stop(1, "1", "1", 1, 1);
        Stop stop2 = new Stop(2, "2", "2", 2, 2);
        Stop stop3 = new Stop(3, "3", "3", 3, 3);
        Stop stop4 = new Stop(4, "4", "4", 4, 4);
        graph.addNode(stop1);
        graph.addNode(stop2);
        graph.addNode(stop3);
        graph.addNode(stop4);
        Link link1 = new Link(1, stop1, stop2, 10, 10, 10, true);
        Link link2 = new Link(2, stop1, stop3, 10, 10, 10, true);
        Link link3 = new Link(3, stop2, stop4, 10, 10, 10, true);
        Link link4 = new Link(4, stop3, stop4, 10, 10, 10, true);
        graph.addEdge(link1);
        graph.addEdge(link2);
        graph.addEdge(link3);
        graph.addEdge(link4);
        Dijkstra<Stop, Link, Graph<Stop, Link>> dijkstra = new Dijkstra<>(graph, stop1, Link::getLength);
        dijkstra.computeShortestPaths();
        Collection<Path<Stop, Link>> paths = dijkstra.getPaths(stop4);
        Assert.assertEquals(2, paths.size());
        for (Path<Stop, Link> path: paths) {
            Assert.assertEquals(2, path.getEdges().size());
        }
        Assert.assertEquals(20, dijkstra.getDistance(stop4), DELTA);
    }

    @Test
    public void canAdaptReturnedPaths() {
        Graph<Stop, Link> graph = new ArrayListGraph<>();
        Stop stop1 = new Stop(1, "1", "1", 1, 1);
        Stop stop2 = new Stop(2, "2", "2", 2, 2);
        Stop stop3 = new Stop(3, "3", "3", 3, 3);
        Stop stop4 = new Stop(4, "4", "4", 4, 4);
        graph.addNode(stop1);
        graph.addNode(stop2);
        graph.addNode(stop3);
        graph.addNode(stop4);
        Link link1 = new Link(1, stop1, stop2, 10, 10, 10, true);
        Link link2 = new Link(2, stop1, stop3, 10, 10, 10, true);
        Link link3 = new Link(3, stop2, stop4, 10, 10, 10, true);
        Link link4 = new Link(4, stop3, stop4, 10, 10, 10, true);
        Link link5 = new Link(5, stop4, stop2, 10, 10, 10, true);
        graph.addEdge(link1);
        graph.addEdge(link2);
        graph.addEdge(link3);
        graph.addEdge(link4);
        Dijkstra<Stop, Link, Graph<Stop, Link>> dijkstra = new Dijkstra<>(graph, stop1, Link::getLength);
        dijkstra.computeShortestPaths();
        ArrayList<Path<Stop, Link>> paths = new ArrayList<>(dijkstra.getPaths(stop4));
        Assert.assertEquals(2, paths.size());
        for (Path<Stop, Link> path: paths) {
            Assert.assertEquals(2, path.getEdges().size());
        }
        paths.get(0).addLast(link5);
        Assert.assertEquals(3, paths.get(0).getEdges().size());
        Assert.assertEquals(2, new ArrayList<>(dijkstra.getPaths(stop4)).get(0).getEdges().size());
    }

    @Test
    public void canAdaptReturnedPath() {
        Graph<Stop, Link> graph = new ArrayListGraph<>();
        Stop stop1 = new Stop(1, "1", "1", 1, 1);
        Stop stop2 = new Stop(2, "2", "2", 2, 2);
        Stop stop3 = new Stop(3, "3", "3", 3, 3);
        Stop stop4 = new Stop(4, "4", "4", 4, 4);
        graph.addNode(stop1);
        graph.addNode(stop2);
        graph.addNode(stop3);
        graph.addNode(stop4);
        Link link1 = new Link(1, stop1, stop2, 10, 10, 10, true);
        Link link2 = new Link(2, stop1, stop3, 10, 10, 10, true);
        Link link3 = new Link(3, stop2, stop4, 10, 10, 10, true);
        Link link4 = new Link(4, stop3, stop4, 10, 10, 10, true);
        Link link5 = new Link(5, stop4, stop2, 10, 10, 10, true);
        graph.addEdge(link1);
        graph.addEdge(link2);
        graph.addEdge(link3);
        graph.addEdge(link4);
        Dijkstra<Stop, Link, Graph<Stop, Link>> dijkstra = new Dijkstra<>(graph, stop1, Link::getLength);
        dijkstra.computeShortestPaths();
        Path<Stop, Link> path = dijkstra.getPath(stop4);
        Assert.assertEquals(2, path.getEdges().size());
        path.addLast(link5);
        Assert.assertEquals(3, path.getEdges().size());
        Assert.assertEquals(2, dijkstra.getPath(stop4).getEdges().size());
    }
}
