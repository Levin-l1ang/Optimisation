package net.lintim.io.tools;

import net.lintim.exception.LinTimException;
import net.lintim.io.CsvWriter;
import net.lintim.model.*;
import net.lintim.util.LogLevel;
import net.lintim.util.tools.PeriodicEanHelper;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Output class for a periodic timetable in visum compatible format.
 */
public class VisumTimetableWriter {

	private static Logger logger = Logger.getLogger(VisumTimetableWriter.class.getCanonicalName());

	/**
	 * Write the periodic timetable represented in the given ean to the given file, using the given header. The
	 * line concept is needed for reference to the line frequencies
	 * @param ean the periodic ean. The contained events represent the written timetable
	 * @param lineConcept the line concept
	 * @param fileName the filename to write to
	 * @param header the header to use
	 * @throws IOException on io error
	 */
	public static void writeTimetable(Graph<PeriodicEvent, PeriodicActivity> ean, LinePool lineConcept, String fileName,
	                                  String header, LinePool fixedLines) throws IOException {
		CsvWriter writer = new CsvWriter(fileName, header);
		List<PeriodicEvent> events = new ArrayList<>(ean.getNodes());
		Set<Integer> fixedLineIds = fixedLines.getLines().stream().map(Line::getId).collect(Collectors.toSet());
		// Experimentally disable filtering of fixed lines
		//events = events.stream().filter(e -> !fixedLineIds.contains(e.getLineId())).collect(Collectors.toList());
		// Sort by line id and direction. First sort by line id, afterwards, sort forward direction to the front
		events.sort(Comparator.comparingInt(PeriodicEvent::getLineId).thenComparing(PeriodicEvent::getDirection)
				.thenComparing(PeriodicEvent::getLineFrequencyRepetition));
		logger.log(LogLevel.DEBUG, "Done sorting events, iterating");
		while (events.size() > 0) {
			PeriodicEvent event = events.get(0);
			// Always look for the forward direction first
			PeriodicEvent startEvent = PeriodicEanHelper.getStartEventOfLineWithFrequency(ean, event);
			if (startEvent == null) {
				throw new LinTimException("Could find the start of line " + event.getLineId() + event.getDirection());
			}
			// Process the line in the helper method
			processLine(startEvent, writer, ean, lineConcept);
			events = events.stream()
					.filter(periodicEvent -> !(periodicEvent.getLineId() == startEvent.getLineId()
							&& periodicEvent.getDirection() == startEvent.getDirection()
							&& periodicEvent.getLineFrequencyRepetition() == startEvent.getLineFrequencyRepetition()))
					.collect(Collectors.toList());
		}
		writer.close();
	}

	/**
	 * Process the line starting at the given event. Will write the visum compatible timetable to the given writer.
	 * The ean and the line concept are used as reference.
	 * @param startEvent the start of the line to write
	 * @param writer the writer to write to
	 * @param ean the used ean
	 * @param lineConcept the used line concept
	 * @throws IOException on io error
	 */
	private static void processLine(PeriodicEvent startEvent, CsvWriter writer, Graph<PeriodicEvent,
			PeriodicActivity> ean, LinePool lineConcept) throws IOException {
		ArrayList<Integer> times = new ArrayList<>();
		ArrayList<Integer> stopIds = new ArrayList<>();
		// We first need to add the arrival time at the first stop. By convention, this is equal to the departure time
		times.add(startEvent.getTime());
		times.add(startEvent.getTime());
		stopIds.add(startEvent.getStopId());
		PeriodicEvent currentEvent = startEvent;
		// Iterate the line, starting at startEvent. Store all results for later processing
		while (true) {
			PeriodicEvent searchEvent = currentEvent;
			PeriodicActivity outgoingActivity = ean.getEdge(periodicActivity -> periodicActivity.getLeftNode().equals
					(searchEvent) && (periodicActivity.getType() == ActivityType.DRIVE || periodicActivity.getType()
					== ActivityType.WAIT), true);
			if (outgoingActivity == null) {
				break;
			}
			currentEvent = outgoingActivity.getRightNode();
			if(outgoingActivity.getType() == ActivityType.DRIVE) {
				stopIds.add(currentEvent.getStopId());
			}
			times.add(currentEvent.getTime());
		}
		// We need to add the departure time for the last stop. By convention, this is equal to the arrival time
		times.add(currentEvent.getTime());
		// Now we have all times and can iterate them accordingly for output
		Iterator<Integer> timeIterator = times.iterator();
		Iterator<Integer> idIterator = stopIds.iterator();
		int stopIndex = 1;
		int lineId = startEvent.getLineId();
		boolean forward = startEvent.getDirection() == LineDirection.FORWARDS;
		String direction = forward ? ">" : "<";
		String lineCode = lineId + (forward ? "H" : "R");
		Line line = lineConcept.getLine(lineId);
		if (line == null) {
			throw new LinTimException("Inconsistent state, event " + startEvent + " has line " + lineId + " that is not present in the line concept!");
		}
		while (timeIterator.hasNext()) {
			writer.writeLine(
					String.valueOf(lineId),
					lineCode,
					direction,
					String.valueOf(stopIndex),
					String.valueOf(idIterator.next()),
					String.valueOf(line.getFrequency()),
					String.valueOf(timeIterator.next()),
					String.valueOf(timeIterator.next()),
					String.valueOf(startEvent.getLineFrequencyRepetition())
			);
			stopIndex += 1;
		}
	}
}
