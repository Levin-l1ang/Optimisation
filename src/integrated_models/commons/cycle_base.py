import networkx as nx
from ean_data import *
from typing import Tuple


def cycle_base(ean: 'Ean') -> Dict['EanActivity', List[Tuple['EanActivity', int]]]:
    # creating a networkx graph from our ean
    EAN = build_nx_graph(ean)
    tree = nx.minimum_spanning_tree(EAN, weight='span')
    left_event_function = lambda x: x.left_event
    cycle_base = construct_cycle_base(EAN, tree, left_event_function)
    return cycle_base


def construct_cycle_base(nx_ean, tree, left_event_function):
    cycle_base = {}
    for nx_cycle_base_edge in nx_ean.edges(keys=True):
        if tree.has_edge(*nx_cycle_base_edge):
            continue
        cycle = [(nx_cycle_base_edge[2], 1)]
        if nx_ean.nodes[nx_cycle_base_edge[0]]['name'] == left_event_function(nx_cycle_base_edge[2]):
            k = 1
        else:
            k = -1
        prev_node = -1
        for node in nx.shortest_path(tree, nx_cycle_base_edge[0], nx_cycle_base_edge[1]):
            if prev_node != -1:
                corresponding_edge = [edge for edge in tree[prev_node][node]][0]
                if nx_ean.nodes[prev_node]['name'] == left_event_function(corresponding_edge):
                    cycle.append((corresponding_edge, -1 * k))
                else:
                    cycle.append((corresponding_edge, 1 * k))
            prev_node = node
        cycle_base[nx_cycle_base_edge[2]] = cycle
    return cycle_base


def build_nx_graph(ean: Ean):
    EAN = nx.MultiGraph()
    for event in ean.get_events_network():
        EAN.add_node(event.get_event_id(), name=event)
    for activity in ean.get_activities(['drive', 'wait', 'trans', 'sync', 'turn']):
        left_event = activity.left_event.get_event_id()
        right_event = activity.right_event.get_event_id()
        span = activity.upper_bound - activity.lower_bound
        EAN.add_edge(left_event, right_event, span=span, name=activity, key=activity)
    return EAN



