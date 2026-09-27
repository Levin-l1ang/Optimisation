from itertools import count
from heapq import heappush, heappop
from typing import Any, Union

import networkx as nx


Node = Any


def single_source_shortest_path_length(G: Union[nx.Graph, nx.DiGraph], source: Node, weight: str = 'weight'):
    """Return a dictionary with shortest distances from the source keyed by destination nodes."""
    c = count()
    dist = {source: 0}
    queue = [(0, next(c), source)]
    perm = set()

    succ = G._succ if nx.is_directed(G) else G._adj

    while queue:
        s_u_dist, _, u = heappop(queue)
        while u in perm:
            if not queue:
                return dist
            s_u_dist, _, u = heappop(queue)

        perm.add(u)
        succ_u = succ[u]
        for v in succ_u:
            if v not in perm:
                s_uv_dist = s_u_dist + succ_u[v][weight]
                s_v_dist = dist.get(v)
                if s_v_dist is None or s_uv_dist < s_v_dist:
                    dist[v] = s_uv_dist
                    heappush(queue, (s_uv_dist, next(c), v))

        if len(perm) == len(dist):
            return dist

    return dist
