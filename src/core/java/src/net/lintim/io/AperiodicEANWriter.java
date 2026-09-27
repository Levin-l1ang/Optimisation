package net.lintim.io;

import net.lintim.model.AperiodicActivity;
import net.lintim.model.AperiodicEvent;
import net.lintim.model.Graph;
import net.lintim.model.Timetable;
import net.lintim.util.Config;

import java.util.Comparator;
import java.util.function.Function;

/**
 * Class to write files of an aperiodic ean.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the writer and use {@link #write()} afterwards.
 */
public class AperiodicEANWriter {
    private final boolean writeEvents;
    private final boolean writeActivities;
    private final boolean writeTimetable;
    private final String eventFileName;
    private final String activityFileName;
    private final String timetableFileName;
    private final String eventHeader;
    private final String activityHeader;
    private final String timetableHeader;
    private final Graph<AperiodicEvent, AperiodicActivity> ean;
    private final Timetable<AperiodicEvent> timetable;

    private AperiodicEANWriter(Builder builder) {
        ean = builder.ean;
        timetable = builder.timetable;
        writeEvents = builder.writeEvents;
        if (writeEvents) {
            eventFileName = "".equals(builder.eventFileName) ? builder.config.getStringValue
                ("default_events_expanded_file") : builder.eventFileName;
            eventHeader = "".equals(builder.eventHeader) ? builder.config.getStringValue("events_header") : builder
                .eventHeader;
        }
        else {
            eventFileName = "";
            eventHeader = "";
        }
        writeActivities = builder.writeActivities;
        if (writeActivities) {
            activityFileName = "".equals(builder.activityFileName) ? builder.config.getStringValue
                ("default_activities_expanded_file") : builder.activityFileName;
            activityHeader = "".equals(builder.activityHeader) ? builder.config.getStringValue("activities_header") :
                builder.activityHeader;
        }
        else {
            activityFileName = "";
            activityHeader = "";
        }
        writeTimetable = builder.writeTimetable || builder.writeDispositionTimetable;
        boolean writeDispositionTimetable = builder.writeDispositionTimetable;
        if (writeDispositionTimetable) {
            timetableFileName = "".equals(builder.timetableFileName) ? builder.config.getStringValue
                ("default_disposition_timetable_file") : builder.timetableFileName;
            timetableHeader = "".equals(builder.timetableHeader) ? builder.config.getStringValue
                ("timetable_header_disposition") : builder.timetableHeader;
        }
        else if (writeTimetable) {
            timetableFileName = "".equals(builder.timetableFileName) ? builder.config.getStringValue
                ("default_timetable_expanded_file") : builder.timetableFileName;
            timetableHeader = "".equals(builder.timetableHeader) ? builder.config.getStringValue("timetable_header")
                : builder.timetableHeader;
        }
        else {
            timetableFileName = "";
            timetableHeader = "";
        }

    }

    /**
     * Start the writing process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     */
    public void write() {
        if (writeEvents) {
            CsvWriter.writeCollection(
                eventFileName,
                eventHeader,
                ean.getNodes(),
                AperiodicEvent::toCsvStrings,
                Comparator.comparingInt(AperiodicEvent::getId)
            );
        }
        if (writeActivities) {
            CsvWriter.writeCollection(
                activityFileName,
                activityHeader,
                ean.getEdges(),
                AperiodicActivity::toCsvStrings,
                Comparator.comparingInt(AperiodicActivity::getId)
            );
        }
        if (writeTimetable) {
            Function<AperiodicEvent, String[]> outputFunction = AperiodicEvent::toTimetableCsvStrings;
            if (timetable != null) {
                outputFunction = e -> e.toTimetableCsvStrings(timetable);
            }
            CsvWriter.writeCollection(
                timetableFileName,
                timetableHeader,
                ean.getNodes(),
                outputFunction,
                Comparator.comparingInt(AperiodicEvent::getId)
            );
        }
    }

    /**
     * Set the time members in the aperiodic events according to the values mapped to by the given Timetable object
     *
     * @param ean           the ean in which the event times are to be updated
     * @param timetable     the timetable object from which the updated times shall be taken
     */
    public static void setEventTimesFromTimetable(Graph<AperiodicEvent, AperiodicActivity> ean,
                                                  Timetable<AperiodicEvent> timetable) {
        ean.getNodes().forEach(event -> event.setTime(timetable.getOrDefault(event, (long) event.getTime()).intValue()));
    }

    /**
     * Builder object for an aperiodic ean writer.
     *
     * Use {@link #Builder(Graph)} to create a builder with default options, afterwards use the setter to adapt it.
     * The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder(Graph)}. To create a writer object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private boolean writeEvents = true;
        private boolean writeActivities = false;
        private boolean writeTimetable = false;
        private boolean writeDispositionTimetable = false;
        private Config config = Config.getDefaultConfig();
        private String eventFileName = "";
        private String activityFileName = "";
        private String timetableFileName = "";
        private String eventHeader = "";
        private String activityHeader = "";
        private String timetableHeader = "";
        private Graph<AperiodicEvent, AperiodicActivity> ean;
        private Timetable<AperiodicEvent> timetable;

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         write events (true) - whether to write the events
         *     </li>
         *     <li>
         *         write activities (false) - whether to write the activities
         *     </li>
         *     <li>
         *         write timetable (false) - whether to write the timetable to a separate file
         *     </li>
         *     <li>
         *          write disposition timetable (false) - whether to write the timetable to the disposition timetable file
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()}) - the config to read the file names and headers from.
         *          This will only happen, if the file names or headers are not given, but queried.
         *     </li>
         *     <li>
         *          event file name (dependent on config) - the file name to write the events to
         *     </li>
         *     <li>
         *          activity file name (dependent on config) - the file name to write the activities to
         *     </li>
         *     <li>
         *          timetable file name (dependent on config) - the file name to write the separate timetable to. Note
         *          that this will result in the disposition timetable file name if a disposition timetable should be
         *          written and no file name is set beforehand
         *     </li>
         *     <li>
         *          event header (dependent on config) - the header to use for the event file
         *     </li>
         *     <li>
         *          activities header (dependent on config) - the header to use for the activity file
         *     </li>
         *     <li>
         *          timetable header (dependent on config) - the header to use for the separate timetable file. Note that
         *          this will result in the header for the disposition timetable if a disposition timetable should be
         *          written and no header is set beforehand
         *     </li>
         *     <li>
         *          ean (given in constructor) - the ean to write. Can only be set in the constructor
         *     </li>
         *     <li>
         *          timetable (null) - the timetable to write. If no timetable is given, it should be assumed that the
         *          times in the events are correct and should be used for writing.
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a writer with the given parameters.
         * @param ean the ean to write
         */
        public Builder(Graph<AperiodicEvent, AperiodicActivity> ean) {
            if (ean == null) {
                throw new IllegalArgumentException("The ean in the aperiodic ean writer builder can not be null");
            }
            this.ean = ean;
        }

        /**
         * Set whether to write the events.
         * @param writeEvents whether to write the events
         * @return this object
         */
        public Builder writeEvents(boolean writeEvents) {
            this.writeEvents = writeEvents;
            return this;
        }

        /**
         * Set whether to write the activities.
         * @param writeActivities whether to write the activities
         * @return this object
         */
        public Builder writeActivities(boolean writeActivities) {
            this.writeActivities = writeActivities;
            return this;
        }

        /**
         * Set whether to write the timetable to a separate file.
         * @param writeTimetable whether to write the timetable
         * @return this object
         */
        public Builder writeTimetable(boolean writeTimetable) {
            this.writeTimetable = writeTimetable;
            return this;
        }

        /**
         * Set whether to write the timetable to a separate disposition timetable file.
         * @param writeDispositionTimetable whether to write the disposition timetable
         * @return this object
         */
        public Builder writeDispositionTimetable(boolean writeDispositionTimetable) {
            this.writeDispositionTimetable = writeDispositionTimetable;
            return this;
        }

        /**
         * Set the config. The config is used to read file names or headers that are queried but not given.
         * @param config the config
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Set the event file name.
         * @param eventFileName the file to write the events to
         * @return this object
         */
        public Builder setEventFileName(String eventFileName) {
            this.eventFileName = eventFileName;
            return this;
        }

        /**
         * Set the activity file name
         * @param activityFileName the file to write the activities to
         * @return this object
         */
        public Builder setActivityFileName(String activityFileName) {
            this.activityFileName = activityFileName;
            return this;
        }

        /**
         * Set the timetable file name
         * @param timetableFileName the file to write the timetable to
         * @return this object
         */
        public Builder setTimetableFileName(String timetableFileName) {
            this.timetableFileName = timetableFileName;
            return this;
        }

        /**
         * Set the header for the event file
         * @param eventHeader the event header
         * @return this object
         */
        public Builder setEventHeader(String eventHeader) {
            this.eventHeader = eventHeader;
            return this;
        }

        /**
         * Set the header for the activity file
         * @param activityHeader the activity header
         * @return this object
         */
        public Builder setActivityHeader(String activityHeader) {
            this.activityHeader = activityHeader;
            return this;
        }

        /**
         * Set the header for the timetable file
         * @param timetableHeader the timetable header
         * @return this object
         */
        public Builder setTimetableHeader(String timetableHeader) {
            this.timetableHeader = timetableHeader;
            return this;
        }

        /**
         * Set the timetable for the ean. If a timetable is set, the times from the timetable should be used instead of
         * the times in the events
         * @param timetable the timetable
         * @return this object
         */
        public Builder setTimetable(Timetable<AperiodicEvent> timetable) {
            this.timetable = timetable;
            return this;
        }

        /**
         * Create a new aperiodic ean writer with the current builder settings
         * @return the new writer. Use {@link #write()} for the writing process.
         */
        public AperiodicEANWriter build() {
            return new AperiodicEANWriter(this);
        }
    }
}
