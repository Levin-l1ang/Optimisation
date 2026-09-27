import ptn_data
from typing import List
from typing import Dict

from core.model.ptn import Stop, Link


class LinePool:
    def __init__(self) -> None:
        self.pool = []
        self.max_id = 0

    def get_lines(self) -> List["Line"]:
        return self.pool

    def get_original_lines(self) -> List["Line"]:
        lines = []
        for line in self.pool:
            if line.get_directed_line_id() > 0 and line.get_repetition() == 1:
                lines.append(line)
        return lines

    def add_line(self, line: "Line") -> None:
        self.pool.append(line)
        if line.get_directed_line_id() > self.max_id:
            self.max_id = line.get_directed_line_id()

    def get_max_id(self) -> int:
        return self.max_id

    # Always finds the line with repetition 1!
    def get_line_by_directed_id(self, line_id: int) -> "Line":
        for line in self.pool:
            if line.get_directed_line_id() == line_id and line.get_repetition() == 1:
                return line
        raise Exception("There is no line with id %d and repetition 1!" % line_id)

    def get_line_by_directed_id_and_repetition(self, line_id: int, repetition: int) -> "Line":
        for line in self.pool:
            if line.get_directed_line_id() == line_id and line.get_repetition() == repetition:
                return line
        raise Exception("There is no line with id %d and repetition %d!" % (line_id, repetition))

    def get_lines_by_directed_id(self, line_id: int) -> Dict[int, "Line"]:
        lines = {}
        for line in self.pool:
            if line.get_directed_line_id() == line_id:
                lines[line.get_repetition()] = line
        return lines

    def get_max_n_edges_in_line(self) -> int:
        max_n_edges = 0
        for line in self.pool:
            if line.get_edges().__len__() > max_n_edges:
                max_n_edges = line.get_edges().__len__()
        return max_n_edges

    def delete_line(self, line: "Line") -> None:
        self.pool.remove(line)


class Line:
    def __init__(self, directed_line_id: int, pool: LinePool, undirected_line_id: int = -1, frequency: int = -1,
                 repetition: int = 1) -> None:
        self.length = 0
        self.cost = 0
        self.edges: List[Link] = []
        self.directed_line_id = directed_line_id
        if undirected_line_id != -1:
            self.undirected_line_id = undirected_line_id
        else:
            self.undirected_line_id = directed_line_id
        self.first_stop = None
        self.last_stop = None
        self.frequency = frequency
        self.repetition = repetition
        pool.add_line(self)

    def add_edge(self, edge: Link, ptn: ptn_data.Ptn, directed: bool = True, forward: bool = True) -> None:
        if directed:
            if not self.edges:
                self.edges.append(edge)
            elif self.edges[- 1].getRightNode().getId() != edge.getLeftNode().getId():
                raise RuntimeError("Edge %d does not fit in line %d!" % (edge.getId(), self.directed_line_id))
            else:
                self.edges.append(edge)
                self.last_stop = edge.getRightNode()
        else:
            if not self.edges:
                if forward:
                    self.edges.append(edge)
                else:
                    self.edges.append(ptn.get_backward_edge(edge))
            else:
                if self.edges.__len__() == 1:
                    if forward and self.edges[0].getRightNode().getId() not in \
                            [edge.getLeftNode().getId(), edge.getRightNode().getId()]:
                        first_edge = self.edges.pop()
                        self.edges.append(ptn.get_backward_edge(first_edge))
                    elif not forward and self.edges[0].getLeftNode().getId() not in \
                        [edge.getLeftNode().getId(), edge.getRightNode().getId()]:
                        first_edge = self.edges.pop()
                        self.edges.append(ptn.get_backward_edge(first_edge))
                if forward and self.edges[- 1].getRightNode().getId() == edge.getLeftNode().getId():
                    self.edges.append(edge)
                elif forward and self.edges[- 1].getRightNode().getId() == edge.getRightNode().getId():
                    self.edges.append(ptn.get_backward_edge(edge))
                elif not forward and self.edges[0].getLeftNode().getId() == edge.getRightNode().getId():
                    self.edges.insert(0, edge)
                elif not forward and self.edges[0].getLeftNode().getId() == edge.getLeftNode().getId():
                    self.edges.insert(0, ptn.get_backward_edge(edge))
                else:
                    raise RuntimeError("Edge %d does not fit in line %d!" % (edge.getId(), self.directed_line_id))

    def set_undirected_line_id(self, undirected_line_id: int) -> None:
        self.undirected_line_id = undirected_line_id

    def get_undirected_line_id(self) -> int:
        return self.undirected_line_id

    def get_directed_line_id(self) -> int:
        return self.directed_line_id

    def get_edges(self) -> List[Link]:
        return self.edges

    def get_nodes(self) -> List[Stop]:
        nodes = [self.get_first_stop()]
        for edge in self.edges:
            nodes.append(edge.getRightNode())
        return nodes

    def to_string(self) -> str:
        return str(self)

    def __str__(self):
        if self.directed_line_id > 0:
            direction = ">"
        else:
            direction = "<"
        return f"({self.undirected_line_id},{direction},{self.repetition}/{self.frequency})"

    def get_first_stop(self) -> Stop:
        # return self.first_stop
        if not self.edges:
            return None
        return self.edges[0].getLeftNode()

    def get_last_stop(self) -> Stop:
        # return self.last_stop
        if not self.edges:
            return None
        return self.edges[-1].getRightNode()

    def set_length(self, length: float) -> None:
        self.length = length

    def set_cost(self, cost: float) -> None:
        self.cost = cost

    def get_length(self) -> float:
        return self.length

    def compute_length_from_ptn(self) -> float:
        length_from_ptn = sum([x.getLength() for x in self.edges])
        return length_from_ptn

    def get_cost(self) -> float:
        return self.cost

    def get_frequency(self) -> int:
        return self.frequency

    def get_repetition(self) -> int:
        return self.repetition

    def get_direction(self) -> str:
        if self.directed_line_id > 0:
            return ">"
        else:
            return "<"

    def __eq__(self, other: object) -> bool:
        if other is None:
            return False
        if not isinstance(other, Line):
            return False
        return self.directed_line_id == other.get_directed_line_id() \
               and self.repetition == other.get_repetition()

    def __ne__(self, o: object) -> bool:
        return not self.__eq__(o)

    def __hash__(self) -> int:
        return hash((self.directed_line_id, self.repetition))

    def __lt__(self, other) -> int:
        return self.directed_line_id.__lt__(other.directed_line_id)
