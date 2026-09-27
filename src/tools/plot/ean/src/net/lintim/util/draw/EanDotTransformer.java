package net.lintim.util.draw;

import net.lintim.exception.LinTimException;
import net.lintim.exception.OutputFileException;
import net.lintim.model.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;

/**
 * Transform an event activity network to a .dot file. Use {@link #writePeriodicDotFile(Graph, boolean, int, String)}
 * for periodic and {@link #writeAperiodicDotFile(Graph, Timetable, String)} for aperiodic eans.
 */
public class EanDotTransformer {

	private static DecimalFormat decimalFormat = new DecimalFormat("#.##");

	/**
	 * Write a periodic ean in .dot format, with or without the times.
	 * @param ean the ean to write
	 * @param writeTimes whether to include the times of the events
	 * @param periodLength the length of a period
	 * @param dotFileName the file to write
	 */
	public static void writePeriodicDotFile(Graph<PeriodicEvent, PeriodicActivity> ean, boolean writeTimes, int
											periodLength,  String dotFileName) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(dotFileName));
			writer.write("digraph PeriodicEventActivityNetwork {\nrankdir=\"LR\"\nnode [shape=record,style=rounded];\n");

			for (PeriodicEvent event : ean.getNodes()) {
				writer.write(convertPeriodicEvent(event, writeTimes));
			}
			for (PeriodicActivity activity : ean.getEdges()) {
				if (activity.getType() == ActivityType.CHANGE && activity.getNumberOfPassengers() == 0) {
					continue;
				}
				writer.write(convertPeriodicActivity(activity, writeTimes, periodLength));
			}
			writer.write("}");
			writer.close();
		} catch (IOException e) {
			throw new OutputFileException(dotFileName);
		}
	}

	/**
	 * Write an aperiodic ean in .dot format, with or without the times.
	 * @param ean the ean to write
	 * @param dispositionTimetable the dispo timetable to write. If null, the times in the events are used
	 * @param dotFileName the file to write
	 */
	public static void writeAperiodicDotFile(Graph<AperiodicEvent, AperiodicActivity> ean, Timetable<AperiodicEvent>
											 dispositionTimetable, String dotFileName) {
		long maxDelay = 0;
		if (dispositionTimetable != null) {
			for (AperiodicEvent event : ean.getNodes()) {
				long currentDelay = dispositionTimetable.get(event) - event.getTime();
				if (currentDelay > maxDelay) {
					maxDelay = currentDelay;
				}
			}
		}
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(dotFileName));
			writer.write("digraph AperiodicEventActivityNetwork {\nrankdir=\"LR\"\nnode [shape=record,style=rounded];\n");

			for (AperiodicEvent event : ean.getNodes()) {
				writer.write(convertAperiodicEvent(event, dispositionTimetable, maxDelay));
			}
			for (AperiodicActivity activity : ean.getEdges()) {
				if (activity.getType() == ActivityType.CHANGE && activity.getNumberOfPassengers() == 0) {
					continue;
				}
				writer.write(convertAperiodicActivity(activity, dispositionTimetable));
			}
			writer.write("}");
			writer.close();
		} catch (IOException e) {
			throw new OutputFileException(dotFileName);
		}
	}

	private static String convertPeriodicEvent(PeriodicEvent event, boolean writeTimes) {
		return "node [label=\""
				+ event.getId()
				+ ", "
				+ convertEventType(event.getType())
				+ " |{S " + event.getStopId()
				+ " |L " + event.getLineId() + "  }| "
				+ (writeTimes ? ("  @ " + event.getTime()) : "")
				+ "\"] "
				+ event.getId() + "\n";
	}

	private static String convertPeriodicActivity(PeriodicActivity activity, boolean writeTimes, int periodLength) {
		return activity.getLeftNode().getId()
				+ " -> "
				+ activity.getRightNode().getId()
				+ " [label=<<table border=\"0\"><tr><td>"
				+ activity.getId()
				+ ", "
				+ convertActivityType(activity.getType())
				+ "</td></tr><tr><td>"
				+ (writeTimes ? activity.getDuration(periodLength) + " &isin; " : "")
				+ "["
				+ activity.getLowerBound() + "; "
				+ activity.getUpperBound() + "]"
				+ "</td></tr><tr><td>"
				+ decimalFormat.format(activity.getNumberOfPassengers())
				+ "</td></tr></table>>]\n";
	}

	private static String convertAperiodicEvent(AperiodicEvent event, Timetable<AperiodicEvent> dispositionTimetable,
	                                            long maxDelay) {
		boolean readDispoTimes = dispositionTimetable != null;
		String timeString = " @ " + event.getTime();
		long currentDelay = 0;
		int delayRatioOutputValue = 0;
		if (readDispoTimes) {
			currentDelay = dispositionTimetable.get(event) - event.getTime();
			delayRatioOutputValue = (int) Math.round((((double) currentDelay) / maxDelay) * 255);
			if (currentDelay > 0) {
				timeString += " (+" + currentDelay + ")";
			}
		}
		return "node [label=\""
				+ event.getId()
				+ ", "
				+ convertEventType(event.getType())
				+ " |{S " + event.getStopId()
				+ " |P " + event.getPeriodicEventId() + "  }| "
				+ timeString
				+ "\""
				+ (currentDelay > 0 ? ", style=\"filled, rounded\", fillcolor=\"#FF0000 " + String.format("%02X",
							delayRatioOutputValue)
				+ "\"" : ", " +
				"style=rounded")
				+ "] "
				+ event.getId() + "\n";
	}

	private static String convertAperiodicActivity(AperiodicActivity activity, Timetable<AperiodicEvent>
												   dispositionTimetable) {
		return activity.getLeftNode().getId()
				+ " -> "
				+ activity.getRightNode().getId()
				+ " [label=<<table border=\"0\"><tr><td>"
				+ activity.getId()
				+ ", "
				+ convertActivityType(activity.getType())
				+ "</td></tr><tr><td>"
				+ (activity.getRightNode().getTime() - activity.getLeftNode().getTime()) + " &isin; "
				+ "["
				+ activity.getLowerBound() + "; "
				+ activity.getUpperBound() + "]"
				+ "</td></tr><tr><td>"
				+ decimalFormat.format(activity.getNumberOfPassengers())
				+ ", P" + activity.getPeriodicActivityId()
				+ "</td></tr></table>>]\n";
	}

	private static String convertEventType(EventType type) {
		switch (type) {
			case ARRIVAL:
				return "arr";
			case DEPARTURE:
				return "dep";
			case VIRTUAL:
				return "vir";
			default:
				throw new LinTimException("Ean draw cannot work with event type " + type);
		}
	}

	private static String convertActivityType(ActivityType type) {
		// Remove leading and trailing quotes, if there are any
		return type.toString().replaceAll("^\"|\"$", "");
	}
}
