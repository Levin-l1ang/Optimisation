import logging
import sys
import numpy as np
import networkx as nx
import random

from typing import Callable, Tuple
from core.util.config import Config

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException, ConfigInvalidValueException
from core.exceptions.input_exceptions import InputUnsupportedModalityCategoryException
from core.io.config import ConfigReader
from core.io.lines import LineWriter
from core.model.ptn import Link
from core.io.ptn import PTNReader
from core.io.od import ODReader
from core.model.lines import LinePool, Line
from core.model.impl.list_path import ListPath

from core.solver.solver_parameters import SolverParameters

from core.util.networkx import convert_graph_to_networkx
from core.util.networkx import convert_nxpath_to_listpath


logger = logging.getLogger(__name__)


def is_subpath(small, large):
    n = len(small)

    for i in range(len(large) - n + 1):
        if large[i:i+n] == small:
            return True

    return False


def path_equal(p1, p2, directed=True):
    if directed:
        return p1 == p2
    return p1 == p2 or p1 == p2[::-1]


if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    modality_category = config.getStringValue("modality_category")
    if modality_category.lower() != "line-based":
        logger.error("Can only create a line pool, if modality is of modality category line-based!")
        raise InputUnsupportedModalityCategoryException(modality_category)

    K = config.getIntegerValue("lpool_number_shortest_paths")
    undirected = config.getBooleanValue("ptn_is_undirected")
    directed = not undirected
    logger.info("Finished reading configuration")


    logger.info("Begin reading input data")

    ptn = PTNReader.read()
    od = ODReader.read()

    logger.info("Finished reading input data")


    logger.info("Begin generating linepool")

    # convert PTN to nx
    nxPTN = convert_graph_to_networkx(ptn, multi_graph=False, weight_function=lambda e: e.getLength())

    # get all kSP (for non-zero OD-pairs)
    all_k_paths = {}

    # for od_pair in od.getODPairs():
    for origin in ptn.getNodes():
        for destination in ptn.getNodes():
            if origin == destination:
                continue
            val = od.getValue(origin.getId(), destination.getId())
            if val > 0:
                path_generator = nx.shortest_simple_paths(nxPTN, origin.getId(), destination.getId(), weight="weight")
                paths = []
                try:
                    for _ in range(K):
                        paths.append(next(path_generator))
                except StopIteration:
                    # fewer than K paths exist
                    pass

                except nx.NetworkXNoPath:
                    # no path exists
                    continue

                # all_k_paths[od_pair] = paths
                all_k_paths[(origin.getId(), destination.getId())] = paths

    # get all paths as lintim paths
    all_paths: list[ListPath] = []
    for paths in all_k_paths.values():
        for path in paths:
            all_paths.append(convert_nxpath_to_listpath([ptn.getNode(n) for n in path], ptn))


    # find the non-contained paths

    # for saving the line paths
    maximal_paths = []
    # sort by number of edges (ascending)
    all_paths.sort(key=lambda x: len(x.getEdges()))
    # find non-contained paths
    for i, small_path in enumerate(all_paths):
        for large_path in all_paths[i+1:]:
            if large_path.contains(small_path):
                break
        else:
            # small path is not contained in any larger path
            maximal_paths.append(small_path)


    # build the linepool
    logger.debug("Initializing empty line pool")
    lpool = LinePool()
    line_id = 1

    for path in maximal_paths:
        length = sum([e.getLength() for e in path.getEdges()])
        line = Line(line_id, directed, length, line_path=path)
        lpool.addLine(line)
        line_id += 1

    logger.info("Finished generating linepool")

    logger.info("Begin writing output data")
    LineWriter.write(lpool, write_line_concept=False)
    logger.info("Finished writing output data")
