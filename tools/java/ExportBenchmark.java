package exporter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import graph.structures.digraph.DirectedGraphArc;
import linePlanning.DPTN.DPTNArc;
import linePlanning.DPTN.DPTNDrivingArc;
import linePlanning.DPTN.DPTNRoute;
import linePlanning.DPTN.DPTNTransferArc;
import linePlanning.PTN.Connection;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.ODPair;
import linePlanning.instance.Station;
import linePlanning.lineGeneration.Line;
import linePlanning.lineGeneration.LinePoolGenerator;
import linePlanning.routeGeneration.RouteGenerator;
import util.Reader;

/**
 * Thin, CPLEX-free driver around the OFFICIAL benchmark generators
 * (LinePoolGenerator, RouteGenerator, DPTN shortest paths).  It does not
 * re-implement any rule: it only serialises what the reference code produces.
 *
 * args: instanceFolder odFile budgetFile transferPenaltyFile lineGenFile
 *       routeGenFile outDir [--no-routes]
 */
public class ExportBenchmark
{
	private static String q(String s)
	{
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static String num(double v)
	{
		return String.format(Locale.ROOT, "%.10f", v);
	}

	private static String ints(List<Integer> xs)
	{
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < xs.size(); i++)
		{
			if (i > 0) sb.append(",");
			sb.append(xs.get(i));
		}
		return sb.append("]").toString();
	}

	public static void main(String[] args) throws IOException
	{
		String folder = args[0];
		String odFile = args[1];
		String budgetFile = args[2];
		String transferFile = args[3];
		String lineGenFile = args[4];
		String routeGenFile = args[5];
		Path out = Paths.get(args[6]);
		boolean routes = !(args.length > 7 && args[7].equals("--no-routes"));
		Files.createDirectories(out);

		LinePlanningInstance instance = new LinePlanningInstance(folder, odFile, budgetFile, transferFile);
		instance.setLineGenerationFile(lineGenFile);
		instance.setRouteGenerationFile(routeGenFile);

		// ---- OD pairs with DPTN lower bound (official penalty = factor * lower bound)
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("od_bounds.jsonl"), StandardCharsets.UTF_8)))
		{
			for (ODPair od : instance.getODPairs())
			{
				w.println("{\"origin\":" + q(od.getOrigin().getName()) + ",\"destination\":"
						+ q(od.getDestination().getName()) + ",\"volume\":" + num(od.getVolume())
						+ ",\"min_travel_time\":" + num(instance.getDPTN().getMinimumTravelTime(od.getOrigin(), od.getDestination()))
						+ ",\"official_penalty\":" + num(instance.getMissedDemandPenalty(od)) + "}");
			}
		}

		// ---- Official line pool
		LinePoolGenerator generator = new LinePoolGenerator(instance);
		List<Line> lines = generator.getLines();
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("lines.jsonl"), StandardCharsets.UTF_8)))
		{
			int id = 0;
			for (Line line : lines)
			{
				java.util.ArrayList<Integer> fwd = new java.util.ArrayList<>();
				for (Connection c : line.getConnectionPathForward()) fwd.add(c.getIndex());
				java.util.ArrayList<Integer> bwd = new java.util.ArrayList<>();
				for (Connection c : line.getConnectionPathBackward()) bwd.add(c.getIndex());
				StringBuilder st = new StringBuilder("[");
				List<Station> sts = new java.util.ArrayList<>();
				sts.add(line.getOrigin());
				for (Connection c : line.getConnectionPathForward()) sts.add(c.getTo());
				for (int i = 0; i < sts.size(); i++)
				{
					if (i > 0) st.append(",");
					st.append(q(sts.get(i).getName()));
				}
				st.append("]");
				w.println("{\"line_id\":" + id + ",\"origin\":" + q(line.getOrigin().getName())
						+ ",\"destination\":" + q(line.getDestination().getName())
						+ ",\"type\":" + line.getType() + ",\"duration\":" + num(line.getDuration())
						+ ",\"cost_per_frequency\":" + num(line.getCost())
						+ ",\"max_frequency\":" + line.getMaxFrequency()
						+ ",\"capacity\":" + line.getCapacity()
						+ ",\"stations\":" + st + ",\"connections_forward\":" + ints(fwd)
						+ ",\"connections_backward\":" + ints(bwd) + "}");
				id++;
			}
		}

		if (!routes) return;

		// ---- Official DPTN passenger routes (unfiltered, as in DPTNSolver)
		RouteGenerator rpGen = new RouteGenerator(instance, routeGenFile);
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("routes.jsonl"), StandardCharsets.UTF_8)))
		{
			for (ODPair od : instance.getODPairs())
			{
				List<DPTNRoute> rs = rpGen.getRoutes(od, false);
				Double shortest = rpGen.getShortestPathDurations().get(od);
				StringBuilder sb = new StringBuilder();
				sb.append("{\"origin\":").append(q(od.getOrigin().getName()))
						.append(",\"destination\":").append(q(od.getDestination().getName()))
						.append(",\"shortest\":").append(shortest == null ? "null" : num(shortest))
						.append(",\"routes\":[");
				boolean first = true;
				for (DPTNRoute r : rs)
				{
					java.util.ArrayList<Integer> conns = new java.util.ArrayList<>();
					java.util.ArrayList<Integer> transferAfter = new java.util.ArrayList<>();
					for (DPTNArc a : r.getArcs().stream().map(x -> (DPTNArc) x).toList())
					{
						if (a instanceof DPTNDrivingArc)
						{
							conns.add(((DPTNDrivingArc) a).getConnection().getIndex());
						}
						else if (a instanceof DPTNTransferArc)
						{
							transferAfter.add(conns.size());
						}
					}
					if (!first) sb.append(",");
					first = false;
					sb.append("{\"duration\":").append(num(r.getDuration()))
							.append(",\"transfers\":").append(r.getNrTransfers())
							.append(",\"connections\":").append(ints(conns))
							.append(",\"transfer_after\":").append(ints(transferAfter)).append("}");
				}
				sb.append("]}");
				w.println(sb);
			}
		}
	}
}
