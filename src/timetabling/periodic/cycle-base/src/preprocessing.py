import logging

from event_activity_network import PeriodicEventActivityNetwork

from helper import Parameters

logger_ = logging.getLogger(__name__)


# Deleting edges with zero / few passengers.
def deleting_light_edges(ean: PeriodicEventActivityNetwork, parameters: Parameters):
    logger_.debug("Run: deleting_light_edges")
    cnt = 0
    wt = 0
    rm_list = []

    for edge in ean.graph.getEdges():
        span = edge.getUpperBound() - edge.getLowerBound()
        if edge.getNumberOfPassengers() <= parameters.passenger_cut and span >= parameters.period_length - 1:
            rm_list.append(edge)
            cnt += 1
            wt += edge.getNumberOfPassengers()

    for edge in rm_list:
        ean.graph.removeEdge(edge)

    ean.edge_table = {(e.getLeftNode().getId(), e.getRightNode().getId()): e for e in ean.graph.getEdges()}

    logger_.info('Removed {} full span edges with not more than {} passengers having weight {}'.format(cnt,
                 parameters.passenger_cut, wt))
