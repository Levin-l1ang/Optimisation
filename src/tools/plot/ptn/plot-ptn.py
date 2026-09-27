#! python

import matplotlib
matplotlib.use('Agg')

import sys
import matplotlib.pyplot as plt
import os.path
import networkx as nx

# input data required:
# Stop.giv
# Edge.giv
# if above files are not existing:
# Existing-Stop.giv
# Existing-Edge.giv

# input data optional (if existing):
# Demand.giv

# output:
# ptn graph file (config param: default_ptn_graph_file) as png
# Note that this may have a different format in the configuration (dot) but this is replaced by png.

def read_stops(stops_file):
    items = []
    stops=[]
    for line in stops_file:
        line = line.split("#")[0]
        if line:
            items = line.split(";")
            index = int(float(items[0]))
            while index + 1 > len(stops):
                stops.append(0)
            stops[index] = (index, items[1].strip(), items[2].strip(), float(items[3]), float(items[4])) # 0 index; 1 shortname; 2 longname; 3 x-coordinate; 4 y-coordinate
    return stops

def read_edges(edges_file):
    items = []
    edges = []
    for line in edges_file:
        line = line.split("#")[0]
        if line:
            items = line.split(";")
            index = int(float(items[0]))
            while index + 1 > len(edges):
                edges.append(0)
            edges[index] = (index, int(float(items[1])), int(float(items[2])), float(items[3]), float(items[4])) # 0 index; 1 left stop index; 2 right stop index; 3 length; 4 mintravelingtime
    return edges

def read_demand(demand_file):
    items = []
    demand = []
    for line in demand_file:
        line = line.split("#")[0]
        if line:
            items = line.split(";")
            index = int(float(items[0]))
            while index + 1 > len(demand):
                demand.append(0)
            demand[index] = (index, items[1].strip(), items[2].strip(), float(items[3]), float(items[4]), float(items[5])) # 0 index; 1 shortname; 2 longname; 3 x-coordinate; 4 y-coordinate; 5 num passengers
    return demand

def read_config(config_file, current_config):
    items = []
    for line in config_file:
        line = line.split("#")[0]
        if line:
            items = line.split(";")
            key = items[0]
            key = key.replace("\"","")
            value = items[1].strip()
            value = value.replace("\"","")
            current_config.update({key:value})

### THIS SHOULD BE REPLACED BY SOME CENTRAL CONFIG PYTHON CLASS SOME TIME IN THE FUTURE
config = {}
read_config(open('../Global-Config.cnf','r'),config)
read_config(open('basis/Config.cnf','r'),config)
if os.path.isfile('basis/Private-Config.cnf'):
    read_config(open('basis/Private-Config.cnf','r'), config)

####### READ INPUT

if os.path.isfile(config['default_stops_file']):
    stops = read_stops(open(config['default_stops_file'],'r'))
elif os.path.isfile(config['default_existing_stop_file']):
    stops = read_stops(open(config['default_existing_stop_file'],'r'))
else:
    print("No valid stops file found.")
    sys.exit(1)

if os.path.isfile(config['default_edges_file']):
    edges = read_stops(open(config['default_edges_file'],'r'))
elif os.path.isfile(config['default_existing_edge_file']):
    edges = read_stops(open(config['default_existing_edge_file'],'r'))
else:
    print("No valid edges file found.")
    sys.exit(1)

if os.path.isfile(config['default_demand_file']):
    demand = read_demand(open(config['default_demand_file'],'r'))
else:
    demand = []

######## DONE READING INPUT, SETUP GRAPH


G = nx.Graph()
node_labels = {}
edge_labels = {}

# add stop nodes to graph
if bool(stops):
    for node in stops:
        if not node == 0:
            node_labels.update({str(node[0]): node[1]})
            G.add_node(str(node[0]), pos=(node[3], node[4]))

# add demand nodes to graph
if bool(demand):
    for demand_node in demand:
        if not demand_node == 0:
            node_labels.update({'d'+str(demand_node[0]): str(demand_node[1])})
            G.add_node('d'+str(demand_node[0]), pos=(demand_node[3],demand_node[4]))

# add edges to graph
if bool(edges):
    for edge in edges:
        if not edge == 0:
            edge_labels.update({(edge[1],edge[2]):str(edge[0])})
            G.add_edge(edge[1],edge[2])

# get node positions
pos=nx.get_node_attributes(G,'pos')

# draw node_labels separately, so arbitrary node_labels could be drawn
nx.draw(G,pos,with_node_labels=False)
nx.draw_networkx_labels(G,pos,node_labels)
nx.draw_networkx_edge_labels(G,pos,edge_labels=edge_labels)

# create folder if not existing
if not os.path.isdir('graphics'):
    os.makedirs('graphics')

# save picture. note that any dot file type is replaced by png
plt.savefig(config['default_ptn_graph_file'].replace("dot","png"))

print("Success! You can find the graphics file under", config['default_ptn_graph_file'].replace("dot","png"),".")
