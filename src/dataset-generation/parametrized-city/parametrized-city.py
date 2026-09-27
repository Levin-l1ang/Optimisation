import sys
import logging
import numpy as np

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.od import ODWriter
from core.io.ptn import PTNWriter
from core.model.impl.fullOD import FullOD
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.ptn import Stop, Link

logger = logging.getLogger(__name__)

def id2coord(node_id_pol: int) -> tuple[float, float]:
    """
    Returns the cartesian coordinates of the Stop specified by id
    """
    if node_id_pol == 2*n+1:
        return np.round(0), np.round(-L*eta, 4)
    if node_id_pol > n:
        node_id_pol -= n
        radius = L
    else:
        radius = (1+g)*L
    x = radius * np.cos(2*np.pi / n * (node_id_pol - 1))
    y = radius * np.sin(2*np.pi / n * (node_id_pol - 1))
    return np.round(x, 4), np.round(y, 4)


def euclidean_distance(first_stop_id: int, second_stop_id: int) -> float:
    """
    Returns the euclidean distance of the two Stops specified by id
    """
    left_point = np.array((stop_by_id[first_stop_id].getXCoordinate(), stop_by_id[first_stop_id].getYCoordinate()))
    right_point = np.array((stop_by_id[second_stop_id].getXCoordinate(), stop_by_id[second_stop_id].getYCoordinate()))
    return np.linalg.norm(left_point-right_point)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    logger.info("Begin reading configuration")
    config = ConfigReader.read(sys.argv[1])
    logger.info("Finished reading configuration")

    TIME_UNITS_PER_MINUNTE = 60
    n = config.getIntegerValue("dg_param_city_number_subcenters")    # number of peripheries/subcenters
    alpha = config.getDoubleValue("dg_param_city_alpha")        # trips proportion from periphery to CBD
    beta = config.getDoubleValue("dg_param_city_beta")          # trips proportion from periphery to own subcenter
    gamma = 1 - alpha - beta                                    # trips proportion from periphery to foreign subcenter
    L = config.getDoubleValue("dg_param_city_L")                # distance from any subcenter to the geometrical center of the city
    g = config.getDoubleValue("dg_param_city_g")                # distance periphery-subcenter / distance subcenter-CBD
    eta = config.getDoubleValue("dg_param_city_eta")            # Protion of displacement of the CBD from the geometrical center in an axis CBD-subcenter
    a = config.getDoubleValue("dg_param_city_a")                # trips proportion that depart from the periphery
    b = 1 - a                                                   # trips proportion that depart from a subcenter
    Y = config.getIntegerValue("dg_param_city_Y")               # total patronage = total number of trips
    speed = config.getIntegerValue("gen_vehicle_speed")
    default_stop_file_name = config.getStringValue("default_stops_file")
    default_edges_file_name = config.getStringValue("default_edges_file")
    default_od_file_name = config.getStringValue("default_od_file")
    ptn_name = config.getStringValue("ptn_name")

    logger.info("Begin building PTN of parametrized city")
    ptn = SimpleDictGraph()
    stop_by_id ={}
    edge_by_id = {}

    # Add nodes
    # periphery
    for node_id in range(1, n+1):
        x_coordinate, y_coordinate = id2coord(node_id)
        stop = Stop(node_id, "p_%d" % node_id, "Periphery %d" % node_id, x_coordinate, y_coordinate)
        stop_by_id[node_id] = stop
        ptn.addNode(stop)
    # subcenters
    for node_id in range(n+1, 2*n+1):
        x_coordinate, y_coordinate = id2coord(node_id)
        stop = Stop(node_id, "sc_%d" % (node_id-n), "Subcenter %d" % (node_id-n), x_coordinate, y_coordinate)
        stop_by_id[node_id] = stop
        ptn.addNode(stop)
    # central business district
    for node_id in [2*n+1]:
        x_coordinate, y_coordinate = id2coord(node_id)
        stop = Stop(node_id, "cbd", "Central Business District", x_coordinate, y_coordinate)
        stop_by_id[node_id] = stop
        ptn.addNode(stop)

    # Add edges
    edge_id= 1
    # periphery to subcenter
    for i in range(1,n+1):
        length = euclidean_distance(i, i+n)
        lower_bound = int(np.floor(length / speed * TIME_UNITS_PER_MINUNTE))
        upper_bound = int(np.ceil(length / speed * TIME_UNITS_PER_MINUNTE))
        edge = Link(edge_id, stop_by_id[i], stop_by_id[i+n], length, lower_bound, upper_bound, False)
        edge_by_id[edge_id] = edge
        ptn.addEdge(edge)
        edge_id += 1

    # subcenter to center
    for i in range(n+1, 2*n+1):
        length = euclidean_distance(i, 2*n+1)
        lower_bound = int(np.floor(length / speed * TIME_UNITS_PER_MINUNTE))
        upper_bound = int(np.ceil(length / speed * TIME_UNITS_PER_MINUNTE))
        edge = Link(edge_id, stop_by_id[i], stop_by_id[2*n+1], length, lower_bound, upper_bound, False)
        edge_by_id[edge_id] = edge
        ptn.addEdge(edge)
        edge_id += 1

    # subcenter to subcenter
    for i in range(n+1, 2*n+1):
        if i == 2*n:
            right_stop_id = n+1
        else:
            right_stop_id = i+1
        length = euclidean_distance(i, right_stop_id)
        lower_bound = int(np.floor(length / speed * TIME_UNITS_PER_MINUNTE))
        upper_bound = int(np.ceil(length / speed * TIME_UNITS_PER_MINUNTE))
        edge = Link(edge_id, stop_by_id[i], stop_by_id[right_stop_id], length, lower_bound, upper_bound, False)
        edge_by_id[edge_id] = edge
        ptn.addEdge(edge)
        edge_id += 1

    logger.debug("Built PTN with the following Stops and Links")
    for stop in ptn.getNodes():
        logger.debug(stop)
    for edge in ptn.getEdges():
        logger.debug(edge)

    logger.info(f"Finished building PTN of parametrized city with {2*n + 1} Stops and {edge_id - 1} Links")

    logger.info("Begin computing OD Matrix")
    od = FullOD(2*n+1)
    for origin_id in range(1, 2*n+1): # origin cannot be center
        for destination_id in range(n+1,2*n+2): # destination cannot be periphery
            if origin_id <= n: # origin is periphery
                if destination_id == 2*n+1: # destination is center
                    value = a * alpha / n * Y
                elif destination_id == origin_id + n: # destination is the fitting subcenter
                    value = a * beta / n * Y
                else: # destination is other subcenter
                    value = a * gamma / (n * (n-1)) * Y
            else: # origin is subcenter
                if destination_id == origin_id:
                    continue
                elif destination_id == 2*n+1: # destination is center
                    value = b * alpha / ((1-beta) * n) * Y
                else: # destionation is other subcenter
                    value = b * gamma / ((1-beta)*n*(n-1))*Y
            od.setValue(origin_id, destination_id, value)
    logger.info(f"Finished computing OD matrix with {od.computeNumberOfPassengers()} passengers")

    logger.info("Begin writing output data")
    PTNWriter.write(ptn, config, stop_file_name=f"./../{ptn_name}/{default_stop_file_name}", link_file_name=f"./../{ptn_name}/{default_edges_file_name}")
    ODWriter.write(ptn, od, config=config, file_name=f"./../{ptn_name}/{default_od_file_name}")
    logger.info("Finished writing output data")
