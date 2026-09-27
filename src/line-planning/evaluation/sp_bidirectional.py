from itertools import count
from typing import List, Optional
from heapq import heappush, heappop

import networkx as nx


# @profile
def shortest_path_length(graph, s, t):
    """
    Gibt einen kürzesten st-Pfad im Graphen zurück (oder None, falls
    kein solcher Pfad existiert).

    Parameters
    ----------
    graph : nx.DiGraph
        Graph mit Attributen 'weight' an den Kanten, welche die Kosten
        darstellen.
    s : Any
        Der Startknoten im Graphen Graph.
    t : Any
        Der Zielknoten.

    Returns
    -------
    Optional[float]
        Laenge des kuerzesten Weges. Falls kein solcher Pfad existiert,
        gibt None zurück.
    """

    if s == t:
        return 0

    c = count()

    dist_from_s = {s: 0}
    forward_queue = [(0, next(c), s)]
    forward_perm = set()

    dist_to_t = {t: 0}
    backward_queue = [(0, next(c), t)]
    backward_perm = set()

    current_best_length = float("inf")

    directed = nx.is_directed(graph)

    while True:
        if not forward_queue:
            return None
        s_u_dist, _, u = heappop(forward_queue)
        while u in forward_perm:
            if not forward_queue:
                return None
            s_u_dist, _, u = heappop(forward_queue)

        if u in backward_perm:
            break

        forward_perm.add(u)
        succ_u = graph._succ[u] if directed else graph._adj[u]
        for v in succ_u:
            if v not in forward_perm:
                s_uv_dist = s_u_dist + succ_u[v]['weight']
                s_v_dist = dist_from_s.get(v)
                if s_v_dist is None or s_uv_dist < s_v_dist:
                    dist_from_s[v] = s_uv_dist
                    heappush(forward_queue, (s_uv_dist, next(c), v))
                    v_t_dist = dist_to_t.get(v)
                    if v_t_dist is not None and s_uv_dist + v_t_dist < current_best_length:
                        current_best_length = s_uv_dist + v_t_dist

        if not backward_queue:
            return None
        u_t_dist, _, u = heappop(backward_queue)
        while u in backward_perm:
            if not backward_queue:
                return None
            u_t_dist, _, u = heappop(backward_queue)

        if u in forward_perm:
            break

        backward_perm.add(u)
        pred_u = graph._pred[u] if directed else graph._adj[u]
        for v in pred_u:
            if v not in backward_perm:
                vu_t_dist = pred_u[v]['weight'] + u_t_dist
                v_t_dist = dist_to_t.get(v)
                if v_t_dist is None or vu_t_dist < v_t_dist:
                    dist_to_t[v] = vu_t_dist
                    heappush(backward_queue, (vu_t_dist, next(c), v))
                    s_v_dist = dist_from_s.get(v)
                    if s_v_dist is not None and s_v_dist + vu_t_dist < current_best_length:
                        current_best_length = s_v_dist + vu_t_dist

    return current_best_length
