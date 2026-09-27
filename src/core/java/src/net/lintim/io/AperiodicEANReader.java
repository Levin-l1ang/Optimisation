package net.lintim.io;

import net.lintim.exception.*;
import net.lintim.model.*;
import net.lintim.model.impl.SimpleMapGraph;
import net.lintim.util.Config;
import net.lintim.util.Pair;

/**
 * Class to read files of an aperiodic ean.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class AperiodicEANReader {
    private final boolean readEvents;
    private final boolean readActivities;
    private final boolean readSeparateTimetable;
    private final boolean readDispositionTimetable;
    private final String eventFileName;
    private final String activityFileName;
    private final String timetableFileName;
    private final Graph<AperiodicEvent, AperiodicActivity> aperiodicEAN;
    private final Timetable<AperiodicEvent> timetable;

    private AperiodicEANReader(Builder builder) {
        aperiodicEAN = builder.ean == null ? new SimpleMapGraph<>() : builder.ean;
        int timeUnitsPerMinute = builder.timeUnitsPerMinute == 0 ? builder.config.getIntegerValue
            ("time_units_per_minute") : builder.timeUnitsPerMinute;
        timetable = builder.timetable == null ? new Timetable<>(timeUnitsPerMinute) : builder.timetable;
        readEvents = builder.readEvents;
        if (readEvents) {
            eventFileName = "".equals(builder.eventFileName) ? builder.config.getStringValue
                ("default_events_expanded_file") : builder.eventFileName;
        }
        else {
            eventFileName = "";
        }
        readActivities = builder.readActivities;
        if (readActivities) {
            activityFileName = "".equals(builder.activityFileName) ? builder.config.getStringValue
                ("default_activities_expanded_file") : builder.activityFileName;
        }
        else {
            activityFileName = "";
        }
        readSeparateTimetable = builder.readSeparateTimetable;
        readDispositionTimetable = builder.readDispositionTimetable;
        if (readDispositionTimetable) {
            timetableFileName = "".equals(builder.timetableFileName) ? builder.config.getStringValue
                ("default_disposition_timetable_file") : builder.timetableFileName;
        }
        else if (readSeparateTimetable) {
            timetableFileName = "".equals(builder.timetableFileName) ? builder.config.getStringValue
                ("default_timetable_expanded_file") : builder.timetableFileName;
        }
        else {
            timetableFileName = "";
        }
    }

    /**
     * Start the reading process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     * @return the read ean and the timetable. Note, that the objects may be empty, dependent on the reader behavior
     * (i.e., the timetable will be empty, if no events should be read).
     */
    public Pair<Graph<AperiodicEvent, AperiodicActivity>, Timetable<AperiodicEvent>> read() {
        if (readEvents) {
            CsvReader.readCsv(eventFileName, this::processAperiodicEvent);
        }
        if (readActivities) {
            CsvReader.readCsv(activityFileName, this::processAperiodicActivity);
        }
        if (readSeparateTimetable || readDispositionTimetable) {
            CsvReader.readCsv(timetableFileName, this::processAperiodicTimetable);
        }
        return new Pair<>(aperiodicEAN, timetable);
    }

    /**
     * Process the content of an aperiodic event file.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException                 if the line contains not exactly 6 entries
     * @throws InputTypeInconsistencyException      if the specific types of the entries do not match the expectations
     * @throws DataIllegalEventTypeException        if the event type is not defined
     * @throws GraphNodeIdMultiplyAssignedException if the event cannot be added to the EAN
     */
    private void processAperiodicEvent(String args[], int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException, DataIllegalEventTypeException, GraphNodeIdMultiplyAssignedException {
        if (args.length != 6) {
            throw new InputFormatException(eventFileName, args.length, 6);
        }

        int eventId;
        int periodicEventId;
        EventType type;
        int time;
        double passengers;
        int stopId;

        try {
            eventId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(eventFileName, 1, lineNumber, "int", args[0]);
        }

        try {
            periodicEventId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(eventFileName, 2, lineNumber, "int", args[1]);
        }

        switch (args[2].toLowerCase()) {
            case "arrival":
            case "\"arrival\"":
                type = EventType.ARRIVAL;
                break;
            case "departure":
            case "\"departure\"":
                type = EventType.DEPARTURE;
                break;
            default:
                throw new DataIllegalEventTypeException(eventId, args[2]);

        }

        try {
            time = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(eventFileName, 4, lineNumber, "int", args[3]);
        }

        try {
            passengers = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(eventFileName, 5, lineNumber, "double", args[4]);
        }

        try {
            stopId = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(eventFileName, 6, lineNumber, "int", args[5]);
        }

        AperiodicEvent aperiodicEvent = new AperiodicEvent(eventId, periodicEventId, stopId, type, time, passengers);

        boolean addedEvent = aperiodicEAN.addNode(aperiodicEvent);

        if (!addedEvent) {
            throw new GraphNodeIdMultiplyAssignedException(eventId);
        }

        timetable.put(aperiodicEvent, (long) time);
    }

    /**
     * Process the content of a periodic activity file.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException                 if the line contains not exactly 8 entries
     * @throws InputTypeInconsistencyException      if the specific types of the entries do not match the expectations
     * @throws DataIllegalActivityTypeException     if the activity type is not defined
     * @throws GraphIncidentNodeNotFoundException   if an event incident to the activity is not found
     * @throws GraphEdgeIdMultiplyAssignedException if the activity cannot be added to the EAN
     */
    private void processAperiodicActivity(String args[], int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException, DataIllegalActivityTypeException, GraphIncidentNodeNotFoundException,
        GraphEdgeIdMultiplyAssignedException {
        if (args.length != 8) {
            throw new InputFormatException(activityFileName, args.length, 8);
        }

        int activityId;
        int periodicActivityId;
        ActivityType type;
        int sourceEventId;
        int targetEventId;
        int lowerBound;
        int upperBound;
        double passengers;

        try {
            activityId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(activityFileName, 1, lineNumber, "int", args[0]);
        }

        try {
            periodicActivityId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(activityFileName, 2, lineNumber, "int", args[1]);
        }

        switch (args[2].toLowerCase()) {
            case "drive":
            case "\"drive\"":
                type = ActivityType.DRIVE;
                break;
            case "wait":
            case "\"wait\"":
                type = ActivityType.WAIT;
                break;
            case "change":
            case "\"change\"":
                type = ActivityType.CHANGE;
                break;
            case "headway":
            case "\"headway\"":
                type = ActivityType.HEADWAY;
                break;
            case "turnaround":
            case "\"turnaround\"":
            case "fixed-turnaround":
            case "\"fixed-turnaround\"":
                type = ActivityType.TURNAROUND;
                break;
            default:
                throw new DataIllegalActivityTypeException(activityId, args[2]);
        }

        try {
            sourceEventId = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(activityFileName, 4, lineNumber, "int", args[3]);
        }

        try {
            targetEventId = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(activityFileName, 5, lineNumber, "int", args[4]);
        }

        try {
            lowerBound = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(activityFileName, 6, lineNumber, "int", args[5]);
        }

        try {
            upperBound = Integer.parseInt(args[6]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(activityFileName, 7, lineNumber, "int", args[6]);
        }

        try {
            passengers = Double.parseDouble(args[7]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(activityFileName, 8, lineNumber, "double", args[7]);
        }

        AperiodicEvent sourceEvent = aperiodicEAN.getNode(sourceEventId);

        if (sourceEvent == null) {
            throw new GraphIncidentNodeNotFoundException(activityId, sourceEventId);
        }

        AperiodicEvent targetEvent = aperiodicEAN.getNode(targetEventId);

        if (targetEvent == null) {
            throw new GraphIncidentNodeNotFoundException(activityId, targetEventId);
        }

        boolean activityAdded = aperiodicEAN.addEdge(new AperiodicActivity(activityId, periodicActivityId, type,
            sourceEvent, targetEvent, lowerBound, upperBound, passengers));

        if (!activityAdded) {
            throw new GraphEdgeIdMultiplyAssignedException(activityId);
        }

    }

    /**
     * Process the content of a timetable file.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException            if the line contains not exactly 2 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     * @throws DataIndexNotFoundException      if the event does not exist
     */
    private void processAperiodicTimetable(String args[], int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException, DataIndexNotFoundException {
        if (args.length != 2) {
            throw new InputFormatException(timetableFileName, args.length, 2);
        }

        int eventId;
        int time;

        try {
            eventId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(timetableFileName, 1, lineNumber, "int", args[0]);
        }

        try {
            time = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(timetableFileName, 2, lineNumber, "int", args[1]);
        }

        AperiodicEvent event = aperiodicEAN.getNode(eventId);

        if (event == null) {
            throw new DataIndexNotFoundException("Aperiodic event", eventId);
        }

        event.setTime(time);
        if (timetable != null) timetable.put(event, (long) time);
    }

    /**
     * Builder object for an aperiodic ean reader.
     *
     * Use {@link #Builder()} to create a builder with default options, afterwards use the setter to adapt it. The
     * setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder()}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private boolean readEvents = true;
        private boolean readActivities = true;
        private boolean readSeparateTimetable = false;
        private boolean readDispositionTimetable = false;
        private Config config = Config.getDefaultConfig();
        private String eventFileName = "";
        private String activityFileName = "";
        private String timetableFileName = "";
        private Graph<AperiodicEvent, AperiodicActivity> ean;
        private Timetable<AperiodicEvent> timetable;
        private int timeUnitsPerMinute = 0;


        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         read events (true) - whether to read events
         *     </li>
         *     <li>
         *         read activities (true) - whether to read activities
         *     </li>
         *     <li>
         *         read separate timetable (false) - whether to read a separate timetable file, overwriting the times in
         *         the events. This will only happen, if the disposition timetable should not be read!
         *     </li>
         *     <li>
         *          read disposition timetable (false) - whether to read the disposition timetable, overwriting the times
         *          in the events. This will only be used to determine the file to read, if no timetable file name is
         *          set.
         *     </li>
         *     <li>
         *         config ({@link Config#getDefaultConfig()}) - the config to read the file names from. This will only
         *         happen, if the file names are not given, but queried.
         *     </li>
         *     <li>
         *         event file name (dependent on config) - the file name to read the events from
         *     </li>
         *     <li>
         *         activity file name (dependent on config) - the file name to read the activities from
         *     </li>
         *     <li>
         *         timetable file name (dependent on config) - the file name to read the timetable from. When queried but
         *         not set, this will get the appropriate file name from the config, depend on whether to read the
         *         disposition timetable
         *     </li>
         *     <li>
         *         ean (Empty {@link SimpleMapGraph}) - the ean to store the read objects in.
         *     </li>
         *     <li>
         *         timetable (Empty {@link Timetable}) - the timetable to store the times in. The time units per minute
         *         to use for a new timetable (i.e. if none is set), can be set in this builder as well
         *     </li>
         *     <li>
         *         time units per minute (dependent on config) - the time units per minute of the timetable. Will be
         *         used to create a new timetable, if none is set.
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         */
        public Builder(){}

        /**
         * Set whether to read the events.
         * @param readEvents whether to read the events
         * @return this object
         */
        public Builder readEvents(boolean readEvents) {
            this.readEvents = readEvents;
            return this;
        }

        /**
         * Set whether to read the activities.
         * @param readActivities whether to read the activities
         * @return this object
         */
        public Builder readActivities(boolean readActivities) {
            this.readActivities = readActivities;
            return this;
        }

        /**
         * Set whether to read a separate timetable
         * @param readSeparateTimetable whether to read a separate timetable
         * @return this object
         */
        public Builder readSeparateTimetable(boolean readSeparateTimetable) {
            this.readSeparateTimetable = readSeparateTimetable;
            return this;
        }

        /**
         * Set whether to read a disposition timetable
         * @param readDispositionTimetable whether to read the disposition timetable
         * @return this object
         */
        public Builder readDispositionTimetable(boolean readDispositionTimetable) {
            this.readDispositionTimetable = readDispositionTimetable;
            return this;
        }

        /**
         * Set the config. The config is used to read file names that are queried but not given.
         * @param config the config
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Set the file name to read the events from.
         * @param eventFileName the event file name
         * @return this object
         */
        public Builder setEventFileName(String eventFileName) {
            this.eventFileName = eventFileName;
            return this;
        }

        /**
         * Set the filename to read the activities from.
         * @param activityFileName the activity file name
         * @return this object
         */
        public Builder setActivityFileName(String activityFileName) {
            this.activityFileName = activityFileName;
            return this;
        }

        /**
         * Set the filename to read the timetable from.
         * @param timetableFileName the timetable file name
         * @return this object
         */
        public Builder setTimetableFileName(String timetableFileName) {
            this.timetableFileName = timetableFileName;
            return this;
        }

        /**
         * Set the ean to store the data in.
         * @param ean the ean
         * @return this object
         */
        public Builder setEan(Graph<AperiodicEvent, AperiodicActivity> ean) {
            this.ean = ean;
            return this;
        }

        /**
         * Set the timetable to store the data in.
         * @param timetable the timetable
         * @return this object
         */
        public Builder setTimetable(Timetable<AperiodicEvent> timetable) {
            this.timetable = timetable;
            return this;
        }

        /**
         * Set the time units per minute. This will be used to create a new {@link Timetable}, if none was set in
         * this builder.
         * @param timeUnitsPerMinute the time units per minute
         * @return this object
         */
        public Builder setTimeUnitsPerMinute(int timeUnitsPerMinute) {
            this.timeUnitsPerMinute = timeUnitsPerMinute;
            return this;
        }

        /**
         * Create a new aperiodic ean reader with the current builder settings
         * @return the new reader. Use {@link #read()} for the reading process.
         */
        public AperiodicEANReader build() {
            return new AperiodicEANReader(this);
        }
    }
}
