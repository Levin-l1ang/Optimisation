from typing import List

from core.model.ptn import Stop


class OD:
    def __init__(self) -> None:
        self.od_pairs = []  # type: List[ODPair]

    def add_od_pair(self, od_pair: "ODPair") -> None:
        self.od_pairs.append(od_pair)

    def get_active_od_pairs(self) -> List["ODPair"]:
        od_pairs = []
        for od_pair in self.od_pairs:
            if od_pair.is_active():
                od_pairs.append(od_pair)
        return od_pairs

    def get_all_od_pairs(self) -> List["ODPair"]:
        return self.od_pairs

    def get_od_pair(self, origin: Stop, destination: Stop) -> "ODPair":
        for od_pair in self.od_pairs:
            if od_pair.get_origin() == origin and od_pair.get_destination() == destination:
                return od_pair
        return None

    def get_n_time_slices(self, origin: Stop, destination: Stop) -> int:
        for od_pair in self.od_pairs:
            if od_pair.get_origin() == origin and od_pair.get_destination() == destination:
                return od_pair.get_n_time_slices()
        raise RuntimeError('Requested OD-pair (%d,%d) not in list.' % (origin.getId(), destination.getId()))

    def get_n_passengers(self, origin: Stop, destination: Stop, time_slice: int) -> int:
        for od_pair in self.od_pairs:
            if od_pair.get_origin() == origin and od_pair.get_destination() == destination:
                return od_pair.get_n_passengers(time_slice)
        raise RuntimeError('Requested OD-pair (%d,%d) not in list.' % (origin.getId(), destination.getId()))


class ODPair:
    def __init__(self, od: OD, origin: Stop, destination: Stop, n_time_slices: int = -1,
                 active: bool = True) -> None:
        self.origin = origin
        self.destination = destination
        self.n_time_slices = n_time_slices
        self.active = active
        self.passengers = {}
        self.transfer_in_shortest_paths = False
        self.diff_bounds_sp = 0
        od.add_od_pair(self)

    def get_n_time_slices(self) -> int:
        return self.n_time_slices

    def get_origin(self) -> Stop:
        return self.origin

    def get_destination(self) -> Stop:
        return self.destination

    def get_n_passengers(self, time_slice: int) -> int:
        return self.passengers[time_slice]

    def is_active(self) -> bool:
        n_passengers = 0
        for time_slice in self.passengers.keys():
            n_passengers += self.passengers[time_slice]
        return self.active and n_passengers > 0

    def set_n_passengers(self, time_slice: int, n_passengers: int) -> None:
        self.passengers[time_slice] = n_passengers

    def set_min_turn_around_time(self, min_turn_around_time: int) -> None:
        self.min_turn_around_time = min_turn_around_time

    def set_min_turn_around_distance(self, min_turn_around_distance: float) -> None:
        self.min_turn_around_distance = min_turn_around_distance

    def get_total_passengers(self) -> int:
        total_passengers = 0
        for t in range(1, self.n_time_slices + 1):
            total_passengers += self.passengers[t]
        return total_passengers

    def get_weighted_diff_bounds_sp(self) -> float:
        return self.diff_bounds_sp*self.get_total_passengers()

    def get_penalty(self, time_1: int, time_2: int) -> float:
        return abs(time_1 - time_2) / self.n_time_slices

    def to_string(self) -> str:
        return f"({self.origin},{self.destination})"

    def __str__(self):
        return f"({self.origin.getId()},{self.destination.getId()})"
