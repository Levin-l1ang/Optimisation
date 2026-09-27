from itertools import count
from heapq import heappush, heappop
from typing import Any, List, Union

import networkx as nx


Node = Any
Path = List[Node]


class DijkstraAllShortestPaths:
    """Compute all shortest paths from one source node to all other nodes."""

    def __init__(self, graph: Union[nx.Graph, nx.DiGraph],
                 source: Node,
                 weight: str = 'weight'):
        self.graph = graph
        self.source = source
        self.weight = weight

        c = count()
        self.dist = {source: 0}
        self.preds = {source: []}
        queue = [(0, next(c), source)]
        perm = set()

        succ = graph._succ if nx.is_directed(graph) else graph._adj

        while queue:
            s_u_dist, _, u = heappop(queue)
            while u in perm:
                if not queue:
                    return
                s_u_dist, _, u = heappop(queue)

            perm.add(u)
            succ_u = succ[u]
            for v in succ_u:
                if v not in perm:
                    s_uv_dist = s_u_dist + succ_u[v][weight]
                    s_v_dist = self.dist.get(v)
                    if s_v_dist is None or s_uv_dist < s_v_dist:
                        self.dist[v] = s_uv_dist
                        self.preds[v] = [u]
                        heappush(queue, (s_uv_dist, next(c), v))
                    elif s_uv_dist == s_v_dist:
                        self.preds[v].append(u)

            if len(perm) == len(self.dist):
                return

    def getPaths(self, target: Node) -> List[Path]:
        preds = self.preds[target]
        if not preds:
            if target == self.source:
                return [[target]]
            else:
                return []
        paths = []
        for u in preds:
            paths.extend(self.getPaths(u))
        for path in paths:
            path.append(target)
        return paths
