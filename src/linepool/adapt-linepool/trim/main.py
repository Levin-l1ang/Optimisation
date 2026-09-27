import logging
import sys

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException, ConfigInvalidValueException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.io.lines import LineReader, LineWriter
from core.model.lines import Line

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    vehicle_capacity = config.getIntegerValue("gen_passengers_per_vehicle")
    method = config.getStringValue("lpool_adapt_method")
    demand_factor = config.getDoubleValue("lpool_trim_capacity_factor")
    fix = config.getDoubleValue("lpool_costs_fixed")
    factor = config.getDoubleValue("lpool_costs_length")
    nb_edges_to_trim = config.getIntegerValue("lpool_nb_edges_to_trim")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    ptn = PTNReader.read(read_loads=(method.lower()=="trim_low_demand"))
    lpool = LineReader.read(ptn, read_frequencies=False)
    logger.info("Finished reading input data")


    logger.info("Begin adapting linepool")

    if method.lower() == "trim_all":
        current_id = len(lpool.getLines()) + 1
        for line in lpool.getLines():
            path = line.getLinePath()
            line_length = len(path.getEdges())
            for i in range(1, min(nb_edges_to_trim, line_length-1)+1):
                line = Line(current_id, path.isDirected())
                current_id += 1
                for link in path.getEdges()[:-i]:
                    line.addLink(link, compute_cost_and_length=True)
                line.setCost(fix + line.getLength()*factor)
                lpool.addLine(line)
                line = Line(current_id, path.isDirected())
                current_id += 1
                for link in path.getEdges()[i:]:
                    line.addLink(link, compute_cost_and_length=True)
                line.setCost(fix + line.getLength()*factor)
                lpool.addLine(line)
                if line_length -2*i >0:
                    line = Line(current_id, path.isDirected())
                    current_id += 1
                    for link in path.getEdges()[i:-i]:
                        line.addLink(link, compute_cost_and_length=True)
                    line.setCost(fix + line.getLength()*factor)
                    lpool.addLine(line)

    elif method.lower() == "trim_low_demand":
        current_id = len(lpool.getLines()) + 1
        for line in lpool.getLines():
            path = line.getLinePath()
            edges_to_add = []
            for edge in path.getEdges():
                if edge.getLoad() > demand_factor*vehicle_capacity:
                    edges_to_add.append(edge)
                else:
                    if len(edges_to_add) != 0:
                        line = Line(current_id, path.isDirected())

                        for link in edges_to_add:
                            line.addLink(link, compute_cost_and_length=True)

                        if all(not line.getLinePath() == otherline.getLinePath() for otherline in lpool.getLines()):
                            current_id += 1
                            line.setCost(fix + line.getLength()*factor)
                            lpool.addLine(line)
                            edges_to_add = []


    else:
        raise ConfigInvalidValueException("lpool_adapt_method")
    logger.info("Finished adapting linepool")


    logger.info("Begin writing output data")
    LineWriter.write(lpool, write_line_concept=False, write_costs=True)
    logger.info("Finished writing output data")