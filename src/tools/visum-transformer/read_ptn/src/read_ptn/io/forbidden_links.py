from typing import List

from core.io.csv import CsvWriter
from core.model.ptn import Link


def write_forbidden_links(forbidden_links: List[Link], filename: str, header: str) -> None:
    CsvWriter.writeListStatic(filename,
                              forbidden_links,
                              lambda l: l.toCsvStrings(),
                              Link.getId,
                              header
                              )
