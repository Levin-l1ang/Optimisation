import logging
import math
import od_data
import ptn_data
import line_data
from typing import List, Union
from typing import Dict

from core.model.ptn import Stop, Link
from core.util.constants import SECONDS_PER_MINUTE
from parameters import PeriodicEanParameters

logger = logging.getLogger(__name__)


class Ean:

    def __init__(self, ptn: ptn_data.Ptn = None, line_pool: line_data.LinePool = None, od: "od_data.OD" = None,
                 ean_parameters: PeriodicEanParameters = None, multiple_frequencies: bool = False) -> None:
        self.events = []
        self.first_events_in_line = {}
        self.last_events_in_line = {}
        if ptn is not None and line_pool is not None and od is not None and ean_parameters is not None:
            self.min_wait_time = ean_parameters.min_wait_time
            self.max_wait_time = ean_parameters.max_wait_time
            self.min_transfer_time = ean_parameters.min_trans_time
            self.max_transfer_time = ean_parameters.max_trans_time
            self.init_ptn(ptn, line_pool)
            self.init_od(ptn, od)
            if multiple_frequencies:
                self.init_sync(ean_parameters.period_length)

    def add_event(self, event: "EanEvent") -> None:
        if not self.events.__contains__(event):
            self.events.append(event)

    def get_all_events(self) -> List["EanEvent"]:
        return self.events

    def get_events_network(self) -> List["EanEventNetwork"]:
        events = []
        for event in self.events:
            if isinstance(event, EanEventNetwork):
                events.append(event)
        return events

    def get_events_od(self) -> List["EanEventOD"]:
        events = []
        for event in self.events:
            if isinstance(event, EanEventOD):
                events.append(event)
        return events

    def get_events(self, event_type_list: list) -> List["EanEvent"]:
        events = []
        for event in self.events:
            if event.get_event_type() in event_type_list:
                events.append(event)
        return events

    def get_activities(self, activity_type_list: list) -> List["EanActivity"]:
        activities = []
        for event in self.events:
            for activity in event.get_outgoing_activities():
                if activity.get_activity_type() in activity_type_list and activity not in activities:
                    activities.append(activity)
        return activities

    def get_all_activities(self) -> List["EanActivity"]:
        activities = []
        for event in self.events:
            for activity in event.get_outgoing_activities():
                if activity not in activities:
                    activities.append(activity)
        return activities

    def get_activities_in_line(self, line: line_data.Line) -> List["EanActivity"]:
        activities = []
        for event in self.events:
            if isinstance(event, EanEventNetwork) and event.get_line() == line:
                for activity in event.get_outgoing_activities():
                    if isinstance(activity.get_right_event(),
                                  EanEventNetwork) and activity.get_right_event().get_line() == line:
                        activities.append(activity)
        return activities

    def get_event_network(self, ptn_node: Stop, line: line_data.Line, event_type: str) -> "EanEventNetwork":
        for event in self.events:
            if isinstance(event, EanEventNetwork) and event.check_attributes(ptn_node, line, event_type):
                return event

    def get_event_od(self, start: Stop, end: Stop, od_event_type: str, time_1: int,
                     time_2: int) -> "EanEventOD":
        for event in self.events:
            if isinstance(event, EanEventOD) and event.check_attributes(start, end, od_event_type, time_1, time_2):
                return event

    def get_events_to_ptn_node(self, ptn_node: Stop, event_type: str) -> List["EanEventNetwork"]:
        events = []
        for event in self.events:
            if isinstance(event, EanEventNetwork) and event.get_ptn_node().getId() == ptn_node.getId() \
                    and event.get_event_type() == event_type:
                events.append(event)
        return events

    def get_dict_events_to_ptn_nodes(self, ptn: ptn_data.Ptn, event_type: str) -> Dict[Stop, List["EanEventNetwork"]]:
        events_to_ptn_node = {}
        for ptn_node in ptn.get_nodes():
            events_to_ptn_node[ptn_node] = []
        for event in self.events:
            if isinstance(event, EanEventNetwork) and event.get_event_type() == event_type:
                events_to_ptn_node[event.get_ptn_node()].append(event)
        return events_to_ptn_node

    def get_event_by_event_id(self, event_id: int) -> "EanEvent":
        for event in self.events:
            if event.get_event_id() == event_id:
                return event
        return None

    def get_first_events_in_line(self) -> Dict[int, "EanEventNetwork"]:
        return self.first_events_in_line

    def get_last_events_in_line(self) -> Dict[int, "EanEventNetwork"]:
        return self.last_events_in_line

    def get_first_event_in_line(self, line) -> "EanEventNetwork":
        return self.first_events_in_line[line]

    def get_last_event_in_line(self, line) -> "EanEventNetwork":
        return self.last_events_in_line[line]

    def init_ptn(self, ptn: ptn_data.Ptn, line_pool: line_data.LinePool) -> None:
        for line in line_pool.get_lines():
            first = True
            for edge in line.get_edges():
                left_node = edge.getLeftNode()
                right_node = edge.getRightNode()
                if first:
                    # First Wait-Activity
                    departure = EanEventNetwork(self, left_node, line, 'dep')
                    self.first_events_in_line[line] = departure
                    first = False
                # Drive-Activity
                arrival = EanEventNetwork(self, right_node, line, 'arr')
                EanActivity(departure, arrival, 'drive', edge.getLowerBound(), edge.getUpperBound())
                self.last_events_in_line[line] = arrival
                # Wait-Activity
                if right_node != line.get_last_stop():
                    departure = EanEventNetwork(self, right_node, line, 'dep')
                    EanActivity(arrival, departure, 'wait', self.min_wait_time,
                                self.max_wait_time)
                    # self.last_events_in_line[line.get_directed_line_id()] = departure
        # Change-Activities
        arr_events_to_ptn_node = self.get_dict_events_to_ptn_nodes(ptn, 'arr')
        dep_events_to_ptn_node = self.get_dict_events_to_ptn_nodes(ptn, 'dep')
        for ptn_node in ptn.get_nodes():
            lower_bound = self.min_transfer_time
            upper_bound = self.max_transfer_time
            for arr_event in arr_events_to_ptn_node[ptn_node]:
                for dep_event in dep_events_to_ptn_node[ptn_node]:
                    if arr_event.get_line().get_undirected_line_id() != dep_event.get_line().get_undirected_line_id():
                        EanActivity(arr_event, dep_event, 'trans', lower_bound, upper_bound)

    def init_od(self, ptn: ptn_data.Ptn, od: "od_data.OD") -> None:
        arr_events_to_ptn_node = self.get_dict_events_to_ptn_nodes(ptn, 'arr')
        dep_events_to_ptn_node = self.get_dict_events_to_ptn_nodes(ptn, 'dep')
        for od_pair in od.get_all_od_pairs():
            if od_pair.get_total_passengers() == 0:
                continue
            origin = od_pair.get_origin()
            destination = od_pair.get_destination()
            n_time_slices = od_pair.get_n_time_slices()
            dep_events_origin = dep_events_to_ptn_node[origin]
            arr_events_destination = arr_events_to_ptn_node[destination]
            for t in range(1, n_time_slices + 1):
                # A time
                start_event = EanEventOD(self, origin, destination, 'source', t, t)
                for event in dep_events_origin:
                    EanActivity(start_event, event, 'to', None, None)
                for t_2 in range(1, n_time_slices + 1):
                    if t != t_2:
                        event_t_2 = EanEventOD(self, origin, destination, 'source', t, t_2)
                        EanActivity(start_event, event_t_2, 'time', None, None)
                        # A to
                        for event in dep_events_origin:
                            EanActivity(event_t_2, event, 'to', None, None)
                # A from
                end_event = EanEventOD(self, origin, destination, 'target', t)
                for event in arr_events_destination:
                    EanActivity(event, end_event, 'from', None, None)

    def init_sync(self, period_length: int) -> None:
        network_events = self.get_events_network()
        for event_1 in network_events:
            line_1 = event_1.get_line()
            line_id_1 = line_1.get_directed_line_id()
            frequency_1 = line_1.get_frequency()
            repetition_1 = line_1.get_repetition()
            if frequency_1 > 1 and repetition_1 == 1:
                events_to_sync = {}
                events_to_sync[1] = event_1
                for event_2 in network_events:
                    line_2 = event_2.get_line()
                    line_id_2 = line_2.get_directed_line_id()
                    repetition_2 = line_2.get_repetition()
                    if line_id_1 == line_id_2 and repetition_2 != 1 \
                            and event_1.get_ptn_node() == event_2.get_ptn_node() \
                            and event_1.get_event_type() == event_2.get_event_type():
                        events_to_sync[repetition_2] = event_2
                for repetition in range(1, frequency_1):
                    EanActivity(events_to_sync[repetition], events_to_sync[repetition + 1], 'sync',
                                int(period_length / frequency_1), int(period_length / frequency_1))

    def compute_max_upper_bound(self) -> int:
        max_upper_bound = 0
        for activity in self.get_activities(['wait', 'drive', 'trans']):
            if activity.get_upper_bound() > max_upper_bound:
                max_upper_bound = activity.get_upper_bound()
        return max_upper_bound

    def compute_first_last_events_in_line(self, line_pool: line_data.LinePool) -> None:
        for line in line_pool.get_lines():
            first_event = self.get_event_network(line.get_first_stop(), line, 'dep')
            last_event = self.get_event_network(line.get_last_stop(), line, 'arr')
            self.first_events_in_line[line] = first_event
            self.last_events_in_line[line] = last_event

    def reset_events_and_activities(self) -> None:
        for event in self.get_events_network():
            event.set_n_passengers(0)

        for activity in self.get_activities(['drive', 'wait', 'trans']):
            activity.set_n_passengers(0)

class EanActivity:
    def __init__(self, left_event: "EanEvent", right_event: "EanEvent", activity_type: str, lower_bound: int,
                 upper_bound: int, n_passengers: int = 0, activity_id: int = -1) -> None:
        self.left_event = left_event
        self.right_event = right_event
        self.lower_bound = lower_bound
        self.upper_bound = upper_bound
        self.activity_type = activity_type
        left_event.add_outgoing_activity(self)
        right_event.add_incoming_activity(self)
        self.activity_id = activity_id
        self.n_passengers = n_passengers

    def set_activity_id(self, activity_id: int) -> None:
        self.activity_id = activity_id

    def get_activity_id(self) -> int:
        return self.activity_id

    def get_activity_type(self) -> str:
        return self.activity_type

    def get_lower_bound(self) -> int:
        return self.lower_bound

    def get_upper_bound(self) -> int:
        return self.upper_bound

    def get_n_passengers(self) -> int:
        return self.n_passengers

    def set_n_passengers(self, n_passengers: int) -> None:
        self.n_passengers = n_passengers

    def add_passengers(self, n_passengers: int) -> None:
        self.n_passengers += n_passengers

    def to_string(self) -> str:
        return str(self)

    def __str__(self):
        return "(" + str(self.left_event) + "," + str(self.right_event) + "," + self.activity_type + ")"

    def get_left_event(self) -> "EanEvent":
        return self.left_event

    def get_right_event(self) -> "EanEvent":
        return self.right_event

    def belongs_to_edge_drive(self, edge: Link) -> bool:
        if self.activity_type != 'drive':
            return False
        return self.left_event.get_ptn_node() == edge.getLeftNode() \
               and self.right_event.get_ptn_node() == edge.getRightNode()

    def belongs_to_node_wait(self, node: Stop) -> bool:
        if self.activity_type != 'wait':
            return False
        return self.get_left_event().get_ptn_node() == node

    def belongs_to_transfer_node(self, edge_in: Link, edge_out: Link) -> bool:
        if self.activity_type not in ['trans', 'wait']:
            return False
        if edge_in.getRightNode() != edge_out.getLeftNode():
            raise Exception("There is no transfer possible between edge %s and edge %s!"
                            % (str(edge_in), str(edge_out)))
        transfer_node = edge_in.getRightNode()
        if self.get_left_event().get_ptn_node() != transfer_node:
            return False
        return self.left_event.get_line().get_edges().contains(edge_in) \
               and self.right_event.get_line().get_edges().contains(edge_out)

    def to_activities_periodic(self) -> str:
        if self.activity_type == 'drive':
            type_string = '\"drive\"'
        elif self.activity_type == 'wait':
            type_string = '\"wait\"'
        elif self.activity_type == 'sync':
            type_string = '\"sync\"'
        elif self.activity_type == 'trans':
            type_string = '\"change\"'
        else:
            type_string = '\"%s\"' % self.activity_type
        return "%d; %s; %d; %d; %d; %d;" % (
            self.activity_id, type_string, self.left_event.get_event_id(), self.right_event.get_event_id(),
            self.lower_bound,
            self.upper_bound)

    def __lt__(self, other):
        return self.activity_id.__lt__(other.activity_id)


class EanEvent:
    def __init__(self, ean: Ean, event_type: str, event_id=-1) -> None:
        self.outgoing_activities = []
        self.incoming_activities = []
        self.event_type = event_type
        ean.add_event(self)
        self.event_id = event_id

    def add_outgoing_activity(self, activity: EanActivity) -> None:
        if not self.outgoing_activities.__contains__(activity):
            self.outgoing_activities.append(activity)

    def add_incoming_activity(self, activity: EanActivity) -> None:
        if not self.incoming_activities.__contains__(activity):
            self.incoming_activities.append(activity)

    def get_outgoing_activities(self) -> List[EanActivity]:
        return self.outgoing_activities

    def get_incident_activities(self) -> List[EanActivity]:
        incident_activities = []
        for activity in self.outgoing_activities:
            incident_activities.append(activity)
        for activity in self.incoming_activities:
            if not incident_activities.__contains__(activity):
                incident_activities.append(activity)
        return incident_activities

    def get_event_type(self) -> str:
        return self.event_type

    def get_event_id(self) -> int:
        return self.event_id

    def set_event_id(self, event_id: int) -> None:
        self.event_id = event_id

    def get_neighbours(self) -> List["EanEvent"]:
        neighbours = []
        for activity in self.outgoing_activities:
            if activity.left_event != self:
                neighbours.append(activity.left_event)
            elif activity.right_event != self:
                neighbours.append(activity.right_event)
        return neighbours


class EanEventNetwork(EanEvent):
    def __init__(self, ean: Ean, ptn_node: Stop, line: line_data.Line, event_type: str,
                 n_passengers: float = 0, event_id: int = -1) -> None:
        super().__init__(ean, event_type, event_id)
        self.ptn_node = ptn_node
        self.line = line
        self.n_passengers = n_passengers
        self.event_time = -1

    def get_n_passengers(self) -> float:
        return self.n_passengers

    def set_n_passengers(self, n_passengers: float) -> None:
        self.n_passengers = n_passengers

    def add_passengers(self, n_passengers: int) -> None:
        self.n_passengers += n_passengers

    def set_event_id(self, event_id: int) -> None:
        self.event_id = event_id

    # Test
    def set_event_time(self, event_time: int) -> None:
        self.event_time = event_time

    def get_event_time(self) -> int:
        return self.event_time

    def get_event_id(self) -> int:
        return self.event_id

    def get_ptn_node(self) -> Stop:
        return self.ptn_node

    def get_directed_line_id(self) -> int:
        return self.line.get_directed_line_id()

    def get_line(self) -> line_data.Line:
        return self.line

    def get_event_type(self) -> str:
        return self.event_type

    def check_attributes(self, ptn_node: Stop, line: line_data.Line, event_type: str) -> bool:
        return self.ptn_node.getId() == ptn_node.getId() and self.line == line and self.event_type == event_type

    def to_string(self) -> str:
        return str(self)

    def __str__(self):
        return "(" + str(self.ptn_node.getId()) + "," + self.line.to_string() + "," + self.event_type + ")"

    def to_events_periodic(self) -> str:
        if self.event_type == 'arr':
            type_string = '\"arrival\"'
        else:
            type_string = '\"departure\"'
        return "%d; %s; %d; %d; %d; %s; %d" % (
            self.event_id, type_string, self.ptn_node.getId(), math.floor(self.line.get_undirected_line_id()),
            self.get_n_passengers(), self.get_line().get_direction(), self.get_line().get_repetition())

    def get_neighbours(self) -> List["EanEventNetwork"]:
        neighbours = []
        for activity in self.outgoing_activities:
            if activity.left_event != self:
                neighbours.append(activity.left_event)
            elif activity.right_event != self:
                neighbours.append(activity.right_event)
        return neighbours

    def __lt__(self, other):
        return self.event_id.__lt__(other.event_id)


class EanEventOD(EanEvent):
    def __init__(self, ean: Ean, start: Stop, end: Stop, od_event_type: str, time_1: int,
                 time_2: int = None, event_id: int = -1) -> None:
        super().__init__(ean, od_event_type, event_id)
        self.start = start
        self.end = end
        self.od_event_type = od_event_type
        self.time_1 = time_1
        self.time_2 = time_2  # none, if type=target

    def get_start(self) -> Stop:
        return self.start

    def get_end(self) -> Stop:
        return self.end

    def get_time_1(self) -> int:
        return self.time_1

    def get_time_2(self) -> int:
        return self.time_2

    def check_attributes(self, start: Stop, end: Stop, od_event_type: str, time_1: int,
                         time_2: Union[int, None]) -> bool:
        return self.start == start and self.end == end and self.od_event_type == od_event_type and \
               self.time_1 == time_1 and self.time_2 == time_2

    def check_attributes_od(self, start: Stop, end: Stop, time_1: int) -> bool:
        return self.start == start and self.end == end and self.time_1 == time_1

    def to_string(self) -> str:
        return str(self)

    def __str__(self):
        if self.time_2 is None:
            return "(" + str(self.start.getId()) + "," + str(self.end.getId()) + "," + str(
                self.time_1) + "," + self.od_event_type + ")"
        else:
            return "(" + str(self.start.getId()) + "," + str(self.end.getId()) + "," + str(
                self.time_1) + "," + str(self.time_2) + "," + self.od_event_type + ")"


class AperiodicEanEvent:
    def __init__(self, event_id: int, periodic_event: EanEventNetwork, aperiodic_time: int) -> None:
        self.event_id = event_id
        self.periodic_event = periodic_event
        self.aperiodic_time = aperiodic_time
        self.aperiodic_time_with_offset = aperiodic_time

    def get_aperiodic_time(self) -> int:
        return self.aperiodic_time

    def get_aperiodic_time_with_offset(self) -> int:
        return self.aperiodic_time_with_offset

    def increase_aperiodic_time_with_offset_by(self, time: int) -> None:
        self.aperiodic_time_with_offset += time

    def get_event_id(self) -> int:
        return self.event_id

    def reset_event_id(self, event_id) -> None:
        self.event_id = event_id

    def to_events_expanded(self) -> str:
        if self.periodic_event.get_event_type() == 'arr':
            type_string = '\"arrival\"'
        else:
            type_string = '\"departure\"'
        # event-id; periodic-id; type; time; passengers
        return "%d; %d; %s; %d; %d; %d" % (
            self.event_id, self.periodic_event.get_event_id(), type_string, self.aperiodic_time_with_offset * 60,
            self.periodic_event.get_n_passengers(), self.periodic_event.get_ptn_node().getId())

    def to_timetable_expanded(self) -> str:
        return "%d; %d" % (self.event_id, self.aperiodic_time_with_offset * 60)


class AperiodicEanActivity:
    def __init__(self, activity_id: int, periodic_activity: EanActivity, aperiodic_left_event: AperiodicEanEvent,
                 aperiodic_right_event: AperiodicEanEvent):
        self.activity_id = activity_id
        self.periodic_activity = periodic_activity
        self.aperiodic_left_event = aperiodic_left_event
        self.aperiodic_right_event = aperiodic_right_event

    def reset_activity_id(self, activity_id: int) -> None:
        self.activity_id = activity_id

    def to_activities_expanded(self) -> str:
        activity_type = self.periodic_activity.get_activity_type()
        if activity_type == 'drive':
            type_string = '\"drive\"'
        elif activity_type == 'wait':
            type_string = '\"wait\"'
        elif activity_type == 'sync':
            type_string = '\"sync\"'
        elif activity_type == 'trans':
            type_string = '\"change\"'
        elif activity_type == 'headway':
            type_string = '\"headway\"'
        else:
            type_string = '\"%s\"' % activity_type
        # activity-id; periodic-id; type; tail-event-id; head-event-id; lower-bound; upper-bound; passengers
        return "%d; %d; %s; %d; %d; %d; %d; %d" % (
            self.activity_id, self.periodic_activity.get_activity_id(), type_string,
            self.aperiodic_left_event.get_event_id(), self.aperiodic_right_event.get_event_id(),
            self.periodic_activity.get_lower_bound(), self.periodic_activity.get_upper_bound(),
            self.periodic_activity.get_n_passengers())


class AperiodicEan:
    def __init__(self, ean: Ean) -> None:
        self.max_event_id = 0
        self.max_activity_id = 0
        self.aperiodic_events_by_periodic_event = {}  # type: Dict[EanEvent, List[AperiodicEanEvent]]
        self.aperiodic_activities_by_periodic_activity = {}
        for event in ean.get_all_events():
            self.aperiodic_events_by_periodic_event[event] = []
        for activity in ean.get_all_activities():
            self.aperiodic_activities_by_periodic_activity[activity] = []

    def get_aperiodic_events_by_periodic_event(self, event: EanEvent) -> List[AperiodicEanEvent]:
        return self.aperiodic_events_by_periodic_event[event]

    def get_aperiodic_activities_by_periodic_activity(self, activity: EanActivity) -> List[AperiodicEanActivity]:
        return self.aperiodic_activities_by_periodic_activity[activity]

    def add_aperiodic_event(self, periodic_event: EanEventNetwork, aperiodic_time: int) -> None:
        self.max_event_id += 1
        self.aperiodic_events_by_periodic_event[periodic_event].append(
            AperiodicEanEvent(self.max_event_id, periodic_event, aperiodic_time))

    def add_aperiodic_activity(self, periodic_activity: EanActivity, aperiodic_left_event: AperiodicEanEvent,
                               aperiodic_right_event: AperiodicEanEvent) -> None:
        self.max_activity_id += 1
        self.aperiodic_activities_by_periodic_activity[periodic_activity].append(
            AperiodicEanActivity(self.max_activity_id, periodic_activity, aperiodic_left_event, aperiodic_right_event))

    def aperiodic_ean_from_vehicle_schedule(self, ean: Ean, vehicle_schedule: "vehicle_schedule.VehicleSchedule",
                                            duration: Dict[line_data.Line, int], pi: Dict[EanEvent, int],
                                            period_length: int) -> None:
        # Aperiodic events
        for event in ean.get_events_network():
            periodic_time = pi[event]
            line = event.get_line()
            max_period = vehicle_schedule.get_drivings(line)
            latest_time = max_period * period_length + duration[line]
            for time in range(periodic_time, latest_time + 1, period_length):
                self.add_aperiodic_event(event, time)

        # Aperiodic activities (drive, wait, sync)
        for activity in ean.get_activities(['drive', 'wait', 'sync']):
            lower_bound = activity.get_lower_bound()
            upper_bound = activity.get_upper_bound()
            left_periodic_event = activity.get_left_event()
            right_periodic_event = activity.get_right_event()
            for left_aperiodic_event in self.aperiodic_events_by_periodic_event[left_periodic_event]:
                time_left = left_aperiodic_event.get_aperiodic_time()
                for right_aperiodic_event in self.aperiodic_events_by_periodic_event[right_periodic_event]:
                    time_right = right_aperiodic_event.get_aperiodic_time()
                    if lower_bound <= time_right - time_left <= upper_bound:
                        self.add_aperiodic_activity(activity, left_aperiodic_event, right_aperiodic_event)

        # Aperiodic activities (change)
        for activity in ean.get_activities(['trans']):
            lower_bound = activity.get_lower_bound()
            upper_bound = activity.get_upper_bound()
            left_periodic_event = activity.get_left_event()
            right_periodic_event = activity.get_right_event()
            for left_aperiodic_event in self.aperiodic_events_by_periodic_event[left_periodic_event]:
                time_left = left_aperiodic_event.get_aperiodic_time()
                for right_aperiodic_event in self.aperiodic_events_by_periodic_event[right_periodic_event]:
                    time_right = right_aperiodic_event.get_aperiodic_time()
                    if lower_bound <= time_right - time_left <= upper_bound:
                        self.add_aperiodic_activity(activity, left_aperiodic_event, right_aperiodic_event)

        # Aperiodic activities (headway)
        for activity in ean.get_activities(['headway']):
            lower_bound = activity.get_lower_bound()
            upper_bound = activity.get_upper_bound()
            left_periodic_event = activity.get_left_event()
            right_periodic_event = activity.get_right_event()
            for left_aperiodic_event in self.aperiodic_events_by_periodic_event[left_periodic_event]:
                time_left = left_aperiodic_event.get_aperiodic_time()
                for right_aperiodic_event in self.aperiodic_events_by_periodic_event[right_periodic_event]:
                    time_right = right_aperiodic_event.get_aperiodic_time()
                    if lower_bound <= time_right - time_left <= upper_bound:
                        self.add_aperiodic_activity(activity, left_aperiodic_event, right_aperiodic_event)


    def get_aperiodic_starting_event(self, periodic_ean: Ean, line: line_data.Line, period: int,
                                     period_length: int) -> AperiodicEanEvent:
        periodic_event = periodic_ean.get_first_event_in_line(line)
        for aperiodic_event in self.aperiodic_events_by_periodic_event[periodic_event]:
            if aperiodic_event.get_aperiodic_time() in range((period - 1) * period_length, period * period_length):
                return aperiodic_event
        raise Exception(
            "Aperiodic event for event " + str(period) + " in period " + str(period) + " does not exist.")

    def get_aperiodic_ending_event(self, periodic_ean: Ean, line: line_data.Line, period: int,
                                   duration: Dict[line_data.Line, int],
                                   period_length: int) -> AperiodicEanEvent:
        periodic_event = periodic_ean.get_last_event_in_line(line)
        for aperiodic_event in self.aperiodic_events_by_periodic_event[periodic_event]:
            if aperiodic_event.get_aperiodic_time() in range((period - 1) * period_length + duration[line],
                                                             period * period_length + duration[line]):
                return aperiodic_event
        raise Exception("Aperiodic event for event " + str(period) + " with line starting in period " + str(
            period) + " does not exist.")

    def update_aperiodic_times(self, ean_earliest_time: int) -> None:
        ean_earliest_time_in_min = ean_earliest_time / SECONDS_PER_MINUTE
        for periodic_event in self.aperiodic_events_by_periodic_event.keys():
            for aperiodic_event in self.aperiodic_events_by_periodic_event[periodic_event]:
                aperiodic_event.increase_aperiodic_time_with_offset_by(ean_earliest_time_in_min)


def construct_aperiodic_ean(ean: Ean, vs: "vehicle_schedule.VehicleSchedule", duration: Dict[line_data.Line, int],
                            pi: Dict[EanEvent, int], ean_earliest_time: int, period_length: int) -> AperiodicEan:
    logger.debug("Construct aperiodic ean")
    aperiodic_ean = AperiodicEan(ean)
    aperiodic_ean.aperiodic_ean_from_vehicle_schedule(ean, vs, duration, pi, period_length)
    aperiodic_ean.update_aperiodic_times(ean_earliest_time)
    return aperiodic_ean