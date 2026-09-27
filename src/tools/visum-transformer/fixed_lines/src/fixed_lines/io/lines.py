from typing import Dict, Tuple

from core.io.csv import CsvWriter
from core.model.lines import Line


def write_line_capacities(line_capacities: Dict[Line, int], filename: str, header: str) -> None:
    CsvWriter.writeListStatic(filename,
                              list(line_capacities.keys()),
                              lambda l: [str(l.getId()), str(line_capacities[l])],
                              Line.getId,
                              header
                              )


def write_line_names(line_names: Dict[int, Tuple[str, str, str]], filename: str, header: str) -> None:
    CsvWriter.writeListStatic(filename,
                              list(line_names.keys()),
                              lambda l: [str(l), f"{line_names[l][0]}-{line_names[l][1]}-{line_names[l][2]}"],
                              lambda i: i,
                              header)
