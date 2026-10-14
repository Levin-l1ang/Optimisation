package linePlanning.lineGeneration;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import graph.algorithms.spprc.AbstractResourceCompletionTest;
import graph.algorithms.spprc.Label;
import graph.algorithms.spprc.REF;
import graph.structures.digraph.DirectedGraph;
import graph.structures.digraph.DirectedGraphArc;
import graph.structures.digraph.DirectedGraphNodeIndex;

public class AllPaths<V extends DirectedGraphNodeIndex, A>
{
	private final List<AbstractResourceCompletionTest<V, A>> completionTests;

	public AllPaths()
	{
		this.completionTests = new ArrayList<>();
	}

	public void addCompletionBound(AbstractResourceCompletionTest<V, A> completionTest)
	{
		completionTests.add(completionTest);
	}

	public List<List<DirectedGraphArc<V, A>>> applyLabelling(DirectedGraph<V, A> graph, V sourceNode, V sinkNode,
			REF<V, A> extensionFunction, ActivationFunction<V, A> activationFunction)
	{
		// Initialise a list of paths.
		List<List<DirectedGraphArc<V, A>>> paths = new ArrayList<>();

		// Initialise queue with a single source node.
		LinkedList<Label<V, A>> queue = new LinkedList<>();
		Label<V, A> sourceLabel = new Label<>(null, null, extensionFunction.getEmptyResourceVector(), 0, 0);
		addLabel(sourceLabel, sourceNode, queue);

		// Perform labelling.
		while (!queue.isEmpty())
		{
			Label<V, A> label = queue.pop();
			// set current node based on the arc of the label
			V currentNode = sourceNode;
			if (label.getArc() != null)
			{
				currentNode = label.getArc().getTo();
			}

			{
				// System.out.println("\n Scanning "+label+" currentNode: "+currentNode);

				// Expand label.
				for (DirectedGraphArc<V, A> arc : graph.getOutArcs(currentNode))
				{
					// System.out.println("OutArc: "+arc);
					// Skip deactivated arcs.
					if (!activationFunction.isActiveArc(arc))
					{
						continue;
					}

					// Skip arcs that lead to infeasible labels.
					int[] extendedResourceVector = extensionFunction.extendResourceVector(label, arc, true);
					if (!extensionFunction.isFeasible(extendedResourceVector))
					{
						continue;
					}

					// Expand label.
					Label<V, A> expandedLabel = new Label<>(label, arc, extendedResourceVector, 0, 0);
					// System.out.println("Expanded label: "+expandedLabel);
					// If at sink, terminate.
					if (arc.getTo().equals(sinkNode))
					{
						if (!extensionFunction.isFeasibleAtSink(extendedResourceVector))
						{
							continue;
						}

						// Use backtracking to construct a path and add to path list.
						paths.add(backtrackPath(expandedLabel));
					}
					else
					{
						boolean failedCompletionTest = false;
						// Completion bound based on resource.
						for (AbstractResourceCompletionTest<V, A> completionTest : completionTests)
						{
							if (!completionTest.isFeasible(expandedLabel, true))
							{
								failedCompletionTest = true;
							}
						}
						if (!failedCompletionTest)
						{
							// Add the label.
							addLabel(expandedLabel, arc.getTo(), queue);
						}
					}
				}
			}
		}
		return paths;
	}

	private List<DirectedGraphArc<V, A>> backtrackPath(Label<V, A> label)
	{
		List<DirectedGraphArc<V, A>> path = new ArrayList<>();
		Label<V, A> currentLabel = label;
		while (true)
		{
			path.add(0, currentLabel.getArc());
			currentLabel = currentLabel.getPreviousLabel();
			// if (currentLabel == null)
			if (currentLabel.getArc() == null)
			{
				break;
			}
		}
		return path;
	}

	private void addLabel(Label<V, A> label, V to, LinkedList<Label<V, A>> queue)
	{
		// Could add dominance checks here.
		queue.add(label);
		return;
	}
}
