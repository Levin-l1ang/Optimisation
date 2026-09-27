from typing import Dict, Tuple


def read_line_names(filename: str) -> Dict[int, Tuple[str, str, str]]:
    names = {}
    with open(filename) as input_file:
        for line in input_file:
            line = line.split("#")[0].strip()
            if not line:
                continue
            values = line.split(";")
            line_id = int(values[0].strip())
            name = values[1].strip()
            names[line_id] = ("-".join(name.split("-")[:-2]), name.split("-")[-2], name.split("-")[-1])
    return names
