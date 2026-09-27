import logging
import sys
import math
import inspect

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.od import ODWriter
from core.io.ptn import PTNWriter
from core.model.ptn import Stop, Link
from core.model.od import ODPair
from core.model.impl.dictOD import DictOD
from core.model.impl.simple_dict_graph import SimpleDictGraph
from random import *
from core.algorithm.dijkstra import Dijkstra

logger = logging.getLogger(__name__)

def is_spoke_edge(nodes_per_ring,edge):
    if abs(edge.getLeftNode().getId() - edge.getRightNode().getId()) == nodes_per_ring or edge.getLeftNode().getId() == 1 or edge.getRightNode().getId() == 1:
        return True
    else:
        return False


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    logger.info("Start reading configuration")
    config = ConfigReader.read(sys.argv[1])

    number_of_rings = config.getIntegerValue("dg_ring_number_of_rings")
    nodes_per_ring = config.getIntegerValue("dg_ring_nodes_per_ring")
    R = config.getIntegerValue("dg_ring_radius")
    length_1 = config.getBooleanValue("dg_ring_length_1")
    demand_type = config.getStringValue("dg_ring_demand_type")
    spoke_edge_demand = config.getDoubleValue("dg_ring_spoke_edge_demand")
    ring_edge_demand = config.getDoubleValue("dg_ring_ring_edge_demand")
    demand_scaling_factor = config.getDoubleValue("dg_ring_demand_scaling_factor")
    ptn_name = config.getStringValue("ptn_name")
    default_stop_file_name = config.getStringValue("default_stops_file")
    default_edges_file_name = config.getStringValue("default_edges_file")
    default_od_file_name = config.getStringValue("default_od_file")
    logger.info("Finished reading configuration")

    logger.info("Begin execution of generate ring dataset")
    ptn = SimpleDictGraph()

    coordinate_step = 360/nodes_per_ring
    ptn.addNode(Stop(1,"0","R0B0",0,0))
    k = 1
    l = 1
    for i in range(1,number_of_rings+1):
        for j in range(nodes_per_ring):
            ptn.addNode(Stop(k+1,"%d%d" %(i,j),"R%dB%d" %(i,j),math.cos(math.radians(j*coordinate_step))*i*R,math.sin(math.radians(j*coordinate_step))*i*R))
            if length_1:
                ptn.addEdge(Link(l,ptn.getNode(max(1, k-nodes_per_ring+1)),ptn.getNode(k+1),1,1,20,False))
            else:
                ptn.addEdge(Link(l,ptn.getNode(max(1, k-nodes_per_ring+1)),ptn.getNode(k+1),R,1,20,False))
            l += 1
            if j > 0:
                if length_1:
                    ptn.addEdge(Link(l,ptn.getNode(k),ptn.getNode(k+1),1,1,20,False))
                else:
                    ptn.addEdge(Link(l,ptn.getNode(k),ptn.getNode(k+1),math.sqrt((i*R)*(i*R)+(i*R)*(i*R)-2*(i*R)*(i*R)*math.cos((2*math.pi)/nodes_per_ring)),1,20,False))
                l += 1
            if j == nodes_per_ring - 1:
                if length_1:
                    ptn.addEdge(Link(l,ptn.getNode(k+1),ptn.getNode(k-nodes_per_ring+2),1,1,20,False))
                else:
                    ptn.addEdge(Link(l,ptn.getNode(k+1),ptn.getNode(k-nodes_per_ring+2),math.sqrt((i*R)*(i*R)+(i*R)*(i*R)-2*(i*R)*(i*R)*math.cos((2*math.pi)/nodes_per_ring)),1,20,False))
                l += 1
            k += 1


    # create OD
    od = DictOD()

    if demand_type == "UNIFORM":
        for i in ptn.getNodes():
            for j in ptn.getNodes():
                if i.getId() < j.getId():
                    od.setValue(i.getId(),j.getId(),1)
                    od.setValue(j.getId(),i.getId(),1)
    elif demand_type == "UNIFORM_CENTRE":
        for i in ptn.getNodes():
            for j in ptn.getNodes():
                if i.getId() < j.getId():
                    if i.getId() == 0:
                        od.setValue(i.getId(),j.getId(),100)
                        od.setValue(j.getId(),i.getId(),100)
                    else:
                        od.setValue(i.getId(),j.getId(),10)
                        od.setValue(j.getId(),i.getId(),10)
    elif demand_type == "RANDOM":
        for i in ptn.getNodes():
            for j in ptn.getNodes():
                if i.getId() < j.getId():
                    demand = randint(1, 100)
                    od.setValue(i.getId(),j.getId(),demand)
                    od.setValue(j.getId(),i.getId(),demand)
    elif demand_type == "RANDOM_NEIGHBOUR_CENTRE":
        for i in ptn.getNodes():
            for j in ptn.getNodes():
                if i.getId() < j.getId():
                    if i.getId() == 1:
                        demand = randint(40, 150)
                        od.setValue(i.getId(),j.getId(),demand)
                        od.setValue(j.getId(),i.getId(),demand)
                    elif (ptn.get_edge_from_nodes(i,j) or ptn.get_edge_from_nodes(j,i)):
                        demand = randint(20, 50)
                        od.setValue(i.getId(),j.getId(),demand)
                        od.setValue(j.getId(),i.getId(),demand)
                    else:
                        demand = randint(0, 30)
                        od.setValue(i.getId(),j.getId(),demand)
                        od.setValue(j.getId(),i.getId(),demand)
    elif demand_type == "SPOKE_RING":
        for i in ptn.getNodes():
            dijkstra = Dijkstra(ptn,i,lambda y: math.sqrt((y.getRightNode().getXCoordinate()-y.getLeftNode().getXCoordinate())*(y.getRightNode().getXCoordinate()-y.getLeftNode().getXCoordinate()) + (y.getRightNode().getYCoordinate()-y.getLeftNode().getYCoordinate())*(y.getRightNode().getYCoordinate()-y.getLeftNode().getYCoordinate())))
            for j in ptn.getNodes():
                if i.getId() < j.getId():
                    dijkstra.computeShortestPath(j)
                    sp = dijkstra.getPath(j)
                    spoke_edges = 0
                    ring_edges = 0
                    for edge in sp.getEdges():
                        if is_spoke_edge(nodes_per_ring,edge):
                            spoke_edges += 1
                        else:
                            ring_edges += 1
                    demand = spoke_edge_demand*1/(spoke_edges + 1) + ring_edge_demand*1/(ring_edges + 1)
                    demand_scaled = demand_scaling_factor*demand
                    od.setValue(i.getId(),j.getId(),round(demand_scaled))
                    od.setValue(j.getId(),i.getId(),round(demand_scaled))
    logger.info("Finished execution of generate ring dataset")

    logger.info("Begin writing output data")
    PTNWriter.write(ptn, config, stop_file_name=f"./../{ptn_name}/{default_stop_file_name}", link_file_name=f"./../{ptn_name}/{default_edges_file_name}")
    ODWriter.write(ptn, od, config=config, file_name=f"./../{ptn_name}/{default_od_file_name}")
    logger.info("Finished writing output data")
