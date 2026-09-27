The class Rollout can be used to turn a periodic event-activity network into a
time-expanded (non-periodic) event-activity network.




PARAMETERS that can be defined in a config file:
==========


* periodicEvents: file containing the periodic events. Default (if not set): timetabling/Events-periodic.giv
* periodicActivities: file containing the periodic activities. Default (if not set): timetabling/Activities-periodic.giv
* periodicTimetable: file containing the periodic timetable. Default (if not set): timetabling/Timetable-periodic.tim
* earliestTime: earliest time when time-expanded events may take place. Default (if not set): 0 (meaning 00:00)
* latestTime: latest time when time-expanded events may take place. Default (if not set): 86400 (meaning 24:00).
* period: The global period (in seconds). Default (if not set): 3600 (meaning one hour)
* timeExpandedEvents: output file for the time-expanded events. Default (if not set): Delay_Management/Events-expanded.giv
* timeExpandedActivities: output file for the time-expanded events. Default (if not set): Delay_Management/Activities-expanded.giv




The following ASSUMPTIONS ON THE INPUT FILES are made:
              ==============================


periodicEvents: * one event per line
                * format of each line: event_id; type; stop-id; line-id; passengers
                  with
                  + event_id: an integer value
                  + type: "arrival" or "departure"
                  + stop-id: (ignored)
                  + line-id: (ignored)
                  + passengers: a double value
                * IDs are continous, starting with 1

timeExpandedActivities: * one activity per line
                        * format of each line: activity-id; type; tail-event-id; head-event-id; lower-bound; upper-bound; passengers
                          with
                          + activity-id: an integer value
                          + type: "change", "drive", "headway", "turn" or "wait"
                          + tail-event-id: an integer value corresponding to a valid event ID
                          + head-event-id: an integer value corresponding to a valid event ID
                          + lower bound: an integer value
                          + upper-bound: (ignored)
                          + passengers: a double value
                        * IDs are continous, starting with 1

periodicTimetable: * one time of one event per line
                   * format of each line: event-id; periodic_time
                     with
                     event-id: an integer value corresponding to a valid event ID
                     periodic_time: an integer value
                   * if more than one periodic time is assigned to one periodic event (if its frequency is > 1), one line for each periodic time is required




FORMAT OF OUTPUT FILES:
=======================


timeExpandedEvents: * one event per line
                    * format of each line: event-id; periodic-id; type; time; passengers
                      with
                      + event-id: an integer value
                      + periodic-id: an integer value corresponding to a valid (periodic) event ID
                      + type: "arrival" or "departure"
                      + time: an integer value
                      + passengers: a double value
                    * IDs are continous, starting with 1

timeExpandedActivities: * one activity per line
                        * format of each line: activity-id; periodic-id; type; tail-event-id; head-event-id; lower-bound; passengers
                          with
                          + activity-id: an integer value
                          + periodic-id: an integer value corresponding to a valid (periodic) activity ID
                          + type: "change", "drive", "headway", "turn" or "wait"
                          + tail-event-id: an integer value corresponding to a valid event ID
                          + head-event-id: an integer value corresponding to a valid event ID
                          + lower bound: an integer value
                          + passengers: a double value
                        * IDs are continous, starting with 1
