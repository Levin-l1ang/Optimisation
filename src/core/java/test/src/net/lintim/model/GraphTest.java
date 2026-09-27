package net.lintim.model;

import net.lintim.model.impl.TestEdge;
import net.lintim.model.impl.TestNode;
import org.junit.Before;
import org.junit.Test;

import java.util.Comparator;

import static org.junit.Assert.*;

/**
 * Test cases for the graph interface. Not complete! Needs to be implemented for any Graph implementation, since each
 * test case will use the graph implementation set in {@link GraphTest#supplyGraph()}.
 */
public abstract class GraphTest {
    protected Graph<TestNode, TestEdge> graph;

    @Before
    public abstract void supplyGraph();

    @Test
    public void canAddNodes() {
        TestNode node1 = new TestNode(1);
        TestNode node2 = new TestNode(2);
        assertEquals(0, graph.getNodes().size());
        assertTrue(graph.addNode(node1));
        assertEquals(1, graph.getNodes().size());
        assertTrue(graph.addNode(node2));
        assertEquals(2, graph.getNodes().size());
        assertEquals(node2, graph.getNode(2));
        assertFalse(graph.addNode(node1));
        assertEquals(2, graph.getNodes().size());
        assertEquals(node2, graph.getNode(2));
    }

    @Test
    public void canAddEdges() {
        TestNode node1 = new TestNode(1);
        TestNode node2 = new TestNode(2);
        TestNode node3 = new TestNode(3);
        TestNode node4 = new TestNode(4);
        TestNode node5 = new TestNode(5);
        TestEdge edge1 = new TestEdge(1, node1, node2);
        TestEdge edge2 = new TestEdge(2, node2, node3);
        TestEdge edge3 = new TestEdge(3, node4, node5);
        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addNode(node4);
        graph.addNode(node5);
        assertEquals(0, graph.getEdges().size());
        assertTrue(graph.addEdge(edge1));
        assertEquals(1, graph.getEdges().size());
        assertTrue(graph.addEdge(edge2));
        assertTrue(graph.addEdge(edge3));
        assertEquals(3, graph.getEdges().size());
        assertEquals(2, graph.getIncidentEdges(node2).size());
        assertEquals(1, graph.getIncomingEdges(node2).size());
        assertEquals(edge1, graph.getOutgoingEdges(node1).iterator().next());
        assertFalse(graph.addEdge(edge1));
        assertEquals(3, graph.getEdges().size());
        assertEquals(2, graph.getIncidentEdges(node2).size());
        assertEquals(1, graph.getIncomingEdges(node2).size());
    }

    @Test
    public void canRemoveNode() {
        TestNode node1 = new TestNode(1);
        TestNode node2 = new TestNode(2);
        TestNode node3 = new TestNode(3);
        TestNode node4 = new TestNode(4);
        TestNode node5 = new TestNode(5);
        TestEdge edge1 = new TestEdge(1, node1, node2);
        TestEdge edge2 = new TestEdge(2, node2, node3);
        TestEdge edge3 = new TestEdge(3, node4, node5);
        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addNode(node4);
        graph.addNode(node5);
        graph.addEdge(edge1);
        graph.addEdge(edge2);
        graph.addEdge(edge3);
        assertEquals(3, graph.getEdges().size());
        assertEquals(5, graph.getNodes().size());
        assertTrue(graph.removeNode(node5));
        assertEquals(4, graph.getNodes().size());
        assertEquals(2, graph.getEdges().size());
        assertTrue(graph.removeNode(node2));
        assertEquals(0, graph.getEdges().size());
        assertEquals(3, graph.getNodes().size());
    }

    @Test
    public void canRemoveEdge() {
        TestNode node1 = new TestNode(1);
        TestNode node2 = new TestNode(2);
        TestNode node3 = new TestNode(3);
        TestNode node4 = new TestNode(4);
        TestNode node5 = new TestNode(5);
        TestEdge edge1 = new TestEdge(1, node1, node2);
        TestEdge edge2 = new TestEdge(2, node2, node3);
        TestEdge edge3 = new TestEdge(3, node4, node5);
        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addNode(node4);
        graph.addNode(node5);
        graph.addEdge(edge1);
        graph.addEdge(edge2);
        graph.addEdge(edge3);
        assertEquals(3, graph.getEdges().size());
        assertEquals(5, graph.getNodes().size());
        assertTrue(graph.removeEdge(edge3));
        assertEquals(2, graph.getEdges().size());
        assertEquals(5, graph.getNodes().size());
        assertTrue(graph.removeEdge(edge2));
        assertEquals(1, graph.getEdges().size());
        assertEquals(5, graph.getNodes().size());
    }

    @Test
    public void canFindEdge() {
        TestNode node1 = new TestNode(1);
        TestNode node2 = new TestNode(2);
        TestNode node3 = new TestNode(3);
        TestEdge edge1 = new TestEdge(1, node1, node2);
        TestEdge edge2 = new TestEdge(2, node1, node3);
        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addEdge(edge1);
        graph.addEdge(edge2);
        assertEquals(graph.getEdge(node1, node2).get(), edge1);
        assertEquals(graph.getEdge(node1, node3).get(), edge2);
    }

    @Test
    public void cannotFindNonexistingEdge() {
        TestNode node1 = new TestNode(1);
        TestNode node2 = new TestNode(2);
        TestNode node3 = new TestNode(3);
        TestEdge edge1 = new TestEdge(1, node1, node2);
        TestEdge edge2 = new TestEdge(2, node1, node3);
        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addEdge(edge1);
        graph.addEdge(edge2);
        assertFalse(graph.getEdge(node2, node3).isPresent());
    }

    @Test
    public void canFindLoop() {
        TestNode node1 = new TestNode(1);
        TestNode node2 = new TestNode(2);
        TestEdge edge1 = new TestEdge(1, node1, node2);
        TestEdge edge2 = new TestEdge(2, node1, node1);
        graph.addNode(node1);
        graph.addNode(node2);
        graph.addEdge(edge1);
        graph.addEdge(edge2);
        assertEquals(graph.getEdge(node1, node1).get(), edge2);
    }

    @Test
    public void canReorderSimilarNodes() {
        TestNode node1 = new TestNode(1);
        TestNode node2 = new TestNode(2);
        TestEdge edge1 = new TestEdge(1, node1, node2);
        TestEdge edge2 = new TestEdge(2, node2, node1);
        graph.addNode(node1);
        graph.addNode(node2);
        graph.addEdge(edge1);
        graph.addEdge(edge2);
        graph.orderNodes(Comparator.comparingInt(TestNode::getId).reversed());
        assertEquals(graph.getIncidentEdges(node1).size(), 2);
        assertEquals(graph.getIncidentEdges(node2).size(), 2);
        assertEquals(graph.getIncomingEdges(node1).stream().findAny().get(), edge2);
    }
}
