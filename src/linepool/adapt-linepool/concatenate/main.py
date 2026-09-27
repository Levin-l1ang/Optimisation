import logging
import sys
from copy import deepcopy

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.lines import LineWriter, LineReader
from core.io.ptn import PTNReader
from core.model.lines import LinePool, Line



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

    undirected = config.getBooleanValue("ptn_is_undirected")
    directed = not undirected
    keep_lines = config.getBooleanValue("lpool_concatenate_keep_lines")
    max_line_length = config.getIntegerValue("lpool_concatenate_max_length")
    logger.info("Finished reading configuration")


    logger.info("Begin reading input data")
    ptn = PTNReader.read()
    lpool = LineReader.read(ptn, read_frequencies=False)
    # TODO: ACHTUNG DATENSATZ MUSS GERICHTET SEIN!!!!!!!!
    logger.info("Finished reading input data")


    logger.info("Begin adapting linepool by concatenating lines")

    lines = lpool.getLines()
    lines.sort(key=lambda line: len(line.getLinePath().getEdges()))

    current_idx = 0

    max_line_id = max([l.getId() for l in lines]) +1

    # if concatenated lines should not be deleted, save them in this list and add them at the end back to the pool
    lines_to_keep = []

    while current_idx < len(lines):
        line = lines[current_idx]

        # search for a line that has the same start or end node and no other common nodes
        if directed:
            incident_lines = [l for l in lines if (l.getLinePath().getNodes()[0] == line.getLinePath().getNodes()[-1]
                                               or l.getLinePath().getNodes()[-1] == line.getLinePath().getNodes()[0])
                                               and len([n for n in line.getLinePath().getNodes() if n in l.getLinePath().getNodes()])==1]
        else:
            incident_lines = [l for l in lines if (l.getLinePath().getNodes()[0] == line.getLinePath().getNodes()[-1]
                                               or l.getLinePath().getNodes()[-1] == line.getLinePath().getNodes()[0]
                                               or l.getLinePath().getNodes()[0] == line.getLinePath().getNodes()[0]
                                               or l.getLinePath().getNodes()[-1] == line.getLinePath().getNodes()[-1])
                                               and len([n for n in line.getLinePath().getNodes() if n in l.getLinePath().getNodes()])==1]

        if len(incident_lines) == 0:
            current_idx += 1

        else:
            line2 = incident_lines[0]

            # test, if one of the lines is shorter than the max number of edges:
            if max_line_length < 0 or (len(line.getLinePath().getEdges()) <= max_line_length or len(line2.getLinePath().getEdges()) <= max_line_length):

                if keep_lines:
                    lines_to_keep.append(deepcopy(line))
                    lines_to_keep.append(deepcopy(line2))


                if line.getLinePath().getNodes()[0] == line2.getLinePath().getNodes()[-1]:
                    path = line2.getLinePath()
                    path.addLast(line.getLinePath().getEdges())
                    new_line = Line(max_line_id, directed, line.getLength()+line2.getLength(), line.getCost()+line2.getCost(), line_path = path)
                    max_line_id += 1
                elif line.getLinePath().getNodes()[-1] == line2.getLinePath().getNodes()[0]:
                    path = line.getLinePath()
                    path.addLast(line2.getLinePath().getEdges())
                    new_line = Line(max_line_id, directed, line.getLength()+line2.getLength(), line.getCost()+line2.getCost(), line_path = path)
                    max_line_id += 1
                # now the two cases for undirected lines
                elif line.getLinePath().getNodes()[0] == line2.getLinePath().getNodes()[0]:
                    path = line.getLinePath()
                    path.addFirst(reversed(line2.getLinePath().getEdges()))
                    new_line = Line(max_line_id, directed, line.getLength()+line2.getLength(), line.getCost()+line2.getCost(), line_path = path)
                    max_line_id += 1
                elif line.getLinePath().getNodes()[-1] == line2.getLinePath().getNodes()[-1]:
                    path = line2.getLinePath()
                    path.addLast(reversed(line.getLinePath().getEdges()))
                    new_line = Line(max_line_id, directed, line.getLength()+line2.getLength(), line.getCost()+line2.getCost(), line_path = path)
                    max_line_id += 1


                lines.remove(line)
                lines.remove(line2)
                lines.append(new_line)
                lines.sort(key=lambda li: len(li.getLinePath().getEdges()))

            else:
                # lines were to long
                current_idx += 1


    lpool = LinePool()
    for line in lines + lines_to_keep:
        lpool.addLine(line)


    logger.info("Finished adapting line pool")

    logger.info("Begin writing output data")
    LineWriter.write(lpool, write_line_concept=False)
    logger.info("Finished writing output data")
