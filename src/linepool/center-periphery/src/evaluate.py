import logging
from InstanceClass import Instance
import numpy as np

import csv
import os.path


logger = logging.getLogger(__name__)

def createCSVFile(path: str) -> None:
    header = [
        "p_centers",
        "p_periphery", 
        "p_od", 
        "center_radius", 
        "p_od_res", 
        "minimal_node_degree_for_center", 
        "min_times_edge_covered", 
        "centers",
        "Peripheries",
        "number of lines", 
        "cost",
        "line length [min, mean, max, var]",
        "line distance [min, mean, max, var]",
        "line edges [min, mean, max, var]",
        "times edges are covered [min, mean, max, var]",
        "feasible [before, inbetween, after] postprocessing"
    ]
    with open(path, "w", newline="") as f:
        writer = csv.writer(f, delimiter=";")
        writer.writerow(header)

def evaluateLinepool(path: str, instance: Instance) -> None:
    logger.debug("Entered evaluateLinepool")

    line_data = np.array([[line.getLength(), -1, len(line.getLinePath().getEdges())] for line in instance.linepool.getLines()])
    mins = np.min(line_data, 0)
    means = np.mean(line_data, 0)
    maxs = np.max(line_data, 0)
    variances = np.var(line_data, 0)

    edge_data = np.array([len(instance.edge_line_dict[edge]) for edge in instance.Edges])

    row = [
        instance.p_centers,
        instance.p_periphery, 
        instance.p_od_periphery, 
        instance.p_center_radius, 
        instance.p_od_lines, 
        instance.minimal_node_degree_for_center, 
        instance.min_times_edge_covered,
        [center.getId() for center in instance.Centers],             # centers
        [per.getId() for per in instance.Periphery],                 # Periphery

        instance.idx,                                                # number of lines
        None,                                               # cost
        [                                                   # line length [min, mean, max, var]
            mins[0],
            means[0],
            maxs[0],
            variances[0]
        ],
        [                                                   # line distance [min, mean, max, var]
            mins[1],
            means[1],
            maxs[1],
            variances[1]
        ],
        [                                                   # line edges [min, mean, max, var]
            mins[2],
            means[2],
            maxs[2],
            variances[2]
        ],
        [                                                   # times edges are covered [min, mean, max, var]
            np.min(edge_data),
            np.mean(edge_data),
            np.max(edge_data),
            np.var(edge_data)
        ],
        [                                                   # feasible
            instance.feasibility_before,                   
            "False",
            instance.feasibility_after
        ]                                            
    ]

    if not os.path.isfile(path):
        logger.warning(f"file at {path} does not exist yet, creating it")
        createCSVFile(path)

    with open(path, "w") as f:
        writer = csv.writer(f, delimiter=";")
        writer.writerow(row)
    
    logger.debug("Exiting evaluateLinepool")
