import logging
import sys

from core.io.config import ConfigReader
from core.io.ptn import PTNReader, PTNWriter

if __name__ == '__main__':
    logger = logging.getLogger(__name__)
    if len(sys.argv) != 2:
        logger.fatal("Program takes exactly one argument, the name of the config to read")
        exit(1)
    logging.info("Begin reading config")
    ConfigReader.read(sys.argv[1])
    logging.info("Finished reading config")

    logger.info("Begin reading input files")
    original_ptn = PTNReader.read(read_loads=True)
    forbidden_ptn = PTNReader.read(link_file_name="basis/Edge-forbidden.giv")
    logging.info("Finished reading input files")

    logging.info("Begin processing links")
    for link in forbidden_ptn.getEdges():
        original_link = original_ptn.getEdge(link.getId())
        original_link.setLowerFrequencyBound(0)
        original_link.setUpperFrequencyBound(0)
    logging.info("Finished processing PTN")

    logging.info("Begin writing output files")
    PTNWriter.write(original_ptn, write_stops=False, write_links=False, write_loads=True)
    logging.info("Finished writing output files")
    exit(0)