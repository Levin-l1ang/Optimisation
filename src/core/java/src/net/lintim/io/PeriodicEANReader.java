package net.lintim.io;

import net.lintim.exception.*;
import net.lintim.model.*;
import net.lintim.model.impl.SimpleMapGraph;
import net.lintim.util.Config;
import net.lintim.util.Pair;

/**
 * Class to read files of a periodic ean.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class PeriodicEANReader {
    private final boolean readEvents;
    private final boolean readActivities;
    private final boolean readTimetable;
    private final String activityFileName;
    private final String eventFileName;
    private final String timetableFileName;
    private final Graph<PeriodicEvent, PeriodicActivity> ean;
    private final PeriodicTimetable<PeriodicEvent> timetable;

    private PeriodicEANReader(Builder builder) {
        this.readEvents = builder.readEvents;
        this.readActivities = builder.readActivities;
        this.readTimetable = builder.readTimetable;
        if (this.readEvents) {
            this.eventFileName = "".equals(builder.eventFileName) ?
                builder.config.getStringValue("default_events_periodic_file") : builder.eventFileName;
        }
        else {
            this.eventFileName = "";
        }
        if (this.readActivities) {
            this.activityFileName = "".equals(builder.activityFileName) ?
                builder.config.getStringValue("default_activities_periodic_file") : builder.activityFileName;
        }
        else {
            this.activityFileName = "";
        }
        if (this.readTimetable) {
            this.timetableFileName = "".equals(builder.timetableFileName) ?
                builder.config.getStringValue("default_timetable_periodic_file") : builder.timetableFileName;
        }
        else {
            this.timetableFileName = "";
        }
        this.ean = builder.ean == null ? new SimpleMapGraph<>() : builder.ean;
        if (builder.timetable == null && readTimetable) {
            int periodLength = builder.periodLength == 0 ? builder.config.getIntegerValue("period_length") :
                builder.periodLength;
            int timeUnitsPerMinute = builder.timeUnitsPerMinute == 0 ?
                builder.config.getIntegerValue("time_units_per_minute") : builder.timeUnitsPerMinute;
            this.timetable = new PeriodicTimetable<>(timeUnitsPerMinute, periodLength);
        }
        else {
            this.timetable = builder.timetable;
        }
    }

    /**
     * Start the reading process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     * @return the read ean and the timetable. Note, that the objects may be empty or null, dependent on the reader
     * behavior
     * (i.e., the timetable will be null, if no timetable should be read).
     */
    public Pair<Graph<PeriodicEvent, PeriodicActivity>, PeriodicTimetable<PeriodicEvent>> read() {
        if (readEvents) {
            CsvReader.readCsv(eventFileName, this::processPeriodicEvent);
        }
        if (readActivities) {
            CsvReader.readCsv(activityFileName, this::processPeriodicActivity);
        }
        if (readTimetable) {
            CsvReader.readCsv(timetableFileName, this::processPeriodicTimetable);
        }
        return new Pair<>(ean, timetable);
    }

    /**
     * Process the content of a periodic event file.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException                 if the line contains not exactly 5 entries
     * @throws InputTypeInconsistencyException      if the specific types of the entries do not match the expectations
     * @throws DataIllegalEventTypeException        if the event type is not defined
     * @throws GraphNodeIdMultiplyAssignedException if the event cannot be added to the EAN
     */
    private void processPeriodicEvent(String args[], int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException, DataIllegalEventTypeException, GraphNodeIdMultiplyAssignedException {
        if (args.length != 7) {
            throw new InputFormatException(eventFileName, args.length, 7);
        }

        int eventId;
        EventType type;
        int stopId;
        int lineId;
        double passengers;
        LineDirection direction;
        int lineFrequencyRepetition;

        try {
            eventId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(eventFileName, 1, lineNumber, "int", args[0]);
        }

        switch (args[1].toLowerCase()){
            case "arrival":
            case "\"arrival\"":
                type= EventType.ARRIVAL;
                break;
            case "departure":
            case "\"departure\"":
                type = EventType.DEPARTURE;
                break;
            case "fix":
            case "\"fix\"":
                type = EventType.FIX;
                break;
            case "virtual":
            case "\"virtual\"":
                type = EventType.VIRTUAL;
                break;
            default:
                throw new DataIllegalEventTypeException(eventId, args[1]);

        }

        try {
            stopId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(eventFileName, 3, lineNumber, "int", args[2]);
        }

        try {
            lineId = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(eventFileName, 4, lineNumber, "int", args[3]);
        }

        try {
            passengers = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(eventFileName, 5, lineNumber, "double", args[4]);
        }

        switch (args[5]){
            case ">":
                direction = LineDirection.FORWARDS;
                break;
            case "<":
                direction = LineDirection.BACKWARDS;
                break;
            default:
                throw new DataIllegalLineDirectionException(eventId, args[5]);
        }

        try {
            lineFrequencyRepetition = Integer.parseInt(args[6]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(eventFileName, 7, lineNumber, "int", args[6]);
        }

        PeriodicEvent periodicEvent = new PeriodicEvent(eventId, stopId, type, lineId, 0, passengers, direction, lineFrequencyRepetition);

        boolean addedEvent = ean.addNode(periodicEvent);

        if (!addedEvent) {
            throw new GraphNodeIdMultiplyAssignedException(eventId);
        }
    }

    /**
     * Process the content of a periodic activity file.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException                 if the line contains not exactly 7 entries
     * @throws InputTypeInconsistencyException      if the specific types of the entries do not match the expectations
     * @throws DataIllegalActivityTypeException     if the activity type is not defined
     * @throws GraphIncidentNodeNotFoundException   if an event incident to the activity is not found
     * @throws GraphEdgeIdMultiplyAssignedException if the activity cannot be added to the EAN
     */
    private void processPeriodicActivity(String args[], int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException, DataIllegalActivityTypeException, GraphIncidentNodeNotFoundException,
        GraphEdgeIdMultiplyAssignedException {
        if (args.length != 7) {
            throw new InputFormatException(activityFileName, args.length, 7);
        }

        int activityId;
        ActivityType type;
        int sourceEventId;
        int targetEventId;
        double lowerBound;
        double upperBound;
        double passengers;

        try {
            activityId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(activityFileName, 1, lineNumber, "int", args[0]);
        }

        switch (args[1].toLowerCase()) {
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
                type = ActivityType.TURNAROUND;
                break;
            case "sync":
            case "\"sync\"":
                type = ActivityType.SYNC;
                break;
            case "virtual":
            case "\"virtual\"":
                type = ActivityType.VIRTUAL;
                break;
            case "nonscheduled":
            case "\"nonscheduled\"":
                type = ActivityType.NONSCHEDULED;
                break;
            default:
                throw new DataIllegalActivityTypeException(activityId, args[1]);
        }

        try {
            sourceEventId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(activityFileName, 3, lineNumber, "int", args[2]);
        }

        try {
            targetEventId = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(activityFileName, 4, lineNumber, "int", args[3]);
        }

        try {
            lowerBound = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(activityFileName, 5, lineNumber, "double", args[4]);
        }

        try {
            upperBound = Double.parseDouble(args[5]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(activityFileName, 6, lineNumber, "double", args[5]);
        }

        try {
            passengers = Double.parseDouble(args[6]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(activityFileName, 7, lineNumber, "double", args[6]);
        }

        PeriodicEvent sourceEvent = ean.getNode(sourceEventId);

        if (sourceEvent == null) {
            throw new GraphIncidentNodeNotFoundException(activityId, sourceEventId);
        }

        PeriodicEvent targetEvent = ean.getNode(targetEventId);

        if (targetEvent == null) {
            throw new GraphIncidentNodeNotFoundException(activityId, targetEventId);
        }

        boolean activityAdded = ean.addEdge(new PeriodicActivity(activityId, type, sourceEvent, targetEvent,
            lowerBound, upperBound, passengers));

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
    private void processPeriodicTimetable(String args[], int lineNumber) throws InputFormatException,
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

        PeriodicEvent event = ean.getNode(eventId);

        if (event == null) {
            throw new DataIndexNotFoundException("Periodic event", eventId);
        }

        event.setTime(time);
        if (timetable != null) timetable.put(event, (long) time);
    }

    /**
     * Builder object for a periodic ean reader.
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
        private boolean readTimetable = false;
        private String eventFileName = "";
        private String activityFileName = "";
        private String timetableFileName = "";
        private Config config = Config.getDefaultConfig();
        private Graph<PeriodicEvent, PeriodicActivity> ean;
        private PeriodicTimetable<PeriodicEvent> timetable;
        private int timeUnitsPerMinute = 0;
        private int periodLength = 0;

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
         *         read timetable (false) - whether to read a timetable file
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
         *         timetable file name (dependent on config) - the file name to read the timetable from
         *     </li>
         *     <li>
         *         ean (Empty {@link SimpleMapGraph}) - the ean to store the events in.
         *     </li>
         *     <li>
         *         timetable (Empty {@link PeriodicTimetable}) - the timetable to store the times in. The time units per
         *         minute and the period length to use for a new timetable (i.e. if none is set), can be set in this
         *         builder as well.
         *     </li>
         *     <li>
         *         time units per minute (dependent on config) - the time units per minute of the timetable. Will be
         *         used to create a new timetable, if none is set.
         *     </li>
         *     <li>
         *         period length (dependent on config) - the period length of the timetable. Will be used to create
         *         a new timetable if none is set.
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         */
        public Builder() { }

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
         * Set whether to read the timetable
         * @param readTimetable whether to read the timetable
         * @return this object
         */
        public Builder readTimetable(boolean readTimetable) {
            this.readTimetable = readTimetable;
            return this;
        }

        /**
         * Set the file name to read the events from
         * @param eventFileName the event file name
         * @return this object
         */
        public Builder setEventFileName(String eventFileName) {
            this.eventFileName = eventFileName;
            return this;
        }

        /**
         * Set the file name to read the activities from
         * @param activityFileName the activity file name
         * @return this object
         */
        public Builder setActivityFileName(String activityFileName) {
            this.activityFileName = activityFileName;
            return this;
        }

        /**
         * Set the file name to read the timetable from
         * @param timetableFileName the timetable file name
         * @return this object
         */
        public Builder setTimetableFileName(String timetableFileName) {
            this.timetableFileName = timetableFileName;
            return this;
        }

        /**
         * Set the config. The config is used to read file names that are queried but not given
         * @param config the config
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Set the ean to store the data in
         * @param ean the ean
         * @return this object
         */
        public Builder setEan(Graph<PeriodicEvent, PeriodicActivity> ean) {
            this.ean = ean;
            return this;
        }

        /**
         * Set the timetable to store the times of the events in. Will only happen if the timetable should be read
         * @param timetable the timetable
         * @return this object
         */
        public Builder setTimetable(PeriodicTimetable<PeriodicEvent> timetable) {
            this.timetable = timetable;
            return this;
        }

        /**
         * Set the time units per minute. This will be used to create a new {@link PeriodicTimetable}, if none was set in
         * this builder.
         * @param timeUnitsPerMinute the time units per minute
         * @return this object
         */
        public Builder setTimeUnitsPerMinute(int timeUnitsPerMinute) {
            this.timeUnitsPerMinute = timeUnitsPerMinute;
            return this;
        }

        /**
         * Set the period length. This will be used to create a new {@link PeriodicTimetable}, if none was set in
         * this builder.
         * @param periodLength the time units per minute
         * @return this object
         */
        public Builder setPeriodLength(int periodLength) {
            this.periodLength = periodLength;
            return this;
        }

        /**
         * Create a new periodic ean reader with the current builder settings
         * @return the new reader. Use {@link PeriodicEANReader#read()} for the reading process.
         */
        public PeriodicEANReader build() {
            return new PeriodicEANReader(this);
        }
    }

}
