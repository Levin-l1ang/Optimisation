import logging
import sys
import os.path

from core.io.config import ConfigReader
from core.io.periodic_ean import PeriodicEANReader, PeriodicEANWriter

from event_activity_network import PeriodicEventActivityNetwork

from helper import Parameters
import solve

logger_ = logging.getLogger(__name__)

if __name__ == '__main__':
    logger_.info("Begin reading configuration.")
    if len(sys.argv) < 2:
        logger_.fatal("No config file name given to read!")
        exit(1)

    config = ConfigReader.read(sys.argv[1])
    parameters = Parameters(config)
    logger_.info("Finished reading configuration.")

    logger_.info("Begin reading input data.")
    if parameters.use_old_solution and os.path.isfile(parameters.timetable_file):
        graph, _ = PeriodicEANReader.read(read_timetable=True)
    elif parameters.use_old_solution:
        logger_.warning("use_old_solution is true, but no timetable exists. Continuing without starting solution.")
        parameters.use_old_solution = False
        graph, _ = PeriodicEANReader.read()
    else:
        graph, _ = PeriodicEANReader.read()

    ean = PeriodicEventActivityNetwork(graph=graph,
                                       period_length=parameters.period_length,
                                       weight_function=lambda x: (x.getUpperBound() - x.getLowerBound()))

    logger_.info("Finished reading input data.")

    logger_.info("Begin solving PESP with Cycle Base Formulation.")
    solve.solve(ean, parameters)
    logger_.info("Finished solving PESP with Cycle Base Formulation.")

    # calculating time
    ean.get_time_from_tensions()

    logger_.info("Begin writing output.")
    PeriodicEANWriter.write(ean=ean.graph, write_events=False, write_activities=False,
                            write_timetable=True)
    logger_.info("Finished writing output.")
